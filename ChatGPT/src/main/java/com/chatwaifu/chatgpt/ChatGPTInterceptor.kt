package com.chatwaifu.chatgpt

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Description: ChatGPTInterceptor
 * Author: Voine
 * Date: 2023/2/18
 */
class ChatGPTInterceptor: Interceptor {
    var privateKey: String = "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"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return chain.proceed(addHeader(request))
    }

    private fun addHeader(request: Request) = request.newBuilder().run {
        addHeader("Content-Type","application/json")
        addHeader("Authorization", "Bearer $privateKey")
        build()
    }
}