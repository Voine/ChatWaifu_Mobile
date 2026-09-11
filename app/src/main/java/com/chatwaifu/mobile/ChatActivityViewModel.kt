package com.chatwaifu.mobile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatOptions
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatResult
import com.chatwaifu.chat.core.ChatSession
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.memory.FactMemoryContributor
import com.chatwaifu.mobile.data.memory.LlmMemoryExtractor
import com.chatwaifu.mobile.data.memory.MemoryConsolidator
import com.chatwaifu.mobile.data.VITSLoadStatus
import com.chatwaifu.mobile.data.chat.ChatProviderSettings
import com.chatwaifu.mobile.data.model.CharacterModel
import com.chatwaifu.mobile.data.model.CharacterRepository
import com.chatwaifu.mobile.data.model.ModelProvider
import com.chatwaifu.mobile.ui.common.ChatDialogContentUIState
import com.chatwaifu.mobile.utils.ChatErrorMessages
import com.chatwaifu.mobile.utils.ChatHistoryStore
import com.chatwaifu.mobile.utils.LipsValueHandler
import com.chatwaifu.translate.ITranslate
import com.chatwaifu.translate.baidu.BaiduTranslateService
import com.chatwaifu.vits.utils.SoundGenerateHelper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Description: main view model
 * Author: Voine
 * Date: 2023/2/18
 */
class ChatActivityViewModel : ViewModel() {
    companion object {
        private const val TAG = "ChatActivityViewModel"
    }

    enum class ChatStatus {
        DEFAULT,
        FETCH_INPUT,
        SEND_REQUEST,
        TRANSLATE,
        GENERATE_SOUND,
    }

    val drawerShouldBeOpened = MutableLiveData<Boolean>()
    val chatStatusLiveData = MutableLiveData<ChatStatus>().apply { value = ChatStatus.DEFAULT }

    //使用 shared flow 为了解决数据倒灌的问题....
    private val _loadVITSModelLiveData = MutableSharedFlow<VITSLoadStatus>()
    val loadVITSModelLiveData = _loadVITSModelLiveData.asSharedFlow()
    private val _chatContentUIFlow = MutableSharedFlow<ChatDialogContentUIState>()
    val chatContentUIFlow = _chatContentUIFlow.asSharedFlow()

    val generateSoundLiveData = MutableLiveData<Boolean>()
    val initModelResultLiveData = MutableLiveData<List<CharacterModel>>()
    val loadingUILiveData = MutableLiveData<Pair<Boolean, String>>()

    /**
     * 当前选中的角色。以前是 currentLive2DModelPath / currentLive2DModelName /
     * currentVITSModelName 三个散装 String，调用方要自己保证它们同步。
     */
    var currentCharacter: CharacterModel? = null
        private set

    val currentCharacterName: String get() = currentCharacter?.name.orEmpty()

    var needTranslate: Boolean = true

    private var inputFunc: ((input: String) -> Unit)? = null

    private val providerSettings: ChatProviderSettings by lazy {
        ChatProviderSettings(ChatWaifuApplication.context)
    }

    /**
     * 当前基座和会话。会话持有客户端历史，所以切基座时**只重建 provider，历史照旧**
     * （见 [rebuildChatSession]）—— 这是「历史放客户端」带来的直接好处。
     */
    private var chatProvider: ChatProvider? = null
    private var chatSession: ChatSession? = null

    private val vitsHelper: SoundGenerateHelper by lazy {
        SoundGenerateHelper(ChatWaifuApplication.context)
    }
    private val characterRepository: CharacterRepository by lazy {
        ModelProvider.repository(ChatWaifuApplication.context)
    }
    val lipsValueHandler: LipsValueHandler by lazy {
        LipsValueHandler()
    }
    private val sp: SharedPreferences by lazy {
        ChatWaifuApplication.context.getSharedPreferences(
            Constant.SAVED_STORE,
            Context.MODE_PRIVATE
        )
    }
    private val historyStore: ChatHistoryStore by lazy {
        ChatHistoryStore(ChatWaifuApplication.context)
    }

