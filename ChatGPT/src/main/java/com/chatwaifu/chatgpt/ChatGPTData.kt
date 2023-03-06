package com.chatwaifu.chatgpt

import androidx.annotation.Keep
import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONObject

@Keep
data class ChatGPTRequestData(
    val model: String = DEFAULT_MODEL,
    val temperature: Int = DEFAULT_TEMPERATURE,
    val max_tokens: Int = DEFAULT_MAX_TOKENS,
    val messages: List<RequestMessageBean> = emptyList()
) {
    companion object {
        private const val DEFAULT_MODEL = "gpt-3.5-turbo"
        private const val DEFAULT_TEMPERATURE = 1
        private const val DEFAULT_MAX_TOKENS = 2048
    }

    fun toRequestBody(): RequestBody {
        return RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            JSONObject(Gson().toJson(this)).toString()
        )
    }
}
@Keep
data class RequestMessageBean(
    var role: String = ROLE_USER,
    var content: String = ""
){
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

@Keep
data class ChatGPTResponseData(
    var id: String? = null,
    var `object`: String? = null,
    var created: Int? = null,
    var model: String? = null,
    var choices: List<ListBean>? = null,
    var usage: Usage? = null,
    var errorMsg: String? = null,
)
@Keep
data class Usage(var prompt_tokens: Int?, val completion_tokens: Int?, val total_tokens: Int?)

@Keep
data class ListBean(
    var message: ResponseInnerMessageBean?,
    var index: Int?,
    var finish_reason: String?
)

@Keep
data class ResponseInnerMessageBean(
    var role: String?,
    var content: String?
)
