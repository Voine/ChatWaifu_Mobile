package com.chatwaifu.mobile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwaifu.chatgpt.ChatGPTNetService
import com.chatwaifu.chatgpt.ChatGPTResponseData
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.VITSLoadStatus
import com.chatwaifu.mobile.data.model.CharacterModel
import com.chatwaifu.mobile.data.model.CharacterRepository
import com.chatwaifu.mobile.data.model.ModelProvider
import com.chatwaifu.mobile.ui.common.ChatDialogContentUIState
import com.chatwaifu.mobile.utils.AssistantMessageManager
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
    var needChatGPTProxy: Boolean = false

    private var inputFunc: ((input: String) -> Unit)? = null
    private val chatGPTNetService: ChatGPTNetService? by lazy {
        ChatGPTNetService(ChatWaifuApplication.context)
    }
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
    private val assistantMsgManager: AssistantMessageManager by lazy {
        AssistantMessageManager(ChatWaifuApplication.context)
    }
    private var translate: ITranslate? = null

    fun refreshAllKeys() {
        sp.getString(Constant.SAVED_CHAT_KEY, null)?.let {
            chatGPTNetService?.setPrivateKey(it)
        }
        val translateAppId = sp.getString(Constant.SAVED_TRANSLATE_APP_ID, null)
        val translateKey = sp.getString(Constant.SAVED_TRANSLATE_KEY, null)
        setBaiduTranslate(translateAppId ?: return, translateKey ?: return)
        needTranslate = sp.getBoolean(Constant.SAVED_USE_TRANSLATE, true)
        needChatGPTProxy = sp.getBoolean(Constant.SAVED_USE_CHATGPT_PROXY, false)
        val proxyUrl = if(needChatGPTProxy) sp.getString(Constant.SAVED_USE_CHATGPT_PROXY_URL, null) else null
        chatGPTNetService?.updateRetrofit(proxyUrl)
    }

    fun mainLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                chatStatusLiveData.postValue(ChatStatus.FETCH_INPUT)
                val input = fetchInput()
                assistantMsgManager.insertUserMessage(input)

                chatStatusLiveData.postValue(ChatStatus.SEND_REQUEST)
                val response = sendChatGPTRequest(input, assistantMsgManager.getSendAssistantList())
                assistantMsgManager.insertGPTMessage(response)
                Log.d(TAG, "get response $response")
                _chatContentUIFlow.emit(constructUIStateFromResponse(response))

                val responseText = response?.choices?.firstOrNull()?.message?.content
                val translateText = fetchTranslateIfNeed(responseText)
                Log.d(TAG, "translate result: $translateText")
                chatStatusLiveData.postValue(ChatStatus.GENERATE_SOUND)
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
        // 设定为空时不覆盖已有的 system role，保持原来的行为
        characterRepository.getSystemPrompt(character.name)?.let {
            chatGPTNetService?.setSystemRole(it)
        }
        CoroutineScope(Dispatchers.IO).launch {
            assistantMsgManager.loadChatListCache(character.name)
        }
        loadVitsModel(character)
    }

    private fun loadVitsModel(character: CharacterModel) {
        val vitsDir = character.vitsDir
        if (vitsDir == null) {
            // 没有语音的角色也应该能进聊天页，只是不出声。
            // 以前这里会走到加载失败，导致这类模型根本进不去。
            Log.i(TAG, "${character.name} has no vits model, skip loading")
            viewModelScope.launch { _loadVITSModelLiveData.emit(VITSLoadStatus.STATE_SUCCESS) }
            return
        }
        loadingUILiveData.postValue(Pair(true, "Load VITS Model...."))
        viewModelScope.launch(Dispatchers.IO) {
            val rootFiles = File(vitsDir).listFiles()?.toList().orEmpty()
            val configResult = suspendCancellableCoroutine<Boolean> {
                vitsHelper.loadConfigs(rootFiles.find { it.name.endsWith("json") }?.absolutePath) { isSuccess ->
                    it.safeResume(isSuccess)
                }
            }

            val binResult = suspendCancellableCoroutine<Boolean> {
                vitsHelper.loadModel(rootFiles.find { it.name.endsWith("bin") }?.absolutePath) { isSuccess ->
                    it.safeResume(isSuccess)
                }
            }
            _loadVITSModelLiveData.emit(if (binResult && configResult) VITSLoadStatus.STATE_SUCCESS else VITSLoadStatus.STATE_FAILED)
            loadingUILiveData.postValue(Pair(false, ""))
        }
    }

    private suspend fun fetchInput(): String {
        return suspendCancellableCoroutine {
            inputFunc = { input ->
                it.safeResume(input)
            }
        }
    }

    private suspend fun sendChatGPTRequest(
        msg: String,
        assistantList: List<String>
    ): ChatGPTResponseData? {
        return suspendCancellableCoroutine {
            chatGPTNetService?.setAssistantList(assistantList)
            chatGPTNetService?.sendChatMessage(msg) { response ->
                it.safeResume(response)
            }
        }
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

    private fun generateAndPlaySound(needPlayText: String?) {
        val character = currentCharacter
        if (character == null || !character.hasVoice) {
            chatStatusLiveData.postValue(ChatStatus.DEFAULT)
            return
        }
        vitsHelper.generateAndPlay(text = needPlayText,
            targetSpeakerId = character.speakerId,
            callback = { isSuccess ->
            Log.d(TAG, "generate sound $isSuccess")
            if (chatStatusLiveData.value == ChatStatus.GENERATE_SOUND) {
                chatStatusLiveData.postValue(ChatStatus.DEFAULT)
            }},
            forwardResult = {
                lipsValueHandler.sendLipsValues(it)
            }
        )
    }

    private fun constructUIStateFromResponse(response: ChatGPTResponseData?): ChatDialogContentUIState {

        if (!response?.errorMsg.isNullOrEmpty()) {
            // isNullOrEmpty() 为 false 说明 response?.errorMsg 非空，response 已被智能转换成非空
            return ChatDialogContentUIState(isFromMe = false, errorMsg = response.errorMsg)
        }

        return ChatDialogContentUIState(
            isFromMe = false,
            chatContent = response?.choices?.firstOrNull()?.message?.content?.trim() ?: ""
        )
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
