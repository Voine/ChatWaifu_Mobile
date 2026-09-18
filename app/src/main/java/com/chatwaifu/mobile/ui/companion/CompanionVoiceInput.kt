package com.chatwaifu.mobile.ui.companion

import com.chatwaifu.mobile.data.asr.AsrError
import com.chatwaifu.mobile.data.asr.AsrEvent
import kotlinx.coroutines.flow.Flow

/**
 * Description: [CompanionViewModel] 看到的语音输入能力。
 *
 * **为什么 ViewModel 不直接持 `AsrEngine`**：ViewModel 还需要两样 engine 不该管的东西 ——
 * 麦克风权限（属于 UI/platform 层）和错误文案（需要 Resources）。把这三件事收在
 * 一个窄接口后面，ViewModel 就既不认识 `AsrEngine` 的 prepare/session 生命周期，
 * 也不认识 Sherpa；测试里换个 fake 实现即可，不需要 Android context。
 *
 * 换 MNN ASR 时这个接口和它的实现都不用改 —— 只有
 * [com.chatwaifu.mobile.data.asr.AsrProvider] 里创建 engine 那一行会变。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
interface CompanionVoiceInput {

    /** 当前是否已授予 RECORD_AUDIO */
    fun hasPermission(): Boolean

    /**
     * 发起权限申请。结果由宿主通过
     * [CompanionEvent.VoicePermissionResult] 回传，**不在这里等**（等系统弹窗
     * 会把 ViewModel 卷进 Activity 生命周期）。
     */
    fun requestPermission()

    /**
     * 开一次录音。冷流，collect 才真正开始；collector 被取消即释放录音。
     *
     * 内部负责 prepare 引擎、拿音频焦点，并保证同一时刻只有一个活跃 session。
     */
    fun listen(): Flow<AsrEvent>

    /** 正常停止：会产出 [AsrEvent.FinalResult] */
    suspend fun stop()

    /** 取消：不产出 FinalResult */
    suspend fun cancel()

    /**
     * 不挂起的取消。给 `onCleared()` / 切角色这些**可能已经没有可用 scope**
     * 的路径用 —— 那时候 `viewModelScope` 已经取消，suspend 版本根本跑不起来。
     */
    fun cancelBlocking()

    /** 把 [AsrError] 翻成给用户看的文案。engine 不碰 Resources。 */
    fun describe(error: AsrError): String
}
