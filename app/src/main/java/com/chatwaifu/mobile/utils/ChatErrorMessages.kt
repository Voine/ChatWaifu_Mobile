package com.chatwaifu.mobile.utils

import android.content.Context
import com.chatwaifu.chat.core.ChatError
import com.chatwaifu.mobile.R

/**
 * Description: [ChatError] → 用户能看懂的一句话。
 *
 * 改造前是把基座返回的原始 JSON 直接 Toast 出来（`GPT Error: {"error":{...}}`），
 * 用户看不出该去改哪。有了统一的错误类型才能做这一层。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
object ChatErrorMessages {

    fun describe(context: Context, throwable: Throwable): String = when (throwable) {
        is ChatError.Auth -> context.getString(R.string.chat_error_auth)

        is ChatError.RateLimited -> throwable.retryAfterSeconds?.let {
            context.getString(R.string.chat_error_rate_limited_retry, it)
        } ?: context.getString(R.string.chat_error_rate_limited)

        is ChatError.Network -> context.getString(R.string.chat_error_network)
        is ChatError.Timeout -> context.getString(R.string.chat_error_timeout)
        is ChatError.ContextOverflow -> context.getString(R.string.chat_error_context_overflow)
        is ChatError.ContentFiltered -> context.getString(R.string.chat_error_content_filtered)
        is ChatError.Refused -> context.getString(R.string.chat_error_refused)
        is ChatError.ModelNotFound -> context.getString(R.string.chat_error_model_not_found)

        is ChatError.CapabilityUnsupported ->
            context.getString(R.string.chat_error_capability, throwable.what)

        is ChatError.ServerError ->
            context.getString(R.string.chat_error_server, throwable.httpCode)

        // 选中了还没实现的 provider（Claude / Gemini / 端内）
        is NotImplementedError -> context.getString(R.string.chat_error_not_implemented)

        else -> throwable.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.chat_error_unknown)
    }
}
