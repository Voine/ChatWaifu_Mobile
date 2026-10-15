package com.chatwaifu.mobile.data.memory

import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.ChatLogRole
import com.chatwaifu.log.MessageStatus

/**
 * Description: L1 切段规则。纯函数，单测守着。
 *
 * **两条规则结合**（docs/memory.md 第七节）：
 * 1. **按时间**：相邻两条消息隔了超过 [GAP_MS] 就是两次聊天；最后一段在「现在」也已经
 *    冷了 [GAP_MS] 以上才算结束。这让一段摘要对应一次真实的聊天，读起来像日记
 * 2. **按条数兜底**：一段聊到 [MAX_MESSAGES] 条还没断，就从前面切一块先封起来。
 *    这是给 L0 淘汰兜底的——工作记忆最多留 `ContextBudget.DEFAULT_MAX_MESSAGES` 条，
 *    只要 [MAX_MESSAGES] 比它小，被挤出窗口的原文一定已经写进了摘要，
 *    不会出现「窗口里看不到、摘要里也没有」的空洞
 *
 * Author: Voine
 * Date: 2026/10/15
 */
internal object EpisodeSegmenter {

    /** 隔多久算「另一次聊天」 */
    const val GAP_MS = 3L * 60 * 60 * 1000

    /** 一段最多多少条。必须明显小于工作记忆的条数上限（100），见类注释第 2 条 */
    const val MAX_MESSAGES = 40

    /** 少于这么多条的段不值得写日记（比如只说了一句「在吗」就走了） */
    const val MIN_MESSAGES = 2

    /**
     * @param pending 摘要游标之后的消息，时间正序
     * @return 已经可以封存的段，时间正序。还在进行中的那段不返回
     */
    fun closedSegments(
        pending: List<ChatLogEntry>,
        now: Long,
        gapMs: Long = GAP_MS,
        maxMessages: Int = MAX_MESSAGES,
    ): List<List<ChatLogEntry>> {
        // 正在流式输出的那条（以及它之后的）是这一轮还没结束的部分，不碰
        val settled = pending.takeWhile { it.status != MessageStatus.STREAMING }
            .filter { it.role == ChatLogRole.USER || it.role == ChatLogRole.ASSISTANT }
        if (settled.isEmpty()) return emptyList()

        val runs = mutableListOf<MutableList<ChatLogEntry>>(mutableListOf(settled.first()))
        settled.zipWithNext().forEach { (prev, next) ->
            if (next.timeline - prev.timeline > gapMs) runs += mutableListOf(next) else runs.last() += next
        }

        val closed = mutableListOf<List<ChatLogEntry>>()
        runs.forEachIndexed { index, run ->
            val isOpen = index == runs.lastIndex && now - run.last().timeline <= gapMs
            var rest: List<ChatLogEntry> = run
            while (rest.size > maxMessages) {
                val cut = cutPoint(rest, maxMessages)
                closed += rest.subList(0, cut)
                rest = rest.subList(cut, rest.size)
            }
            if (!isOpen) closed += rest
        }
        return closed
    }

    /**
     * 在 [max] 附近找一个「下一条是 user」的位置切，别把一问一答拆到两段里。
     * 找不到（一长串都是同一方）就硬切在 [max]。
     */
    private fun cutPoint(run: List<ChatLogEntry>, max: Int): Int {
        for (i in max downTo max / 2) {
            if (run[i].role == ChatLogRole.USER) return i
        }
        return max
    }
}
