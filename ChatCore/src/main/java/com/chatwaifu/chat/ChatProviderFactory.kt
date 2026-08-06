package com.chatwaifu.chat

import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ModelInfo
import com.chatwaifu.chat.core.ProviderCapabilities
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.chat.provider.anthropic.AnthropicMessagesProvider
import com.chatwaifu.chat.provider.compat.OpenAICompatProvider
import com.chatwaifu.chat.provider.google.GeminiProvider
import com.chatwaifu.chat.provider.local.LocalLlmProvider
import com.chatwaifu.chat.provider.openai.OpenAIResponsesProvider

/**
 * Description: 按 [ProviderId] 造 provider。
 *
 * 工程里没有 DI 框架，这个对象就是唯一的装配入口（和 `ModelProvider` 之于角色数据一样）。
 * 接 Hilt 时只改这一个文件。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
object ChatProviderFactory {

    fun create(id: ProviderId, config: ProviderConfig): ChatProvider = when (id) {
        ProviderId.OPENAI_RESPONSES -> OpenAIResponsesProvider(config)
        ProviderId.OPENAI_COMPAT -> OpenAICompatProvider(config)
        ProviderId.ANTHROPIC -> AnthropicMessagesProvider(config)
        ProviderId.GOOGLE -> GeminiProvider(config)
        ProviderId.LOCAL -> LocalLlmProvider(config)
    }

    /** 设置页用：不构造实例就能拿到展示信息。 */
    fun descriptors(): List<ProviderDescriptor> = listOf(
        ProviderDescriptor(
            id = ProviderId.OPENAI_RESPONSES,
            displayName = "OpenAI",
            summary = "Responses API，gpt-5.x 系列",
            defaultBaseUrl = OpenAIResponsesProvider.DEFAULT_BASE_URL,
            capabilities = OpenAIResponsesProvider.CAPABILITIES,
            models = OpenAIResponsesProvider.MODELS,
            implemented = true,
            requiresApiKey = true,
        ),
        ProviderDescriptor(
            id = ProviderId.OPENAI_COMPAT,
            displayName = "OpenAI 兼容",
            summary = "代理 / DeepSeek / Ollama / llama.cpp 等，地址可填",
            defaultBaseUrl = OpenAICompatProvider.DEFAULT_BASE_URL,
            capabilities = OpenAICompatProvider.DEFAULT_CAPABILITIES,
            models = OpenAICompatProvider.DEFAULT_MODELS,
            implemented = true,
            // 本机推理服务通常不校验 key
            requiresApiKey = false,
        ),
        ProviderDescriptor(
            id = ProviderId.ANTHROPIC,
            displayName = "Claude",
            summary = "Messages API（待实现）",
            defaultBaseUrl = AnthropicMessagesProvider.DEFAULT_BASE_URL,
            capabilities = AnthropicMessagesProvider.CAPABILITIES,
            models = AnthropicMessagesProvider.MODELS,
            implemented = false,
            requiresApiKey = true,
        ),
        ProviderDescriptor(
            id = ProviderId.GOOGLE,
            displayName = "Gemini",
            summary = "Interactions API（待实现）",
            defaultBaseUrl = GeminiProvider.DEFAULT_BASE_URL,
            capabilities = GeminiProvider.CAPABILITIES,
            models = GeminiProvider.MODELS,
            implemented = false,
            requiresApiKey = true,
        ),
        ProviderDescriptor(
            id = ProviderId.LOCAL,
            displayName = "端内模型",
            summary = "离线推理（待实现）",
            defaultBaseUrl = null,
            capabilities = LocalLlmProvider.CAPABILITIES,
            models = emptyList(),
            implemented = false,
            requiresApiKey = false,
        ),
    )

    fun descriptor(id: ProviderId): ProviderDescriptor =
        descriptors().first { it.id == id }
}

/**
 * provider 的静态元信息，给设置页画 UI 用。
 *
 * [implemented] 为 false 的项要么置灰要么隐藏 —— 选中了也只会在发消息时抛
 * `NotImplementedError`。
 */
data class ProviderDescriptor(
    val id: ProviderId,
    val displayName: String,
    val summary: String,
    val defaultBaseUrl: String?,
    val capabilities: ProviderCapabilities,
    val models: List<ModelInfo>,
    val implemented: Boolean,
    val requiresApiKey: Boolean,
)
