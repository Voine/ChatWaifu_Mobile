package com.chatwaifu.chatgpt

import androidx.annotation.Keep
import com.chatwaifu.chatgpt.ChatGPTData.MAX_GENERATE_LIMIT
import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONObject


object ChatGPTData {
    const val MAX_TOKEN_LIMIT = 4096 //turbo 3.5 max context length
    const val MAX_GENERATE_LIMIT = 1000 // chat gpt response limit
    const val MAX_SEND_LIMIT = MAX_TOKEN_LIMIT - MAX_GENERATE_LIMIT //send message length limit
    const val DEFAULT_SYSTEM_ROLE_LIMIT = 100 //default system role token length
}

@Keep
data class ChatGPTRequestData(
    val model: String = DEFAULT_MODEL,
    val temperature: Int = DEFAULT_TEMPERATURE,
    val max_tokens: Int = MAX_GENERATE_LIMIT,
    val messages: List<RequestMessageBean> = emptyList()
) {
    companion object {
        private const val DEFAULT_MODEL = "gpt-3.5-turbo"
        private const val DEFAULT_TEMPERATURE = 1
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

@Keep
data class ErrorBody(
    val error: ErrorInnerBody? = null
)

@Keep
data class ErrorInnerBody(
    var message: String? = null,
    var type: String? = null,
    var param: String? = null,
    var code: String? = null
)