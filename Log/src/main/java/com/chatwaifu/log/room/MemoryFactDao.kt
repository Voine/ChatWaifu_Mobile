package com.chatwaifu.log.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Description: MemoryFactDao
 *
 * 排序在 SQL 里做而不是取出来再排：`pinned DESC, importance DESC, updatedAt DESC`
 * 就是注入 prompt 时的截断顺序，调用方不该有机会弄错。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
@Dao
internal interface MemoryFactDao {

    @Query(ORDERED_QUERY)
    suspend fun facts(characterId: String): List<MemoryFactEntity>

    @Query(ORDERED_QUERY)
    fun observeFacts(characterId: String): Flow<List<MemoryFactEntity>>

    @Query("select * from memory_fact where characterId = :characterId and slot = :slot limit 1")
    suspend fun findBySlot(characterId: String, slot: String): MemoryFactEntity?

    @Insert
    suspend fun insert(fact: MemoryFactEntity): Long

    @Query(
        """
        update memory_fact
        set content = :content, importance = :importance,
            sourceMessageId = :sourceMessageId, updatedAt = :updatedAt
        where id = :id
        """
    )
    suspend fun updateContent(
        id: Long,
        content: String,
        importance: Int,
        sourceMessageId: Long?,
        updatedAt: Long,
    )

    /**
     * 抽取器发起的删除，**跳过 pinned**。
     *
     * 条件写在 SQL 里而不是先查再判断：那样是两次查询，中间被用户在记忆页 pin 住会漏。
     */
    @Query("delete from memory_fact where characterId = :characterId and slot = :slot and pinned = 0")
    suspend fun deleteBySlotUnpinned(characterId: String, slot: String): Int

    @Query("delete from memory_fact where id = :id")
    suspend fun deleteById(id: Long)

    @Query("update memory_fact set pinned = :pinned, updatedAt = :updatedAt where id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long)

    @Query("delete from memory_fact where characterId = :characterId")
    suspend fun deleteAll(characterId: String)

    /**
     * 按 `(characterId, slot)` upsert。
     *
     * 没用 `@Insert(onConflict = REPLACE)`：REPLACE 是「删了再插」，会换掉自增 id、
     * 丢掉 `createdAt` 和用户设的 `pinned`，还会顺带触发外键动作。
     * 这里手动分支，保住那三样。
     */
    @Transaction
    suspend fun upsert(fact: MemoryFactEntity) {
        val existing = findBySlot(fact.characterId, fact.slot)
        if (existing == null) {
            insert(fact)
        } else {
            updateContent(
                id = existing.id,
                content = fact.content,
                importance = fact.importance,
                sourceMessageId = fact.sourceMessageId,
                updatedAt = fact.updatedAt,
            )
        }
    }

    companion object {
        private const val ORDERED_QUERY =
            "select * from memory_fact where characterId = :characterId " +
                "order by pinned desc, importance desc, updatedAt desc"
    }
}
