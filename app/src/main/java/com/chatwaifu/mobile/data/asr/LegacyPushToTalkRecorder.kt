package com.chatwaifu.mobile.data.asr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Description: 老聊天页「按住说话」的适配。
 *
 * 改造前它自己持有 Sherpa 的 `ISherpaAidlInterface` + `ServiceConnection`，
 * 是 ncnn 退场的最后一个障碍。现在改成**走和 Companion 同一个 [AsrEngine]** ——
 * 老页面的「按住-松手-一次性出结果」只是忽略 partial 的特例，
 * 不需要第二条 AIDL 链路。
 *
 * 保留这个类而不是直接让 ViewModel 用 AsrEngine：老页面没有协程作用域，
 * 也不处理权限和错误文案，这里替它把这些兜住。
 * **新代码不要用它**，用 [AsrEngine]。
 *
 * Author: Voine
 * Date: 2026/9/17（迁到 AsrEngine 于 2026/10/13）
 */
internal class LegacyPushToTalkRecorder(context: Context) {

    private val appContext = context.applicationContext
    private val engine: AsrEngine by lazy { AsrProvider.engine(appContext) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var session: AsrSession? = null
    private var listenJob: Job? = null

    /** 最近一次 partial/final。松手时直接取它，不用等跨进程回调绕回来。 */
    @Volatile
    private var latestText: String = ""

    /** 老接口是同步的，engine 的 prepare 是挂起的，这里提前预热。 */
    fun bind(@Suppress("UNUSED_PARAMETER") context: Context) {
        scope.launch {
            engine.prepare(AsrConfig())
                .onFailure { Log.e(TAG, "asr prepare failed", it) }
        }
    }

    fun unbind(@Suppress("UNUSED_PARAMETER") context: Context) {
        listenJob?.cancel()
        listenJob = null
        scope.launch { session?.cancel() }
        session = null
    }

    fun start() {
        if (listenJob?.isActive == true) return
        latestText = ""
        listenJob = scope.launch {
            // prepare 是幂等的；bind 时的预热没跑完也能在这里补上
            if (engine.prepare(AsrConfig()).isFailure) return@launch
            val newSession = engine.startSession(AsrSessionConfig())
            session = newSession
            newSession.events.collect { event ->
                when (event) {
                    is AsrEvent.PartialResult -> latestText = event.text
                    is AsrEvent.FinalResult -> latestText = event.text
                    is AsrEvent.Error -> Log.e(TAG, "asr failed: ${event.error}")
                    else -> Unit
                }
            }
        }
    }

    fun finish(onResult: (String?) -> Unit) {
        val current = session
        scope.launch {
            runCatching { current?.stop() }
                .onFailure { Log.e(TAG, "stop record failed", it) }
            // stop 之后 FinalResult 会从流里回来，给它一点时间落到 latestText；
            // 超时就用已有的 partial 兜底，不让老页面的回调悬着
            repeat(FINAL_WAIT_TICKS) {
                if (latestText.isNotBlank()) return@repeat
                kotlinx.coroutines.delay(FINAL_TICK_MS)
            }
            listenJob?.cancel()
            listenJob = null
            session = null
            onResult(latestText.ifBlank { null })
        }
    }

    private companion object {
        const val TAG = "LegacyPushToTalk"
        const val FINAL_WAIT_TICKS = 10
        const val FINAL_TICK_MS = 100L
    }
}
