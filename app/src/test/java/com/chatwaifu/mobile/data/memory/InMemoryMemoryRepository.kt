package com.chatwaifu.mobile.data.memory

import com.chatwaifu.log.MemoryFact
import com.chatwaifu.log.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * JVM 测试用的 [MemoryRepository]。语义照 `MemoryFactDao` 抄：
 * `(characterId, slot)` upsert 保留 id / createdAt / pinned，抽取器的删除跳过 pinned，排序同 SQL。
 */
internal class InMemoryMemoryRepository : MemoryRepository {
    private val rows = MutableStateFlow<List<MemoryFact>>(emptyList())
    private var nextId = 1L

    private fun ordered(characterId: String, all: List<MemoryFact>) = all
        .filter { it.characterId == characterId }
        .sortedWith(
            compareByDescending<MemoryFact> { it.pinned }
                .thenByDescending { it.importance }
                .thenByDescending { it.updatedAt }
        )

    override suspend fun facts(characterId: String) = ordered(characterId, rows.value)

    override fun observeFacts(characterId: String): Flow<List<MemoryFact>> =
        rows.map { ordered(characterId, it) }

    override suspend fun upsert(fact: MemoryFact) {
        val existing = rows.value.firstOrNull {
            it.characterId == fact.characterId && it.slot == fact.slot
        }
        rows.value = if (existing == null) {
            rows.value + fact.copy(id = nextId++)
        } else {
            rows.value.map {
                if (it.id != existing.id) it
                else it.copy(
                    content = fact.content,
                    importance = fact.importance,
                    sourceMessageId = fact.sourceMessageId,
                    subject = fact.subject,
                    dueAt = fact.dueAt,
                    updatedAt = fact.updatedAt,
                )
            }
        }
    }

    override suspend fun deleteBySlot(characterId: String, slot: String): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot {
            it.characterId == characterId && it.slot == slot && !it.pinned
        }
        return before - rows.value.size
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(pinned = pinned) else it }
    }

    override suspend fun deleteAll(characterId: String) {
        rows.value = rows.value.filterNot { it.characterId == characterId }
    }
}
