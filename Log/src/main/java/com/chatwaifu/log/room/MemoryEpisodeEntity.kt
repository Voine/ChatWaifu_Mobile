package com.chatwaifu.log.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.chatwaifu.log.MemoryEpisode
import kotlinx.coroutines.flow.Flow

/**
 * Description: [MemoryEpisode] 的 Room 实体。internal，不跨模块边界。
 *
 * 刻意**没有**到 `chat_message` 的外键：[startMessageId] / [endMessageId] 是一个区间，
 * 不是一条引用，外键表达不了；而且摘要要比原文活得久（见 [MemoryEpisode] KDoc）。
 *
 * `(characterId, endMessageId)` 索引服务两个查询：切段游标 `max(endMessageId)`
 * 和「最近几段」。
 *
 * **用户删日记是软删除**（`summary` 置空，读的时候过滤掉）：游标就是 `max(endMessageId)`，
 * 真删掉最新那行游标会回退，刚删的那段会被重新摘要一遍——用户想忘掉的又被写回来了。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
@Entity(
    tableName = "memory_episode",
    indices = [Index(value = ["characterId", "endMessageId"])],
)
internal data class MemoryEpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: String,
    val startMessageId: Long,
    val endMessageId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val summary: String,
    val keywords: String,
    val createdAt: Long,
    val updatedAt: Long,
)

internal fun MemoryEpisodeEntity.toDomain() = MemoryEpisode(
    id = id,
    characterId = characterId,
    startMessageId = startMessageId,
    endMessageId = endMessageId,
    startedAt = startedAt,
    endedAt = endedAt,
    summary = summary,
    keywords = keywords,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun MemoryEpisode.toEntity() = MemoryEpisodeEntity(
    id = id,
    characterId = characterId,
    startMessageId = startMessageId,
    endMessageId = endMessageId,
    startedAt = startedAt,
    endedAt = endedAt,
    summary = summary,
    keywords = keywords,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

@Dao
internal interface MemoryEpisodeDao {

    @Query(
        "select * from memory_episode where characterId = :characterId and summary != '' " +
            "order by endMessageId asc"
    )
    suspend fun episodes(characterId: String): List<MemoryEpisodeEntity>

    @Query(
        "select * from memory_episode where characterId = :characterId and summary != '' " +
            "order by endMessageId desc"
    )
    fun observeEpisodes(characterId: String): Flow<List<MemoryEpisodeEntity>>

    @Query(
        """
        select * from (
            select * from memory_episode where characterId = :characterId and summary != ''
            order by endMessageId desc limit :limit
        ) order by endMessageId asc
        """
    )
    suspend fun recent(characterId: String, limit: Int): List<MemoryEpisodeEntity>

    @Query("select coalesce(max(endMessageId), 0) from memory_episode where characterId = :characterId")
    suspend fun coveredUntil(characterId: String): Long

    @Insert
    suspend fun insert(episode: MemoryEpisodeEntity): Long

    @Query("select * from memory_episode where id = :id and summary != ''")
    suspend fun find(id: Long): MemoryEpisodeEntity?

    /** 有没有任何可用的日记（软删除的不算）。决定要不要给模型声明回想工具 */
    @Query("select exists(select 1 from memory_episode where characterId = :characterId and summary != '')")
    suspend fun hasAny(characterId: String): Boolean

    @Query("update memory_episode set summary = :summary, updatedAt = :updatedAt where id = :id")
    suspend fun updateSummary(id: Long, summary: String, updatedAt: Long)

    /** 软删除，见类注释。行留着当游标 */
    @Query("update memory_episode set summary = '', keywords = '', updatedAt = :updatedAt where id = :id")
    suspend fun forget(id: Long, updatedAt: Long)

    @Query("delete from memory_episode where characterId = :characterId")
    suspend fun deleteAll(characterId: String)
}
