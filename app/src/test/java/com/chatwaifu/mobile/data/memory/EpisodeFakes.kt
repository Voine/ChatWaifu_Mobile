package com.chatwaifu.mobile.data.memory

import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.ChatLogRepository
import com.chatwaifu.log.ChatLogRole
import com.chatwaifu.log.EpisodeRepository
import com.chatwaifu.log.MemoryEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** JVM 测试用的 [EpisodeRepository]，软删除语义照 `MemoryEpisodeDao` 抄。 */
internal class InMemoryEpisodeRepository : EpisodeRepository {
    val rows = MutableStateFlow<List<MemoryEpisode>>(emptyList())
    private var nextId = 1L

    private fun live(characterId: String) = rows.value
        .filter { it.characterId == characterId && it.summary.isNotEmpty() }
        .sortedBy { it.endMessageId }

    override suspend fun episodes(characterId: String) = live(characterId)

    override fun observeEpisodes(characterId: String): Flow<List<MemoryEpisode>> =
        rows.map { live(characterId).asReversed() }

    override suspend fun recentEpisodes(characterId: String, limit: Int) = live(characterId).takeLast(limit)

    override suspend fun coveredUntil(characterId: String): Long =
        rows.value.filter { it.characterId == characterId }.maxOfOrNull { it.endMessageId } ?: 0

    override suspend fun insert(episode: MemoryEpisode): Long {
        val id = nextId++
        rows.value = rows.value + episode.copy(id = id)
        return id
    }

    override suspend fun search(characterId: String, terms: List<String>, limit: Int) =
        live(characterId).asReversed()
            .filter { e -> terms.any { e.summary.contains(it) || e.keywords.contains(it) } }
            .take(limit)

    override suspend fun find(id: Long) = rows.value.firstOrNull { it.id == id && it.summary.isNotEmpty() }

    override suspend fun updateSummary(id: Long, summary: String) {
        rows.value = rows.value.map { if (it.id == id) it.copy(summary = summary) else it }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(summary = "", keywords = "") else it }
    }

    override suspend fun hasAny(characterId: String) = live(characterId).isNotEmpty()

    override suspend fun deleteAll(characterId: String) {
        rows.value = rows.value.filterNot { it.characterId == characterId }
    }
}

/** 只实现记忆层用到的读方法 */
internal class FakeChatLogRepository(private val entries: List<ChatLogEntry>) : ChatLogRepository {

    override suspend fun getChatLogAfter(characterId: String, afterId: Long, limit: Int) =
        entries.filter { it.characterId == characterId && it.id > afterId }.sortedBy { it.id }.take(limit)

    override suspend fun getChatLogRange(characterId: String, fromId: Long, toId: Long) =
        entries.filter { it.characterId == characterId && it.id in fromId..toId }.sortedBy { it.id }

    override suspend fun insertChatLog(entry: ChatLogEntry): Long = error("unused")
    override suspend fun updateChatLog(entry: ChatLogEntry) = error("unused")
    override suspend fun getRecentChatLog(characterId: String, limit: Int): List<ChatLogEntry> = error("unused")
    override suspend fun getOlderChatLog(characterId: String, beforeId: Long, limit: Int): List<ChatLogEntry> =
        error("unused")
    override fun observeChatLog(characterId: String, limit: Int): Flow<List<ChatLogEntry>> = error("unused")
    override suspend fun deleteChatLog(characterId: String) = error("unused")
    override suspend fun failDanglingStreams(): Int = error("unused")
    override suspend fun referencedAttachmentPaths(): Set<String> = error("unused")
    override suspend fun attachmentPathsOf(characterId: String): Set<String> = error("unused")
    override suspend fun rememberRemoteFileId(relPath: String, fileId: String, providerId: String, expiresAt: Long) =
        error("unused")
}

internal const val MINUTE = 60_000L
internal const val HOUR = 60 * MINUTE

/** 一问一答 n 轮，每条间隔 [step]，id 从 [firstId] 连续编 */
internal fun chat(
    characterId: String,
    firstId: Long,
    startAt: Long,
    turns: Int,
    step: Long = MINUTE,
    text: (Int) -> String = { "第${it}句" },
): List<ChatLogEntry> = (0 until turns * 2).map { i ->
    ChatLogEntry(
        id = firstId + i,
        characterId = characterId,
        role = if (i % 2 == 0) ChatLogRole.USER else ChatLogRole.ASSISTANT,
        text = text(i),
        timeline = startAt + i * step,
    )
}
