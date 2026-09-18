package com.chatwaifu.mobile.ui.companion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.asr.AsrConfig
import com.chatwaifu.mobile.data.asr.AsrEngine
import com.chatwaifu.mobile.data.asr.AsrError
import com.chatwaifu.mobile.data.asr.AsrEvent
import com.chatwaifu.mobile.data.asr.AsrProvider
import com.chatwaifu.mobile.data.asr.AsrSession
import com.chatwaifu.mobile.data.asr.AsrSessionConfig
import com.chatwaifu.mobile.data.asr.AudioFocusGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Description: [CompanionVoiceInput] 的落地实现：[AsrEngine] + 权限 + 音频焦点 + 文案。
 *
 * 这一层是**唯一**同时接触这四样东西的地方，也是唯一需要 Android `Context` 的地方，
 * 所以 ViewModel 侧可以完全用 fake 测。它不认识 Sherpa —— engine 由
 * [AsrProvider] 给，换成 MNN 时这个文件一行不改。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
class AsrCompanionVoiceInput(
    context: Context,
    private val engine: AsrEngine = AsrProvider.engine(context),
    /** 发起权限申请。由宿主（Activity）注入，因为只有它能弹系统对话框。 */
    private val permissionRequester: () -> Unit,
) : CompanionVoiceInput {

    private val appContext = context.applicationContext
    private val focusGate = AudioFocusGate(appContext)

    /** 释放录音这条路径不能依赖 viewModelScope（它可能已经取消了） */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sessionMutex = Mutex()

    @Volatile
    private var activeSession: AsrSession? = null

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun requestPermission() = permissionRequester()

    override fun listen(): Flow<AsrEvent> = flow {
        // 权限在这里再查一次：从「点麦克风」到真正录音之间用户可能在系统设置里撤销了
        if (!hasPermission()) {
            emit(AsrEvent.Error(AsrError.PermissionDenied))
            emit(AsrEvent.Ended)
            return@flow
        }

        val prepared = engine.prepare(AsrConfig())
        prepared.exceptionOrNull()?.let { failure ->
            Log.w(TAG, "asr prepare failed", failure)
            emit(AsrEvent.Error(failure.toAsrError()))
            emit(AsrEvent.Ended)
            return@flow
        }

        val session = sessionMutex.withLock {
            // 开新的之前先把旧的关掉：engine 侧也有闸门，这里是第二道
            activeSession?.cancel()
            engine.startSession(AsrSessionConfig()).also { activeSession = it }
        }

        // 焦点只是给外部应用（音乐/视频）的协作信号；拿不到也继续录 ——
        // 用户明确按了麦克风。本应用自己的 TTS 冲突由 CompanionViewModel 显式处理
        focusGate.acquire()

        emitAll(session.events)
    }.onCompletion {
        // 正常结束、出错、被取消（页面退出/切角色）都会走到这里
        focusGate.release()
        sessionMutex.withLock { activeSession = null }
    }

    override suspend fun stop() {
        activeSession?.stop()
    }

    override suspend fun cancel() {
        val session = activeSession
        activeSession = null
        session?.cancel()
        focusGate.release()
    }

    override fun cancelBlocking() {
        val session = activeSession ?: run {
            focusGate.release()
            return
        }
        activeSession = null
        focusGate.release()
        // 自己的 scope，不受 viewModelScope 取消影响
        cleanupScope.launch { session.cancel() }
    }

    override fun describe(error: AsrError): String = appContext.getString(
        when (error) {
            AsrError.PermissionDenied -> R.string.asr_error_permission
            AsrError.ModelUnavailable -> R.string.asr_error_model_unavailable
            AsrError.InitializationFailed -> R.string.asr_error_init_failed
            AsrError.AudioRecordFailed -> R.string.asr_error_audio_record
            AsrError.RecognitionFailed -> R.string.asr_error_recognition
            AsrError.Timeout -> R.string.asr_error_timeout
            AsrError.NotPrepared -> R.string.asr_error_not_prepared
            AsrError.Unknown -> R.string.asr_error_unknown
        }
    )

    private companion object {
        const val TAG = "AsrCompanionVoiceInput"
    }
}

private fun Throwable.toAsrError(): AsrError =
    (this as? com.chatwaifu.mobile.data.asr.sherpa.AsrPrepareException)?.error
        ?: AsrError.InitializationFailed
