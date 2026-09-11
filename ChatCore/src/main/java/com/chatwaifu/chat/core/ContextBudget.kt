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
                is ChatContent.Image -> estimateImage(content)
                is ChatContent.Audio -> estimateAudio(content)
                // 文档没有可用的维度信息（页数要解析 PDF 才知道），只能给固定值
                is ChatContent.Doc -> DOC_TOKENS_GUESS
            }
        }
        return sum
    }

    /**
     * 图片 token 估算。
     *
     * 各家公式不同但同数量级：OpenAI 按 512px 切片计价（`ceil(w/512)*ceil(h/512)*每片 + 基数`），
     * Anthropic 约 `w*h/750`，Gemini 按 768px 分块。这里取 `w*h/750`——
     * 在常见尺寸上它是三者里偏大的那个，**宁可高估**：低估会让裁剪以为还塞得下，
     * 结果换来基座一个 `ContextOverflow`。
     *
     * `detail = LOW` 时各家都只发一张缩略图，成本是个小常量。
     *
     * 尺寸未知（调用方没填）时回落到 [IMAGE_TOKENS_GUESS]。
     */
    private fun estimateImage(image: ChatContent.Image): Int {
        if (image.detail == ImageDetail.LOW) return IMAGE_LOW_DETAIL_TOKENS
        val w = image.width ?: return IMAGE_TOKENS_GUESS
        val h = image.height ?: return IMAGE_TOKENS_GUESS
        if (w <= 0 || h <= 0) return IMAGE_TOKENS_GUESS
        return (w.toLong() * h / IMAGE_PIXELS_PER_TOKEN)
            .coerceAtLeast(IMAGE_LOW_DETAIL_TOKENS.toLong())
            .toInt()
    }

    /** 音频成本基本是时长的线性函数。时长未知时回落到固定值。 */
    private fun estimateAudio(audio: ChatContent.Audio): Int {
        val ms = audio.durationMs ?: return AUDIO_TOKENS_GUESS
        if (ms <= 0) return AUDIO_TOKENS_GUESS
        return (ms * AUDIO_TOKENS_PER_SECOND / 1000).toInt().coerceAtLeast(1)
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

    /** 尺寸/时长未知时的回落值。 */
    private const val IMAGE_TOKENS_GUESS = 1_600
    private const val AUDIO_TOKENS_GUESS = 1_000
    private const val DOC_TOKENS_GUESS = 3_000

    private const val IMAGE_PIXELS_PER_TOKEN = 750
    private const val IMAGE_LOW_DETAIL_TOKENS = 85
    private const val AUDIO_TOKENS_PER_SECOND = 32
}

/**
 * 按 token 预算裁历史。
 *
 * @param maxPromptTokens 允许发出去的 prompt 上限。
 * @param maxMessages 条数硬上限，防止一堆短消息把请求体撑得很长。
 * @param memoryTokens 留给记忆块的额度，见 [MemoryContributor]。从 [maxPromptTokens]
 *   里先扣掉，这样记忆和工作记忆各有各的额度，超支各自降级而不是互相挤。
 */