    /** 注入 system 区的 L2 记忆块。见 docs/memory.md */
    private val memoryContributor: FactMemoryContributor by lazy {
        FactMemoryContributor(ChatWaifuApplication.context)
    }

    /**
     * 抽取器每次现取：它绑在当前 provider 上，切基座后不能再用旧的。
     * provider 还没建好时返回 null，巩固器会安静跳过。
     */
    private val memoryConsolidator: MemoryConsolidator by lazy {
        MemoryConsolidator(ChatWaifuApplication.context) {
            chatProvider?.let { provider ->
                LlmMemoryExtractor(
                    provider = provider,
                    modelOverride = sp.getString(Constant.SAVED_MEMORY_MODEL, null),
                )
            }
        }
    }
    private var translate: ITranslate? = null

    fun refreshAllKeys() {
        rebuildChatSession()
        val translateAppId = sp.getString(Constant.SAVED_TRANSLATE_APP_ID, null)
        val translateKey = sp.getString(Constant.SAVED_TRANSLATE_KEY, null)
        needTranslate = sp.getBoolean(Constant.SAVED_USE_TRANSLATE, true)
        setBaiduTranslate(translateAppId ?: return, translateKey ?: return)
    }

    /**
     * 按当前设置重建 provider。历史会从旧会话里搬过去，切基座不断上下文。
     */
    private fun rebuildChatSession() {
        val id = providerSettings.activeProviderId
        val config = providerSettings.config(id)
        val previous = chatSession

        chatProvider?.close()
        val provider = ChatProviderFactory.create(id, config)
        chatProvider = provider
        chatSession = ChatSession(
            provider = provider,
            systemPrompt = previous?.systemPrompt
                ?: currentCharacter?.let { characterRepository.getSystemPrompt(it.name) },
            options = ChatOptions(model = config.model),
            memory = memoryContributor,
        ).apply {
            previous?.let { restore(it.snapshot()) }
        }
        Log.d(TAG, "chat provider rebuilt: ${provider.displayName} / ${config.model ?: provider.defaultModel}")
    }

    private fun requireSession(): ChatSession =
        chatSession ?: run { rebuildChatSession(); chatSession!! }

    /** 落库时记的基座标识。thinking 签名跨基座不能重放，恢复历史时要靠它对齐。 */
    private val activeProviderKey: String get() = providerSettings.activeProviderId.key

    private val activeModel: String?
        get() = providerSettings.config().model?.ifBlank { null }
            ?: chatProvider?.defaultModel

    fun mainLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            // 上次进程被杀时留下的半截流式记录，收尾成 FAILED，
            // 否则它们会永远停在 STREAMING、既不上屏也不参与上下文
            historyStore.failDanglingStreams()
            historyStore.gcAttachments()

