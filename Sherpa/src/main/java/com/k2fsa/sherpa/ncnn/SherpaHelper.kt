package com.k2fsa.sherpa.ncnn

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Description: Sherpa-ncnn 的录音 + 流式解码。跑在 `:sherpa` 进程里。
 *
 * **AudioRecord 归这一层**：整个 app 进程都不碰 PCM。跨进程回传 PCM 只会多一次拷贝，
 * 而 ncnn 又像是进程独享资源，所以录音和推理放在同一个进程里最省事。
 *
 * 改造前的三个问题：
 * 1. 模型在 `init{}` 里构造 —— 在 Binder 线程上同步读上百 MB 权重，且失败只能崩
 * 2. `model.text` 每 20ms 就有值，但只往 `results` 里塞，`finishRecord` 才一次性回传，
 *    partial 能力被丢掉了
 * 3. `stopRecord` 无条件回调结果，没有「取消」这个语义
 *
 * 现在：模型懒加载且可失败（[prepare]），录音按 session 走并实时发 partial，
 * 取消和正常停止分开。**native 层和 [SherpaNcnn] 的 JNI 签名一行没改。**
 *
 * Author: Voine
 * Date: 2023/3/7（session 化改造于 2026/9/17）
 */
@SuppressLint("MissingPermission")
class SherpaHelper(val context: Context) {

