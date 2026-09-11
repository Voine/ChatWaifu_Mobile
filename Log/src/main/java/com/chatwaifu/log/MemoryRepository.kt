package com.chatwaifu.log

import kotlinx.coroutines.flow.Flow

/**
 * Description: 长期记忆读写的唯一入口。和 [ChatLogRepository] 同一套约定：
 * suspend / Flow、返回领域类型、**没有默认实现**。
 *
 * （老 `IChatLogDbApi` 每个方法都给了默认空实现，写一半的实现类能编译通过并静默返回空，
 * 排查起来很痛。这个坑不要再踩一次。）
 *
 * Author: Voine
 * Date: 2026/9/11
 */
interface MemoryRepository {

    /** 按 `pinned` 降序 → `importance` 降序 → `updatedAt` 降序。注入 prompt 时按这个顺序截断 */
    suspend fun facts(characterId: String): List<MemoryFact>

    /** 记忆页订阅用，排序同 [facts] */
    fun observeFacts(characterId: String): Flow<List<MemoryFact>>

    /**
     * 按 `(characterId, slot)` upsert。
     *
     * 已存在时**保留原 `id` / `createdAt` / `pinned`**，只更新内容、重要度、来源和
     * `updatedAt`——`pinned` 是用户的选择，抽取器不该把它冲掉。
     */
    suspend fun upsert(fact: MemoryFact)

    /**
     * 抽取器发起的删除。**`pinned` 的行不会被删**。
     *
     * @return 实际删除的行数，0 表示不存在或者被 pin 住了
     */
    suspend fun deleteBySlot(characterId: String, slot: String): Int

    /** 用户在记忆页手动删，无视 `pinned` */
    suspend fun deleteById(id: Long)

    suspend fun setPinned(id: Long, pinned: Boolean)

    /**
     * 清掉某个角色的全部记忆。
     *
     * 删角色时**必须显式调**：`memory_fact` 没有指向角色的外键（角色层根本没有表），
     * 不会被任何 cascade 带走。
     */
    suspend fun deleteAll(characterId: String)
}
