package com.chatwaifu.log.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Description: Room 手写迁移。
 *
 * 为什么不用 auto-migration：1→2 里有一步是 `sendFromMe: Boolean` → `role: String` 的
 * **值变换**，auto-migration 只能加删改列、搬不了值。既然已经要手写，
 * 顺便把表名和列名一起改干净。
 *
 * **注意**：老库（v1）压根没有 migration 也没有 `fallbackToDestructiveMigration`，
 * 所以在这之前加任何一列都会让老用户升级即崩。这个文件就是那条通道。
 *
 * Author: Voine
 * Date: 2026/8/7
 */
internal object Migrations {

    /**
     * v1 → v2。
     *
     * - 表 `ChatMessage` → `chat_message`
     * - `characterName` → `characterId`（值原样搬：角色层现在还没有真 uuid，
     *   名字就是当时的身份。见 docs/chat-storage.md「characterId 这道缝」）
     * - `chatMessage` → `text`
     * - `sendFromMe` → `role`：1 → `USER`，0 → `ASSISTANT`
     * - 新增 provider / model / source / status / thinking 五组字段，历史行填默认值
     * - 新增 `(characterId, timeline)` 复合索引
     *
     * `id` 一并搬过去，保持消息 id 稳定——将来 `chat_attachment` 要按它做外键。
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_message` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `characterId` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `timeline` INTEGER NOT NULL,
                    `promptTokens` INTEGER NOT NULL,
                    `completionTokens` INTEGER NOT NULL,
                    `providerId` TEXT,
                    `model` TEXT,
                    `source` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `thinkingText` TEXT,
                    `thinkingOpaque` TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `chat_message` (
                    `id`, `characterId`, `role`, `text`, `timeline`,
                    `promptTokens`, `completionTokens`,
                    `providerId`, `model`, `source`, `status`,
                    `thinkingText`, `thinkingOpaque`
                )
                SELECT
                    `id`,
                    `characterName`,
                    CASE WHEN `sendFromMe` != 0 THEN 'USER' ELSE 'ASSISTANT' END,
                    `chatMessage`,
                    `timeline`,
                    `promptTokens`,
                    `completionTokens`,
                    NULL, NULL, 'TYPED', 'OK', NULL, NULL
                FROM `ChatMessage`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `ChatMessage`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_message_characterId_timeline` " +
                    "ON `chat_message` (`characterId`, `timeline`)"
            )
        }
    }

    /**
     * v2 → v3：新增 `chat_attachment` 子表。纯建表，没有数据搬迁。
     *
     * `ON DELETE CASCADE` 要真的生效还得**运行时打开外键约束**——
     * SQLite 默认是关的，Room 在 `openHelper` 里会开，但迁移过程中不保证，
     * 所以这里只负责建表，约束靠 Room 打开后的连接生效。
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_attachment` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `messageId` INTEGER NOT NULL,
                    `kind` TEXT NOT NULL,
                    `relPath` TEXT NOT NULL,
                    `mime` TEXT NOT NULL,
                    `byteSize` INTEGER NOT NULL,
                    `width` INTEGER,
                    `height` INTEGER,
                    `durationMs` INTEGER,
                    `remoteFileId` TEXT,
                    `remoteProvider` TEXT,
                    `remoteExpiresAt` INTEGER NOT NULL,
                    FOREIGN KEY(`messageId`) REFERENCES `chat_message`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_attachment_messageId` " +
                    "ON `chat_attachment` (`messageId`)"
            )
        }
    }

    /**
     * v3 → v4：`chat_attachment` 加六列，全部**纯加列**，所以能用 `ALTER TABLE ADD COLUMN`
     * 而不必重建表。
     *
     * - `sampleRate` / `channels`：音频参数，判「能不能直接发」要用
     * - `sourceRelPath` / `posMs`：派生关系（视频抽帧 / 抽音轨），见 [com.chatwaifu.log.AttachmentRef]
     * - `origMime` / `origByteSize`：用户原本给的形态
     *
     * 五列可空所以历史行自然是 NULL；`origByteSize` 是 NOT NULL，
     * 必须给 `DEFAULT 0` —— 老行没有这个信息，0 就是「未记录」这个语义。
     * 加 NOT NULL 列不带默认值，SQLite 会直接拒绝执行这条 ALTER。
     *
     * **实体里 `origByteSize` 不能声明默认值**：Room 校验 schema 时比对的是列定义，
     * 这里写了 `DEFAULT 0` 而实体没有对应的 `@ColumnInfo(defaultValue = "0")`，
     * identityHash 就对不上、启动即崩。所以下面 ALTER 里的默认值和实体的
     * `@ColumnInfo(defaultValue = "0")` 是一对，改一个必须改另一个。
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chat_attachment` ADD COLUMN `sampleRate` INTEGER")
            db.execSQL("ALTER TABLE `chat_attachment` ADD COLUMN `channels` INTEGER")
            db.execSQL("ALTER TABLE `chat_attachment` ADD COLUMN `sourceRelPath` TEXT")
            db.execSQL("ALTER TABLE `chat_attachment` ADD COLUMN `posMs` INTEGER")
            db.execSQL("ALTER TABLE `chat_attachment` ADD COLUMN `origMime` TEXT")
            db.execSQL(
                "ALTER TABLE `chat_attachment` ADD COLUMN `origByteSize` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * v4 → v5：新增 `memory_fact`（L2 长期记忆，见 docs/memory.md）。
     *
     * 纯建表，不动任何已有数据，所以没有数据兼容风险。三处要和
     * [MemoryFactEntity] 严格对齐，改一个必须改另一个：
     * - `importance` / `pinned` 的 `DEFAULT 0` ↔ 实体上的 `@ColumnInfo(defaultValue = "0")`
     * - 外键是 `SET NULL` 不是 `CASCADE`（记忆是派生事实，不随来源消息消失）
     * - `(characterId, slot)` 的 UNIQUE 索引是 upsert 语义的执行者，不能漏
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_fact` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `characterId` TEXT NOT NULL,
                    `slot` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `importance` INTEGER NOT NULL DEFAULT 0,
                    `pinned` INTEGER NOT NULL DEFAULT 0,
                    `sourceMessageId` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`sourceMessageId`) REFERENCES `chat_message`(`id`)
                        ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_fact_characterId_slot` " +
                    "ON `memory_fact` (`characterId`, `slot`)"
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
