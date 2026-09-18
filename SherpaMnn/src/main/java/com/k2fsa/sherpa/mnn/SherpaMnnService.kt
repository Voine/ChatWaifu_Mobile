package com.k2fsa.sherpa.mnn

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log

/**
 * Description: sherpa-mnn 的 Remote Service。跑在 `:sherpa` 进程。
 *
 * 这一层只做 AIDL ↔ [SherpaMnnHelper] 的转接，不含任何识别逻辑。
 * 跨进程回调可能因为客户端已死而抛 [RemoteException]，全部吞掉并让 session 失效 ——
 * 让服务因为客户端退出而崩掉是没有意义的。
 *
 * Author: Voine
 * Date: 2026/10/13
 */
class SherpaMnnService : Service() {
    companion object {
        private const val TAG = "SherpaMnnService"
    }

    private val sherpaHelper by lazy {
        SherpaMnnHelper(this)
    }

    /** 把 AIDL 的 Stub 包成 helper 认识的回调，并吞掉客户端已死导致的 RemoteException。 */
    private class RemoteSessionCallback(
        private val remote: ISherpaMnnSessionCallback,
    ) : SherpaMnnHelper.SessionCallback {

        private inline fun safely(block: () -> Unit) {
            try {
                block()
            } catch (e: RemoteException) {
                // 客户端进程没了。session 会在下一帧因为 id 变化自然结束
                Log.w(TAG, "asr client is gone, dropping callback", e)
            }
        }

        override fun onListeningStarted(sessionId: Long) =
            safely { remote.onListeningStarted(sessionId) }

        override fun onPartialResult(sessionId: Long, text: String) =
            safely { remote.onPartialResult(sessionId, text) }

        override fun onEndpoint(sessionId: Long) = safely { remote.onEndpoint(sessionId) }

        override fun onFinalResult(sessionId: Long, text: String) =
            safely { remote.onFinalResult(sessionId, text) }

        override fun onError(sessionId: Long, code: Int) = safely { remote.onError(sessionId, code) }

        override fun onEnded(sessionId: Long) = safely { remote.onEnded(sessionId) }
    }

    /** sessionId → 回调。stop/cancel 时要用同一个回调对象，否则结果发不回去。 */
    private val callbacks = mutableMapOf<Long, RemoteSessionCallback>()

    private val binder: ISherpaMnnAidlInterface.Stub = object : ISherpaMnnAidlInterface.Stub() {

        override fun prepare(): Int = sherpaHelper.prepare()

        override fun startSession(
            sessionId: Long,
            endpointDetection: Boolean,
            callback: ISherpaMnnSessionCallback?,
        ): Int {
            callback ?: return SherpaMnnErrorCodes.UNKNOWN
            val wrapped = RemoteSessionCallback(callback)
            synchronized(callbacks) {
                // 只保留当前这一个：老 session 已经被 helper 顶掉了，留着只会泄漏
                callbacks.clear()
                callbacks[sessionId] = wrapped
            }
            val code = sherpaHelper.startSession(sessionId, endpointDetection, wrapped)
            if (code != SherpaMnnErrorCodes.OK) {
                synchronized(callbacks) { callbacks.remove(sessionId) }
            }
            return code
        }

        override fun startSessionFromWav(
            sessionId: Long,
            endpointDetection: Boolean,
            wavPath: String?,
            callback: ISherpaMnnSessionCallback?,
        ): Int {
            callback ?: return SherpaMnnErrorCodes.UNKNOWN
            wavPath ?: return SherpaMnnErrorCodes.UNKNOWN
            val wrapped = RemoteSessionCallback(callback)
            synchronized(callbacks) {
                callbacks.clear()
                callbacks[sessionId] = wrapped
            }
            val code = sherpaHelper.startSession(
                sessionId = sessionId,
                endpointDetection = endpointDetection,
                callback = wrapped,
                wavPath = wavPath,
            )
            if (code != SherpaMnnErrorCodes.OK) {
                synchronized(callbacks) { callbacks.remove(sessionId) }
            }
            return code
        }

        override fun stopSession(sessionId: Long) {
            val wrapped = synchronized(callbacks) { callbacks.remove(sessionId) } ?: return
            sherpaHelper.stopSession(sessionId, wrapped)
        }

        override fun cancelSession(sessionId: Long) {
            val wrapped = synchronized(callbacks) { callbacks.remove(sessionId) } ?: return
            sherpaHelper.cancelSession(sessionId, wrapped)
        }

    }

    override fun onDestroy() {
        synchronized(callbacks) { callbacks.clear() }
        sherpaHelper.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder = binder
}
