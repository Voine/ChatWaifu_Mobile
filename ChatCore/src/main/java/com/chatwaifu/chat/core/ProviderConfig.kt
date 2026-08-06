package com.chatwaifu.chat.core

/**
 * Description: provider 的运行时配置（密钥、地址、模型）。
 * Author: Voine
 * Date: 2026/8/6
 */

/**
 * 内置的 provider 标识。[key] 会进 SharedPreferences 的 key 名和持久化的配置里，
 * **改了就等于让老用户的配置失效**，所以只能追加不能改名。
 */
enum class ProviderId(val key: String) {
    /** OpenAI Responses API（`POST /v1/responses`），gpt-5.x 系列的主接口。 */
    OPENAI_RESPONSES("openai_responses"),

    /**
     * 任何说 OpenAI Chat Completions 协议的服务：官方兼容层、第三方代理、
     * DeepSeek、Ollama、llama.cpp server、vLLM……baseUrl 可配。
     */
    OPENAI_COMPAT("openai_compat"),

    /** Anthropic Messages API（`POST /v1/messages`）。 */
    ANTHROPIC("anthropic"),

    /** Google Gemini Interactions API。 */
    GOOGLE("google"),

    /** 端内推理。 */
    LOCAL("local"),
    ;

    companion object {
        fun fromKey(key: String?): ProviderId? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 一个 provider 实例的配置。由 app 层从 SharedPreferences 组装后交给
 * [com.chatwaifu.chat.ChatProviderFactory]。
 */
data class ProviderConfig(
    val apiKey: String? = null,

    /**
     * 自定义服务地址，null 用 provider 默认。**必须以 `/` 结尾**（Retrofit baseUrl 的要求）。
     * 这是 [ProviderId.OPENAI_COMPAT] 一份实现能覆盖那么多服务的关键。
     */
    val baseUrl: String? = null,

    /** 选中的模型 id，null 用 [ChatProvider.defaultModel]。 */
    val model: String? = null,

    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,

    /** 额外请求头，用于代理鉴权、Azure 的 `api-key` 之类的特殊情况。 */
    val extraHeaders: Map<String, String> = emptyMap(),

    /** 端内模型的落地路径等，provider 自解释。 */
    val localModelPath: String? = null,
) {
    companion object {
        /**
         * 100 秒，沿用改造前 ChatGPTNetService 的取值。
         * 长思考模型首字延迟可能很久，不能按普通接口的 15 秒来。
         */
        const val DEFAULT_TIMEOUT_SECONDS = 100L
    }
}
