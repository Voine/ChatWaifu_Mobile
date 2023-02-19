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