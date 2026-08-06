package com.chatwaifu.chat.net

import com.chatwaifu.chat.core.ChatError
import com.chatwaifu.chat.core.ProviderConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Description: 各 provider 共用的 OkHttp 装配和错误映射。
 * Author: Voine
 * Date: 2026/8/6
 */
object HttpSupport {

    /**
     * @param authHeaders 由 provider 提供鉴权头 —— 三家不一样：
     *   OpenAI `Authorization: Bearer`、Anthropic `x-api-key` + `anthropic-version`、
     *   Gemini 走 `x-goog-api-key`。
     */
    fun client(config: ProviderConfig, authHeaders: () -> Map<String, String>): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .addInterceptor(HeaderInterceptor(authHeaders, config.extraHeaders))
            .build()

    /**
     * 拼出完整请求地址。
     *
     * 之所以不用 Retrofit 的相对路径而是每次算全量 URL：`OPENAI_COMPAT` 的 baseUrl 由用户填，
     * 现实里两种写法都很常见 —— `https://api.deepseek.com/v1/` 和 `http://10.0.2.2:11434/`。
     * 这里统一兜住，用户少踩一个「404 但看不出为什么」的坑。
     *
     * @param path 不带前导 `/` 的路径，形如 `v1/chat/completions`。
     */
    fun resolveEndpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val versionPrefix = path.substringBefore('/')          // "v1"
        return if (base.endsWith("/$versionPrefix")) {
            "$base/${path.substringAfter('/')}"
        } else {
            "$base/$path"
        }
    }

    private class HeaderInterceptor(
        private val authHeaders: () -> Map<String, String>,
        private val extraHeaders: Map<String, String>,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val builder = chain.request().newBuilder()
            builder.header("Content-Type", "application/json")
            authHeaders().forEach { (k, v) -> builder.header(k, v) }
            extraHeaders.forEach { (k, v) -> builder.header(k, v) }
            return chain.proceed(builder.build())
        }
    }

    /**
     * HTTP 错误 → [ChatError]。
     *
     * 三家的错误体都是 `{"error": {"message":..., "code"/"type":...}}` 或者
     * `{"error": {"message":..., "status":...}}`（Google），所以能共用一套抽取逻辑。
     */
    fun toChatError(httpCode: Int, errorBody: String?, retryAfterHeader: String? = null): ChatError {
        val detail = parseErrorDetail(errorBody)
        val message = detail?.message ?: errorBody?.takeIf { it.isNotBlank() } ?: "http $httpCode"
        val code = detail?.code

        // 内容安全拦截可能挂在 400 上，要先于状态码判断
        if (code != null && code.contains("content_filter", ignoreCase = true)) {
            return ChatError.ContentFiltered(category = code, message = message)
        }
        if (code != null && code.contains("context_length", ignoreCase = true)) {
            return ChatError.ContextOverflow(message)
        }

        return when (httpCode) {
            401, 403 -> ChatError.Auth(message)
            404 -> ChatError.ModelNotFound(message)
            408 -> ChatError.Timeout(message)
            413 -> ChatError.ContextOverflow(message)
            429 -> ChatError.RateLimited(retryAfterHeader?.toLongOrNull(), message)
            in 500..599 -> ChatError.ServerError(httpCode, message)
            else -> ChatError.Unknown("http $httpCode: $message")
        }
    }

    /** IO 层异常 → [ChatError]。协程取消要原样抛出，不能吞成 Network。 */
    fun toChatError(throwable: Throwable): ChatError = when (throwable) {
        is ChatError -> throwable
        is SocketTimeoutException -> ChatError.Timeout(throwable.message, throwable)
        is UnknownHostException -> ChatError.Network(throwable.message, throwable)
        is IOException -> ChatError.Network(throwable.message, throwable)
        else -> ChatError.Unknown(throwable.message, throwable)
    }

    private data class ErrorDetail(val message: String?, val code: String?)

    private fun parseErrorDetail(body: String?): ErrorDetail? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = JsonParser.parseString(body)
            if (!root.isJsonObject) return null
            val error = root.asJsonObject.getAsJsonObject("error") ?: return null
            ErrorDetail(
                message = error.optString("message"),
                // OpenAI 用 code/type，Anthropic 用 type，Google 用 status
                code = error.optString("code") ?: error.optString("type") ?: error.optString("status"),
            )
        } catch (e: Exception) {
            // 错误体不是 JSON（网关返回 HTML 之类）时不该再抛一次
            null
        }
    }

    private fun JsonObject.optString(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
}
