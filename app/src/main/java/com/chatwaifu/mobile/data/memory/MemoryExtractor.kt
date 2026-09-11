package com.chatwaifu.mobile.data.memory

import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.log.MemoryFact

/**
 * Description: 从最近几轮对话里抽出长期记忆的操作。
 *
 * **为什么是接口**：第一个实现走 LLM（[LlmMemoryExtractor]），但端上小模型做抽取是
 * 明确规划里的下一步——这类任务（短输入、结构化输出、不要求文采）正是小模型的甜区，
 * 而且省掉一次网络往返。所以从第一天就把形状固定成可替换的。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
interface MemoryExtractor {

    /**
     * @param recentTurns 最近几轮原文，时间正序
     * @param existingFacts 该角色**当前已有的全部事实**。必须传 ——
     *   不给模型看已有槽位的话它只会不停 ADD，很快长出「猫的名字」和「宠物名」
     *   这种同义槽位，事实库再也收敛不回来
     * @return 要应用的操作；空表示这几轮没有值得记的东西（很常见，不是异常）
     */
    suspend fun extract(
        recentTurns: List<ChatMessage>,
        existingFacts: List<MemoryFact>,
    ): List<MemoryOp>
}

/**
 * 抽取结果。**刻意只有 Upsert / Delete，没有 Add**——
 * 「新增还是覆盖」由 `(characterId, slot)` 的唯一约束在数据库层决定，
 * 不让模型来判断它自己有没有见过这个槽位。
 */
sealed interface MemoryOp {

    /** 槽位不存在就插入，存在就覆盖内容（保留原 `pinned` 和 `createdAt`） */
    data class Upsert(
        val slot: String,
        val content: String,
        val importance: Int,
    ) : MemoryOp

    /** 事实已失效（"我把猫送人了"）。`pinned` 的行不会真的被删掉 */
    data class Delete(val slot: String) : MemoryOp
}
