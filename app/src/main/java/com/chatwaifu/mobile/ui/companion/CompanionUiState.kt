package com.chatwaifu.mobile.ui.companion

enum class CompanionRuntimeState {
    IDLE,
    THINKING,
    SPEAKING,
    ERROR,
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

data class CompanionUiState(
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
}

sealed interface CompanionResponseEvent {
    data class Streaming(val text: String) : CompanionResponseEvent
    data class Reply(val text: String) : CompanionResponseEvent
    data object Finished : CompanionResponseEvent
    data class Failed(val reason: String) : CompanionResponseEvent
    data class TtsFailed(val reason: String) : CompanionResponseEvent
}
