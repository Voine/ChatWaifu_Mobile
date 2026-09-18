package com.chatwaifu.mobile.ui.companion

import com.chatwaifu.mobile.data.asr.AsrError
import com.chatwaifu.mobile.data.asr.AsrEvent
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
    /**
     * 语音输入。null = 这个宿主不提供语音（比如纯 mock 演示），
     * 此时麦克风按钮会给出提示而不是崩。
     */
    private val voiceInput: CompanionVoiceInput? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompanionUiState(history = initialHistory))
    val uiState = _uiState.asStateFlow()

    private var preparationJob: Job? = null
    private var responseJob: Job? = null
    private var voiceJob: Job? = null
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
            CompanionEvent.ToggleVoiceInput -> toggleVoiceInput()
            CompanionEvent.CancelVoiceInput -> cancelVoiceInput()
            is CompanionEvent.VoicePermissionResult -> onVoicePermissionResult(event)
            CompanionEvent.RetryRenderer -> Unit
            CompanionEvent.More -> Unit
        }
    }

    fun setCharacter(id: String, name: String) {
        // 切角色必须先把录音停掉：旧角色的迟到 partial 不能落进新角色的草稿
        teardownVoiceInput()
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
                voice = VoiceInputUiState(),
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

    // ---- 语音输入 ----

    /**
     * 点麦克风。录音中 = 正常停止（发 FinalResult）；否则 = 开始。
     * PREPARING 期间的重复点击直接忽略 —— 否则会开出第二个 session。
     */
    private fun toggleVoiceInput() {
        val voice = voiceInput ?: run {
            _uiState.update { it.copy(notice = "当前构建未接入语音输入") }
            return
        }
        when (_uiState.value.voice.state) {
            VoiceInputState.LISTENING -> stopVoiceInput()
            VoiceInputState.PREPARING -> Unit
            VoiceInputState.OFF -> {
                if (!voice.hasPermission()) {
                    // 权限属于 UI/platform 层，engine 不碰。这里只发起请求，
                    // 结果由宿主通过 VoicePermissionResult 回传
                    voice.requestPermission()
                    return
                }
                startVoiceInput()
            }
        }
    }

    private fun startVoiceInput() {
        val voice = voiceInput ?: return
        if (voiceJob?.isActive == true) return
        _uiState.update {
            it.copy(
                voice = it.voice.copy(
                    state = VoiceInputState.PREPARING,
                    partialText = "",
                    permissionBlocked = false,
                ),
                // 录音时把输入面板打开：partial 要写进同一个输入框，
                // 用户停止后就地可编辑，不需要额外的确认弹层
                overlay = CompanionOverlayState.INPUT,
                requestInputFocus = false,
                notice = null,
                error = null,
            )
        }
        voiceJob = viewModelScope.launch {
            // 角色正在说话时先把 TTS 停掉并等它真的停 —— 不让 AudioTrack 和
            // AudioRecord 无控制并发。复用 Phase 2.1 的取消语义，不重构 TTS
            if (_uiState.value.runtime == CompanionRuntimeState.SPEAKING) {
                responseDriver.cancel()
                responseJob?.cancel()
                _uiState.update { it.copy(runtime = CompanionRuntimeState.IDLE) }
            }
            try {
                voice.listen().collect { event -> onAsrEvent(event) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onVoiceFailure(voice.describe(AsrError.Unknown))
            }
        }
    }

    private fun onAsrEvent(event: AsrEvent) {
        val voice = voiceInput ?: return
        when (event) {
            AsrEvent.ListeningStarted -> _uiState.update {
                it.copy(
                    runtime = if (it.runtime == CompanionRuntimeState.IDLE) {
                        CompanionRuntimeState.LISTENING
                    } else {
                        it.runtime
                    },
                    voice = it.voice.copy(state = VoiceInputState.LISTENING),
                )
            }

            is AsrEvent.PartialResult -> _uiState.update {
                // partial 是「到目前为止的完整文本」，直接覆盖，不拼接。
                // 只更新同一个 draft/partial，不产生任何 history item、不写 Room
                it.copy(
                    draft = event.text,
                    voice = it.voice.copy(partialText = event.text),
                )
            }

            // endpoint 只是信号。**不自动停止、更不自动发送** ——
            // 识别可能出错，用户应该有一次修改机会（第八节）
            AsrEvent.EndpointReached -> Unit

            is AsrEvent.FinalResult -> _uiState.update {
                val text = event.text.ifBlank { it.voice.partialText }
                it.copy(
                    // 落到可编辑草稿，等用户点发送才进聊天链路
                    draft = text,
                    overlay = CompanionOverlayState.INPUT,
                    requestInputFocus = text.isNotBlank(),
                    notice = if (text.isBlank()) "没有听到内容，请再试一次" else it.notice,
                    voice = it.voice.copy(partialText = ""),
                )
            }

            is AsrEvent.Error -> onVoiceFailure(voice.describe(event.error))

            AsrEvent.Ended -> _uiState.update {
                it.copy(
                    runtime = if (it.runtime == CompanionRuntimeState.LISTENING) {
                        CompanionRuntimeState.IDLE
                    } else {
                        it.runtime
                    },
                    voice = it.voice.copy(state = VoiceInputState.OFF, partialText = ""),
                )
            }
        }
    }

    /** ASR 出错只是一条提示：**不写历史、不进 ERROR 态**（那是聊天请求失败才用的）。 */
    private fun onVoiceFailure(message: String) {
        _uiState.update {
            it.copy(
                runtime = if (it.runtime == CompanionRuntimeState.LISTENING) {
                    CompanionRuntimeState.IDLE
                } else {
                    it.runtime
                },
                notice = message,
                voice = it.voice.copy(state = VoiceInputState.OFF, partialText = ""),
            )
        }
    }

    private fun stopVoiceInput() {
        viewModelScope.launch { voiceInput?.stop() }
    }

    /** 取消：丢弃已识别内容，草稿回到录音前的样子（也就是空）。 */
    private fun cancelVoiceInput() {
        voiceJob?.cancel()
        voiceJob = null
        viewModelScope.launch { voiceInput?.cancel() }
        _uiState.update {
            it.copy(
                runtime = if (it.runtime == CompanionRuntimeState.LISTENING) {
                    CompanionRuntimeState.IDLE
                } else {
                    it.runtime
                },
                draft = "",
                voice = it.voice.copy(state = VoiceInputState.OFF, partialText = ""),
            )
        }
    }

    private fun onVoicePermissionResult(event: CompanionEvent.VoicePermissionResult) {
        when {
            event.granted -> startVoiceInput()
            // 永久拒绝：只能引导去系统设置，再申请也不会弹窗
            !event.canAskAgain -> _uiState.update {
                it.copy(
                    notice = "麦克风权限已被拒绝，请在系统设置中开启后再使用语音输入",
                    voice = it.voice.copy(
                        state = VoiceInputState.OFF,
                        permissionBlocked = true,
                    ),
                )
            }
            else -> _uiState.update {
                it.copy(
                    notice = "需要麦克风权限才能使用语音输入",
                    voice = it.voice.copy(state = VoiceInputState.OFF),
                )
            }
        }
    }

    /** 页面退出 / 切角色 / 清理时统一收口，保证录音一定被释放。 */
    private fun teardownVoiceInput() {
        voiceJob?.cancel()
        voiceJob = null
        voiceInput?.let { voice ->
            // viewModelScope 可能已经被取消，所以取消录音不能依赖它
            voice.cancelBlocking()
        }
        _uiState.update {
            it.copy(voice = VoiceInputUiState())
        }
    }

    private fun submit() {
        val current = _uiState.value
        val input = current.draft.trim()
        if (input.isEmpty()) return
        // 还在录音就直接发送：先把 session 停掉，否则迟到的 final 会盖掉
        // 用户已经提交的文本
        if (current.voice.busy) {
            voiceJob?.cancel()
            voiceJob = null
            voiceInput?.cancelBlocking()
            _uiState.update { it.copy(voice = VoiceInputUiState()) }
        }
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
        teardownVoiceInput()
        responseDriver.cancel()
        preparationJob?.cancel()
        responseJob?.cancel()
        responseDriver.close()
    }

    fun releaseConversation() {
        teardownVoiceInput()
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
            voiceInput: CompanionVoiceInput? = null,
        ): ViewModelProvider.Factory = factory(
            responseDriverFactory = { responseDriver },
            initialHistory = initialHistory,
            voiceInputFactory = { voiceInput },
        )

        fun factory(
            responseDriverFactory: () -> CompanionResponseDriver,
            initialHistory: List<CompanionHistoryItem> = emptyList(),
            voiceInputFactory: () -> CompanionVoiceInput? = { null },
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CompanionViewModel(
                    responseDriverFactory(),
                    initialHistory,
                    voiceInputFactory(),
                ) as T
        }
    }
}