            while (true) {
                chatStatusLiveData.postValue(ChatStatus.FETCH_INPUT)
                val input = fetchInput()
                historyStore.appendUser(input)

                chatStatusLiveData.postValue(ChatStatus.SEND_REQUEST)
                // 先占一行 STREAMING 再发请求：流中途挂掉时历史里留下的是一条
                // 标记为失败的半截回复，而不是凭空少一轮对话
                val messageId = historyStore.beginAssistant(activeProviderKey, activeModel)
                val result = streamChatRequest(input, messageId) ?: continue

                historyStore.finishAssistant(
                    messageId = messageId,
                    message = result.message,
                    usage = result.usage,
                    providerId = activeProviderKey,
                    model = activeModel,
                )
                Log.d(TAG, "get response ${result.text}")

                val translateText = fetchTranslateIfNeed(result.text)
                Log.d(TAG, "translate result: $translateText")
                chatStatusLiveData.postValue(ChatStatus.GENERATE_SOUND)
                // 抽取和 TTS 并发：这几秒用户在听、主循环在等输入，是白送的异步窗口。
                // 放在 generateAndPlaySound 之前启动，两者重叠（一个等网络一个吃 CPU）
                consolidateMemoryInBackground()
                generateAndPlaySound(translateText)
            }
        }
    }

    fun sendMessage(input: String) {
        CoroutineScope(Dispatchers.IO).launch {
            if (inputFunc != null) {
                inputFunc?.invoke(input)
            }
        }
    }

    private fun setBaiduTranslate(appid: String, privateKey: String) {
        translate = BaiduTranslateService(
            ChatWaifuApplication.context,
            appid = appid,
            privateKey = privateKey
        )
    }

    /**
     * 加载角色列表。首次调用会把内置模型从 assets 解出来，之后走版本 gate 直接跳过。
     */
    fun initModel() {
        initModelResultLiveData.postValue(emptyList())
        loadingUILiveData.postValue(Pair(true, "Init Models...."))
        viewModelScope.launch(Dispatchers.IO) {
            val characters = characterRepository.loadCharacters()
            initModelResultLiveData.postValue(characters)
            loadingUILiveData.postValue(Pair(false, ""))
        }
        lipsValueHandler.initLipSync()
    }

    /**
     * 选中一个角色：装载人物设定、聊天记录，然后加载它的 VITS 模型。
     * 加载结果通过 [loadVITSModelLiveData] 通知，UI 收到 SUCCESS 后才跳聊天页。
     */
    fun selectCharacter(character: CharacterModel) {
        currentCharacter = character
        // 记忆按角色隔离，切角色时 scope 和轮次计数一起换
        memoryContributor.characterId = character.name
        memoryConsolidator.reset()
        val session = requireSession()
        // 设定为空时不覆盖已有的 system prompt，保持原来的行为
        characterRepository.getSystemPrompt(character.name)?.let {
            session.systemPrompt = it
        }
        CoroutineScope(Dispatchers.IO).launch {
            session.restore(historyStore.load(character.name, activeProviderKey))
        }
        loadVitsModel(character)
    }

    /**
     * 装载角色的 BV2 声库。
     *
     * 改造前是 `loadConfigs()` + `loadModel()` 两次 `suspendCancellableCoroutine`，
     * 因为老 VITS 必须先从 config 解出 symbols 才能建 textUtils。BV2 的 G2P 在
     * `text-preprocess` 里，config 只提供采样率，所以合成了一次 suspend 调用。
     */
    private fun loadVitsModel(character: CharacterModel) {
        val vitsDir = character.vitsDir
        if (vitsDir == null) {
            // 没有语音的角色也应该能进聊天页，只是不出声。
            // 以前这里会走到加载失败，导致这类模型根本进不去。
            Log.i(TAG, "${character.name} has no voice model, skip loading")
            viewModelScope.launch { _loadVITSModelLiveData.emit(VITSLoadStatus.STATE_SUCCESS) }
            return
        }
        loadingUILiveData.postValue(Pair(true, "Load TTS Model...."))
        viewModelScope.launch(Dispatchers.IO) {
            val success = vitsHelper.init(
                bv2Dir = vitsDir,
                bertDir = character.bertDir,
                language = character.language,
                targetSpeakerId = character.speakerId,
            )
            _loadVITSModelLiveData.emit(
                if (success) VITSLoadStatus.STATE_SUCCESS else VITSLoadStatus.STATE_FAILED
            )
            loadingUILiveData.postValue(Pair(false, ""))
        }
    }

    /**
     * fire-and-forget 地跑一次记忆巩固。
     *
     * **不 await**：主循环接着去做 TTS，抽取在后台跑完自己落库。
     * 巩固器内部有 Mutex 和轮次闸门，这里可以每轮无脑调。
     */
    /** 用户在记忆页改了内容，让注入侧的缓存失效 */
    fun onMemoryChanged() {
        memoryContributor.invalidate()
    }

    private fun consolidateMemoryInBackground() {
        val characterName = currentCharacter?.name ?: return
        val snapshot = chatSession?.snapshot() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            memoryConsolidator.onTurnCompleted(characterName, snapshot)
            // 事实可能变了，让下一轮重新读库
            memoryContributor.invalidate()
        }
    }

    private suspend fun fetchInput(): String {
        return suspendCancellableCoroutine {
            inputFunc = { input ->
                it.safeResume(input)
            }
        }
    }

    /**
     * 发一轮请求，**边收边渲染**。
     *
     * 改造前是一次性拿到完整回复才上屏；现在每个增量都会 emit 一次累积后的文本，
     * 气泡逐字出现。VITS 仍然吃完整文本 —— 按句切分提前合成属于后续优化。
     *
     * @param messageId [ChatHistoryStore.beginAssistant] 占好的那一行。失败时用它标 FAILED，
     *   已经收到的片段一并留下 —— 用户能看出「答到一半断了」，而不是这轮凭空消失。
     * @return null 表示这一轮失败了（错误已经 emit 给 UI），调用方应该跳过后面的翻译和合成。
     */
    private suspend fun streamChatRequest(input: String, messageId: Long): ChatResult? {
        val session = requireSession()
        val buffer = StringBuilder()
        var result: ChatResult? = null

        try {
            session.send(input).collect { delta ->
                when (delta) {
                    is ChatDelta.TextDelta -> {
                        buffer.append(delta.text)
                        _chatContentUIFlow.emit(
                            ChatDialogContentUIState(
                                isFromMe = false,
                                chatContent = buffer.toString(),
                                isStreaming = true,
                            )
                        )
                    }

                    is ChatDelta.Completed -> {
                        result = ChatResult(delta.message, delta.usage, delta.finishReason)
                        _chatContentUIFlow.emit(
                            ChatDialogContentUIState(
                                isFromMe = false,
                                chatContent = delta.message.text.trim(),
                            )
                        )
                    }

                    // 思考过程和工具调用当前不上屏
                    else -> Unit
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "chat request failed", e)
            historyStore.failAssistant(messageId, buffer.toString())
            _chatContentUIFlow.emit(
                ChatDialogContentUIState(
                    isFromMe = false,
                    errorMsg = ChatErrorMessages.describe(ChatWaifuApplication.context, e),
                )
            )
            chatStatusLiveData.postValue(ChatStatus.DEFAULT)
            return null
        }
        if (result == null) {
            // 流正常结束但没有 Completed（provider 只发了 text 就断流）。
            // 不收尾的话这行会永远停在 STREAMING。
            Log.w(TAG, "stream ended without Completed delta")
            historyStore.failAssistant(messageId, buffer.toString())
        }
        return result
    }

    private suspend fun fetchTranslateIfNeed(responseText: String?): String? {
        translate ?: return responseText
        responseText ?: return null
        if (!needTranslate) {
            return responseText
        }
        chatStatusLiveData.postValue(ChatStatus.TRANSLATE)
        return suspendCancellableCoroutine {
            translate?.getTranslateResult(responseText) { result ->
                it.safeResume(result?.ifBlank { responseText } ?: responseText)
            }
        }
    }

    private suspend fun generateAndPlaySound(needPlayText: String?) {
        val character = currentCharacter
        if (character == null || !character.hasVoice) {
            chatStatusLiveData.postValue(ChatStatus.DEFAULT)
            return
        }
        // speakerId 不再每次传：它在 loadVitsModel() 里就随模型一起设好了，
        // 一个 SoundGenerateHelper 实例同时只服务一个角色
        val isSuccess = vitsHelper.generateAndPlay(text = needPlayText) { pcm, sampleRate ->
            lipsValueHandler.sendLipsValues(pcm, sampleRate)
        }
        Log.d(TAG, "generate sound $isSuccess")
        if (chatStatusLiveData.value == ChatStatus.GENERATE_SOUND) {
            chatStatusLiveData.postValue(ChatStatus.DEFAULT)
        }
    }

    fun sendMineMsgUIState(content: String) {
        CoroutineScope(Dispatchers.Main).launch {
            _chatContentUIFlow.emit(
                ChatDialogContentUIState(
                    isFromMe = true,
                    chatContent = content
                )
            )
        }
    }

    override fun onCleared() {
        vitsHelper.clear()
        lipsValueHandler.shutDown()
        chatProvider?.close()
        // 不调 super.onCleared()：ViewModel.onCleared() 的实现是空的，调了触发 EmptySuperCall
    }

    fun openDrawer() {
        drawerShouldBeOpened.value = true
    }

    fun resetOpenDrawerAction() {
        drawerShouldBeOpened.value = false
    }
}

fun <T> CancellableContinuation<T>.safeResume(value: T) {
    if (this.isActive) {
        (this as? Continuation<T>)?.resume(value)
    }
}
