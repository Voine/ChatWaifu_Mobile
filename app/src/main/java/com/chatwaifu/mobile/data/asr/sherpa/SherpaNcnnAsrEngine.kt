package com.chatwaifu.mobile.data.asr.sherpa

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.chatwaifu.mobile.data.asr.AsrCapabilities
import com.chatwaifu.mobile.data.asr.AsrConfig
import com.chatwaifu.mobile.data.asr.AsrEngine
import com.chatwaifu.mobile.data.asr.AsrError
import com.chatwaifu.mobile.data.asr.AsrEvent
import com.chatwaifu.mobile.data.asr.AsrSession
import com.chatwaifu.mobile.data.asr.AsrSessionConfig
import com.k2fsa.sherpa.ncnn.ISherpaAidlInterface
import com.k2fsa.sherpa.ncnn.ISherpaSessionCallback
import com.k2fsa.sherpa.ncnn.SherpaAsrErrorCodes
import com.k2fsa.sherpa.ncnn.SherpaService
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Description: [AsrEngine] 的 Sherpa-ncnn 实现。**唯一知道 AIDL 和 `:sherpa` 进程的地方。**
 *
 * 这一层不做识别，只做三件事：
 * 1. 绑 [SherpaService]（ASR 跑在独立进程，因为多个 ncnn 库像是进程独享资源）
 * 2. 把 AIDL 回调翻成 [AsrEvent]
 * 3. 把 Sherpa 的错误码翻成 [AsrError]，**原始异常和模型路径不出这个进程**
 *
 * 模型推理一行没重写，native 层也没动 —— 只是把 `SherpaHelper` 从「一次性 finishRecord」
 * 改成「按 session 发 partial」，见那个类的 KDoc。
 *
 * 上层拿到的是接口；换 MNN 时新写一个 `MnnAsrEngine : AsrEngine` 即可，
 * Companion 侧不需要任何改动（见 [com.chatwaifu.mobile.data.asr.AsrEngineFactory]）。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
class SherpaNcnnAsrEngine(context: Context) : AsrEngine {

    private val appContext = context.applicationContext

    /**
     * Sherpa 的真实能力，**不是许愿单**：
     * - streaming/partial：recognizer 每 20ms 就能给「当前完整文本」
     * - endpoint：`DecoderConfig.enableEndpoint` + 三条规则（静音 2.4s / 有声后 1.4s / 硬顶 20s）
     * - offline：ncnn 全本机，和网络无关
     * - punctuation：这个 transducer 模型不吐标点
     */
    override val capabilities = AsrCapabilities(
        streaming = true,
        partialResult = true,
        endpointDetection = true,
        offline = true,
        punctuation = false,
    )

    private val bindMutex = Mutex()

    @Volatile
    private var remote: ISherpaAidlInterface? = null

    @Volatile
    private var bound = false

    @Volatile
    private var prepared = false

    /** 等 onServiceConnected 的挂起点。bindService 是异步的，prepare 必须能等到它。 */
    private var connectionWaiter: CancellableContinuation<ISherpaAidlInterface?>? = null

