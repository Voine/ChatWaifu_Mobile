package com.chatwaifu.chat

import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ModelInfo
import com.chatwaifu.chat.core.ProviderCategory
import com.chatwaifu.chat.core.ProviderCapabilities
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderDescriptor
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.chat.core.ProviderSettingField
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
        ProviderId.LOCAL_NETWORK -> OpenAICompatProvider(
            config = config.copy(
                baseUrl = requireNotNull(config.baseUrl?.takeIf { it.isNotBlank() }) {
                    "Local network provider requires an endpoint"
                }
            ),
            id = ProviderId.LOCAL_NETWORK,
            displayName = "局域网模型",
            availableModels = emptyList(),
        )
    }

    /** 设置页用：不构造实例就能拿到展示信息。 */
    fun descriptors(): List<ProviderDescriptor> = listOf(
        ProviderDescriptor(
            id = ProviderId.OPENAI_RESPONSES,
            category = ProviderCategory.CLOUD,
            displayName = "OpenAI",
            summary = "Responses API，gpt-5.x 系列",
            defaultBaseUrl = OpenAIResponsesProvider.DEFAULT_BASE_URL,
            capabilities = OpenAIResponsesProvider.CAPABILITIES.copy(requiresApiKey = true),
            models = OpenAIResponsesProvider.MODELS,
            implemented = true,
            settingFields = REMOTE_FIELDS + ProviderSettingField.API_KEY,
        ),
        ProviderDescriptor(
            id = ProviderId.OPENAI_COMPAT,
            category = ProviderCategory.CLOUD,
            displayName = "OpenAI 兼容",
            summary = "OpenAI-compatible 云服务或代理",
            defaultBaseUrl = OpenAICompatProvider.DEFAULT_BASE_URL,
            capabilities = OpenAICompatProvider.DEFAULT_CAPABILITIES,
            models = OpenAICompatProvider.DEFAULT_MODELS,
            implemented = true,
            // 本机推理服务通常不校验 key
            settingFields = REMOTE_FIELDS + ProviderSettingField.API_KEY,
        ),
        ProviderDescriptor(
            id = ProviderId.ANTHROPIC,
            category = ProviderCategory.CLOUD,
            displayName = "Claude",
            summary = "Messages API（待实现）",
            defaultBaseUrl = AnthropicMessagesProvider.DEFAULT_BASE_URL,
            capabilities = AnthropicMessagesProvider.CAPABILITIES.copy(requiresApiKey = true),
            models = AnthropicMessagesProvider.MODELS,
            implemented = false,
            settingFields = REMOTE_FIELDS + ProviderSettingField.API_KEY,
        ),
        ProviderDescriptor(
            id = ProviderId.GOOGLE,
            category = ProviderCategory.CLOUD,
            displayName = "Gemini",
            summary = "Interactions API（待实现）",
            defaultBaseUrl = GeminiProvider.DEFAULT_BASE_URL,
            capabilities = GeminiProvider.CAPABILITIES.copy(requiresApiKey = true),
            models = GeminiProvider.MODELS,
            implemented = false,
            settingFields = REMOTE_FIELDS + ProviderSettingField.API_KEY,
        ),
        ProviderDescriptor(
            id = ProviderId.LOCAL_NETWORK,
            category = ProviderCategory.LOCAL_NETWORK,
            displayName = "局域网模型",
            summary = "vLLM / llama.cpp / Ollama 等兼容服务",
            defaultBaseUrl = null,
            capabilities = OpenAICompatProvider.DEFAULT_CAPABILITIES.copy(
                requiresApiKey = false,
                remote = true,
                embedded = false,
            ),
            models = emptyList(),
            implemented = true,
            settingFields = REMOTE_FIELDS + ProviderSettingField.AUTH_TOKEN +
                ProviderSettingField.CONNECTION_TIMEOUT,
        ),
        ProviderDescriptor(
            id = ProviderId.LOCAL,
            category = ProviderCategory.EMBEDDED_LOCAL,
            displayName = "端内模型",
            summary = "离线推理（待实现）",
            defaultBaseUrl = null,
            capabilities = LocalLlmProvider.CAPABILITIES.copy(
                remote = false,
                embedded = true,
            ),
            models = emptyList(),
            implemented = false,
            settingFields = setOf(
                ProviderSettingField.MODEL_PATH,
                ProviderSettingField.RUNTIME,
                ProviderSettingField.CONTEXT_LENGTH,
            ),
        ),
    )

    fun descriptor(id: ProviderId): ProviderDescriptor =
        descriptors().first { it.id == id }

    private val REMOTE_FIELDS = setOf(
        ProviderSettingField.ENDPOINT,
        ProviderSettingField.MODEL_ID,
        ProviderSettingField.STREAMING,
        ProviderSettingField.TEMPERATURE,
        ProviderSettingField.MAX_TOKENS,
    )
}
