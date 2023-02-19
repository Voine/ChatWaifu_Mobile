package com.chatwaifu.chatgpt

import android.content.Context
import android.util.Log
import android.widget.Toast
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Description: ChatGPTNetService
 * Author: Voine
 * Date: 2023/2/18
 */
class ChatGPTNetService(val context: Context) {
    companion object {
        private const val TAG = "ChatGPTNetService"
        private const val CHATGPT_BASE_URL = "https://api.openai.com/"
    }

    private val interceptor = ChatGPTInterceptor()

    private val chatAPI: GhatGPTAPI by lazy {
        val retrofit = Retrofit.Builder().run {
            addConverterFactory(GsonConverterFactory.create())
            baseUrl(CHATGPT_BASE_URL)
            client(createClient())
            build()
        }
        retrofit.create(GhatGPTAPI::class.java)
    }

    fun setPrivateKey(key: String): Boolean {
        if (key.isNotBlank()) {
            interceptor.privateKey = key
            return true
        }
        return false
    }

    fun sendChatMessage(msg: String, callback: (response: ChatGPTResponseData?) -> Unit) {
        val call = chatAPI.sendMsg(ChatGPTRequestData(prompt = msg).toRequestBody())
        call.enqueue(object : Callback<ChatGPTResponseData> {
            override fun onResponse(
                call: Call<ChatGPTResponseData>,
                response: Response<ChatGPTResponseData>
            ) {
                Log.d(TAG, "send $call, receive $response")
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
            addInterceptor(interceptor)
            build()
        }
    }
}