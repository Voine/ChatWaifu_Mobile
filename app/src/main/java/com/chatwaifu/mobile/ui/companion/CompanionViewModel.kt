package com.chatwaifu.mobile.ui.companion

import com.chatwaifu.mobile.data.model.CharacterModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompanionRequest(
    val input: String,
    val isRetry: Boolean,
)

data class CompanionPreparation(
    val history: List<CompanionHistoryItem> = emptyList(),
    val warning: String? = null,
)

fun interface CompanionResponseDriver {
    fun responses(request: CompanionRequest): Flow<CompanionResponseEvent>

    suspend fun prepare(character: CharacterModel): CompanionPreparation = CompanionPreparation()

    fun onRendererReady() = Unit

    fun onRendererUnavailable() = Unit

    fun cancel() = Unit

    fun close() = Unit

    suspend fun awaitClosed() = Unit
}

class CompanionViewModel(
    private val responseDriver: CompanionResponseDriver,
    initialHistory: List<CompanionHistoryItem> = emptyList(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompanionUiState(history = initialHistory))
    val uiState = _uiState.asStateFlow()

    private var preparationJob: Job? = null
    private var responseJob: Job? = null
    private var overlayBeforeHistory = CompanionOverlayState.NONE
    private var lastFailedInput: String? = null
    private var conversationReady = true
    private var characterInitialized = false
    private var nextHistoryId = (initialHistory.maxOfOrNull { it.id } ?: 0L) + 1L

    val hasInitializedCharacter: Boolean
        get() = characterInitialized

    fun onEvent(event: CompanionEvent) {
        when (event) {
            CompanionEvent.OpenInput ->
                _uiState.update {
                    it.copy(
                        overlay = CompanionOverlayState.INPUT,
                        requestInputFocus = true,
                        notice = null,
                        error = null,
                    )
                }
            CompanionEvent.DismissInput ->
                _uiState.update {
                    it.copy(
                        overlay = if (it.overlay == CompanionOverlayState.INPUT) {
                            CompanionOverlayState.NONE
                        } else {
                            it.overlay
                        },
                        requestInputFocus = false,
                    )
                }
            is CompanionEvent.DraftChanged -> _uiState.update { it.copy(draft = event.value) }
            CompanionEvent.Submit -> submit()
            CompanionEvent.OpenHistory -> {
                overlayBeforeHistory = _uiState.value.overlay
                _uiState.update {
                    it.copy(
                        overlay = CompanionOverlayState.HISTORY,
                        historyExpanded = false,
                        requestInputFocus = false,
                    )
                }
            }
            CompanionEvent.DismissHistory ->
                _uiState.update { it.copy(overlay = overlayBeforeHistory, historyExpanded = false) }
            CompanionEvent.ToggleHistoryHeight ->
                _uiState.update { it.copy(historyExpanded = !it.historyExpanded) }
            CompanionEvent.Interact ->
                _uiState.update { it.copy(notice = "互动动作将在后续版本开放") }
            CompanionEvent.Vision ->
                _uiState.update { it.copy(notice = "视觉能力尚未接入，本演示不会申请额外权限") }
            CompanionEvent.DismissNotice -> _uiState.update { it.copy(notice = null) }
            CompanionEvent.RetryResponse -> retryResponse()
            CompanionEvent.RetryRenderer -> Unit
            CompanionEvent.More -> Unit
        }
    }

    fun setCharacter(id: String, name: String) {
        responseDriver.cancel()
        responseJob?.cancel()
        preparationJob?.cancel()
        characterInitialized = true
        conversationReady = true
        lastFailedInput = null
        _uiState.update {
            it.copy(
                characterId = id,
                characterName = name,
                runtime = CompanionRuntimeState.IDLE,
                overlay = CompanionOverlayState.NONE,
                renderer = RendererUiState.Loading,
                utterance = "",
                draft = "",
                requestInputFocus = false,
                notice = null,
                error = null,
                history = emptyList(),
            )
        }
    }

    fun prepareConversation(character: CharacterModel) {
        preparationJob?.cancel()
        conversationReady = false
        preparationJob = viewModelScope.launch {
            try {
                val preparation = responseDriver.prepare(character)
                nextHistoryId = (preparation.history.maxOfOrNull { it.id } ?: 0L) + 1L
                conversationReady = true
                _uiState.update {
                    it.copy(
                        history = preparation.history,
                        notice = preparation.warning,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        runtime = CompanionRuntimeState.ERROR,
                        error = CompanionErrorState(
                            message = e.message ?: "聊天链路初始化失败",
                            canRetry = false,
                        ),
                    )
                }
            }
        }
    }

    fun setRendererLoading() {
        _uiState.update { it.copy(renderer = RendererUiState.Loading) }
    }

    fun setRendererReady() {
        responseDriver.onRendererReady()
        _uiState.update { it.copy(renderer = RendererUiState.Ready) }
    }

    fun setRendererFailed(reason: String) {
        responseDriver.onRendererUnavailable()
        _uiState.update { it.copy(renderer = RendererUiState.Failed(reason)) }
    }

    private fun submit() {
        val current = _uiState.value
        val input = current.draft.trim()
        if (input.isEmpty()) return
        if (!conversationReady) {
            _uiState.update { it.copy(notice = "聊天链路正在准备，请稍候") }
            return
        }
        if (responseJob?.isActive == true) {
            _uiState.update { it.copy(notice = "正在准备回复，请稍候") }
            return
        }

        lastFailedInput = null
        appendHistory(CompanionAuthor.USER, input)
        startResponse(input = input, isRetry = false)
    }

    private fun retryResponse() {
        val input = lastFailedInput ?: return
        if (!conversationReady || responseJob?.isActive == true) return
        startResponse(input = input, isRetry = true)
    }

    private fun startResponse(input: String, isRetry: Boolean) {
        _uiState.update {
            it.copy(
                runtime = CompanionRuntimeState.THINKING,
                overlay = CompanionOverlayState.NONE,
                utterance = input,
                draft = "",
                requestInputFocus = false,
                notice = null,
                error = null,
            )
        }
        responseJob = viewModelScope.launch {
            var assistantHistoryId: Long? = null
            try {
                responseDriver.responses(CompanionRequest(input, isRetry)).collect { response ->
                    when (response) {
                        is CompanionResponseEvent.Streaming -> {
                            assistantHistoryId = upsertAssistantHistory(
                                assistantHistoryId,
                                response.text,
                            )
                            _uiState.update {
                                it.copy(
                                    runtime = CompanionRuntimeState.THINKING,
                                    utterance = response.text,
                                    error = null,
                                )
                            }
                        }

                        is CompanionResponseEvent.Reply -> {
                            assistantHistoryId = upsertAssistantHistory(
                                assistantHistoryId,
                                response.text,
                            )
                            _uiState.update {
                                it.copy(
                                    runtime = CompanionRuntimeState.SPEAKING,
                                    utterance = response.text,
                                    error = null,
                                )
                            }
                        }

                        CompanionResponseEvent.Finished -> {
                            lastFailedInput = null
                            _uiState.update {
                                it.copy(runtime = CompanionRuntimeState.IDLE, error = null)
                            }
                        }

                        is CompanionResponseEvent.Failed -> handleResponseFailure(input, response.reason)

                        is CompanionResponseEvent.TtsFailed -> {
                            lastFailedInput = null
                            _uiState.update {
                                it.copy(
                                    runtime = CompanionRuntimeState.IDLE,
                                    notice = response.reason,
                                    error = null,
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                handleResponseFailure(input, e.message ?: "聊天请求失败")
            }
        }
    }

    private fun handleResponseFailure(input: String, reason: String) {
        lastFailedInput = input
        _uiState.update {
            it.copy(
                runtime = CompanionRuntimeState.ERROR,
                overlay = CompanionOverlayState.NONE,
                notice = null,
                error = CompanionErrorState(reason, canRetry = true),
            )
        }
    }

    private fun appendHistory(author: CompanionAuthor, text: String): Long {
        val id = nextHistoryId++
        val item = CompanionHistoryItem(
            id = id,
            author = author,
            text = text,
            time = "刚刚",
        )
        _uiState.update { it.copy(history = it.history + item) }
        return id
    }

    private fun upsertAssistantHistory(historyId: Long?, text: String): Long {
        if (historyId == null) {
            return appendHistory(CompanionAuthor.ASSISTANT, text)
        }
        _uiState.update { state ->
            state.copy(
                history = state.history.map { item ->
                    if (item.id == historyId) item.copy(text = text) else item
                }
            )
        }
        return historyId
    }

    override fun onCleared() {
        responseDriver.cancel()
        preparationJob?.cancel()
        responseJob?.cancel()
        responseDriver.close()
    }

    fun releaseConversation() {
        responseDriver.cancel()
        preparationJob?.cancel()
        responseJob?.cancel()
        responseDriver.close()
    }

    suspend fun awaitConversationRelease() {
        responseDriver.awaitClosed()
    }

    fun onRendererUnavailable() {
        responseDriver.onRendererUnavailable()
    }

    companion object {
        fun factory(
            responseDriver: CompanionResponseDriver,
            initialHistory: List<CompanionHistoryItem> = emptyList(),
        ): ViewModelProvider.Factory = factory(
            responseDriverFactory = { responseDriver },
            initialHistory = initialHistory,
        )

        fun factory(
            responseDriverFactory: () -> CompanionResponseDriver,
            initialHistory: List<CompanionHistoryItem> = emptyList(),
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CompanionViewModel(responseDriverFactory(), initialHistory) as T
        }
    }
}
