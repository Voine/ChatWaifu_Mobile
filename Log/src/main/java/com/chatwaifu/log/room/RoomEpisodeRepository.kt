package com.chatwaifu.log.room

import android.content.Context
import com.chatwaifu.log.EpisodeRepository
import com.chatwaifu.log.MemoryEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Description: [EpisodeRepository] 的 Room 实现。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
class RoomEpisodeRepository(context: Context) : EpisodeRepository {

    private val dao: MemoryEpisodeDao by lazy {
        ChatDatabase.getDataBase(context).memoryEpisodeDao()
    }

    override suspend fun episodes(characterId: String): List<MemoryEpisode> =
        dao.episodes(characterId).map { it.toDomain() }

    override fun observeEpisodes(characterId: String): Flow<List<MemoryEpisode>> =
        dao.observeEpisodes(characterId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun recentEpisodes(characterId: String, limit: Int): List<MemoryEpisode> =
        dao.recent(characterId, limit).map { it.toDomain() }

    override suspend fun coveredUntil(characterId: String): Long = dao.coveredUntil(characterId)

    override suspend fun insert(episode: MemoryEpisode): Long = dao.insert(episode.toEntity())

    /**
     * 词数不定，`@Query` 拼不出可变个 `OR LIKE`，所以整表取出来在内存里匹配——
     * 语义和 `LIKE '%词%'` 一样，量级上也一样是全表扫（docs/memory.md 4.3：不上 FTS）。
     * 一个角色的摘要几年也就几百行。
     */
    override suspend fun search(
        characterId: String,
        terms: List<String>,
        limit: Int,
    ): List<MemoryEpisode> {
        val cleaned = terms.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return emptyList()
        return dao.episodes(characterId)
            .asReversed()
            .filter { row -> cleaned.any { row.summary.contains(it) || row.keywords.contains(it) } }
            .take(limit)
            .map { it.toDomain() }
    }

    override suspend fun find(id: Long): MemoryEpisode? = dao.find(id)?.toDomain()

    override suspend fun updateSummary(id: Long, summary: String) =
        dao.updateSummary(id, summary, System.currentTimeMillis())

    override suspend fun deleteById(id: Long) = dao.forget(id, System.currentTimeMillis())

    override suspend fun hasAny(characterId: String): Boolean = dao.hasAny(characterId)

    override suspend fun deleteAll(characterId: String) = dao.deleteAll(characterId)
}
