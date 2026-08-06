package com.chatwaifu.chat.core

/**
 * Description: 流式输出事件。
 *
 * 核心接口是流式的（[ChatProvider.chatStream]），因为三家云端 API 都是 SSE、
 * 端内推理也是 token 回调，流式才是最小公倍数；非流式反而是它的特例
 * （见 [chat] 扩展函数）。
 *
 * **约定：[TextDelta] / [ThinkingDelta] 发的是增量，不是累计值。**
 * 累加统一在 [ChatSession] 里做，这样每个 provider 不用各自实现一遍拼接逻辑。
 *
 * 失败不走事件，而是让 Flow 直接抛 [ChatError] —— `catch {}` 是 Flow 的惯用法，
 * 也免得调用方同时处理「异常」和「失败事件」两条路径。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
sealed interface ChatDelta {

    /** 已建立连接、开始出内容。UI 可以在这里把 loading 态换成打字态。 */
    data object Started : ChatDelta

    data class TextDelta(val text: String) : ChatDelta

    data class ThinkingDelta(val text: String) : ChatDelta

    /**
     * 工具调用的增量。参数是流式拼出来的，所以 [argsFragment] 是片段。
     * [index] 用于区分同一轮里的并发工具调用。
     */
    data class ToolCallDelta(
        val index: Int,
        val id: String? = null,
        val name: String? = null,
        val argsFragment: String? = null,
    ) : ChatDelta

    /** 本轮结束，[message] 是拼装好的完整 assistant 消息。Flow 在此之后正常结束。 */
    data class Completed(
        val message: ChatMessage,
        val usage: TokenUsage? = null,
        val finishReason: FinishReason = FinishReason.STOP,
    ) : ChatDelta
}

enum class FinishReason {
    STOP,
    MAX_TOKENS,
    TOOL_CALL,

    /** 被内容安全策略拦截（OpenAI/Azure `content_filter`、Gemini `blockReason`）。 */
    CONTENT_FILTER,

    /** 模型主动拒答（Anthropic `stop_reason = refusal`）。和被系统拦截不是一回事。 */
    REFUSAL,
    OTHER,
}

data class TokenUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    /** 命中提示缓存的 token 数，三家都有各自的字段名。 */
    val cachedTokens: Int? = null,
)

/** [chat] 的返回值：把整段流收完之后的结果。 */
data class ChatResult(
    val message: ChatMessage,
    val usage: TokenUsage? = null,
    val finishReason: FinishReason = FinishReason.STOP,
) {
    val text: String get() = message.text
}