class ContextBudget(
    val maxPromptTokens: Int = DEFAULT_MAX_PROMPT_TOKENS,
    val maxMessages: Int = DEFAULT_MAX_MESSAGES,
    val memoryTokens: Int = DEFAULT_MEMORY_TOKENS,
) {

    /**
     * 从后往前保留，返回**时间正序**的结果。
     *
     * ## 为什么要传 [previousStart]
     *
     * 改造前这个方法是无状态的「从尾部往前塞到装不下为止」，效果是**每轮从头部掉一条**，
     * prompt 的前缀每轮都在变。而所有基座的缓存都是按前缀匹配的，前缀一动整段作废——
     * 命中率基本是 0。
     *
     * 现在起点是粘性的：先拿 [previousStart] 试算，**塞得下就原样返回**；塞不下才把起点
     * 按 [EVICT_CHUNK] 整块往后推。于是每淘汰一次能换来连续 [EVICT_CHUNK] 轮前缀不变。
     * 起点**只增不减**（历史只会增长，往回退没有意义，只会让前缀反复横跳）。
     *
     * 两个必须遵守的结构约束（不满足会被基座直接拒）：
     * - 首条必须是 user 消息（Anthropic 强制；OpenAI 宽松但语义上也该如此）
     * - assistant 的 tool_use 和后续的 tool_result 不能被切散，所以裁到 TOOL 消息时
     *   连它前面那条 assistant 一起丢
     *
     * @param previousStart 上一轮返回的 [TrimResult.startIndex]，首次传 0
     */
    fun trim(
        history: List<ChatMessage>,
        systemPrompt: String?,
        previousStart: Int = 0,
    ): TrimResult {
        val budget = maxPromptTokens -
            memoryTokens -
            (systemPrompt?.let { TokenEstimator.estimate(it) } ?: 0)

        var start = previousStart.coerceIn(0, maxOf(0, history.size - 1))
        while (true) {
            val kept = collectFrom(history, start, budget)
            // 装得下，或者已经退无可退（只剩本轮输入）：就用这个起点
            if (kept.size >= history.size - start || start >= history.size - 1) {
                return TrimResult(dropDanglingHead(kept), start)
            }
            // 装不下：整块往后推，再试
            start = (start + EVICT_CHUNK).coerceAtMost(history.size - 1)
        }
    }

    /**
     * 从 [start] 开始、在 [budget] 内**从后往前**收集，返回时间正序。
     *
     * 从后往前是因为「最近的对话最重要」；`start` 只是个不可越过的下界。
     */
    private fun collectFrom(
        history: List<ChatMessage>,
        start: Int,
        budget: Int,
    ): ArrayDeque<ChatMessage> {
        var remaining = budget
        val kept = ArrayDeque<ChatMessage>()
        for (i in history.indices.reversed()) {
            if (i < start) break
            if (kept.size >= maxMessages) break
            val cost = TokenEstimator.estimate(history[i])
            // 最后一条（也就是本轮用户输入）无论多长都得留下，否则请求没有意义；
            // 真的超窗就让基座返回 ContextOverflow，由调用方决定怎么办
            if (cost > remaining && kept.isNotEmpty()) break
            kept.addFirst(history[i])
            remaining -= cost
        }
        return kept
    }

    /** 丢掉开头不成对的 assistant / tool 残片 */
    private fun dropDanglingHead(kept: ArrayDeque<ChatMessage>): List<ChatMessage> {
        while (kept.isNotEmpty() && kept.first().role != ChatRole.USER) {
            kept.removeFirst()
        }
        return kept.toList()
    }

    companion object {
        /** 保守值，绝大多数 2026 年的模型窗口都远大于此；调大只影响成本不影响正确性。 */
        const val DEFAULT_MAX_PROMPT_TOKENS = 24_000
        const val DEFAULT_MAX_MESSAGES = 100

        /**
         * 记忆块额度。够装几十条事实，再多就该考虑摘要压缩而不是继续加额度了。
         */
        const val DEFAULT_MEMORY_TOKENS = 600

        /**
         * 一次淘汰多少条。越大越省缓存、越浪费上下文；20 条大约是 10 轮对话。
         */
        const val EVICT_CHUNK = 20

        /** 按模型窗口推一个预算，留 1/4 给输出。 */
        fun forContextWindow(contextTokens: Int?): ContextBudget =
            if (contextTokens == null) ContextBudget()
            else ContextBudget(maxPromptTokens = (contextTokens * 3) / 4)
    }
}

/**
 * [ContextBudget.trim] 的结果。
 *
 * @param messages 时间正序，可直接塞进 [ChatRequest]
 * @param startIndex 本次实际使用的历史起点。**调用方要存下来下轮传回**，
 *   否则粘性起点就退化回「每轮掉一条」的老行为。
 */
data class TrimResult(
    val messages: List<ChatMessage>,
    val startIndex: Int,
) {
    /** 起点大于 0 说明前面有内容被丢掉了，要给模型一个断层提示 */
    val truncated: Boolean get() = startIndex > 0
}
