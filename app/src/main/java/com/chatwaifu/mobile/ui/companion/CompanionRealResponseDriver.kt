package com.chatwaifu.mobile.ui.companion

import android.content.Context
import android.util.Log
import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ChatContent
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.capabilitiesFor
import com.chatwaifu.chat.core.cheapModel
import com.chatwaifu.chat.core.ChatOptions
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatResult
import com.chatwaifu.chat.core.ChatRole
import com.chatwaifu.chat.core.ChatSession
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.mobile.BuildConfig
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.attachment.AttachmentIngest
import com.chatwaifu.mobile.data.attachment.AttachmentProvider
import com.chatwaifu.mobile.data.attachment.NormalizeError
import com.chatwaifu.mobile.data.chat.ChatProviderSettings
import com.chatwaifu.mobile.data.memory.CompanionTurnContext
import com.chatwaifu.mobile.data.memory.EpisodeConsolidator
import com.chatwaifu.mobile.data.memory.LlmEpisodeSummarizer
import com.chatwaifu.mobile.data.memory.LlmMemoryExtractor
import com.chatwaifu.mobile.data.memory.MemoryBlockContributor
import com.chatwaifu.mobile.data.memory.MemoryConsolidator
import com.chatwaifu.mobile.data.memory.MemoryRecallTools
import com.chatwaifu.mobile.data.model.CharacterModel
import com.chatwaifu.mobile.data.model.CharacterRepository
import com.chatwaifu.mobile.data.model.ModelProvider
import com.chatwaifu.mobile.utils.ChatErrorMessages
import com.chatwaifu.mobile.utils.ChatHistoryStore
import com.chatwaifu.mobile.utils.LipsValueHandler
import com.chatwaifu.log.AttachmentKind
import com.chatwaifu.log.AttachmentRef
import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.ChatLogRole
import com.chatwaifu.log.room.RoomChatLogRepository
import com.chatwaifu.log.room.RoomEpisodeRepository
import com.chatwaifu.log.room.RoomMemoryRepository
import com.chatwaifu.translate.ITranslate
import com.chatwaifu.translate.baidu.BaiduTranslateService
import com.chatwaifu.vits.utils.SoundGenerateHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class CompanionRealResponseDriver(context: Context) : CompanionResponseDriver {
    private val appContext = context.applicationContext
    private val providerSettings = ChatProviderSettings(appContext)
    private val characterRepository: CharacterRepository = ModelProvider.repository(appContext)
    private val historyStore = ChatHistoryStore(appContext)
    private val chatLogRepository = RoomChatLogRepository(appContext)
    private val episodeRepository = RoomEpisodeRepository(appContext)

    /** system 区的记忆块：L2 事实 + 最近几段 L1 日记。见 docs/memory.md */
    private val memoryContributor = MemoryBlockContributor(appContext)

    /** 每轮附在用户消息前的备注：现在几点、隔了多久、自动想起的老日记 */
    private val turnContext = CompanionTurnContext(
        episodes = episodeRepository,
        facts = RoomMemoryRepository(appContext),
        residentIds = { memoryContributor.residentEpisodeIds },
    )

    /** 分层回忆的第二层：模型自己决定要不要搜老日记、读原话 */
    private val recallTools = MemoryRecallTools(episodeRepository, chatLogRepository)

    /**
     * L2 记忆巩固。抽取器每次现取：它绑在当前 provider 上，provider 随 [prepare] 重建。
     * provider 还没建好时返回 null，巩固器会安静跳过。见 docs/memory.md
     */
    private val memoryConsolidator = MemoryConsolidator(appContext) {
        provider?.let { activeProvider ->
            LlmMemoryExtractor(provider = activeProvider, modelOverride = memoryModel())
        }
    }

    /** L1：聊完一段（隔了几小时 / 聊得太长）就写一篇日记 */
    private val episodeConsolidator = EpisodeConsolidator(chatLogRepository, episodeRepository, {
        provider?.let { activeProvider ->
            LlmEpisodeSummarizer(provider = activeProvider, modelOverride = memoryModel())
        }
    })

    /**
     * 记忆后台活用的模型。设置页填了就用填的；**空着时用这家最便宜的一档**
     * （Claude → Haiku、Gemini → Flash Lite、OpenAI → nano、兼容基座 → DeepSeek V4 Flash），
     * 而不是跟着主模型——主模型选 Opus 时，抽事实 / 写日记这种结构化小活没必要按 Opus 计费。
     * 这家没标便宜档（局域网 / 本地）时回落主模型。
     *
     * 兼容基座的便宜档只在主模型本身就是 deepseek 系列时才用：兼容基座对面是什么服务由用户决定，
     * 对着 OpenAI 官方 / Ollama 发 `deepseek-v4-flash` 只会 404。
     */
    private fun memoryModel(): String? {
        val configured = appContext
            .getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
            .getString(Constant.SAVED_MEMORY_MODEL, null)
            ?.takeIf { it.isNotBlank() }
        if (configured != null) return configured
        val active = provider ?: return null
        if (active.id == ProviderId.OPENAI_COMPAT && activeModel?.startsWith("deepseek") != true) return null
        return active.cheapModel
    }
    private val requestMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var provider: ChatProvider? = null
    private var session: ChatSession? = null
    private var activeProviderKey = ""
    private var activeModel: String? = null
    private var activeCharacterStorageKey = ""
    private var pendingPersistedInput: String? = null

    /**
     * 已导入、还没随消息发出去的附件（父附件 relPath → 父 + 派生物）。
     * 视频一个附件对应一组 ref（父行 + 抽帧 + 音轨），要整组落库、整组清理。
     * 用户删掉或换角色时要清文件
     */
    private val pendingRefs = linkedMapOf<String, List<AttachmentRef>>()

    /** 已经落库的附件，重试时从这里取回同一批引用 */
    private val sentRefs = linkedMapOf<String, List<AttachmentRef>>()

    /** 当前模型收哪些附件。导入时和发给模型时（含历史重放）都按它降级 */
    private var support = AttachmentSupport.NONE

    private var soundHelper: SoundGenerateHelper? = null
    private var voiceReady = false
    private var translator: ITranslate? = null
    private var lipsValueHandler: LipsValueHandler? = null
    private var lipsInitialized = false
    private var lipsContextReady = false
    @Volatile
    private var closeJob: Job? = null
    @Volatile
    private var rendererReady = false

    override suspend fun prepare(character: CharacterModel): CompanionPreparation =
        withContext(Dispatchers.IO) {
            requestMutex.withLock {
                releaseConversationResources()

                val providerId = providerSettings.activeProviderId
                val storedSelection = providerSettings.selection(providerId)
                val storedConfig = storedSelection.providerConfig
                val config = if (
                    providerId in setOf(ProviderId.OPENAI_COMPAT, ProviderId.OPENAI_RESPONSES) &&
                    storedConfig.apiKey.isNullOrBlank() &&
                    BuildConfig.CHAT_CHPT_KEY.isNotBlank()
                ) {
                    storedConfig.copy(apiKey = BuildConfig.CHAT_CHPT_KEY)
                } else {
                    storedConfig
                }

                val newProvider = ChatProviderFactory.create(providerId, config)
                provider = newProvider
                activeProviderKey = providerId.key
                activeModel = config.model?.ifBlank { null } ?: newProvider.defaultModel.ifBlank { null }
                activeCharacterStorageKey = character.storageKey
                // 记忆按角色隔离，切角色时 scope 和轮次计数一起换
                memoryContributor.characterId = character.storageKey
                memoryContributor.invalidate()
                memoryConsolidator.reset()
                turnContext.characterId = character.storageKey
                turnContext.resetSession()
                recallTools.characterId = character.storageKey
                // 按「当前选中的模型」算，不是按 provider：OpenAI 兼容基座选了
                // deepseek-chat 就不该让用户附图（会一路发过去换回 400）
                val caps = newProvider.capabilitiesFor(activeModel)
                // 不支持工具调用的模型（部分 OpenAI 兼容基座 / 本地模型）不声明工具，只靠自动回想
                recallTools.enabled = caps.toolCalling
                support = AttachmentSupport(
                    image = caps.imageInput,
                    audio = caps.audioInput,
                    doc = caps.fileInput,
                )

                historyStore.failDanglingStreams()
                historyStore.gcAttachments()
                val restored = historyStore.load(character.storageKey, activeProviderKey, ::accepts)
                val displayHistory = historyStore.loadDisplayHistory(character.storageKey, HISTORY_PAGE_SIZE)
                turnContext.lastMessageAt = displayHistory.lastOrNull()?.timeline
                session = ChatSession(
                    provider = newProvider,
                    systemPrompt = characterRepository.getSystemPrompt(character.storageKey),
                    options = storedSelection.sessionOptions.copy(model = config.model),
                    memory = memoryContributor,
                    turnContext = turnContext,
                    tools = recallTools,
                ).apply {
                    restore(restored)
                }
                // 上次聊完隔了几个小时再打开：上一段在这里封存成日记。后台跑，不挡准备
                consolidateEpisodesInBackground(character.storageKey)

                translator = createTranslator()
                val warning = prepareVoice(character)
                CompanionPreparation(
                    history = displayHistory.toHistoryPage(),
                    warning = warning,
                    attachmentSupport = support,
                )
            }
        }

    override fun responses(request: CompanionRequest): Flow<CompanionResponseEvent> = channelFlow {
        val turnStart = android.os.SystemClock.elapsedRealtime()
        requestMutex.withLock {
            val activeSession = checkNotNull(session) { "聊天链路尚未准备完成" }
            val freshGroups = synchronized(pendingRefs) {
                request.attachments.mapNotNull { a -> pendingRefs.remove(a.relPath)?.let { a.relPath to it } }
            }
            freshGroups.forEach { (relPath, group) -> sentRefs[relPath] = group }
            val freshRefs = freshGroups.flatMap { it.second }
            if (!request.isRetry || pendingPersistedInput != request.input) {
                // 落库之后这些文件就归 Room 管了（删角色历史时随记录清理），不再是「待发送」
                historyStore.appendUser(
                    activeCharacterStorageKey,
                    request.input,
                    attachmentRefs = freshRefs,
                )
                pendingPersistedInput = request.input
            }
            // 重试时同一批附件已经落过库，从 sentRefs 取回引用重新发给模型
            val attachmentRefs = request.attachments.flatMap { sentRefs[it.relPath].orEmpty() }
            val userMessage = ChatMessage(
                ChatRole.USER,
                listOfNotNull(request.input.takeIf { it.isNotBlank() }?.let(ChatContent::Text)) +
                    historyStore.toModelContents(attachmentRefs, activeProviderKey, ::accepts),
            )

            if (request.isRetry) {
                val last = activeSession.snapshot().lastOrNull()
                if (last?.role == ChatRole.USER && last.text.trim() == request.input) {
                    activeSession.dropLast()
                }
            }

            val partial = StringBuilder()
            var assistantTerminal = false
            var assistantId: Long? = null
            // 语音流水线在 LLM 流开始前就建好：首句凑齐立刻进翻译 / 推理，不等整段回复
            val voice = openVoicePipeline(this)
            try {
                val messageId = withContext(NonCancellable) {
                    historyStore.beginAssistant(
                        activeCharacterStorageKey,
                        activeProviderKey,
                        activeModel,
                    )
                }
                assistantId = messageId
                val result = requestAssistant(
                    activeSession, userMessage, messageId, partial, voice, turnStart,
                )
                if (result == null) {
                    assistantTerminal = true
                    return@withLock
                }

                val reply = result.text.trim()
                if (reply.isEmpty()) {
                    withContext(NonCancellable) {
                        historyStore.failAssistant(activeCharacterStorageKey, messageId, "")
                        if (activeSession.snapshot().lastOrNull()?.role == ChatRole.ASSISTANT) {
                            activeSession.dropLast()
                        }
                    }
                    assistantTerminal = true
                    send(
                        CompanionResponseEvent.Failed(
                            appContext.getString(R.string.companion_response_incomplete)
                        )
                    )
                    return@withLock
                }

                withContext(NonCancellable) {
                    historyStore.finishAssistant(
                        characterStorageKey = activeCharacterStorageKey,
                        messageId = messageId,
                        message = result.message,
                        usage = result.usage,
                        providerId = activeProviderKey,
                        model = activeModel,
                    )
                    pendingPersistedInput = null
                }
                assistantTerminal = true
                // 缓存命中情况：Room 只存 prompt / completion，cached 只在这里看得到
                result.usage?.let {
                    Log.i(VoicePipeline.LATENCY_TAG, "usage prompt=${it.promptTokens} cached=${it.cachedTokens}")
                }
                // 抽取和 TTS 并发：这几秒用户在听，是白送的异步窗口（网络等待 vs CPU 推理）
                consolidateMemoryInBackground(activeSession)

                send(CompanionResponseEvent.Reply(reply))

                if (voice == null) {
                    send(CompanionResponseEvent.Finished)
                    return@withLock
                }

                val played = try {
                    voice.finish()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "TTS failed", e)
                    false
                }
                Log.i(VoicePipeline.LATENCY_TAG, "done ${android.os.SystemClock.elapsedRealtime() - turnStart}")
                send(
                    if (played) {
                        CompanionResponseEvent.Finished
                    } else {
                        CompanionResponseEvent.TtsFailed(
                            appContext.getString(R.string.companion_tts_failed)
                        )
                    }
                )
            } finally {
                // 正常结束时 finish 已经收完尾，这里是幂等的；中途取消 / 出错时负责停播，
                // 并且**等残余的 native 推理退出**才放开 requestMutex
                voice?.cancel()
                if (!assistantTerminal) {
                    assistantId?.let { messageId ->
                        withContext(NonCancellable) {
                            historyStore.failAssistant(
                                activeCharacterStorageKey,
                                messageId,
                                partial.toString(),
                            )
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun ProducerScope<CompanionResponseEvent>.requestAssistant(
        activeSession: ChatSession,
        userMessage: ChatMessage,
        assistantId: Long,
        partial: StringBuilder,
        voice: VoicePipeline?,
        turnStart: Long,
    ): ChatResult? {
        var result: ChatResult? = null
        var lastPersistAt = 0L
        try {
            activeSession.send(userMessage).collect { delta ->
                when (delta) {
                    is ChatDelta.TextDelta -> {
                        if (partial.isEmpty()) {
                            Log.i(
                                VoicePipeline.LATENCY_TAG,
                                "first_token ${android.os.SystemClock.elapsedRealtime() - turnStart}",
                            )
                        }
                        partial.append(delta.text)
                        voice?.append(delta.text)
                        val streamedText = partial.toString()
                        // 落库节流：每个 delta 都写一次的话，长回复是几百次「删附件行 + 改消息行」的事务。
                        // 占位行只是给「中途被杀进程」兜底的，丢最后几百毫秒无所谓——
                        // 正常收尾有 finishAssistant、失败有 failAssistant，都会写全量
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastPersistAt >= STREAM_PERSIST_INTERVAL_MS) {
                            lastPersistAt = now
                            historyStore.updateStreamingAssistant(
                                characterStorageKey = activeCharacterStorageKey,
                                messageId = assistantId,
                                partialText = streamedText,
                                providerId = activeProviderKey,
                                model = activeModel,
                            )
                        }
                        send(CompanionResponseEvent.Streaming(streamedText))
                    }
                    is ChatDelta.Completed ->
                        result = ChatResult(delta.message, delta.usage, delta.finishReason)
                    else -> Unit
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "chat request failed", e)
            historyStore.failAssistant(
                activeCharacterStorageKey,
                assistantId,
                partial.toString(),
            )
            send(CompanionResponseEvent.Failed(ChatErrorMessages.describe(appContext, e)))
            return null
        }

        if (result == null) {
            historyStore.failAssistant(
                activeCharacterStorageKey,
                assistantId,
                partial.toString(),
            )
            send(
                CompanionResponseEvent.Failed(
                    appContext.getString(R.string.companion_response_incomplete)
                )
            )
        }
        return result
    }

    /**
     * fire-and-forget 地跑一次记忆巩固。巩固器内部有 Mutex 和轮次闸门，每轮无脑调即可；
     * 它永远不抛异常，抽取失败不影响对话。
     */
    private fun consolidateMemoryInBackground(activeSession: ChatSession) {
        val characterStorageKey = activeCharacterStorageKey
        val snapshot = activeSession.snapshot()
        cleanupScope.launch {
            // 抽到的新事实**不**让记忆块失效：system 区是缓存前缀，每 3 轮改一次等于
            // 每 3 轮从头写一遍缓存。刚抽出的事实就来自眼前这段对话，原文还在工作记忆里，
            // 模型本来就看得到；下次准备会话（或有新日记封存）时再进 system 区。
            // 同 Hermes Agent 的「会话内冻结快照」，见 docs/memory.md 7.6
            memoryConsolidator.onTurnCompleted(characterStorageKey, snapshot)
            consolidateEpisodes(characterStorageKey)
        }
    }

    private fun consolidateEpisodesInBackground(characterStorageKey: String) {
        cleanupScope.launch { consolidateEpisodes(characterStorageKey) }
    }

    /**
     * 绝大多数时候没有可封存的段，只是一次本地查询。
     *
     * 封存了新日记才刷新记忆块：这意味着一段聊天已经结束（隔了几小时 / 聊满一段），
     * 本来就是缓存该换的时候，顺带把这期间攒下的新事实也带进来
     */
    private suspend fun consolidateEpisodes(characterStorageKey: String) {
        if (episodeConsolidator.consolidate(characterStorageKey) > 0) {
            memoryContributor.invalidate()
            turnContext.invalidate()
        }
    }

    private fun createTranslator(): ITranslate? {
        val preferences = appContext.getSharedPreferences(
            Constant.SAVED_STORE,
            Context.MODE_PRIVATE,
        )
        if (!preferences.getBoolean(Constant.SAVED_USE_TRANSLATE, true)) return null
        val appId = preferences.getString(Constant.SAVED_TRANSLATE_APP_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.TRANSLATE_APP_ID.takeIf { it.isNotBlank() }
        val key = preferences.getString(Constant.SAVED_TRANSLATE_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.TRANSLATE_KEY.takeIf { it.isNotBlank() }
        if (appId == null || key == null) return null
        return BaiduTranslateService(appContext, appid = appId, privateKey = key)
    }

    /** 单句翻译。失败 / 空结果返回 null，由流水线回落原文。 */
    private suspend fun translateOnce(activeTranslator: ITranslate, text: String): String? =
        suspendCancellableCoroutine { continuation ->
            activeTranslator.getTranslateResult(text) { translated ->
                if (continuation.isActive) {
                    continuation.resume(translated?.takeIf { it.isNotBlank() })
                }
            }
        }

    private suspend fun prepareVoice(character: CharacterModel): String? {
        if (!character.hasVoice) return null
        val helper = SoundGenerateHelper(appContext)
        soundHelper = helper
        voiceReady = try {
            helper.init(
                bv2Dir = checkNotNull(character.vitsDir),
                bertDir = character.bertDir,
                language = character.language,
                targetSpeakerId = character.speakerId,
            )
        } catch (e: Throwable) {
            Log.e(TAG, "voice model initialization failed", e)
            false
        }
        if (!voiceReady) return appContext.getString(R.string.companion_tts_unavailable)

        try {
            withContext(Dispatchers.Main.immediate) {
                val lips = lipsValueHandler ?: LipsValueHandler().also {
                    lipsValueHandler = it
                }
                if (!lipsInitialized) {
                    lips.initLipSync()
                    lipsInitialized = true
                }
                if (rendererReady && !lipsContextReady) {
                    lips.createContext()
                    lipsContextReady = true
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "lip sync initialization failed; audio remains available", e)
            releaseLips()
        }
        return null
    }

    /**
     * 这一轮的语音流水线。没有语音（角色无声库 / 模型没装上）时返回 null，整轮只出文字。
     *
     * 第一句入队时发 [CompanionResponseEvent.VoiceStarted]：从这一刻起 UI 是 SPEAKING，
     * 哪怕 LLM 还在往下写。推理在流水线自己的子协程里跑，所以整个 [responses] 用的是
     * `channelFlow`（普通 `flow {}` 不允许从别的协程 emit）。
     */
    private fun openVoicePipeline(scope: ProducerScope<CompanionResponseEvent>): VoicePipeline? {
        val helper = soundHelper?.takeIf { voiceReady } ?: return null
        val stream = helper.openStream { pcm, sampleRate ->
            lipsValueHandler?.sendLipsValues(pcm, sampleRate)
        } ?: return null
        val activeTranslator = translator
        return VoicePipeline(
            scope = scope,
            translate = activeTranslator?.let { tr -> { sentence -> translateOnce(tr, sentence) } },
            speak = { sentence ->
                stream.speak(sentence)
                stream.anyQueued
            },
            awaitPlayback = stream::finish,
            stopPlayback = stream::cancel,
            onFirstAudio = { scope.trySend(CompanionResponseEvent.VoiceStarted) },
        )
    }

    override suspend fun ingestAttachment(
        uri: String,
        expected: AttachmentType?,
    ): AttachmentIngestResult = withContext(Dispatchers.IO) {
        val characterKey = activeCharacterStorageKey
        if (characterKey.isEmpty()) {
            return@withContext AttachmentIngestResult.Failed(
                appContext.getString(R.string.companion_attachment_not_ready)
            )
        }
        val store = AttachmentProvider.store(appContext)
        var outcome: AttachmentIngestResult = AttachmentIngestResult.Failed(
            appContext.getString(R.string.companion_attachment_unreadable)
        )
        AttachmentProvider.ingest(appContext).ingest(characterKey, android.net.Uri.parse(uri)).collect { progress ->
            when (progress) {
                is AttachmentIngest.Progress.Working -> Unit
                is AttachmentIngest.Progress.Failed ->
                    outcome = AttachmentIngestResult.Failed(describe(progress.error))
                is AttachmentIngest.Progress.Success -> {
                    val refs = progress.refs
                    val type = refs.firstOrNull()?.kind?.toAttachmentType()
                    val rejection = when {
                        type == null -> R.string.companion_attachment_unreadable
                        // SAF 的 mime 过滤只是建议，文件管理器里照样能选到别的类型
                        !typeMatches(type, expected) -> R.string.companion_attachment_wrong_type
                        // 选择器打开之后用户可能去设置里换了模型
                        !support.canAccept(type) -> R.string.companion_attachment_model_unsupported
                        type == AttachmentType.VIDEO && refs.none { it.kind == AttachmentKind.IMAGE } ->
                            R.string.companion_attachment_video_no_frames
                        else -> null
                    }
                    val display = if (rejection == null) historyStore.displayAttachment(refs) else null
                    outcome = if (type == null || rejection != null || display == null) {
                        // 已经落盘的整组清掉，不留孤儿
                        store.deletePaths(refs.map { it.relPath })
                        AttachmentIngestResult.Failed(
                            appContext.getString(rejection ?: R.string.companion_attachment_write_failed)
                        )
                    } else {
                        val parent = refs.first()
                        synchronized(pendingRefs) { pendingRefs[parent.relPath] = refs }
                        AttachmentIngestResult.Success(display.toPending(type))
                    }
                }
            }
        }
        outcome
    }

    /** 相册入口（[expected] 为 null）图片和视频都收；菜单入口只收选中的那种 */
    private fun typeMatches(type: AttachmentType, expected: AttachmentType?): Boolean =
        if (expected == null) {
            type == AttachmentType.IMAGE || type == AttachmentType.VIDEO
        } else {
            type == expected
        }

    override fun discardAttachment(attachment: PendingAttachment) {
        val refs = synchronized(pendingRefs) { pendingRefs.remove(attachment.relPath) } ?: return
        cleanupScope.launch { AttachmentProvider.store(appContext).deletePaths(refs.map { it.relPath }) }
    }

    /** 把还没发出去的附件删掉。切角色 / 关闭时调，不然它们会成为孤儿文件 */
    private fun discardPendingAttachments() {
        val orphans = synchronized(pendingRefs) {
            pendingRefs.values.flatten().map { it.relPath }.also { pendingRefs.clear() }
        }
        sentRefs.clear()
        if (orphans.isNotEmpty()) {
            cleanupScope.launch { AttachmentProvider.store(appContext).deletePaths(orphans) }
        }
    }

    private fun accepts(kind: AttachmentKind): Boolean = when (kind) {
        AttachmentKind.IMAGE -> support.image
        AttachmentKind.AUDIO -> support.audio
        AttachmentKind.DOC -> support.doc
        // 视频本体从来不发（发的是派生的帧和音轨），交给 toCoreContent 跳过
        AttachmentKind.VIDEO -> true
    }

    private fun describe(error: NormalizeError): String = when (error) {
        is NormalizeError.TooLarge -> appContext.getString(
            R.string.companion_attachment_too_large,
            error.byteSize / BYTES_PER_MB,
            error.limit / BYTES_PER_MB,
        )
        is NormalizeError.TooLong -> appContext.getString(
            R.string.companion_attachment_too_long,
            (error.limitMs / 60_000).coerceAtLeast(1),
        )
        is NormalizeError.UnsupportedFormat, is NormalizeError.DecodeFailed ->
            appContext.getString(R.string.companion_attachment_unsupported)
        is NormalizeError.NoTrack -> appContext.getString(R.string.companion_attachment_no_track)
        NormalizeError.WriteFailed -> appContext.getString(R.string.companion_attachment_write_failed)
        NormalizeError.Unreadable -> appContext.getString(R.string.companion_attachment_unreadable)
    }

    override suspend fun loadOlderHistory(beforeId: Long): CompanionHistoryPage =
        withContext(Dispatchers.IO) {
            val characterKey = activeCharacterStorageKey
            if (characterKey.isEmpty()) return@withContext CompanionHistoryPage()
            historyStore.loadOlderDisplayHistory(characterKey, beforeId, HISTORY_PAGE_SIZE)
                .toHistoryPage()
        }

    override fun onRendererReady() {
        rendererReady = true
        if (lipsInitialized && !lipsContextReady) {
            lipsValueHandler?.createContext()
            lipsContextReady = true
        }
    }

    override fun onRendererUnavailable() {
        rendererReady = false
        if (lipsContextReady) {
            runCatching { lipsValueHandler?.destroyContext() }
                .onFailure { Log.w(TAG, "destroy lip sync context failed", it) }
            lipsContextReady = false
        }
    }

    override fun cancel() {
        soundHelper?.cancelPlayback()
    }

    override fun close() {
        cancel()
        scheduleClose()
    }

    override suspend fun awaitClosed() {
        cancel()
        scheduleClose().join()
    }

    private fun scheduleClose(): Job = synchronized(this) {
        closeJob ?: cleanupScope.launch {
            requestMutex.withLock {
                releaseConversationResources()
                releaseLips()
            }
        }.also { closeJob = it }
    }

    private fun releaseConversationResources() {
        provider?.close()
        provider = null
        session = null
        soundHelper?.clear()
        soundHelper = null
        voiceReady = false
        translator = null
        pendingPersistedInput = null
        support = AttachmentSupport.NONE
        discardPendingAttachments()
    }

    private fun releaseLips() {
        onRendererUnavailable()
        if (lipsInitialized) {
            runCatching { lipsValueHandler?.shutDown() }
                .onFailure { Log.w(TAG, "shut down lip sync failed", it) }
        }
        lipsInitialized = false
        lipsValueHandler = null
    }

    /**
     * Room 的一页 → UI 的一页。游标取**过滤前**最老那条的 id（见 [CompanionHistoryPage]），
     * 取满一页就认为前面可能还有——多翻一次空页的代价远小于漏掉记录。
     */
    private fun List<ChatLogEntry>.toHistoryPage(): CompanionHistoryPage = CompanionHistoryPage(
        items = toCompanionHistory(),
        olderCursor = firstOrNull()?.id,
        hasOlder = size >= HISTORY_PAGE_SIZE,
    )

    private fun List<ChatLogEntry>.toCompanionHistory(): List<CompanionHistoryItem> =
        mapNotNull { entry ->
            val author = when (entry.role) {
                ChatLogRole.USER -> CompanionAuthor.USER
                ChatLogRole.ASSISTANT -> CompanionAuthor.ASSISTANT
                else -> return@mapNotNull null
            }
            val attachments = historyStore.displayAttachments(entry).mapNotNull { display ->
                display.ref.kind.toAttachmentType()?.let { display.toPending(it) }
            }
            if (entry.text.isBlank() && attachments.isEmpty()) return@mapNotNull null
            CompanionHistoryItem(
                id = entry.id,
                author = author,
                text = entry.text,
                time = appContext.getString(R.string.companion_history_previous),
                attachments = attachments,
            )
        }

    private fun ChatHistoryStore.DisplayAttachment.toPending(type: AttachmentType) = PendingAttachment(
        relPath = ref.relPath,
        type = type,
        previewPath = preview?.absolutePath,
        durationMs = durationMs,
    )

    private fun AttachmentKind.toAttachmentType(): AttachmentType? = when (this) {
        AttachmentKind.IMAGE -> AttachmentType.IMAGE
        AttachmentKind.VIDEO -> AttachmentType.VIDEO
        AttachmentKind.AUDIO -> AttachmentType.AUDIO
        AttachmentKind.DOC -> AttachmentType.DOC
    }

    private companion object {
        const val TAG = "CompanionRealDriver"
        const val BYTES_PER_MB = 1024L * 1024

        /**
         * 历史面板一页多少条。首屏不用取满模型上下文的 200 条：
         * 每条都要解析附件路径、Compose 也要为每条建节点，翻到顶再取下一页就够了
         */
        const val HISTORY_PAGE_SIZE = 40

        /** 流式回复写 Room 的最小间隔 */
        const val STREAM_PERSIST_INTERVAL_MS = 500L
    }
}
