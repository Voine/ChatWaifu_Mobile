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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompanionRequest(
    val input: String,
    val isRetry: Boolean,
    /** 随这一轮发出去的附件（已导入落盘），见 [CompanionResponseDriver.ingestAttachment] */
    val attachments: List<PendingAttachment> = emptyList(),
)

/**
 * 一页历史，时间正序。
 *
 * [olderCursor] 是这一页里**最老那条记录的 Room id**，往前翻页时原样传回。
 * 不能用 [CompanionHistoryItem.id] 代替：UI 会滤掉空白行 / SYSTEM 行，
 * 拿过滤后的第一条当游标会把被滤掉的那几条反复取回来。
 */
data class CompanionHistoryPage(
    val items: List<CompanionHistoryItem> = emptyList(),
    val olderCursor: Long? = null,
    val hasOlder: Boolean = false,
)

data class CompanionPreparation(
    val history: CompanionHistoryPage = CompanionHistoryPage(),
    val warning: String? = null,
    /** 当前模型能收哪些附件。决定入口是打开选择器还是给提示 */
    val attachmentSupport: AttachmentSupport = AttachmentSupport.NONE,
)

/** 一次附件导入的结果 */
sealed interface AttachmentIngestResult {
    data class Success(val attachment: PendingAttachment) : AttachmentIngestResult

    /** [reason] 已经是给用户看的文案（太大 / 格式不支持 / 读不了） */
    data class Failed(val reason: String) : AttachmentIngestResult
}

fun interface CompanionResponseDriver {
    fun responses(request: CompanionRequest): Flow<CompanionResponseEvent>

    suspend fun prepare(character: CharacterModel): CompanionPreparation = CompanionPreparation()

    /**
     * 导入一个用户选的文件：探测、体积闸门、归一化、落盘。默认不支持（mock 宿主）。
     * [expected] 是用户在菜单里选的类型，选到别的类型时拒绝（相册入口两种都收，传 null）
     */
    suspend fun ingestAttachment(uri: String, expected: AttachmentType?): AttachmentIngestResult =
        AttachmentIngestResult.Failed("当前构建未接入附件")

    /** 用户在发送前把附件删了：把已经落盘的文件清掉，不留孤儿 */
    fun discardAttachment(attachment: PendingAttachment) = Unit

    /** 往前翻一页历史。[beforeId] 是上一页的 [CompanionHistoryPage.olderCursor] */
    suspend fun loadOlderHistory(beforeId: Long): CompanionHistoryPage = CompanionHistoryPage()

    fun onRendererReady() = Unit

    fun onRendererUnavailable() = Unit

    fun cancel() = Unit

    fun close() = Unit

    suspend fun awaitClosed() = Unit
}

