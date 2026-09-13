package com.chatwaifu.chat.core

/**
 * Description: provider 能力声明。
 *
 * UI 用它来 gate 功能（要不要显示图片附件按钮、要不要显示温度滑杆），
 * 而不是写 `if (provider == OPENAI)` —— 后者每加一个基座就要回来改一遍。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
data class ProviderCapabilities(
    val streaming: Boolean = true,
    val imageInput: Boolean = false,
    val audioInput: Boolean = false,
    val audioOutput: Boolean = false,
    val fileInput: Boolean = false,
    val toolCalling: Boolean = false,
    val systemPrompt: Boolean = true,
    val modelDiscovery: Boolean = false,
    val requiresApiKey: Boolean = false,
    val remote: Boolean = true,
    val embedded: Boolean = false,

    /** 空集表示这个基座没有推理档概念，[ChatRequest.reasoning] 会被忽略。 */
    val reasoningLevels: Set<ReasoningLevel> = emptySet(),

    /**
     * 是否接受 `temperature`。false 不代表「不支持采样」，而是**传了会报错** ——
     * gpt-5 推理档和 claude-opus-5 都是这种情况。
     */
    val supportsTemperature: Boolean = true,

    /**
     * 是否支持服务端会话（`previous_response_id` / `previous_interaction_id`）。
     * 当前实现一律走客户端历史，这个字段只是为将来的「省 token / 提高缓存命中」优化留标记。
     */
    val serverSideHistory: Boolean = false,

    /** 用于 [ContextBudget] 估算，null 表示未知（比如端内模型由用户自己决定）。 */
    val maxContextTokens: Int? = null,
)

/**
 * 一个可选模型。清单是**静态写死**的，不做 `/v1/models` 在线探测：
 * 各家 models 接口返回的东西差别很大（还会混进 embedding、tts 模型），
 * 而且移动端在设置页里等一次网络请求才能显示下拉框，体验并不好。
 */
data class ModelInfo(
    /** 下发给基座的模型 id。 */
    val id: String,
    /** 设置页里显示的名字。 */
    val displayName: String = id,
    val contextTokens: Int? = null,
    val supportsImageInput: Boolean = false,
    val supportsAudioInput: Boolean = false,
    /**
     * 文档（PDF）输入。默认跟随 [supportsImageInput] —— 各家的 PDF 支持实际上都是
     * 「把页面栅格化后当图看」，所以视觉模型基本都能收 PDF，反之则一定不能。
     */
    val supportsFileInput: Boolean = supportsImageInput,
    /** 供 UI 打标，比如「便宜」「最强」。 */
    val note: String? = null,
)

/**
 * 把 provider 级能力和**具体模型**的能力取交集。
 *
 * 为什么必须按模型算：`imageInput` 之类声明在 provider 上，粒度太粗。
 * 最典型的是 [ProviderId.OPENAI_COMPAT] —— 它的 `imageInput = true`（协议层面支持），
 * 但用户在设置页选了 `deepseek-chat`，附一张图就会一路发到对端换回一个 400。
 * [ModelInfo] 里本来就记着每个模型支持什么，之前只是没人读。
 *
 * **模型不在 [ChatProvider.availableModels] 里时回落到 provider 级能力**：
 * 清单只是下拉框候选而不是白名单（用户可以手填 `qwen3:8b`），
 * 认不出来的模型只能按「协议允许」放行，由对端去拒绝。
 */
fun ChatProvider.capabilitiesFor(model: String?): ProviderCapabilities {
    val info = availableModels.firstOrNull { it.id == (model ?: defaultModel) }
        ?: return capabilities
    return capabilities.copy(
        imageInput = capabilities.imageInput && info.supportsImageInput,
        audioInput = capabilities.audioInput && info.supportsAudioInput,
        fileInput = capabilities.fileInput && info.supportsFileInput,
        maxContextTokens = info.contextTokens ?: capabilities.maxContextTokens,
    )
}
