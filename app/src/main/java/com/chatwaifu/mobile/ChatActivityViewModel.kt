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
import com.chatwaifu.mobile.ui.showToast
import com.chatwaifu.translate.ITranslate
import com.chatwaifu.translate.baidu.BaiduTranslateService
import com.chatwaifu.vits.utils.SoundGenerateHelper
import com.chatwaifu.vits.utils.file.copyAssets2Local
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
        private const val SAVED_KEY = "saved_key"
    }
    enum class ChatStatus{
        DEFAULT,
        FETCH_INPUT,
        SEND_REQUEST,
        TRANSLATE,
        GENERATE_SOUND,
        PLAY_SOUND
    }

    val chatStatusLiveData = MutableLiveData<ChatStatus>().apply { value = ChatStatus.DEFAULT }
    val chatResponseLiveData = MutableLiveData<ChatGPTResponseData?>()
    val generateSoundLiveData = MutableLiveData<Boolean>()

    var inputFunc: ((input: String) -> Unit)? = null
    private val chatGPTNetService: ChatGPTNetService? by lazy {
        ChatGPTNetService(ChatWaifuApplication.context)
    }
    private val vitsHelper: SoundGenerateHelper by lazy {
        SoundGenerateHelper(ChatWaifuApplication.context)
    }
    private val sharedPreferences: SharedPreferences  by lazy {
        ChatWaifuApplication.context.getSharedPreferences("PRIVATE_KEY_STORE", Context.MODE_PRIVATE)
    }
    private var translate: ITranslate? = null

    fun mainLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            initPrivateKey()
            while (true) {
                chatStatusLiveData.postValue(ChatStatus.FETCH_INPUT)
                val input = fetchInput()

                chatStatusLiveData.postValue(ChatStatus.SEND_REQUEST)
                val response = sendChatGPTRequest(input)
                chatResponseLiveData.postValue(response)

                val responseText = response?.choices?.firstOrNull()?.text
                val translateText = fetchTranslateIfNeed(responseText)

                chatStatusLiveData.postValue(ChatStatus.GENERATE_SOUND)
                generateSound(translateText)

                chatStatusLiveData.postValue(ChatStatus.PLAY_SOUND)
                playSound()
            }
        }
    }

    fun sendMessage(input: String) {
        if (inputFunc != null) {
            inputFunc?.invoke(input)
        }
    }

    fun setPrivateKey(currentText: String) {
        sharedPreferences.edit().putString(SAVED_KEY, currentText).apply()
        chatGPTNetService?.setPrivateKey(currentText)
    }

    fun setBaiduTranslate(appid: String, privateKey: String){
        translate = BaiduTranslateService(
            ChatWaifuApplication.context,
            appid = appid,
            privateKey = privateKey
        )
    }

    //init yuuka sound~
    fun initYuuka() {
        //copy yuuka to local...
        var yuukaPath = ""
        var srcPath = "model/yuuka"
        ChatWaifuApplication.context.copyAssets2Local(
            deleteIfExists = true,
            srcPath = srcPath,
            desPath = ChatWaifuApplication.baseAppDir + File.separator
        ) { isSuccess: Boolean, absPath: String ->
            if (!isSuccess) {
                showToast("copy yuuka to disk failed....")
                return@copyAssets2Local
            }
            yuukaPath = absPath + File.separator + srcPath
        }
        Log.d(TAG, "save yuuka to local success..")
        val rootFiles = File(yuukaPath).listFiles()
        //load yuuka ~
        loadVitsModel(rootFiles?.toList())
    }

    private fun loadVitsModel(rootFiles: List<File>?) {
        vitsHelper.loadConfigs(rootFiles?.find { it.name.endsWith("json") }?.absolutePath) { isSuccess ->
            if (!isSuccess) {
                showToast("load vits config failed..")
            }
        }
        vitsHelper.loadModel(rootFiles?.find { it.name.endsWith("bin") }?.absolutePath) { isSuccess ->
            if (!isSuccess) {
                showToast("load vits model failed..")
            }
        }
    }

    private fun initPrivateKey() {
        val savedLocalKey = sharedPreferences.getString(SAVED_KEY, null)
        setPrivateKey(savedLocalKey ?: return)
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

    private suspend fun generateSound(needPlayText: String?) {
        val result = suspendCancellableCoroutine {
            vitsHelper.generate(needPlayText) { isSuccess ->
                it.safeResume(isSuccess)
            }
        }
        generateSoundLiveData.postValue(result)
        return
    }

    private fun playSound() {
        vitsHelper.stop()
        vitsHelper.play()
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