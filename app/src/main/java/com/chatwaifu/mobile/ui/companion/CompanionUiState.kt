package com.chatwaifu.mobile.ui.companion

enum class CompanionRuntimeState {
    IDLE,

    /**
     * 正在录音。**和 THINKING 分开**：Listening 期间没有任何请求在跑，
     * 用户随时可以取消，而 THINKING 是已经发出去的请求。
     */
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR,
}

/**
 * 语音输入的独立状态。不并进 [CompanionRuntimeState]：
 * 准备模型（[PREPARING]）时角色可能还在 Speaking，两者是正交的。
 */
enum class VoiceInputState {
    /** 没在用语音 */
    OFF,

    /** 正在装 ASR 模型 / 绑服务。此时再点麦克风应该被忽略而不是开第二个 session */
    PREPARING,

    /** 正在录音 */
    LISTENING,
}

enum class CompanionOverlayState {
    NONE,
    INPUT,
    HISTORY,
}

sealed interface RendererUiState {
    data object Loading : RendererUiState
    data object Ready : RendererUiState
    data class Failed(val reason: String) : RendererUiState
}

enum class CompanionAuthor {
    USER,
    ASSISTANT,
}

data class CompanionHistoryItem(
    val id: Long,
    val author: CompanionAuthor,
    val text: String,
    val time: String,
)

data class CompanionErrorState(
    val message: String,
    val canRetry: Boolean,
)

/**
 * 语音输入的 UI 状态。
 *
 * [partialText] 只用于「正在听」的即时反馈；它同时也被写进 draft，
 * 所以最终提交走的仍是普通输入那条路，不需要第二套提交链路。
 */
data class VoiceInputUiState(
    val state: VoiceInputState = VoiceInputState.OFF,
    val partialText: String = "",
    /** 权限被永久拒绝，需要引导去系统设置 */
    val permissionBlocked: Boolean = false,
) {
    val listening: Boolean get() = state == VoiceInputState.LISTENING
    val busy: Boolean get() = state != VoiceInputState.OFF
}

data class CompanionUiState(
    val characterId: String = "",
    val characterName: String = "Companion",
    val runtime: CompanionRuntimeState = CompanionRuntimeState.IDLE,
    val overlay: CompanionOverlayState = CompanionOverlayState.NONE,
    val renderer: RendererUiState = RendererUiState.Loading,
    val utterance: String = "",
    val draft: String = "",
    val requestInputFocus: Boolean = false,
    val history: List<CompanionHistoryItem> = emptyList(),
    val historyExpanded: Boolean = false,
    val notice: String? = null,
    val error: CompanionErrorState? = null,
    val voice: VoiceInputUiState = VoiceInputUiState(),
)

sealed interface CompanionEvent {
    data object OpenInput : CompanionEvent
    data object DismissInput : CompanionEvent
    data class DraftChanged(val value: String) : CompanionEvent
    data object Submit : CompanionEvent
    data object OpenHistory : CompanionEvent
    data object DismissHistory : CompanionEvent
    data object ToggleHistoryHeight : CompanionEvent
    data object RetryRenderer : CompanionEvent
    data object Interact : CompanionEvent
    data object Vision : CompanionEvent
    data object More : CompanionEvent
    data object DismissNotice : CompanionEvent
    data object RetryResponse : CompanionEvent

    /**
     * 点麦克风。**同一个事件既开始也停止**（第十节：只支持一种交互，
     * 不同时做点击和长按两套手势）：不在录音时开始，在录音时正常停止。
     */
    data object ToggleVoiceInput : CompanionEvent

    /** 用户主动取消录音，**丢弃已识别内容** */
    data object CancelVoiceInput : CompanionEvent

    /** 权限申请结果由 UI 层回传 —— 权限不归 AsrEngine 管 */
    data class VoicePermissionResult(
        val granted: Boolean,
        /** false 且未授予 = 永久拒绝，要引导去系统设置 */
        val canAskAgain: Boolean,
    ) : CompanionEvent
}

sealed interface CompanionResponseEvent {
    data class Streaming(val text: String) : CompanionResponseEvent
    data class Reply(val text: String) : CompanionResponseEvent
    data object Finished : CompanionResponseEvent
    data class Failed(val reason: String) : CompanionResponseEvent
    data class TtsFailed(val reason: String) : CompanionResponseEvent
}
