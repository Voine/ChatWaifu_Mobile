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
import com.chatwaifu.mobile.ui.ChannelListBean
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.mobile.utils.LocalModelManager
import com.chatwaifu.translate.ITranslate
import com.chatwaifu.translate.baidu.BaiduTranslateService
import com.chatwaifu.vits.utils.SoundGenerateHelper
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
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
class ChatActivityViewModel: ViewModel() {
    companion object {
        private const val TAG = "ChatActivityViewModel"
    }
    enum class ChatStatus{
        DEFAULT,
        FETCH_INPUT,
        SEND_REQUEST,
        TRANSLATE,
        GENERATE_SOUND,
    }

    val chatStatusLiveData = MutableLiveData<ChatStatus>().apply { value = ChatStatus.DEFAULT }
    val chatResponseLiveData = MutableLiveData<ChatGPTResponseData?>()
    val generateSoundLiveData = MutableLiveData<Boolean>()
    val initModelResultLiveData = MutableLiveData<List<ChannelListBean>>()
    val loadVITSModelLiveData = MutableLiveData<Boolean>().apply { value = false }
    val loadingUILiveData = MutableLiveData<Pair<Boolean, String>>()

    var currentLive2DModelPath: String = ""
    var currentLive2DModelName: String = ""
    var currentVITSModelName: String = ""

    var inputFunc: ((input: String) -> Unit)? = null
    private val chatGPTNetService: ChatGPTNetService? by lazy {
        ChatGPTNetService(ChatWaifuApplication.context)
    }
    private val vitsHelper: SoundGenerateHelper by lazy {
        SoundGenerateHelper(ChatWaifuApplication.context)
    }
    private val localModelManager: LocalModelManager by lazy {
        LocalModelManager()
    }
    private val sp: SharedPreferences by lazy {
        ChatWaifuApplication.context.getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)
    }
    private var translate: ITranslate? = null

    fun refreshAllKeys() {
        sp.getString(Constant.SAVED_CHAT_KEY, null)?.let {
            chatGPTNetService?.setPrivateKey(it)
        }
        val translateAppId = sp.getString(Constant.SAVED_TRANSLATE_APP_ID, null)
        val translateKey = sp.getString(Constant.SAVED_TRANSLATE_KEY, null)
        setBaiduTranslate(translateAppId ?: return, translateKey ?: return)
    }
    fun mainLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                chatStatusLiveData.postValue(ChatStatus.FETCH_INPUT)
                val input = fetchInput()

                chatStatusLiveData.postValue(ChatStatus.SEND_REQUEST)
                val response = sendChatGPTRequest(input)
                chatResponseLiveData.postValue(response)

                val responseText = response?.choices?.firstOrNull()?.text
                val translateText = fetchTranslateIfNeed(responseText)

                chatStatusLiveData.postValue(ChatStatus.GENERATE_SOUND)
                generateAndPlaySound(translateText)
            }
        }
    }

    fun sendMessage(input: String) {
        if (inputFunc != null) {
            inputFunc?.invoke(input)
        }
    }

    private fun setBaiduTranslate(appid: String, privateKey: String){
        translate = BaiduTranslateService(
            ChatWaifuApplication.context,
            appid = appid,
            privateKey = privateKey
        )
    }


    fun initModel(context: Context) {
        initModelResultLiveData.postValue(emptyList())
        loadingUILiveData.postValue(Pair(true, "Init Models...."))
        viewModelScope.launch(Dispatchers.IO) {
            val finalModelList = mutableListOf<ChannelListBean>()
            localModelManager.initInnerModel(context, finalModelList)
            localModelManager.initExternalModel(context, finalModelList)
            initModelResultLiveData.postValue(finalModelList)
            loadingUILiveData.postValue(Pair(false, ""))
        }
    }

    fun loadVitsModel(rootFiles: List<File>?) {
        loadVITSModelLiveData.postValue(false)
        loadingUILiveData.postValue(Pair(true, "Load VITS Model...."))
        viewModelScope.launch(Dispatchers.IO) {
            suspendCancellableCoroutine<Unit> {
                vitsHelper.loadConfigs(rootFiles?.find { it.name.endsWith("json") }?.absolutePath) { isSuccess ->
                    if (!isSuccess) {
                        showToast("load vits config failed..")
                    }
                }
                it.safeResume(Unit)
            }

            suspendCancellableCoroutine<Unit> {
                vitsHelper.loadModel(rootFiles?.find { it.name.endsWith("bin") }?.absolutePath) { isSuccess ->
                    if (!isSuccess) {
                        showToast("load vits model failed..")
                    }
                }
                it.safeResume(Unit)
            }
            loadVITSModelLiveData.postValue(true)
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

    private suspend fun sendChatGPTRequest(msg: String): ChatGPTResponseData? {
        return suspendCancellableCoroutine {
            chatGPTNetService?.sendChatMessage(msg){ response ->
                it.safeResume(response)
            }
        }
    }

    private suspend fun fetchTranslateIfNeed(responseText: String?): String? {
        translate ?: return responseText
        responseText ?: return null
        chatStatusLiveData.postValue(ChatStatus.TRANSLATE)
        return suspendCancellableCoroutine {
            translate?.getTranslateResult(responseText){result ->
                it.safeResume(result)
            }
        }
    }

    private fun generateAndPlaySound(needPlayText: String?) {
        vitsHelper.generateAndPlay(needPlayText){ isSuccess ->
            Log.d(TAG, "generate sound $isSuccess")
            if (chatStatusLiveData.value == ChatStatus.GENERATE_SOUND) {
                chatStatusLiveData.postValue(ChatStatus.DEFAULT)
            }
        }
    }

    override fun onCleared() {
        vitsHelper.clear()
        super.onCleared()
    }
}
fun <T> CancellableContinuation<T>.safeResume(value: T) {
    if (this.isActive) {
        (this as? Continuation<T>)?.resume(value)
    }
}