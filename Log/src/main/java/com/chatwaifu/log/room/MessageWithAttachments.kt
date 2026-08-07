package com.chatwaifu.log.room

import androidx.room.Embedded
import androidx.room.Relation
import com.chatwaifu.log.ChatLogEntry

/**
 * Description: 消息 + 它的附件。Room 用 `@Relation` 自动做第二次查询并按 messageId 归组。
 *
 * 用 `@Relation` 而不是手写 join：join 出来的是笛卡尔积，一条带 3 个附件的消息会变成 3 行，
 * 还得自己去重归组。代价是多一次查询，但归组逻辑不用自己写。
 *
 * 用到它的 DAO 方法必须加 `@Transaction`，否则两次查询之间可能被写入插队。
 *
 * Author: Voine
 * Date: 2026/8/7
 */
internal data class MessageWithAttachments(
    @Embedded val message: ChatMessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val attachments: List<AttachmentEntity>,
)

internal fun MessageWithAttachments.toDomain(): ChatLogEntry =
    message.toDomain().copy(attachments = attachments.map { it.toDomain() })
