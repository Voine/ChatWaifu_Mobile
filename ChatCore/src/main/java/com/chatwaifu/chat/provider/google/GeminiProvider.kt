package com.chatwaifu.chat.provider.google

import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRequest
import com.chatwaifu.chat.core.ModelInfo
import com.chatwaifu.chat.core.ProviderCapabilities
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.chat.core.ReasoningLevel
import kotlinx.coroutines.flow.Flow

/**
 * Description: Google Gemini —— **尚未实现**。
 *
 * 2026-06 起 Google 的主接口是 **Interactions API**，`generateContent` 转成 legacy
 * （仍然可用，Batch / 显式缓存 / 自定义 safetySettings 目前只有它有）。
 * 新写就直接对 Interactions。
 *
 * ## 两个接口的取舍
 * | | Interactions | generateContent（legacy） |
 * |---|---|---|
 * | 端点 | `interactions.create` | `models/{model}:streamGenerateContent?alt=sse` |
 * | 历史 | `previous_interaction_id` 服务端会话，或 `store:false` 无状态 | 每次重发 `contents[]` |
 * | 结构 | 执行步骤时间线（thought / function_call / function_result / model_output） | `candidates[].content.parts[]` |
 * | 新特性 | 都先落这里（agent、Deep Research） | 冻结 |
 *
 * 我们统一走客户端历史，所以 Interactions 要传 `store: false`。
 * **注意 `store:false` 会同时关掉服务端链式和 background 模式**，但这两个我们都不用。
 *
 * ## 请求
 * ```
 * x-goog-api-key: <key>        // 或 ?key= 查询参数
 * ```
 * ```json
 * {
 *   "system_instruction": { "parts": [{ "text": "人物设定" }] },
 *   "generation_config": { "thinking_level": "high", "temperature": 1.0, "maxOutputTokens": N },
 *   "tools": [...],
 *   "store": false
 * }
 * ```
 * 每轮的 tools / system_instruction / generation_config **不会被继承**，
 * 走服务端链式时也要每次重发（`previous_interaction_id` 只带输入输出）。
 *
 * ## 内容块映射
 * | ChatContent | Gemini |
 * |---|---|
 * | Text | `{"text": ...}` |
 * | Image / Audio / Doc（内联） | `{"inline_data": {"mime_type":..., "data": <base64>}}` |
 * | Image / Audio / Doc（已上传） | `{"file_data": {"mime_type":..., "file_uri":...}}` —— 要先走 Files API |
 * | ToolCall | `{"function_call": {"name":..., "args": {...}}}` |
 * | ToolResult | `{"function_response": {"name":..., "response": {...}}}` |
 *
 * 角色枚举是 `user` / **`model`**（不是 assistant），这是最容易写错的一处。
 *
 * ## 推理档
 * [ReasoningLevel] → `generation_config.thinking_level`。
 *
 * ## 为什么它值得优先补
 * Gemini 原生支持**音频输入和输出**。做完之后有机会把现在
 * 「LLM 出中文 → 百度翻译成日文 → VITS 合成」这条链路里的翻译和 TTS 一起省掉，
 * 直接让模型出日语语音。当然音色和 Live2D 角色的绑定就没了，属于另一个取舍。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class GeminiProvider(
    private val config: ProviderConfig,
) : ChatProvider {

    override val id: ProviderId = ProviderId.GOOGLE
    override val displayName: String = "Gemini"
    override val capabilities: ProviderCapabilities = CAPABILITIES
    override val availableModels: List<ModelInfo> = MODELS
    override val defaultModel: String = DEFAULT_MODEL

    override fun chatStream(request: ChatRequest): Flow<ChatDelta> =
        throw NotImplementedError("GeminiProvider 还没实现，见类注释里的映射方案")

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/"

        private const val DEFAULT_MODEL = "gemini-3.5-flash"

        val CAPABILITIES = ProviderCapabilities(
            streaming = true,
            imageInput = true,
            audioInput = true,
            audioOutput = true,
            fileInput = true,
            toolCalling = true,
            reasoningLevels = ReasoningLevel.entries.toSet(),
            supportsTemperature = true,
            serverSideHistory = true,
            maxContextTokens = 1_000_000,
        )

        val MODELS = listOf(
            ModelInfo(
                "gemini-3.5-flash", "Gemini 3.5 Flash", 1_000_000,
                supportsImageInput = true, supportsAudioInput = true, note = "默认",
            ),
            ModelInfo(
                "gemini-3.6-flash", "Gemini 3.6 Flash", 1_000_000,
                supportsImageInput = true, supportsAudioInput = true,
            ),
            ModelInfo(
                "gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", 1_000_000,
                supportsImageInput = true, note = "便宜",
            ),
        )
    }
}
