package com.chatwaifu.log

import kotlinx.coroutines.flow.Flow

/**
 * Description: 聊天记录读写的唯一入口。取代原来的 `IChatLogDbApi`。
 *
 * 和老接口的三个区别：
 *
 * 1. **没有默认实现**。老 `IChatLogDbApi` 每个方法都有默认空实现（`{}` / `emptyList()`），
 *    写一半的实现类能编译通过并静默返回空——排查起来很痛
 * 2. **`suspend` / `Flow`**。老实现全同步，UI 是 Compose，想要的是记录页订阅
 *    [observeChatLog] 自动更新，而不是每次手动重查
 * 3. **返回领域类型 [ChatLogEntry]**，不漏 Room 实体
 *
 * Author: Voine
 * Date: 2026/8/7
 */
interface ChatLogRepository {

    /** 插入一条，返回自增 id（后续 [updateChatLog] 要用）。 */
    suspend fun insertChatLog(entry: ChatLogEntry): Long

    /** 按 [ChatLogEntry.id] 整条覆盖。流式落库时用来把 `STREAMING` 改成 `OK`。 */
    suspend fun updateChatLog(entry: ChatLogEntry)

    /**
     * 取**最近** [limit] 条，**时间正序**（老的在前），可以直接喂给上下文。
     *
     * 排序由实现保证，调用方不要再排一遍——见 docs/chat-storage.md 里 bug #1。
     */
    suspend fun getRecentChatLog(characterId: String, limit: Int): List<ChatLogEntry>

    /** 往**更老**的方向翻页：取 [beforeId] 之前的 [limit] 条，返回时间正序。 */
    suspend fun getOlderChatLog(characterId: String, beforeId: Long, limit: Int): List<ChatLogEntry>

    /**
     * 订阅最近 [limit] 条，时间正序。写入后自动重发。
     *
     * 刻意**不提供**「订阅全部」：聊天记录页原来是 `getAllChatLog().reversed()`
     * 整表加载进内存，历史长了会卡。要更老的走 [getOlderChatLog] 分页。
     */
    fun observeChatLog(characterId: String, limit: Int): Flow<List<ChatLogEntry>>

    /** 清掉某个角色的全部记录。 */
    suspend fun deleteChatLog(characterId: String)

    /**
     * 把上次异常退出留下的 `STREAMING` 记录收尾成 `FAILED`。
     * 应该在启动时调一次，否则历史里会留下状态不明的半截回复。
     */
    suspend fun failDanglingStreams(): Int

    /**
     * **全库**仍被引用的附件相对路径。给孤儿文件 GC 用。
     *
     * 刻意不按角色过滤——孤儿判定必须是全局的，
     * 按角色算会把别人的文件误判成孤儿删掉。
     */
    suspend fun referencedAttachmentPaths(): Set<String>

    /** 某个角色的附件路径。删它的记录**之前**先取出来，好把文件一起删掉。 */
    suspend fun attachmentPathsOf(characterId: String): Set<String>

    /**
     * 记下某个附件在基座侧的 file id，下一轮直接引用而不用再内联一遍。
     *
     * **按 [AttachmentRef.relPath] 定位而不是按 id**：[updateChatLog] 是「删掉旧附件行再插新的」，
     * 附件自增 id 会变，relPath 才是这份字节的稳定标识。
     *
     * @param expiresAt 过期时间戳，0 表示不过期（OpenAI 的 file id 就是这样）。
     */
    suspend fun rememberRemoteFileId(
        relPath: String,
        fileId: String,
        providerId: String,
        expiresAt: Long = 0,
    )
}
