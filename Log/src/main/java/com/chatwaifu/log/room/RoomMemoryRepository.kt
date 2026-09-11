package com.chatwaifu.log.room

import android.content.Context
import com.chatwaifu.log.MemoryFact
import com.chatwaifu.log.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Description: [MemoryRepository] 的 Room 实现。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
class RoomMemoryRepository(context: Context) : MemoryRepository {

    private val dao: MemoryFactDao by lazy {
        ChatDatabase.getDataBase(context).memoryFactDao()
    }

    override suspend fun facts(characterId: String): List<MemoryFact> =
        dao.facts(characterId).map { it.toDomain() }

    override fun observeFacts(characterId: String): Flow<List<MemoryFact>> =
        dao.observeFacts(characterId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsert(fact: MemoryFact) = dao.upsert(fact.toEntity())

    override suspend fun deleteBySlot(characterId: String, slot: String): Int =
        dao.deleteBySlotUnpinned(characterId, slot)

    override suspend fun deleteById(id: Long) = dao.deleteById(id)

    override suspend fun setPinned(id: Long, pinned: Boolean) =
        dao.setPinned(id, pinned, System.currentTimeMillis())

    override suspend fun deleteAll(characterId: String) = dao.deleteAll(characterId)
}
