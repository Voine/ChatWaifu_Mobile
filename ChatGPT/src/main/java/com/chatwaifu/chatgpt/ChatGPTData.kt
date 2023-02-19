package com.chatwaifu.chatgpt

import androidx.annotation.Keep
import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.RequestBody
import org.json.JSONObject

@Keep
data class ChatGPTRequestData(
    val prompt: String,
    val model: String = DEFAULT_MODEL,
    val temperature: Int = DEFAULT_TEMPERATURE,
    val max_tokens: Int = DEFAULT_MAX_TOKENS,
) {
    companion object {
        private const val DEFAULT_MODEL = "text-davinci-003"
        private const val DEFAULT_TEMPERATURE = 0
        private const val DEFAULT_MAX_TOKENS = 50
    }

    fun toRequestBody(): RequestBody {
        return RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            JSONObject(Gson().toJson(this)).toString()
        )
    }
}
@Keep
data class ChatGPTResponseData(
    var id: String?,
    var `object`: String?,
    var created: Int?,
    var model: String?,
    var choices: List<ListBean>?,
    var usage: Usage?,
)
@Keep
data class Usage(var prompt_tokens: Int?, val completion_tokens: Int?, val total_tokens: Int?)

@Keep
data class ListBean(
    var text: String?,
    var index: Int?,
    var logprobs: String?,
    var finish_reason: String?
)


