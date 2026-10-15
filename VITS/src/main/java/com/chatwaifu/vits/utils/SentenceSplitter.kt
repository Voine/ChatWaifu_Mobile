package com.chatwaifu.vits.utils

/**
 * Description: 增量断句。LLM 流每来一个 delta 就 [append]，凑满一句立刻吐出来，
 * 让「翻译 → 合成 → 播放」不必等整段回复结束（T2 按句流水线的入口）。
 *
 * 规则和整段切分时完全一致，所以同一段文本不管是一次性给还是拆成任意 delta 喂，
 * 切出来的句子都一样（单测守着这一条）：
 * - 句末标点（中日英：。！？；.!? 和换行）结句，**连续的结束符归同一句**
 *   （「真的吗？！」「……。」不会切出只有一个标点的碎句）
 * - 超过 [maxChars] 还没遇到标点就硬切 —— 模型偶尔吐没有标点的长串，
 *   单句推理时间是线性的，不切会让首句卡几十秒
 * - 只有标点 / 空白的片段不算一句
 * - **第一句可以提前在逗号处切**（[firstClauseMinChars]）：首声延迟 = 第一句的推理时间，
 *   而推理时间和句长成正比。第一句长到 [firstClauseMinChars] 之后遇到「、，,」就先吐，
 *   让嘴先张开；之后的句子回到正常规则，免得整段读得磕磕绊绊
 *
 * 唯一的「等待」是结束符后面那一个字符：要看到下一个字符不是结束符才能确定这句结束了。
 * 流结束时调 [finish] 把它和不带标点的尾巴一起吐出来。
 *
 * 不是线程安全的：调用方保证同一时刻只有一个协程在喂。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
class SentenceSplitter(
    private val maxChars: Int = DEFAULT_MAX_CHARS,
    /** 0 = 关闭首句提前切分 */
    private val firstClauseMinChars: Int = 0,
) {
    private val buffer = StringBuilder()
    private var emittedAny = false

    /** buffer 末尾是否已经是结束符（在等下一个字符确认这句真的结束了） */
    private var pendingEnd = false

    /** buffer 末尾是可以提前切的逗号（在等下一个字符确认它不是「1,000」里的千分位） */
    private var pendingClause = false

    /** 喂一段增量，返回这段增量**新凑满**的句子（可能为空，也可能不止一句）。 */
    fun append(delta: String): List<String> {
        if (delta.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        for (ch in delta) {
            val isEnd = ch in SENTENCE_ENDINGS
            if (pendingEnd && !isEnd) emit(out)
            if (pendingClause) {
                pendingClause = false
                // 逗号两边都是数字 → 千分位，不切
                val beforeComma = buffer.getOrNull(buffer.length - 2)
                if (!(ch.isDigit() && beforeComma?.isDigit() == true)) emit(out)
            }
            buffer.append(ch)
            pendingEnd = isEnd
            if (!pendingEnd && buffer.length >= maxChars) {
                emit(out)
            } else if (!emittedAny && firstClauseMinChars > 0 && ch in CLAUSE_BREAKS &&
                buffer.length >= firstClauseMinChars
            ) {
                pendingClause = true
            }
        }
        return out
    }

    /** 流结束：吐出剩下的一切（没有句末标点的尾巴也算一句）。之后可以当新的一段复用。 */
    fun finish(): List<String> {
        val out = mutableListOf<String>()
        emit(out)
        emittedAny = false
        return out
    }

    private fun emit(out: MutableList<String>) {
        val sentence = buffer.toString().trim()
        buffer.clear()
        pendingEnd = false
        pendingClause = false
        if (sentence.any { it !in SENTENCE_ENDINGS && it !in CLAUSE_BREAKS && !it.isWhitespace() }) {
            out += sentence
            emittedAny = true
        }
    }

    companion object {
        const val DEFAULT_MAX_CHARS = 60

        /**
         * 对话播报用的首句提前切分阈值。太短会切出「えっと、」这种两三个字的碎片，
         * 语调很怪；10 个字左右在日语里大致是一个完整短语。
         */
        const val DEFAULT_FIRST_CLAUSE_MIN_CHARS = 10

        private val CLAUSE_BREAKS = charArrayOf('、', '，', ',')

        private val SENTENCE_ENDINGS = charArrayOf(
            '。', '！', '？', '；', '\n',
            '.', '!', '?', ';',
        )

        /** 一次性切整段（不做首句提前切分，和流式的默认行为一致） */
        fun split(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<String> =
            SentenceSplitter(maxChars).run { append(text) + finish() }
    }
}
