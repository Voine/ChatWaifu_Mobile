package com.chatwaifu.log.room

import android.content.Context
import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.ChatLogRepository
import com.chatwaifu.log.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Description: [ChatLogRepository] 的 Room 实现。取代原来的 `ChatLogDbManager`。
 *
 * 老实现每个方法都 `ChatDatabase.getDataBase(context).chatMessageDao()` 重新取一遍，
 * 这里 `by lazy` 拿一次。
 *
 * Author: Voine
 * Date: 2026/8/7
 */
class RoomChatLogRepository(context: Context) : ChatLogRepository {

    private val dao: ChatMessageDao by lazy {
        ChatDatabase.getDataBase(context).chatMessageDao()
    }

    override suspend fun insertChatLog(entry: ChatLogEntry): Long =
        dao.insertWithAttachments(
            msg = entry.toEntity(),
            // messageId 在事务里回填，这里传 0 占位
            attachments = entry.attachments.map { it.toEntity(ownerMessageId = 0) },
        )

    override suspend fun updateChatLog(entry: ChatLogEntry) {
        dao.updateWithAttachments(
            msg = entry.toEntity(),
            attachments = entry.attachments.map { it.toEntity(ownerMessageId = entry.id) },
        )
    }

    override suspend fun getRecentChatLog(characterId: String, limit: Int): List<ChatLogEntry> =
        dao.loadRecent(characterId, limit).map { it.toDomain() }

    override suspend fun getOlderChatLog(
        characterId: String,
        beforeId: Long,
        limit: Int,
    ): List<ChatLogEntry> = dao.loadOlder(characterId, beforeId, limit).map { it.toDomain() }

    override fun observeChatLog(characterId: String, limit: Int): Flow<List<ChatLogEntry>> =
        dao.observeRecent(characterId, limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun deleteChatLog(characterId: String) {
        dao.deleteByCharacter(characterId)
    }

    override suspend fun failDanglingStreams(): Int =
        dao.markDanglingAsFailed(MessageStatus.STREAMING.name, MessageStatus.FAILED.name)

    override suspend fun referencedAttachmentPaths(): Set<String> =
        dao.allReferencedPaths().toSet()

    override suspend fun attachmentPathsOf(characterId: String): Set<String> =
        dao.attachmentPathsOf(characterId).toSet()

    override suspend fun rememberRemoteFileId(
        relPath: String,
        fileId: String,
        providerId: String,
        expiresAt: Long,
    ) {
        dao.updateRemoteFileId(relPath, fileId, providerId, expiresAt)
    }
}
