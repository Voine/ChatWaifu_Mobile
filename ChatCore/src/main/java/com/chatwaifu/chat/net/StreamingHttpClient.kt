package com.chatwaifu.chat.net

import com.chatwaifu.chat.core.ChatError
import com.chatwaifu.chat.core.ProviderConfig
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Description: 所有 provider 共用的「POST JSON → 读 SSE」通道。
 *
 * 为什么不用 Retrofit 的相对路径 + 每个 provider 一个 API 接口：请求体是手写 JSON、
 * 响应是手工解 SSE，Retrofit 在这里只提供了「发请求」这一层，声明式的注解反而
 * 把可配置 baseUrl 的问题搞复杂（见 [HttpSupport.resolveEndpoint]）。
 * 所以只留一个 `@POST @Url` 的通用方法，路径由 provider 自己算。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class StreamingHttpClient(
    config: ProviderConfig,
    authHeaders: () -> Map<String, String>,
) {

    private val okHttpClient = HttpSupport.client(config, authHeaders)

    private val api: Api = Retrofit.Builder()
        // 全部走 @Url 传绝对地址，但 Retrofit 强制要求有个 baseUrl 占位
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(okHttpClient)
        .build()
        .create(Api::class.java)

    /**
     * 发一次流式请求，逐帧回调。异常统一转成 [ChatError]（协程取消照常抛出）。
     */
    suspend fun postSse(url: String, jsonBody: String, onEvent: suspend (SseEvent) -> Unit) {
        val response = try {
            api.postStream(url, jsonBody.toRequestBody(JSON_MEDIA_TYPE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw HttpSupport.toChatError(e)
        }

        if (!response.isSuccessful) {
            val body = runCatching { response.errorBody()?.string() }.getOrNull()
            throw HttpSupport.toChatError(
                httpCode = response.code(),
                errorBody = body,
                retryAfterHeader = response.headers()["retry-after"],
            )
        }

        val body = response.body() ?: throw ChatError.Unknown("empty response body")
        try {
            body.collectSse(onEvent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChatError) {
            throw e
        } catch (e: Throwable) {
            throw HttpSupport.toChatError(e)
        }
    }

    /**
     * 发一次非流式请求，返回响应体字符串。
     * 端内/第三方服务里确实存在不支持 `stream:true` 的实现，留这条路兜底。
     */
    suspend fun postJson(url: String, jsonBody: String): String {
        val response = try {
            api.postStream(url, jsonBody.toRequestBody(JSON_MEDIA_TYPE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw HttpSupport.toChatError(e)
        }

        if (!response.isSuccessful) {
            val body = runCatching { response.errorBody()?.string() }.getOrNull()
            throw HttpSupport.toChatError(response.code(), body, response.headers()["retry-after"])
        }
        return response.body()?.use { it.string() }
            ?: throw ChatError.Unknown("empty response body")
    }

    /**
     * 发一次 multipart 上传，返回响应体字符串。给各家的 Files API 用
     * （[com.chatwaifu.chat.core.ChatProvider.upload]）。
     *
     * @param fields 除文件之外的表单字段，比如 OpenAI 的 `purpose=user_data`。
     */
    suspend fun postMultipart(
        url: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        fields: Map<String, String> = emptyMap(),
        filePartName: String = "file",
    ): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .also { builder ->
                fields.forEach { (k, v) -> builder.addFormDataPart(k, v) }
                builder.addFormDataPart(
                    filePartName,
                    fileName,
                    bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
                )
            }
            .build()

        val response = try {
            api.postStream(url, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw HttpSupport.toChatError(e)
        }

        if (!response.isSuccessful) {
            val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
            throw HttpSupport.toChatError(response.code(), errorBody, response.headers()["retry-after"])
        }
        return response.body()?.use { it.string() }
            ?: throw ChatError.Unknown("empty response body")
    }

    fun close() {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }

    private interface Api {
        @Streaming
        @POST
        suspend fun postStream(
            @Url url: String,
            @Body body: RequestBody,
        ): Response<ResponseBody>
    }

    private companion object {
        const val PLACEHOLDER_BASE_URL = "http://localhost/"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
