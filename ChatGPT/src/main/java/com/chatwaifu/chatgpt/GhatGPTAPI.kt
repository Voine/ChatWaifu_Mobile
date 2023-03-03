package com.chatwaifu.chatgpt

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Description: GhatGPTAPI
 * Author: Voine
 * Date: 2023/2/18
 */
interface GhatGPTAPI {
    @POST("v1/chat/completions")
    fun sendMsg(@Body requestMsg: RequestBody): Call<ChatGPTResponseData>
}