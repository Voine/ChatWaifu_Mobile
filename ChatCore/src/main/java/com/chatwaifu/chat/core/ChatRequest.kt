package com.chatwaifu.chat.core

/**
 * Description: 一次生成请求的参数。
 * Author: Voine
 * Date: 2026/8/6
 */

/**
 * 统一的「思考/推理强度」档位。三家的旋钮名字都不一样，但语义能对齐：
 *
 * - OpenAI Responses：`reasoning: { effort: "minimal" | "low" | "medium" | "high" }`
 * - Anthropic Messages：`thinking: { type: "adaptive" }` + `output_config: { effort: ... }`
 * - Gemini Interactions：`generation_config: { thinking_level: ... }`
 *
 * provider 只挑 [ProviderCapabilities.reasoningLevels] 里声明支持的档位，其余向下取整。
 */
enum class ReasoningLevel { OFF, LOW, MEDIUM, HIGH, MAX }

/**
 * 工具（function calling）声明。这个 App 现在还不用工具，但接口层先留好：
 * 三家的 function 声明都是「名字 + 描述 + JSON Schema」，差异只在外层包装。
 *
 * @param parametersJsonSchema JSON Schema 的原始字符串，不在这一层建模。
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parametersJsonSchema: String,
)

/**
 * 一次生成请求。
 *
 * 关于可空参数：**能空的一定留空**，因为「传了会 400」是真实存在的约束 ——
 * `claude-opus-5` / `claude-sonnet-5` 和 gpt-5 的推理档收到 `temperature` 会直接报错。
 * provider 按自己的 [ProviderCapabilities] 决定丢弃还是透传，调用方不需要知道这些细节。
 */
data class ChatRequest(
    /** 不含 system 的完整历史（含本轮用户输入）。由 [ChatSession] 拼装。 */
    val messages: List<ChatMessage>,

    /** 人物设定。对应 `instructions` / `system` / `system_instruction`。 */
    val systemPrompt: String? = null,

    /** null 表示用 provider 的默认模型（[ChatProvider.defaultModel]）。 */
    val model: String? = null,

    /** null 时由 provider 兜默认值 —— Anthropic 的 `max_tokens` 是必填字段。 */
    val maxOutputTokens: Int? = null,

    /** null = 不下发该字段。见类注释。 */
    val temperature: Float? = null,

    /** null = 不下发，用基座默认行为。 */
    val reasoning: ReasoningLevel? = null,

    val tools: List<ToolSpec> = emptyList(),

    val stopSequences: List<String> = emptyList(),

    /**
     * provider 专属参数的逃生阀，会被原样合并进请求体顶层。
     * 用它承接「只有某一家有」的字段，避免为一次性需求污染公共字段。
     */
    val extras: Map<String, Any?> = emptyMap(),
)