/** 让宿主打开选择器。[type] 为 null = 相册（图片 + 视频） */
data class PickRequest(val type: AttachmentType?)

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

    /** 失败那一轮带的附件，重试时原样带上 */
    private var lastFailedAttachments: List<PendingAttachment> = emptyList()
    private var attachmentJob: Job? = null
    private var olderHistoryJob: Job? = null
    private var olderHistoryCursor: Long? = null

    /** 当前打开的选择器是为哪种类型开的。null = 相册（图片和视频都收） */
    private var pickingType: AttachmentType? = null

    /**
     * 宿主要打开选择器时发一次，值是要选的类型（null = 相册，图片 + 视频）。
     * 选择器只有 Activity 能开（和录音权限一样属于 platform 层），
     * ViewModel 只决定「该不该开」，结果通过 [CompanionEvent.AttachmentPicked] 回来。
     */
    private val _pickRequests = kotlinx.coroutines.flow.MutableSharedFlow<PickRequest>(extraBufferCapacity = 1)
    val pickRequests = _pickRequests.asSharedFlow()
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
            CompanionEvent.LoadOlderHistory -> loadOlderHistory()
            CompanionEvent.Interact ->
                _uiState.update { it.copy(notice = "互动动作将在后续版本开放") }
            CompanionEvent.Vision -> requestPicker(null)
            CompanionEvent.ToggleAttachMenu -> toggleAttachMenu()
            is CompanionEvent.PickAttachment -> requestPicker(event.type)
            is CompanionEvent.AttachmentPicked -> event.uri?.let(::importAttachment)
            is CompanionEvent.RemovePendingAttachment -> removePendingAttachment(event.relPath)
            CompanionEvent.DismissNotice -> _uiState.update { it.copy(notice = null) }
            CompanionEvent.RetryResponse -> retryResponse()
            CompanionEvent.ToggleVoiceInput -> toggleVoiceInput()
            CompanionEvent.CancelVoiceInput -> cancelVoiceInput()
            is CompanionEvent.VoicePermissionResult -> onVoicePermissionResult(event)
            CompanionEvent.More ->
                _uiState.update {
                    it.copy(overlay = CompanionOverlayState.MENU, requestInputFocus = false)
                }
            CompanionEvent.DismissMenu, is CompanionEvent.Navigate ->
                _uiState.update { it.closeOverlay(CompanionOverlayState.MENU) }
            CompanionEvent.StartAdjust ->
                _uiState.update {
                    it.copy(
                        overlay = CompanionOverlayState.ADJUST,
                        requestInputFocus = false,
                        notice = null,
                    )
                }
            CompanionEvent.FinishAdjust ->
                _uiState.update { it.closeOverlay(CompanionOverlayState.ADJUST) }
            CompanionEvent.RetryRenderer, CompanionEvent.ResetPose -> Unit
        }
    }

    /** 只关自己那一层：异步到达的事件不能把用户已经切到的别的覆盖层一起关掉 */
    private fun CompanionUiState.closeOverlay(target: CompanionOverlayState) =
        if (overlay == target) copy(overlay = CompanionOverlayState.NONE) else this

    fun setCharacter(id: String, name: String) {
        // 切角色必须先把录音停掉：旧角色的迟到 partial 不能落进新角色的草稿
        teardownVoiceInput()
        responseDriver.cancel()
        responseJob?.cancel()
        preparationJob?.cancel()
        characterInitialized = true
        conversationReady = true
        lastFailedInput = null
        lastFailedAttachments = emptyList()
        attachmentJob?.cancel()
        olderHistoryJob?.cancel()
        olderHistoryCursor = null
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
                hasOlderHistory = false,
                loadingOlderHistory = false,
                voice = VoiceInputUiState(),
                attachments = AttachmentUiState(),
            )
        }
    }

    fun prepareConversation(character: CharacterModel) {
        preparationJob?.cancel()
        conversationReady = false
        preparationJob = viewModelScope.launch {
            try {
                val preparation = responseDriver.prepare(character)
                nextHistoryId = (preparation.history.items.maxOfOrNull { it.id } ?: 0L) + 1L
                olderHistoryCursor = preparation.history.olderCursor
                conversationReady = true
                _uiState.update {
                    it.copy(
                        history = preparation.history.items,
                        hasOlderHistory = preparation.history.hasOlder,
                        notice = preparation.warning,
                        attachments = it.attachments.copy(
                            support = preparation.attachmentSupport,
                        ),
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

    // ---- 附件 ----

    private fun toggleAttachMenu() {
        val state = _uiState.value
        when {
            !conversationReady ->
                _uiState.update { it.copy(notice = "聊天链路正在准备，请稍候") }
            // 一样都收不了就别展开一个全是灰项的菜单，直接说清楚
            !state.attachments.support.any ->
                _uiState.update { it.copy(notice = "当前模型不支持附件，可以在设置里换一个支持视觉的模型") }
            else -> _uiState.update {
                it.copy(attachments = it.attachments.copy(pickerMenuOpen = !it.attachments.pickerMenuOpen))
            }
        }
    }

    /** [type] 为 null 是「视觉」入口：相册里图片和视频都收 */
    private fun requestPicker(type: AttachmentType?) {
        val attachments = _uiState.value.attachments
        _uiState.update { it.copy(attachments = it.attachments.copy(pickerMenuOpen = false)) }
        when {
            !conversationReady ->
                _uiState.update { it.copy(notice = "聊天链路正在准备，请稍候") }
            // 能力 gate 按模型算：不支持的类型给明确反馈，不打开选择器
            !attachments.support.canAccept(type ?: AttachmentType.IMAGE) ->
                _uiState.update { it.copy(notice = unsupportedNotice(type)) }
            attachments.importing -> Unit
            attachments.pending.size >= MAX_PENDING_ATTACHMENTS ->
                _uiState.update { it.copy(notice = "一次最多带 $MAX_PENDING_ATTACHMENTS 个附件") }
            else -> {
                // 「图片 / 视频」和「视觉」开的是同一个相册，两种都收
                pickingType = type.takeUnless { it == AttachmentType.IMAGE || it == AttachmentType.VIDEO }
                _pickRequests.tryEmit(PickRequest(type))
            }
        }
    }

    private fun unsupportedNotice(type: AttachmentType?): String = when (type) {
        AttachmentType.AUDIO -> "当前模型不支持听音频，可以在设置里换一个支持音频的模型（如 Gemini）"
        AttachmentType.DOC -> "当前模型不支持读 PDF，可以在设置里换一个支持文档的模型"
        else -> "当前模型不支持看图，可以在设置里换一个支持视觉的模型"
    }

    private fun importAttachment(uri: String) {
        if (attachmentJob?.isActive == true) return
        val expected = pickingType
        _uiState.update {
            it.copy(
                attachments = it.attachments.copy(importing = true),
                // 选完直接落到输入条上，用户可以接着打字
                overlay = CompanionOverlayState.INPUT,
                notice = null,
            )
        }
        attachmentJob = viewModelScope.launch {
            val result = try {
                responseDriver.ingestAttachment(uri, expected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AttachmentIngestResult.Failed(e.message ?: "文件读取失败")
            }
            _uiState.update {
                when (result) {
                    is AttachmentIngestResult.Success -> it.copy(
                        attachments = it.attachments.copy(
                            importing = false,
                            pending = it.attachments.pending + result.attachment,
                        ),
                    )
                    is AttachmentIngestResult.Failed -> it.copy(
                        attachments = it.attachments.copy(importing = false),
                        notice = result.reason,
                    )
                }
            }
        }
    }

    private fun removePendingAttachment(relPath: String) {
        val attachment = _uiState.value.attachments.pending.firstOrNull { it.relPath == relPath } ?: return
        responseDriver.discardAttachment(attachment)
        _uiState.update {
            it.copy(attachments = it.attachments.copy(pending = it.attachments.pending - attachment))
        }
    }

    // ---- 历史分页 ----

    private fun loadOlderHistory() {
        val cursor = olderHistoryCursor ?: return
        if (!_uiState.value.hasOlderHistory || olderHistoryJob?.isActive == true) return
        _uiState.update { it.copy(loadingOlderHistory = true) }
        olderHistoryJob = viewModelScope.launch {
            val page = try {
                responseDriver.loadOlderHistory(cursor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 翻页失败不算错误态：停在这里，下次滑到顶再试
                _uiState.update { it.copy(loadingOlderHistory = false) }
                return@launch
            }
            olderHistoryCursor = page.olderCursor ?: cursor
            _uiState.update { state ->
                // 按 id 去重：翻页期间刚好落库的消息不会出现两次
                val known = state.history.mapTo(HashSet()) { it.id }
                state.copy(
                    history = page.items.filter { it.id !in known } + state.history,
                    hasOlderHistory = page.hasOlder,
                    loadingOlderHistory = false,
                )
            }
        }
    }

    private fun submit() {
        val current = _uiState.value
        val input = current.draft.trim()
        val attachments = current.attachments.pending
        // 只发附件不说话也是合法的一轮
        if (input.isEmpty() && attachments.isEmpty()) return
        if (current.attachments.importing) {
            _uiState.update { it.copy(notice = "附件还在处理，请稍候") }
            return
        }
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
        appendHistory(CompanionAuthor.USER, input, attachments)
        _uiState.update {
            it.copy(attachments = it.attachments.copy(pending = emptyList(), pickerMenuOpen = false))
        }
        startResponse(input = input, isRetry = false, attachments = attachments)
    }

    private fun retryResponse() {
        val input = lastFailedInput ?: return
        if (!conversationReady || responseJob?.isActive == true) return
        startResponse(input = input, isRetry = true, attachments = lastFailedAttachments)
    }

    private fun startResponse(
        input: String,
        isRetry: Boolean,
        attachments: List<PendingAttachment> = emptyList(),
    ) {
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
                responseDriver.responses(CompanionRequest(input, isRetry, attachments)).collect { response ->
                    when (response) {
                        is CompanionResponseEvent.Streaming -> {
                            assistantHistoryId = upsertAssistantHistory(
                                assistantHistoryId,
                                response.text,
                            )
                            _uiState.update {
                                it.copy(
                                    // 已经开口了就别退回 THINKING：流水线下文本和语音是交错到达的
                                    runtime = if (it.runtime == CompanionRuntimeState.SPEAKING) {
                                        CompanionRuntimeState.SPEAKING
                                    } else {
                                        CompanionRuntimeState.THINKING
                                    },
                                    utterance = response.text,
                                    error = null,
                                )
                            }
                        }

                        CompanionResponseEvent.VoiceStarted -> _uiState.update {
                            it.copy(runtime = CompanionRuntimeState.SPEAKING, error = null)
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
                            lastFailedAttachments = emptyList()
                            _uiState.update {
                                it.copy(runtime = CompanionRuntimeState.IDLE, error = null)
                            }
                        }

                        is CompanionResponseEvent.Failed -> {
                            lastFailedAttachments = attachments
                            handleResponseFailure(input, response.reason)
                        }

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

    private fun appendHistory(
        author: CompanionAuthor,
        text: String,
        attachments: List<PendingAttachment> = emptyList(),
    ): Long {
        val id = nextHistoryId++
        val item = CompanionHistoryItem(
            id = id,
            author = author,
            text = text,
            time = "刚刚",
            attachments = attachments,
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
        /** 一轮最多带几个附件。各家都收多个，这里只是不让一次请求体积失控 */
        const val MAX_PENDING_ATTACHMENTS = 4

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
