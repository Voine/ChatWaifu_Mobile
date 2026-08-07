package com.chatwaifu.log.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.ChatLogRole
import com.chatwaifu.log.MessageSource
import com.chatwaifu.log.MessageStatus

/**
 * Description: 聊天记录的落盘结构。
 *
 * 和老 `ChatMessage` 实体的区别：
 * - `characterName` → [characterId]：老的那个字段同时是分区键、UI 显示名和 `models/`
 *   目录名，改个角色名历史就成孤儿，两个同名导入模型的历史会合并
 * - `sendFromMe: Boolean` → [role]：ChatCore 有四种 role，布尔表达不了 TOOL
 * - 加了 `(characterId, timeline)` 复合索引：老的一个索引都没有，每个查询全表扫
 * - 加了 provider / model / source / status / thinking 五组字段，理由见 [ChatLogEntry]
 *
 * 枚举一律存 `name` 字符串而不是 ordinal：ordinal 会随枚举顺序变动而错位，
 * 而且直接看 db 文件时是不可读的数字。
 *
 * Author: Voine
 * Date: 2026/8/7
 */
@Entity(
    tableName = ChatMessageEntity.TABLE,
    indices = [Index(value = ["characterId", "timeline"])],
)
internal data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: String,
    val role: String,
    val text: String,
    val timeline: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val providerId: String?,
    val model: String?,
    val source: String,
    val status: String,
    val thinkingText: String?,
    val thinkingOpaque: String?,
) {
    companion object {
        const val TABLE = "chat_message"
    }
}

internal fun ChatMessageEntity.toDomain(): ChatLogEntry = ChatLogEntry(
    id = id,
    characterId = characterId,
    role = enumOrDefault(role, ChatLogRole.ASSISTANT),
    text = text,
    timeline = timeline,
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    providerId = providerId,
    model = model,
    source = enumOrDefault(source, MessageSource.TYPED),
    status = enumOrDefault(status, MessageStatus.OK),
    thinkingText = thinkingText,
    thinkingOpaque = thinkingOpaque,
)

internal fun ChatLogEntry.toEntity(): ChatMessageEntity = ChatMessageEntity(
    id = id,
    characterId = characterId,
    role = role.name,
    text = text,
    timeline = timeline,
    promptTokens = promptTokens,
    completionTokens = completionTokens,
    providerId = providerId,
    model = model,
    source = source.name,
    status = status.name,
    thinkingText = thinkingText,
    thinkingOpaque = thinkingOpaque,
)

/**
 * 读到不认识的枚举值时回退而不是抛。
 * 降级发生在「装了新版本、又回滚到旧版本」这类场景——旧代码不该因为一条记录崩掉整个列表。
 */
private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
