package com.chatwaifu.log.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chatwaifu.log.MemoryFact

/**
 * Description: [MemoryFact] 的 Room 实体。**不跨模块边界**，和 room 包里其余类型一样 internal。
 *
 * 两个约束值得单独记：
 *
 * 1. `(characterId, slot)` 的 **UNIQUE 索引**是 upsert 语义的执行者。抽取器只要
 *    slot 认对了，新值就覆盖旧值，不可能出现同槽位两行并存。
 * 2. 外键是 **SET_NULL 不是 CASCADE**。理由见 [MemoryFact] 的 KDoc。
 *    SET_NULL 要求列可空，所以 `sourceMessageId` 是 `Long?`。
 *
 * `@ColumnInfo(defaultValue = ...)` 和建表 SQL 里的 `DEFAULT` 必须一一对应，
 * 否则 Room 校验 identityHash 时对不上、启动即崩（这个坑 MIGRATION_3_4 踩过一次）。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
@Entity(
    tableName = "memory_fact",
    indices = [
        Index(value = ["characterId", "slot"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceMessageId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
)
internal data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val characterId: String,
    val slot: String,
    val content: String,
    @ColumnInfo(defaultValue = "0")
    val importance: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
    val sourceMessageId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

internal fun MemoryFactEntity.toDomain(): MemoryFact = MemoryFact(
    id = id,
    characterId = characterId,
    slot = slot,
    content = content,
    importance = importance,
    pinned = pinned,
    sourceMessageId = sourceMessageId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun MemoryFact.toEntity(): MemoryFactEntity = MemoryFactEntity(
    id = id,
    characterId = characterId,
    slot = slot,
    content = content,
    importance = importance,
    pinned = pinned,
    sourceMessageId = sourceMessageId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
