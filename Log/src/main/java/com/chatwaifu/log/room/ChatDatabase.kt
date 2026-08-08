package com.chatwaifu.log.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Description: ChatDataBase
 *
 * 版本历史：
 * - v1（2023）：单表 `ChatMessage`，无索引、无迁移通道
 * - v2（2026）：改名 `chat_message`，`characterId` / `role` / provider / status / thinking，
 *   加复合索引。见 [Migrations.MIGRATION_1_2]
 * - v3（2026）：新增 `chat_attachment` 子表。见 [Migrations.MIGRATION_2_3]
 * - v4（2026）：`chat_attachment` 加 `sampleRate` / `channels`（音频参数）、
 *   `sourceRelPath` / `posMs`（派生关系，视频抽帧）、`origMime` / `origByteSize`（原始形态）。
 *   见 [Migrations.MIGRATION_3_4] 和 docs/media-pipeline.md
 *
 * **刻意不加 `fallbackToDestructiveMigration()`**：聊天记录是用户资产，
 * 宁可升级时抛一个能被发现的异常，也不要静默清空。
 *
 * Author: Voine
 * Date: 2023/3/13
 */
@Database(
    version = 4,
    entities = [ChatMessageEntity::class, AttachmentEntity::class],
    exportSchema = true,
)
internal abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private const val DB_NAME = "chat_log"

        @Volatile
        private var instance: ChatDatabase? = null

        fun getDataBase(context: Context): ChatDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): ChatDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ChatDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
