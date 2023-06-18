package com.chatwaifu.chatgpt

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.LinkedList
import java.util.concurrent.TimeUnit

/**
 * Description: ChatGPTNetService
 * Author: Voine
 * Date: 2023/2/18
 */
class ChatGPTNetService(val context: Context) {
    companion object {
        private const val TEST_MODE = false
        private const val TAG = "ChatGPTNetService"
        private const val CHATGPT_BASE_URL = "https://api.openai.com/"
        val CHATGPT_DEAFULT_PROXY_URL = "https://api.openai-proxy.com/"
        private const val TIME_OUT_SECOND = 100L
        private const val MAX_LIST_SIZE = 100
    }

    private val interceptor = ChatGPTInterceptor()
    private var systemRole: RequestMessageBean? = null
    private val sendMessageAssistantList: LinkedList<RequestMessageBean> = LinkedList()

    private lateinit var retrofit: Retrofit

    private lateinit var chatAPI: GhatGPTAPI

    fun updateRetrofit(baseUrl: String?) {
        val usedUrl = if (baseUrl.isNullOrBlank()) {
            CHATGPT_BASE_URL
        } else baseUrl

        Log.d(TAG, "update retrofit $usedUrl")
        retrofit = Retrofit.Builder().run {
            addConverterFactory(GsonConverterFactory.create())
            baseUrl(usedUrl)
            client(createClient())
            build()
        }
        chatAPI = retrofit.create(GhatGPTAPI::class.java)
    }

    fun setPrivateKey(key: String): Boolean {
        if (key.isNotBlank()) {
            interceptor.privateKey = key
            return true
        }
        return false
    }

    fun setAssistantList(list: List<String>) {
        sendMessageAssistantList.clear()
        sendMessageAssistantList.addAll(list.map {
            RequestMessageBean(RequestMessageBean.ROLE_ASSISTANT, it) }
        )
    }

    fun setSystemRole(role: String) {
        sendMessageAssistantList.clear()
        systemRole = RequestMessageBean(
            RequestMessageBean.ROLE_SYSTEM,
            role
        )
    }

    fun sendChatMessage(msg: String, callback: (response: ChatGPTResponseData?) -> Unit) {
        if (TEST_MODE) {
            getResponseFromTest(msg, callback)
            return
        }
        val sendPart = sendMessageAssistantList + RequestMessageBean(
            RequestMessageBean.ROLE_USER,
            msg
        )
        val requestMsg = systemRole?.let { system ->
            listOf(system) + sendPart
        } ?: sendPart

        val call = chatAPI.sendMsg(ChatGPTRequestData(messages = requestMsg).toRequestBody())
        call.enqueue(object : Callback<ChatGPTResponseData> {
            override fun onResponse(
                call: Call<ChatGPTResponseData>,
                response: Response<ChatGPTResponseData>
            ) {
                Log.d(TAG, "send $call, receive ${response.body()}")
                if (!response.isSuccessful || response.body() == null) {
                    val errorJson = response.errorBody()?.string()
                    Log.d(TAG, "error msg is $errorJson")
                    val errorMsg = try {
                        Gson().fromJson(errorJson, ErrorBody::class.java).error?.message
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                    callback.invoke(
                        ChatGPTResponseData(
                            errorMsg = errorMsg ?: errorJson
                        )
                    )
                    return
                }
                response.body()?.choices?.firstOrNull()?.message?.content?.let {
                    if (sendMessageAssistantList.size > MAX_LIST_SIZE) {
                        sendMessageAssistantList.removeFirst()
                    }
                    sendMessageAssistantList.add(
                        RequestMessageBean(
                            RequestMessageBean.ROLE_ASSISTANT,
                            it
                        )
                    )
                }
                callback.invoke(response.body())
            }

            override fun onFailure(call: Call<ChatGPTResponseData>, t: Throwable) {
                t.printStackTrace()
                Toast.makeText(context, "gpt response error ${t.message}", Toast.LENGTH_SHORT)
                callback.invoke(null)
            }
        })
    }

    private fun createClient(): OkHttpClient {
        return OkHttpClient.Builder().run {
            readTimeout(TIME_OUT_SECOND, TimeUnit.SECONDS)
            connectTimeout(TIME_OUT_SECOND, TimeUnit.SECONDS)
            writeTimeout(TIME_OUT_SECOND, TimeUnit.SECONDS)
            addInterceptor(interceptor)
            build()
        }
    }

    private fun getResponseFromTest(msg: String, callback: (response: ChatGPTResponseData?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            delay(5000)
            val response = ChatGPTResponseData().apply {
                choices =
                    listOf(
                        ListBean(
                            message = ResponseInnerMessageBean(
                                role = "atri",
                                content = "萝卜子是对机器人的蔑称！萝卜子是对机器人的蔑称萝卜子是对机器人的蔑称萝卜子是对机器人的蔑称萝卜子是对机器人的蔑称萝卜子是对机器人的蔑称萝卜子是对机器人的蔑称萝卜子是对机器人的蔑称萝卜子是对机器人的蔑称"
                            ), finish_reason = "", index = 0
                        )
                    )
            }
            callback.invoke(response)
        }
    }
}