    /**
     * session id 单调递增。跨进程回调天然可能迟到，
     * 靠它 + [currentSessionId] 判断「这条回调还算不算数」。
     */
    private val sessionIds = AtomicLong(0L)
    private val currentSessionId = AtomicLong(NO_SESSION)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = ISherpaAidlInterface.Stub.asInterface(service)
            remote = binder
            connectionWaiter?.takeIf { it.isActive }?.resume(binder)
            connectionWaiter = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // :sherpa 进程被系统回收了。下次 prepare 会重新绑并重新装模型
            remote = null
            prepared = false
            currentSessionId.set(NO_SESSION)
        }

        override fun onBindingDied(name: ComponentName?) {
            onServiceDisconnected(name)
        }
    }

    override suspend fun prepare(config: AsrConfig): Result<Unit> = bindMutex.withLock {
        if (prepared && remote != null) return Result.success(Unit)
        val binder = connect() ?: return Result.failure(
            AsrPrepareException(AsrError.InitializationFailed)
        )
        val code = runCatching { binder.prepare() }
            .getOrElse { e ->
                Log.e(TAG, "prepare call failed", e)
                SherpaAsrErrorCodes.INITIALIZATION_FAILED
            }
        return if (code == SherpaAsrErrorCodes.OK) {
            prepared = true
            Result.success(Unit)
        } else {
            Result.failure(AsrPrepareException(code.toAsrError()))
        }
    }

    private suspend fun connect(): ISherpaAidlInterface? {
        remote?.let { return it }
        if (!bound) {
            val intent = Intent(appContext, SherpaService::class.java)
            bound = runCatching {
                appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }.getOrElse {
                Log.e(TAG, "bindService failed", it)
                false
            }
            if (!bound) return null
        }
        // 绑定是异步的；超时返回 null 让调用方走 InitializationFailed，而不是永久挂住
        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                remote?.let {
                    continuation.resume(it)
                    return@suspendCancellableCoroutine
                }
                connectionWaiter = continuation
                continuation.invokeOnCancellation { connectionWaiter = null }
            }
        }
    }

    override fun startSession(config: AsrSessionConfig): AsrSession {
        val id = sessionIds.incrementAndGet()
        return SherpaAsrSession(id, config)
    }

    override suspend fun release() = bindMutex.withLock {
        currentSessionId.set(NO_SESSION)
        prepared = false
        remote = null
        if (bound) {
            runCatching { appContext.unbindService(connection) }
                .onFailure { Log.w(TAG, "unbindService failed", it) }
            bound = false
        }
    }

    private inner class SherpaAsrSession(
        private val id: Long,
        private val config: AsrSessionConfig,
    ) : AsrSession {

        /**
         * 冷流：collect 才开始录音。
         *
         * `callbackFlow` 的 `awaitClose` 正好覆盖「collector 被取消」这条路径 ——
         * 页面退出 / 切角色 / ViewModel 清理都会走到那里，录音一定被释放。
         */
        override val events: Flow<AsrEvent> = callbackFlow {
            val binder = remote
            if (binder == null || !prepared) {
                trySend(AsrEvent.Error(AsrError.NotPrepared))
                trySend(AsrEvent.Ended)
                close()
                return@callbackFlow
            }

            // 开新 session 就作废旧的：迟到的回调靠这个 id 被挡掉
            currentSessionId.set(id)

            val callback = object : ISherpaSessionCallback.Stub() {
                /** 所有回调都先过这道闸：不是当前 session 的一律丢弃 */
                private fun isStale(sessionId: Long) =
                    sessionId != id || currentSessionId.get() != id

                override fun onListeningStarted(sessionId: Long) {
                    if (isStale(sessionId)) return
                    trySend(AsrEvent.ListeningStarted)
                }

                override fun onPartialResult(sessionId: Long, text: String?) {
                    if (isStale(sessionId)) return
                    text?.takeIf { it.isNotBlank() }?.let { trySend(AsrEvent.PartialResult(it)) }
                }

                override fun onEndpoint(sessionId: Long) {
                    if (isStale(sessionId)) return
                    trySend(AsrEvent.EndpointReached)
                }

                override fun onFinalResult(sessionId: Long, text: String?) {
                    if (isStale(sessionId)) return
                    trySend(AsrEvent.FinalResult(text.orEmpty()))
                }

                override fun onError(sessionId: Long, code: Int) {
                    // 错误即使在 stale 之后也要让流收尾，但只对本 session 生效
                    if (sessionId != id) return
                    trySend(AsrEvent.Error(code.toAsrError()))
                }

                override fun onEnded(sessionId: Long) {
                    if (sessionId != id) return
                    trySend(AsrEvent.Ended)
                    close()
                }
            }
            val code = runCatching {
                binder.startSession(id, config.endpointDetection, callback)
            }.getOrElse { e ->
                Log.e(TAG, "startSession call failed", e)
                SherpaAsrErrorCodes.AUDIO_RECORD_FAILED
            }
            if (code != SherpaAsrErrorCodes.OK) {
                currentSessionId.compareAndSet(id, NO_SESSION)
                trySend(AsrEvent.Error(code.toAsrError()))
                trySend(AsrEvent.Ended)
                close()
                return@callbackFlow
            }

            awaitClose {
                // collector 走了（页面退出 / 切角色 / 超时）—— 录音必须停
                if (currentSessionId.compareAndSet(id, NO_SESSION)) {
                    runCatching { remote?.cancelSession(id) }
                        .onFailure { Log.w(TAG, "cancelSession on close failed", it) }
                }
            }
        }.buffer(BUFFER_CAPACITY, BufferOverflow.DROP_OLDEST)

        override suspend fun stop() {
            if (currentSessionId.get() != id) return
            runCatching { remote?.stopSession(id) }
                .onFailure { Log.w(TAG, "stopSession failed", it) }
        }

        override suspend fun cancel() {
            if (!currentSessionId.compareAndSet(id, NO_SESSION)) return
            runCatching { remote?.cancelSession(id) }
                .onFailure { Log.w(TAG, "cancelSession failed", it) }
        }
    }

    companion object {
        private const val TAG = "SherpaNcnnAsrEngine"
        private const val NO_SESSION = 0L
        private const val BIND_TIMEOUT_MS = 8_000L

        /**
         * partial 会以 20ms 的节奏来，UI 只关心最新一条，
         * 所以缓冲小、溢出丢最旧的（DROP_OLDEST）而不是挂起生产者 —— 生产者是 Binder 线程。
         */
        private const val BUFFER_CAPACITY = 16
    }
}

/** prepare 失败时塞进 [Result] 的载体。只是搬运 [AsrError]，不携带原始异常。 */
class AsrPrepareException(val error: AsrError) : Exception(error.toString())

private fun Int.toAsrError(): AsrError = when (this) {
    SherpaAsrErrorCodes.MODEL_UNAVAILABLE -> AsrError.ModelUnavailable
    SherpaAsrErrorCodes.INITIALIZATION_FAILED -> AsrError.InitializationFailed
    SherpaAsrErrorCodes.AUDIO_RECORD_FAILED -> AsrError.AudioRecordFailed
    SherpaAsrErrorCodes.RECOGNITION_FAILED -> AsrError.RecognitionFailed
    SherpaAsrErrorCodes.NOT_PREPARED -> AsrError.NotPrepared
    SherpaAsrErrorCodes.PERMISSION_DENIED -> AsrError.PermissionDenied
    else -> AsrError.Unknown
}
