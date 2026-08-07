package com.chatwaifu.log.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chatwaifu.log.AttachmentKind
import com.chatwaifu.log.AttachmentRef

/**
 * Description: 附件的落盘结构。
 *
 * **为什么是子表而不是消息表上的一个 JSON 列**：GC。删角色/删消息之后要清理孤儿文件，
 * 需要能「列出所有仍被引用的文件」——JSON 列意味着全表扫 + 逐行解析。
 * 子表一句 `select relPath from chat_attachment` 就够。
 *
 * `ON DELETE CASCADE` 只清行，**文件还得单独删**，见 `AttachmentStore.gc()`。
 *
 * Author: Voine
 * Date: 2026/8/7
 */
@Entity(
    tableName = AttachmentEntity.TABLE,
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["messageId"])],
)
internal data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: Long,
    val kind: String,
    val relPath: String,
    val mime: String,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val remoteFileId: String?,
    val remoteProvider: String?,
    val remoteExpiresAt: Long,
) {
    companion object {
        const val TABLE = "chat_attachment"
    }
}

internal fun AttachmentEntity.toDomain(): AttachmentRef = AttachmentRef(
    id = id,
    messageId = messageId,
    kind = runCatching { AttachmentKind.valueOf(kind) }.getOrDefault(AttachmentKind.DOC),
    relPath = relPath,
    mime = mime,
    byteSize = byteSize,
    width = width,
    height = height,
    durationMs = durationMs,
    remoteFileId = remoteFileId,
    remoteProvider = remoteProvider,
    remoteExpiresAt = remoteExpiresAt,
)

internal fun AttachmentRef.toEntity(ownerMessageId: Long): AttachmentEntity = AttachmentEntity(
    id = id,
    messageId = ownerMessageId,
    kind = kind.name,
    relPath = relPath,
    mime = mime,
    byteSize = byteSize,
    width = width,
    height = height,
    durationMs = durationMs,
    remoteFileId = remoteFileId,
    remoteProvider = remoteProvider,
    remoteExpiresAt = remoteExpiresAt,
)
