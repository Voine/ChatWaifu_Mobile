package com.chatwaifu.chat.provider.anthropic

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
 * Description: Anthropic Messages API（`POST {base}/v1/messages`）—— **尚未实现**。
 *
 * 能力声明和模型清单是真的（设置页能正常显示、[capabilities] 能正常 gate UI），
 * 只有 [chatStream] 还没写。下面把映射方案记全，补实现时不用重新翻文档。
 *
 * ## 请求头
 * ```
 * x-api-key: <key>            // 不是 Authorization: Bearer
 * anthropic-version: 2023-06-01
 * content-type: application/json
 * ```
 *
 * ## 请求体
 * ```json
 * {
 *   "model": "claude-sonnet-5",
 *   "max_tokens": 4096,                       // 必填！三家里只有它是必填
 *   "system": "人物设定",                      // 顶层字段，string 或 TextBlock 数组
 *   "messages": [ { "role": "user", "content": [ ...content blocks... } ] ],
 *   "stream": true,
 *   "temperature": 1.0,                        // 范围 0..1，不是 OpenAI 的 0..2
 *   "thinking": { "type": "adaptive" },        // 或 {"type":"disabled"}
 *   "output_config": { "effort": "high" },     // low/medium/high/xhigh/max
 *   "stop_sequences": [...],
 *   "tools": [ { "name":..., "description":..., "input_schema": {...} } ]
 * }
 * ```
 *
 * ## 内容块映射（这一层最省事，我们的 ChatContent 几乎就是它的 content block）
 * | ChatContent | Anthropic |
 * |---|---|
 * | Text | `{"type":"text","text":...}` |
 * | Image | `{"type":"image","source":{"type":"base64","media_type":...,"data":...}}`，或 `{"type":"url","url":...}` |
 * | Doc | `{"type":"document","source":{"type":"base64",...}}` |
 * | ToolCall | `{"type":"tool_use","id":...,"name":...,"input":{...}}`（input 是 **对象**，不是 JSON 字符串） |
 * | ToolResult | `{"type":"tool_result","tool_use_id":...,"content":...}`，且必须放在 **user** 消息里 |
 * | Thinking | `{"type":"thinking","thinking":...,"signature":...}` → `opaque` 存 signature |
 *
 * 两条硬约束：
 * 1. `messages` 首条必须是 user（[com.chatwaifu.chat.core.ContextBudget.trim] 已经保证）
 * 2. 带 thinking 的 assistant 消息，thinking block 必须在最前面
 *    （[com.chatwaifu.chat.core.MessageAccumulator.build] 已经这么排）
 *
 * ## 推理档
 * - [ReasoningLevel.OFF] → `thinking: {"type":"disabled"}`
 * - 其余 → `thinking: {"type":"adaptive"}` + `output_config.effort` =
 *   low / medium / high / xhigh(HIGH) / max(MAX)
 *
 * 注意 `claude-haiku-4-5` 走的是老的 `thinking: {"type":"enabled","budget_tokens":N}`
 * （N ≥ 1024 且 < max_tokens），不支持 adaptive；opus-5 / sonnet-5 / fable-5 反过来。
 *
 * ## SSE 事件
 * ```
 * message_start          → ChatDelta.Started，并从 message.usage 取 input_tokens
 * content_block_start    → 按 content_block.type 分流；tool_use 时拿 id / name
 * content_block_delta    → delta.type:
 *                            text_delta       → TextDelta(delta.text)
 *                            thinking_delta   → ThinkingDelta(delta.thinking)
 *                            signature_delta  → 存进 Thinking.opaque
 *                            input_json_delta → ToolCallDelta(argsFragment = delta.partial_json)
 * content_block_stop
 * message_delta          → delta.stop_reason / delta.stop_details + usage.output_tokens
 * message_stop           → ChatDelta.Completed
 * ping                   → 忽略（心跳）
 * error                  → 抛 ChatError
 * ```
 *
 * ## stop_reason → FinishReason
 * `end_turn`/`stop_sequence` → STOP，`max_tokens` → MAX_TOKENS，`tool_use` → TOOL_CALL，
 * `refusal` → REFUSAL（`stop_details.category` 里有 cyber / bio / general_harms 等，
 * 填进 [com.chatwaifu.chat.core.ChatError.Refused.category]），
 * `model_context_window_exceeded` → 抛 ContextOverflow，
 * `pause_turn` → 长任务被暂停，需要把响应原样再发一次续跑（当前 App 用不到）。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class AnthropicMessagesProvider(
    private val config: ProviderConfig,
) : ChatProvider {

    override val id: ProviderId = ProviderId.ANTHROPIC
    override val displayName: String = "Claude"
    override val capabilities: ProviderCapabilities = CAPABILITIES
    override val availableModels: List<ModelInfo> = MODELS
    override val defaultModel: String = DEFAULT_MODEL

    override fun chatStream(request: ChatRequest): Flow<ChatDelta> =
        throw NotImplementedError("AnthropicMessagesProvider 还没实现，见类注释里的映射方案")

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com/"
        const val ANTHROPIC_VERSION = "2023-06-01"

        /** max_tokens 是必填字段，调用方没给时用这个兜底。 */
        const val DEFAULT_MAX_TOKENS = 4096

        private const val DEFAULT_MODEL = "claude-sonnet-5"

        val CAPABILITIES = ProviderCapabilities(
            streaming = true,
            imageInput = true,
            audioInput = false,
            audioOutput = false,
            fileInput = true,          // PDF 走 document block
            toolCalling = true,
            reasoningLevels = ReasoningLevel.entries.toSet(),
            // 支持，但范围是 0..1 —— UI 上的滑杆不能照 OpenAI 的 0..2 来
            supportsTemperature = true,
            // Messages API 没有服务端会话，永远是客户端重发全量。
            // 这也是整个抽象层选择「历史放客户端」的直接原因。
            serverSideHistory = false,
            maxContextTokens = 1_000_000,
        )

        val MODELS = listOf(
            ModelInfo("claude-sonnet-5", "Claude Sonnet 5", 1_000_000, supportsImageInput = true, note = "默认"),
            ModelInfo("claude-opus-5", "Claude Opus 5", 1_000_000, supportsImageInput = true),
            ModelInfo("claude-fable-5", "Claude Fable 5", 1_000_000, supportsImageInput = true, note = "最强"),
            ModelInfo("claude-haiku-4-5", "Claude Haiku 4.5", 200_000, supportsImageInput = true, note = "最快"),
        )
    }
}
