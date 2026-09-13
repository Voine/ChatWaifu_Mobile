package com.chatwaifu.chat.core

enum class ProviderCategory {
    CLOUD,
    LOCAL_NETWORK,
    EMBEDDED_LOCAL,
}

enum class ProviderSettingField {
    ENDPOINT,
    API_KEY,
    AUTH_TOKEN,
    MODEL_ID,
    STREAMING,
    TEMPERATURE,
    MAX_TOKENS,
    CONNECTION_TIMEOUT,
    MODEL_PATH,
    RUNTIME,
    CONTEXT_LENGTH,
}

/**
 * Provider 的静态产品描述。设置页只消费这里，不按具体 [ProviderId] 分支。
 */
data class ProviderDescriptor(
    val id: ProviderId,
    val category: ProviderCategory,
    val displayName: String,
    val summary: String,
    val defaultBaseUrl: String?,
    val capabilities: ProviderCapabilities,
    val models: List<ModelInfo>,
    val implemented: Boolean,
    val settingFields: Set<ProviderSettingField>,
)

/** [ModelInfo] 已经是稳定的模型描述类型，保留旧名兼容现有 provider。 */
typealias ModelDescriptor = ModelInfo
