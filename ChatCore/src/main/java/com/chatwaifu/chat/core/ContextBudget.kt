package com.chatwaifu.chat.core

/**
 * Description: 上下文裁剪策略。
 *
 * 因为历史统一在客户端维护（[ChatSession]），裁剪也就必须在客户端做。
 * 改造前这件事散在 ChatGPTNetService（固定截 100 条）和 AssistantMessageManager
 * （按 `ChatGPTData.MAX_SEND_LIMIT` 算 token）两处，且都写死了 gpt-3.5 的 4096 窗口。
 *
 * Author: Voine
 * Date: 2026/8/6
 */

/**
 * 粗略 token 估算。
 *
 * 不引 tokenizer：真正的 BPE 词表每家不同（还会随模型变），几 MB 的词表带进 APK
 * 换来的精度对「裁历史」这件事没有意义。这里按经验值估，宁可高估：
 * CJK 字符约 1 token，其余字符约 1/4 token。
 */
object TokenEstimator {

    fun estimate(text: String): Int {
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (isCjk(ch)) cjk++ else other++
        }
        return cjk + (other + 3) / 4
    }

    fun estimate(message: ChatMessage): Int {
        var sum = MESSAGE_OVERHEAD_TOKENS
        for (content in message.contents) {
            sum += when (content) {
                is ChatContent.Text -> estimate(content.text)
                is ChatContent.Thinking -> estimate(content.text)
                is ChatContent.ToolCall -> estimate(content.name) + estimate(content.argumentsJson)
                is ChatContent.ToolResult -> estimate(content.output)
                // 图片/音频/文档的 token 数由基座侧的切片规则决定，客户端算不准。
                // 给一个偏大的固定值，保证「带附件的历史」不会把窗口撑爆。
                is ChatContent.Image -> IMAGE_TOKENS_GUESS
                is ChatContent.Audio -> AUDIO_TOKENS_GUESS
                is ChatContent.Doc -> DOC_TOKENS_GUESS
            }
        }
        return sum
    }

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x3040..0x30FF ||   // 日文平假名 / 片假名
            code in 0x4E00..0x9FFF ||      // CJK 统一表意文字
            code in 0x3400..0x4DBF ||      // CJK 扩展 A
            code in 0xAC00..0xD7AF ||      // 韩文
            code in 0xFF00..0xFFEF         // 全角标点
    }

    /** 每条消息的角色、分隔符等固定开销。 */
    private const val MESSAGE_OVERHEAD_TOKENS = 4
    private const val IMAGE_TOKENS_GUESS = 1_600
    private const val AUDIO_TOKENS_GUESS = 1_000
    private const val DOC_TOKENS_GUESS = 3_000
}

/**
 * 按 token 预算裁历史。
 *
 * @param maxPromptTokens 允许发出去的 prompt 上限。
 * @param maxMessages 条数硬上限，防止一堆短消息把请求体撑得很长。
 */
class ContextBudget(
    val maxPromptTokens: Int = DEFAULT_MAX_PROMPT_TOKENS,
    val maxMessages: Int = DEFAULT_MAX_MESSAGES,
) {

    /**
     * 从后往前保留，返回**时间正序**的结果。
     *
     * 两个必须遵守的结构约束（不满足会被基座直接拒）：
     * - 首条必须是 user 消息（Anthropic 强制；OpenAI 宽松但语义上也该如此）
     * - assistant 的 tool_use 和后续的 tool_result 不能被切散，所以裁到 TOOL 消息时
     *   连它前面那条 assistant 一起丢
     */
    fun trim(history: List<ChatMessage>, systemPrompt: String?): List<ChatMessage> {
        var remaining = maxPromptTokens - (systemPrompt?.let { TokenEstimator.estimate(it) } ?: 0)
        val kept = ArrayDeque<ChatMessage>()

        for (message in history.asReversed()) {
            if (kept.size >= maxMessages) break
            val cost = TokenEstimator.estimate(message)
            // 最后一条（也就是本轮用户输入）无论多长都得留下，否则请求没有意义；
            // 真的超窗就让基座返回 ContextOverflow，由调用方决定怎么办
            if (cost > remaining && kept.isNotEmpty()) break
            kept.addFirst(message)
            remaining -= cost
        }

        // 丢掉开头不成对的 assistant / tool 残片
        while (kept.isNotEmpty() && kept.first().role != ChatRole.USER) {
            kept.removeFirst()
        }
        return kept.toList()
    }

    companion object {
        /** 保守值，绝大多数 2026 年的模型窗口都远大于此；调大只影响成本不影响正确性。 */
        const val DEFAULT_MAX_PROMPT_TOKENS = 24_000
        const val DEFAULT_MAX_MESSAGES = 100

        /** 按模型窗口推一个预算，留 1/4 给输出。 */
        fun forContextWindow(contextTokens: Int?): ContextBudget =
            if (contextTokens == null) ContextBudget()
            else ContextBudget(maxPromptTokens = (contextTokens * 3) / 4)
    }
}
