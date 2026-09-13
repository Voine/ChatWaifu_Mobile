package com.chatwaifu.mobile.ui.companion

import android.content.Context
import android.util.Log
import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatOptions
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatResult
import com.chatwaifu.chat.core.ChatRole
import com.chatwaifu.chat.core.ChatSession
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.mobile.BuildConfig
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.chat.ChatProviderSettings
import com.chatwaifu.mobile.data.memory.FactMemoryContributor
import com.chatwaifu.mobile.data.model.CharacterModel
import com.chatwaifu.mobile.data.model.CharacterRepository
import com.chatwaifu.mobile.data.model.ModelProvider
import com.chatwaifu.mobile.utils.ChatErrorMessages
import com.chatwaifu.mobile.utils.ChatHistoryStore
import com.chatwaifu.mobile.utils.LipsValueHandler
import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.ChatLogRole
import com.chatwaifu.translate.ITranslate
import com.chatwaifu.translate.baidu.BaiduTranslateService
import com.chatwaifu.vits.utils.SoundGenerateHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    private val memoryContributor = FactMemoryContributor(appContext)
    private val requestMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var provider: ChatProvider? = null
    private var session: ChatSession? = null
    private var activeProviderKey = ""
    private var activeModel: String? = null
    private var pendingPersistedInput: String? = null

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
                val storedConfig = providerSettings.config(providerId)
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
                activeModel = config.model?.ifBlank { null } ?: newProvider.defaultModel
                memoryContributor.characterId = character.name

                historyStore.failDanglingStreams()
                historyStore.gcAttachments()
                val restored = historyStore.load(character.name, activeProviderKey)
                val displayHistory = historyStore.loadDisplayHistory(character.name)
                session = ChatSession(
                    provider = newProvider,
                    systemPrompt = characterRepository.getSystemPrompt(character.name),
                    options = ChatOptions(model = config.model),
                    memory = memoryContributor,
                ).apply {
                    restore(restored)
                }

                translator = createTranslator()
                val warning = prepareVoice(character)
                CompanionPreparation(
                    history = displayHistory.toCompanionHistory(),
                    warning = warning,
                )
            }
        }

    override fun responses(request: CompanionRequest): Flow<CompanionResponseEvent> = flow {
        requestMutex.withLock {
            val activeSession = checkNotNull(session) { "聊天链路尚未准备完成" }
            if (!request.isRetry || pendingPersistedInput != request.input) {
                historyStore.appendUser(request.input)
                pendingPersistedInput = request.input
            }

            if (request.isRetry) {
                val snapshot = activeSession.snapshot()
                val last = snapshot.lastOrNull()
                if (last?.role == ChatRole.USER && last.text.trim() == request.input) {
                    activeSession.restore(snapshot.dropLast(1))
                }
            }

            val partial = StringBuilder()
            var assistantTerminal = false
            var assistantId: Long? = null
            try {
                val messageId = withContext(NonCancellable) {
                    historyStore.beginAssistant(activeProviderKey, activeModel)
                }
                assistantId = messageId
                val result = requestAssistant(activeSession, request.input, messageId, partial)
                if (result == null) {
                    assistantTerminal = true
                    return@withLock
                }

                val reply = result.text.trim()
                if (reply.isEmpty()) {
                    withContext(NonCancellable) {
                        historyStore.failAssistant(messageId, "")
                        val snapshot = activeSession.snapshot()
                        if (snapshot.lastOrNull()?.role == ChatRole.ASSISTANT) {
                            activeSession.restore(snapshot.dropLast(1))
                        }
                    }
                    assistantTerminal = true
                    emit(
                        CompanionResponseEvent.Failed(
                            appContext.getString(R.string.companion_response_incomplete)
                        )
                    )
                    return@withLock
                }

                withContext(NonCancellable) {
                    historyStore.finishAssistant(
                        messageId = messageId,
                        message = result.message,
                        usage = result.usage,
                        providerId = activeProviderKey,
                        model = activeModel,
                    )
                    pendingPersistedInput = null
                }
                assistantTerminal = true

                emit(CompanionResponseEvent.Reply(reply))

                if (!voiceReady) {
                    emit(CompanionResponseEvent.Finished)
                    return@withLock
                }

                val played = try {
                    val spokenText = try {
                        translateForVoice(reply)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.w(TAG, "translation failed; use original reply for TTS", e)
                        reply
                    }
                    playAndAwait(spokenText)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "TTS failed", e)
                    false
                }
                emit(
                    if (played) {
                        CompanionResponseEvent.Finished
                    } else {
                        CompanionResponseEvent.TtsFailed(
                            appContext.getString(R.string.companion_tts_failed)
                        )
                    }
                )
            } finally {
                if (!assistantTerminal) {
                    assistantId?.let { messageId ->
                        withContext(NonCancellable) {
                            historyStore.failAssistant(messageId, partial.toString())
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<CompanionResponseEvent>.requestAssistant(
        activeSession: ChatSession,
        input: String,
        assistantId: Long,
        partial: StringBuilder,
    ): ChatResult? {
        var result: ChatResult? = null
        try {
            activeSession.send(input).collect { delta ->
                when (delta) {
                    is ChatDelta.TextDelta -> {
                        partial.append(delta.text)
                        val streamedText = partial.toString()
                        historyStore.updateStreamingAssistant(
                            messageId = assistantId,
                            partialText = streamedText,
                            providerId = activeProviderKey,
                            model = activeModel,
                        )
                        emit(CompanionResponseEvent.Streaming(streamedText))
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
            historyStore.failAssistant(assistantId, partial.toString())
            emit(CompanionResponseEvent.Failed(ChatErrorMessages.describe(appContext, e)))
            return null
        }

        if (result == null) {
            historyStore.failAssistant(assistantId, partial.toString())
            emit(
                CompanionResponseEvent.Failed(
                    appContext.getString(R.string.companion_response_incomplete)
                )
            )
        }
        return result
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

    private suspend fun translateForVoice(text: String): String {
        val activeTranslator = translator ?: return text
        return suspendCancellableCoroutine { continuation ->
            activeTranslator.getTranslateResult(text) { translated ->
                if (continuation.isActive) {
                    continuation.resume(translated?.takeIf { it.isNotBlank() } ?: text)
                }
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

    private suspend fun playAndAwait(text: String): Boolean {
        val helper = soundHelper ?: return false
        return helper.generateAndPlay(text) { pcm, sampleRate ->
            lipsValueHandler?.sendLipsValues(pcm, sampleRate)
        }
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

    private fun List<ChatLogEntry>.toCompanionHistory(): List<CompanionHistoryItem> =
        mapNotNull { entry ->
            val author = when (entry.role) {
                ChatLogRole.USER -> CompanionAuthor.USER
                ChatLogRole.ASSISTANT -> CompanionAuthor.ASSISTANT
                else -> return@mapNotNull null
            }
            entry.text.takeIf { it.isNotBlank() }?.let {
                CompanionHistoryItem(
                    id = entry.id,
                    author = author,
                    text = it,
                    time = appContext.getString(R.string.companion_history_previous),
                )
            }
        }

    private companion object {
        const val TAG = "CompanionRealDriver"
    }
}
