package com.chatwaifu.mobile.data.asr.mnn

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
import com.chatwaifu.mobile.data.asr.AsrPrepareException
import com.chatwaifu.mobile.data.asr.AsrSession
import com.chatwaifu.mobile.data.asr.AsrSessionConfig
import com.k2fsa.sherpa.mnn.ISherpaMnnAidlInterface
import com.k2fsa.sherpa.mnn.ISherpaMnnSessionCallback
import com.k2fsa.sherpa.mnn.SherpaMnnErrorCodes
import com.k2fsa.sherpa.mnn.SherpaMnnService
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
 * Description: [AsrEngine] 的 sherpa-mnn 实现。取代 Sherpa-ncnn 成为默认 runtime，
 * 也是工程里最后一处 ncnn 依赖的终点。
 *
 * **这个类是 Phase 2.8 那套抽象的验收单**：它和 `SherpaNcnnAsrEngine` 的结构
 * 几乎逐行对应（绑服务 → 翻 AIDL 回调成 [AsrEvent] → 翻错误码成 [AsrError]），
 * 而 Companion UI、语音输入状态机、权限流程、录音生命周期、ChatSession 接线
 * **一行都没有因为换引擎而改动** —— 只有 `AsrProvider` 里选实现那一行变了。
 *
 * 和 ncnn 版的真实差异只在 `:sherpa` 进程内部：推理从 ncnn 换成 MNN
 * （`libsherpa-mnn-jni.so` + `libMNN.so`），模型从 `.ncnn.bin` 换成 `.mnn`。
 * 见 [com.k2fsa.sherpa.mnn.SherpaMnnHelper]。
 *
 * Author: Voine
 * Date: 2026/10/13
 */
class MnnAsrEngine(context: Context) : AsrEngine {

    private val appContext = context.applicationContext

    /**
     * sherpa-mnn 的真实能力。**和 ncnn 版完全一致**，所以换引擎不会让 UI
     * 突然多出或少掉某种行为：
     * - streaming/partial：OnlineStream 每 20ms 就能给「当前完整文本」
     * - endpoint：`enableEndpoint` + 同样那三条规则（静音 2.4s / 有声后 1.4s / 硬顶 20s）
     * - offline：MNN 全本机，和网络无关
     * - punctuation：zipformer transducer 不吐标点
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
    private var remote: ISherpaMnnAidlInterface? = null

    @Volatile
    private var bound = false

    @Volatile
    private var prepared = false

    /** 等 onServiceConnected 的挂起点。bindService 是异步的，prepare 必须能等到它。 */
    private var connectionWaiter: CancellableContinuation<ISherpaMnnAidlInterface?>? = null

    /**
     * session id 单调递增。跨进程回调天然可能迟到，
     * 靠它 + [currentSessionId] 判断「这条回调还算不算数」。
     */
    private val sessionIds = AtomicLong(0L)
    private val currentSessionId = AtomicLong(NO_SESSION)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = ISherpaMnnAidlInterface.Stub.asInterface(service)
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
                SherpaMnnErrorCodes.INITIALIZATION_FAILED
            }
        return if (code == SherpaMnnErrorCodes.OK) {
            prepared = true
            Result.success(Unit)
        } else {
            Result.failure(AsrPrepareException(code.toAsrError()))
        }
    }

    private suspend fun connect(): ISherpaMnnAidlInterface? {
        remote?.let { return it }
        if (!bound) {
            val intent = Intent(appContext, SherpaMnnService::class.java)
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
        return MnnAsrSession(id, config)
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

    private inner class MnnAsrSession(
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

            val callback = object : ISherpaMnnSessionCallback.Stub() {
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
                val wav = config.debugWavPath
                if (wav != null) {
                    binder.startSessionFromWav(id, config.endpointDetection, wav, callback)
                } else {
                    binder.startSession(id, config.endpointDetection, callback)
                }
            }.getOrElse { e ->
                Log.e(TAG, "startSession call failed", e)
                SherpaMnnErrorCodes.AUDIO_RECORD_FAILED
            }
            if (code != SherpaMnnErrorCodes.OK) {
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
        private const val TAG = "MnnAsrEngine"
        private const val NO_SESSION = 0L
        private const val BIND_TIMEOUT_MS = 8_000L

        /**
         * partial 会以 20ms 的节奏来，UI 只关心最新一条，
         * 所以缓冲小、溢出丢最旧的（DROP_OLDEST）而不是挂起生产者 —— 生产者是 Binder 线程。
         */
        private const val BUFFER_CAPACITY = 16
    }
}

private fun Int.toAsrError(): AsrError = when (this) {
    SherpaMnnErrorCodes.MODEL_UNAVAILABLE -> AsrError.ModelUnavailable
    SherpaMnnErrorCodes.INITIALIZATION_FAILED -> AsrError.InitializationFailed
    SherpaMnnErrorCodes.AUDIO_RECORD_FAILED -> AsrError.AudioRecordFailed
    SherpaMnnErrorCodes.RECOGNITION_FAILED -> AsrError.RecognitionFailed
    SherpaMnnErrorCodes.NOT_PREPARED -> AsrError.NotPrepared
    SherpaMnnErrorCodes.PERMISSION_DENIED -> AsrError.PermissionDenied
    else -> AsrError.Unknown
}
