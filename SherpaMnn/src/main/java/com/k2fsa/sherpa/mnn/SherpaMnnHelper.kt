package com.k2fsa.sherpa.mnn

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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Description: sherpa-mnn 的录音 + 流式解码。跑在 `:sherpa` 进程里。
 *
 * 是 `SherpaHelper`（ncnn 版）的对等实现，**解码循环的结构刻意保持一致**：
 * 同样的 20ms 分帧、同样的「partial 是当前完整文本」语义、同样的
 * session id CAS 闸门。差别只在推理 API ——
 * ncnn 版是 `decodeSamples/isEndpoint/text` 三个方法直接作用在 recognizer 上；
 * MNN 版（移植自 sherpa-onnx）多了一层 [OnlineStream]，要自己驱动
 * `acceptWaveform → isReady → decode` 这个循环。
 *
 * 模型从 assets 读（`newFromAsset`），布局见 `assets/sherpa-mnn-streaming-zipformer-zh-en/`。
 *
 * Author: Voine
 * Date: 2026/10/13
 */
@SuppressLint("MissingPermission")
class SherpaMnnHelper(private val context: Context) {

    companion object {
        private const val TAG = "SherpaMnnHelper"

        private const val SAMPLE_RATE_IN_HZ = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

        /** 20ms 一帧。和 ncnn 版一致 —— 这个粒度下 partial 的更新频率对 UI 正好。 */
        private const val FRAME_INTERVAL_SECONDS = 0.02

        private const val NO_SESSION = 0L

        private const val MODEL_DIR = "sherpa-mnn-streaming-zipformer-zh-en"

        /** 等解码循环收尾的上限。超时就用已有 partial 兜底，不让 UI 卡住 */
        private const val STOP_WAIT_MS = 1500L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recognizer: OnlineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var activeSource: PcmSource? = null

    /** 任何时刻最多一个活跃 session；新的一来就把旧的顶掉 */
    private val activeSessionId = AtomicLong(NO_SESSION)
    private var recordJob: Job? = null

    /** 装载模型。幂等，失败返回错误码而不是抛。 */
    @Synchronized
    fun prepare(): Int {
        recognizer?.let { return SherpaMnnErrorCodes.OK }
        return try {
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE_IN_HZ, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$MODEL_DIR/encoder.mnn",
                        decoder = "$MODEL_DIR/decoder.mnn",
                        joiner = "$MODEL_DIR/joiner.mnn",
                    ),
                    tokens = "$MODEL_DIR/tokens.txt",
                    modelType = "zipformer",
                    numThreads = 2,
                ),
                // endpoint 规则抄 ncnn 版那份，换引擎不该顺带改变「一句话说完了」的判定
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 2.4f, 0.0f),
                    rule2 = EndpointRule(true, 1.4f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 20.0f),
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            )
            recognizer = OnlineRecognizer(context.assets, config)
            Log.i(TAG, "sherpa-mnn model prepared")
            SherpaMnnErrorCodes.OK
        } catch (e: Throwable) {
            // 异常信息里带模型路径，只进本进程日志，不跨 Binder
            Log.e(TAG, "prepare sherpa-mnn model failed", e)
            SherpaMnnErrorCodes.INITIALIZATION_FAILED
        }
    }

    @Synchronized
    fun startSession(
        sessionId: Long,
        endpointDetection: Boolean,
        callback: SessionCallback,
        /** 非 null = 从 WAV 读音频。只给验证用，正式链路传 null。 */
        wavPath: String? = null,
    ): Int {
        val engine = recognizer ?: return SherpaMnnErrorCodes.NOT_PREPARED

        stopRecordingLocked()

        val source = try {
            if (wavPath != null) WavFileSource(wavPath) else AudioRecordSource(ensureAudioRecordLocked())
        } catch (e: Throwable) {
            Log.e(TAG, "create pcm source failed", e)
            return SherpaMnnErrorCodes.AUDIO_RECORD_FAILED
        }

        return try {
            source.start()
            activeSource = source
            activeSessionId.set(sessionId)
            pendingFinalText = ""
            val exit = CountDownLatch(1)
            decodeExit = exit
            recordJob = scope.launch {
                try {
                    decodeLoop(sessionId, engine, source, endpointDetection, callback)
                } finally {
                    exit.countDown()
                }
            }
            callback.onListeningStarted(sessionId)
            SherpaMnnErrorCodes.OK
        } catch (e: Throwable) {
            Log.e(TAG, "start capture failed", e)
            if (wavPath == null) releaseAudioRecordLocked() else source.release()
            SherpaMnnErrorCodes.AUDIO_RECORD_FAILED
        }
    }

    /**
     * 解码循环。
     *
     * 和 ncnn 版的结构差异：这里要自己管 [OnlineStream] 的生命周期，并且
     * `acceptWaveform` 之后得把 `isReady` 为真的帧都 `decode` 掉 ——
     * recognizer 是按 chunk 推理的，不排空的话 partial 会越积越滞后。
     */
    private fun decodeLoop(
        sessionId: Long,
        engine: OnlineRecognizer,
        source: PcmSource,
        endpointDetection: Boolean,
        callback: SessionCallback,
    ) {
        val bufferSize = (FRAME_INTERVAL_SECONDS * SAMPLE_RATE_IN_HZ).toInt()
        val buffer = ShortArray(bufferSize)
        var lastPartial = ""
        val stream = engine.createStream()

        fun currentText(): String = runCatching { engine.getResult(stream).text }.getOrDefault(lastPartial)

        try {
            while (activeSessionId.get() == sessionId) {
                val read = source.read(buffer)
                // <0 = 源耗尽（WAV 放完）。麦克风不会走到这里
                if (read < 0) {
                    if (activeSessionId.compareAndSet(sessionId, NO_SESSION)) {
                        runCatching {
                            stream.inputFinished()
                            while (engine.isReady(stream)) engine.decode(stream)
                        }
                        val text = currentText()
                        synchronized(this) { stopRecordingLocked() }
                        callback.onFinalResult(sessionId, text)
                        callback.onEnded(sessionId)
                    }
                    return
                }
                if (read == 0) continue

                val samples = FloatArray(read) { buffer[it] / 32768.0f }
                stream.acceptWaveform(samples, SAMPLE_RATE_IN_HZ)
                while (engine.isReady(stream)) engine.decode(stream)

                if (activeSessionId.get() != sessionId) return

                val text = currentText()
                if (text.isNotBlank() && text != lastPartial) {
                    lastPartial = text
                    callback.onPartialResult(sessionId, text)
                }
                if (endpointDetection && engine.isEndpoint(stream)) {
                    callback.onEndpoint(sessionId)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "decode loop failed", e)
            if (activeSessionId.compareAndSet(sessionId, NO_SESSION)) {
                synchronized(this) { stopRecordingLocked() }
                callback.onError(sessionId, SherpaMnnErrorCodes.RECOGNITION_FAILED)
                callback.onEnded(sessionId)
            }
        } finally {
            pendingFinalText = currentText()
            runCatching { stream.release() }
        }
    }

    /**
     * 解码线程退出时的最后文本。`stopSession` 在**调用方线程**上跑，
     * 没法直接去问那个已经释放的 stream，所以由解码循环在退出前留下来。
     */
    @Volatile
    private var pendingFinalText: String = ""

    /** 解码循环退出的信号，[stopSession] 靠它拿到最终文本而不是中途的 partial */
    @Volatile
    private var decodeExit: CountDownLatch? = null

    /**
     * 正常停止：发 final + ended。
     *
     * 跑在 Binder 线程上，所以不能 `join()` 解码协程（那是挂起函数）。改成等那个
     * latch：解码循环退出前会在 finally 里写下 [pendingFinalText] 再放行，
     * 这样拿到的就是这一轮真正的最终文本，而不是上一帧的 partial。
     * 等不到也不阻塞太久 —— 超时就用已有的 partial 兜底。
     */
    fun stopSession(sessionId: Long, callback: SessionCallback) {
        if (!activeSessionId.compareAndSet(sessionId, NO_SESSION)) return
        val latch = decodeExit
        synchronized(this) { stopRecordingLocked() }
        runCatching { latch?.await(STOP_WAIT_MS, TimeUnit.MILLISECONDS) }
        callback.onFinalResult(sessionId, pendingFinalText)
        callback.onEnded(sessionId)
    }

    /** 取消：**不发 final**，只发 ended */
    fun cancelSession(sessionId: Long, callback: SessionCallback) {
        if (!activeSessionId.compareAndSet(sessionId, NO_SESSION)) return
        synchronized(this) { stopRecordingLocked() }
        callback.onEnded(sessionId)
    }

    @Synchronized
    fun release() {
        activeSessionId.set(NO_SESSION)
        stopRecordingLocked()
        releaseAudioRecordLocked()
        runCatching { recognizer?.release() }
        recognizer = null
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
            minBytes * 2,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord state=${record.state}" }
        audioRecord = record
        return record
    }

    private fun stopRecordingLocked() {
        recordJob?.cancel()
        recordJob = null
        activeSource?.let { source ->
            source.stop()
            // WAV 源一次性；麦克风源的 AudioRecord 要留着复用
            if (source is WavFileSource) source.release()
        }
        activeSource = null
    }

    private fun releaseAudioRecordLocked() {
        runCatching { audioRecord?.release() }
            .onFailure { Log.w(TAG, "release AudioRecord failed", it) }
        audioRecord = null
    }

    /** 和 ncnn 版同形状的回调，便于 app 侧共用同一套适配逻辑。 */
    interface SessionCallback {
        fun onListeningStarted(sessionId: Long)
        fun onPartialResult(sessionId: Long, text: String)
        fun onEndpoint(sessionId: Long)
        fun onFinalResult(sessionId: Long, text: String)
        fun onError(sessionId: Long, code: Int)
        fun onEnded(sessionId: Long)
    }
}
