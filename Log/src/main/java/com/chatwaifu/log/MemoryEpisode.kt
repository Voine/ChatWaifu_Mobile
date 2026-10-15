package com.chatwaifu.log

/**
 * Description: 一段情节摘要。L1 层，见 docs/memory.md 第七节。
 *
 * 一段 = 一次连续的聊天（中间隔超过几小时就算两段），或者聊得太长时按条数切出的一块。
 * [summary] 是角色第一人称的日记体，**双方说过的话都算**——她答应过什么、你们约定了什么，
 * 恰恰是关系连续性的一部分（L2 事实层只记对方的事实，这里补上「我们之间发生过什么」）。
 *
 * **分层回忆**：摘要是第一层，够用就不碰原文；要原话时按 [startMessageId]..[endMessageId]
 * 回 `chat_message` 取。所以这两个 id 是「回到原文的指针」，不是外键——
 * 删掉一段聊天记录不该让摘要跟着没（和 [MemoryFact.sourceMessageId] 的 SET NULL 同一个理由），
 * 只是那时候原文取不回来了，摘要还在。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
data class MemoryEpisode(
    val id: Long = NO_ID,
    val characterId: String,

    /** 覆盖的消息区间（含两端）。L1 切段以 [endMessageId] 为游标：之后的消息还没被摘要过 */
    val startMessageId: Long,
    val endMessageId: Long,

    /** 这段对话第一条 / 最后一条消息的时间，注入时显示成「10月12日 晚上」 */
    val startedAt: Long,
    val endedAt: Long,

    /** 第一人称日记体摘要。用户可以在记忆页改 */
    val summary: String,

    /**
     * 检索用的关键词，[KEYWORD_SEPARATOR] 分隔。摘要生成时顺带产出——
     * 中文不分词时拿用户原话去 `LIKE` 召回率很差，反过来拿关键词去匹配原话就准得多
     */
    val keywords: String,

    val createdAt: Long,
    val updatedAt: Long,
) {
    val keywordList: List<String>
        get() = keywords.split(KEYWORD_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val NO_ID = 0L
        const val KEYWORD_SEPARATOR = "\n"
    }
}