    companion object {
        private const val TAG = "SherpaHelper"

        private const val SAMPLE_RATE_IN_HZ = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

        /** 20ms 一帧。和改造前一致 —— 这个粒度下 partial 的更新频率对 UI 正好。 */
        private const val FRAME_INTERVAL_SECONDS = 0.02

        private const val NO_SESSION = 0L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 模型和 AudioRecord 都懒建。null 表示还没 prepare 过。 */
    private var model: SherpaNcnn? = null
    private var audioRecord: AudioRecord? = null

    /**
     * 当前活跃 session。**任何时刻最多一个** —— 新 session 一来就把它顶掉，
     * 旧循环下一帧就会看到 id 变了并退出，所以迟到的 partial 不会串台。
     */
    private val activeSessionId = AtomicLong(NO_SESSION)
    private var recordJob: Job? = null

    /** 装载模型。幂等，失败返回错误码而不是抛。 */
    @Synchronized
    fun prepare(): Int {
        model?.let { return SherpaAsrErrorCodes.OK }
        return try {
            val config = getModelConfig(type = 1, useGPU = true)
                ?: return SherpaAsrErrorCodes.MODEL_UNAVAILABLE
            model = SherpaNcnn(
                assetManager = context.assets,
                modelConfig = config,
                decoderConfig = getDecoderConfig(enableEndpoint = true),
                fbankConfig = getFbankConfig(),
            )
            Log.i(TAG, "sherpa model prepared")
            SherpaAsrErrorCodes.OK
        } catch (e: Throwable) {
            // 异常信息里带模型绝对路径，只进本进程日志，不跨 Binder
            Log.e(TAG, "prepare sherpa model failed", e)
            SherpaAsrErrorCodes.INITIALIZATION_FAILED
        }
    }

    /**
     * 开一次带 partial 的录音。
     *
     * @return [SherpaAsrErrorCodes] 之一。非 OK 时不会有任何回调。
     */
    @Synchronized
    fun startSession(
        sessionId: Long,
        endpointDetection: Boolean,
        callback: SessionCallback,
    ): Int {
        val recognizer = model ?: return SherpaAsrErrorCodes.NOT_PREPARED

        // 先把上一轮停掉：不这么做的话两个循环会同时 read 同一个 AudioRecord
        stopRecordingLocked()

        val record = try {
            ensureAudioRecordLocked()
        } catch (e: Throwable) {
            Log.e(TAG, "create AudioRecord failed", e)
            return SherpaAsrErrorCodes.AUDIO_RECORD_FAILED
        }

        return try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord did not enter recording state")
                releaseAudioRecordLocked()
                return SherpaAsrErrorCodes.AUDIO_RECORD_FAILED
            }
            recognizer.reset()
            activeSessionId.set(sessionId)
            recordJob = scope.launch {
                decodeLoop(sessionId, recognizer, record, endpointDetection, callback)
            }
            callback.onListeningStarted(sessionId)
            SherpaAsrErrorCodes.OK
        } catch (e: Throwable) {
            Log.e(TAG, "start recording failed", e)
            releaseAudioRecordLocked()
            SherpaAsrErrorCodes.AUDIO_RECORD_FAILED
        }
    }

    /**
     * 解码循环。每帧读 20ms、喂 recognizer、把「当前完整文本」作为 partial 发出去。
     *
     * 每帧都重新比对 [activeSessionId]：被顶掉或取消之后立刻退出，不再发任何回调。
     */
    private fun decodeLoop(
        sessionId: Long,
        recognizer: SherpaNcnn,
        record: AudioRecord,
        endpointDetection: Boolean,
        callback: SessionCallback,
    ) {
        val bufferSize = (FRAME_INTERVAL_SECONDS * SAMPLE_RATE_IN_HZ).toInt()
        val buffer = ShortArray(bufferSize)
        var lastPartial = ""

        try {
            while (activeSessionId.get() == sessionId) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val samples = FloatArray(read) { buffer[it] / 32768.0f }
                recognizer.decodeSamples(samples)

                if (activeSessionId.get() != sessionId) return

                val text = recognizer.text
                if (text.isNotBlank() && text != lastPartial) {
                    lastPartial = text
                    callback.onPartialResult(sessionId, text)
                }
                // endpoint 只是信号：要不要因此停止录音由 app 侧决定
                if (endpointDetection && recognizer.isEndpoint()) {
                    callback.onEndpoint(sessionId)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "decode loop failed", e)
            if (activeSessionId.compareAndSet(sessionId, NO_SESSION)) {
                stopAudioRecordQuietly()
                callback.onError(sessionId, SherpaAsrErrorCodes.RECOGNITION_FAILED)
                callback.onEnded(sessionId)
            }
        }
    }

    /** 正常停止：发 final + ended */
    fun stopSession(sessionId: Long, callback: SessionCallback) {
        if (!activeSessionId.compareAndSet(sessionId, NO_SESSION)) {
            // 已经被取消或被新 session 顶掉了，什么都不该发
            return
        }
        val text = synchronized(this) {
            stopRecordingLocked()
            model?.let {
                it.inputFinished()
                it.text
            }.orEmpty()
        }
        callback.onFinalResult(sessionId, text)
        callback.onEnded(sessionId)
    }

    /** 取消：**不发 final**，只发 ended */
    fun cancelSession(sessionId: Long, callback: SessionCallback) {
        if (!activeSessionId.compareAndSet(sessionId, NO_SESSION)) return
        synchronized(this) { stopRecordingLocked() }
        callback.onEnded(sessionId)
    }

    /** 释放模型和 AudioRecord。之后要再用必须重新 [prepare]。 */
    @Synchronized
    fun release() {
        activeSessionId.set(NO_SESSION)
        stopRecordingLocked()
        releaseAudioRecordLocked()
        model = null
    }

    private fun ensureAudioRecordLocked(): AudioRecord {
        audioRecord?.let { return it }
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        require(minBytes > 0) { "getMinBufferSize returned $minBytes" }
        val record = AudioRecord(
            AUDIO_SOURCE,
            SAMPLE_RATE_IN_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            // 一个采样两字节，翻倍留够缓冲，和改造前一致
            minBytes * 2,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord state=${record.state}"
        }
        audioRecord = record
        return record
    }

    private fun stopRecordingLocked() {
        recordJob?.cancel()
        recordJob = null
        stopAudioRecordQuietly()
    }

    private fun stopAudioRecordQuietly() {
        val record = audioRecord ?: return
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        }.onFailure { Log.w(TAG, "stop AudioRecord failed", it) }
    }

    private fun releaseAudioRecordLocked() {
        runCatching { audioRecord?.release() }
            .onFailure { Log.w(TAG, "release AudioRecord failed", it) }
        audioRecord = null
    }

    /**
     * 回调抽象。**不直接吃 AIDL 的 Stub** —— 这样 helper 本身可以在
     * 不起 Service 的情况下被测试或复用。
     */
    interface SessionCallback {
        fun onListeningStarted(sessionId: Long)
        fun onPartialResult(sessionId: Long, text: String)
        fun onEndpoint(sessionId: Long)
        fun onFinalResult(sessionId: Long, text: String)
        fun onError(sessionId: Long, code: Int)
        fun onEnded(sessionId: Long)
    }

    // ---- 旧聊天页仍在用的按住说话接口。语义不变，内部走同一套 session ----

    private var legacySessionId = NO_SESSION
    private var legacyText: String = ""

    private val legacyCallback = object : SessionCallback {
        override fun onListeningStarted(sessionId: Long) = Unit
        override fun onPartialResult(sessionId: Long, text: String) {
            if (sessionId == legacySessionId) legacyText = text
        }
        override fun onEndpoint(sessionId: Long) = Unit
        override fun onFinalResult(sessionId: Long, text: String) {
            if (sessionId == legacySessionId) legacyText = text
        }
        override fun onError(sessionId: Long, code: Int) = Unit
        override fun onEnded(sessionId: Long) = Unit
    }

    fun startRecord() {
        if (prepare() != SherpaAsrErrorCodes.OK) return
        legacyText = ""
        legacySessionId = System.nanoTime()
        startSession(legacySessionId, endpointDetection = true, callback = legacyCallback)
    }

    fun stopRecord(recognizeCallback: (result: String) -> Unit) {
        val id = legacySessionId
        stopSession(id, legacyCallback)
        recognizeCallback.invoke(legacyText)
    }

    fun releaseRecord() = release()
}
