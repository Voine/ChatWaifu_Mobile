package com.chatwaifu.log.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Description: ChatMessageDao
 *
 * 所有带 `LIMIT` 的查询**必须**带 `ORDER BY`：没有它 SQLite 按 rowid 返回，
 * 截到的是最老的那一批。这是 bug #1（见 docs/chat-storage.md），
 * 后果是对话超过 limit 之后模型永远只能看到开头。
 *
 * 带附件的读方法一律 `@Transaction`：`@Relation` 是两次查询，
 * 中间被写入插队会读出不一致的组合。
 *
 * Author: Voine
 * Date: 2023/3/13
 */
@Dao
internal interface ChatMessageDao {

    @Insert
    suspend fun insert(msg: ChatMessageEntity): Long

    @Update
    suspend fun update(msg: ChatMessageEntity)

    @Insert
    suspend fun insertAttachments(rows: List<AttachmentEntity>)

    @Query("delete from chat_attachment where messageId = :messageId")
    suspend fun deleteAttachmentsOf(messageId: Long)

    /**
     * 插入消息 + 它的附件，一个事务里完成。
     *
     * 附件的 `messageId` 在这里才能填——它依赖消息插入后回填的自增 id。
     */
    @Transaction
    suspend fun insertWithAttachments(
        msg: ChatMessageEntity,
        attachments: List<AttachmentEntity>,
    ): Long {
        val messageId = insert(msg)
        if (attachments.isNotEmpty()) {
            // id 归零：这些行是新插的，带着旧 id 会撞主键
            insertAttachments(attachments.map { it.copy(id = 0, messageId = messageId) })
        }
        return messageId
    }

    /** 整条覆盖消息 + 附件（先清后插）。流式收尾把 `STREAMING` 改成 `OK` 时用。 */
    @Transaction
    suspend fun updateWithAttachments(
        msg: ChatMessageEntity,
        attachments: List<AttachmentEntity>,
    ) {
        update(msg)
        deleteAttachmentsOf(msg.id)
        if (attachments.isNotEmpty()) {
            insertAttachments(attachments.map { it.copy(id = 0, messageId = msg.id) })
        }
    }

    /** 取最近 [limit] 条，返回时间正序（先 DESC 截取，外层再 ASC 转回来）。 */
    @Transaction
    @Query(
        """
        select * from (
            select * from chat_message where characterId = :characterId
            order by timeline desc, id desc limit :limit
        ) order by timeline asc, id asc
        """
    )
    suspend fun loadRecent(characterId: String, limit: Int): List<MessageWithAttachments>

    /** [loadRecent] 的订阅版，写入后自动重发。 */
    @Transaction
    @Query(
        """
        select * from (
            select * from chat_message where characterId = :characterId
            order by timeline desc, id desc limit :limit
        ) order by timeline asc, id asc
        """
    )
    fun observeRecent(characterId: String, limit: Int): Flow<List<MessageWithAttachments>>

    /**
     * 往**更老**的方向翻页。老实现是 `id > :id`，那是往更新的方向翻，
     * 和聊天记录页往上滚的语义相反。
     */
    @Transaction
    @Query(
        """
        select * from (
            select * from chat_message
            where characterId = :characterId and id < :beforeId
            order by id desc limit :limit
        ) order by id asc
        """
    )
    suspend fun loadOlder(
        characterId: String,
        beforeId: Long,
        limit: Int,
    ): List<MessageWithAttachments>

    @Query("delete from chat_message where characterId = :characterId")
    suspend fun deleteByCharacter(characterId: String)

    /** 把上次异常退出留下的 STREAMING 收尾成 FAILED，返回改了几条。 */
    @Query("update chat_message set status = :failed where status = :streaming")
    suspend fun markDanglingAsFailed(streaming: String, failed: String): Int

    /**
     * 全库仍被引用的附件相对路径。
     *
     * 给 GC 用：磁盘上不在这个集合里的文件就是孤儿。
     * 刻意**不按角色过滤**——孤儿判定必须是全局的，否则会误删别的角色的文件。
     */
    @Query("select relPath from chat_attachment")
    suspend fun allReferencedPaths(): List<String>

    /**
     * 记下基座侧的 file id。按 relPath 定位——附件行会在 [updateWithAttachments] 里被
     * 删掉重插，自增 id 不稳定，relPath 才是这份字节的身份。
     */
    @Query(
        """
        update chat_attachment
        set remoteFileId = :fileId, remoteProvider = :providerId, remoteExpiresAt = :expiresAt
        where relPath = :relPath
        """
    )
    suspend fun updateRemoteFileId(
        relPath: String,
        fileId: String,
        providerId: String,
        expiresAt: Long,
    )

    /** 某个角色的附件路径，删角色记录前先取出来好删文件。 */
    @Query(
        """
        select a.relPath from chat_attachment a
        inner join chat_message m on a.messageId = m.id
        where m.characterId = :characterId
        """
    )
    suspend fun attachmentPathsOf(characterId: String): List<String>
}
