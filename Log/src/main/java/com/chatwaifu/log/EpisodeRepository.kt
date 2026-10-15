package com.chatwaifu.log

import kotlinx.coroutines.flow.Flow

/**
 * Description: L1 情节摘要的读写入口。约定同 [MemoryRepository]：suspend / Flow、
 * 返回领域类型、**没有默认实现**。
 *
 * 和 [MemoryRepository] 分开是因为两层的用法完全不同：事实是「槽位 upsert」，
 * 摘要是「只追加的时间线 + 检索」，硬塞进一个接口会让两边的实现和测试替身都变臃肿。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
interface EpisodeRepository {

    /** 全部摘要，**时间正序**。一个角色几年也就几百段，整表读进内存做匹配是够的 */
    suspend fun episodes(characterId: String): List<MemoryEpisode>

    /** 记忆页订阅，**时间倒序**（新的在上面） */
    fun observeEpisodes(characterId: String): Flow<List<MemoryEpisode>>

    /** 最近 [limit] 段，时间正序。常驻 system 区的那几条 */
    suspend fun recentEpisodes(characterId: String, limit: Int): List<MemoryEpisode>

    /**
     * 已经被摘要覆盖到哪条消息。切段从这之后开始；没有任何摘要时返回 0。
     */
    suspend fun coveredUntil(characterId: String): Long

    /** 插入一段，返回 id */
    suspend fun insert(episode: MemoryEpisode): Long

    /** 摘要 / 关键词文本里 `LIKE` 任意一个词，按时间倒序取前 [limit] 条 */
    suspend fun search(characterId: String, terms: List<String>, limit: Int): List<MemoryEpisode>

    suspend fun find(id: Long): MemoryEpisode?

    /** 用户在记忆页改摘要 */
    suspend fun updateSummary(id: Long, summary: String)

    /**
     * 用户在记忆页删一段日记。**之后不会被重新摘要**（实现上是软删除，
     * 否则切段游标会回退，同一段聊天又被写成日记）。原文聊天记录不受影响
     */
    suspend fun deleteById(id: Long)

    /** 有没有任何（没被删的）日记 */
    suspend fun hasAny(characterId: String): Boolean

    /** 删角色时一起清。和 `memory_fact` 一样没有到角色的外键，必须显式调 */
    suspend fun deleteAll(characterId: String)
}
