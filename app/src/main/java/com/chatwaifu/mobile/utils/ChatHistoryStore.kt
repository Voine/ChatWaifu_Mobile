package com.chatwaifu.mobile.utils

import android.content.Context
import android.util.Log
import com.chatwaifu.chat.core.ChatContent
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRole
import com.chatwaifu.chat.core.MediaSource
import com.chatwaifu.chat.core.MediaUploadPolicy
import com.chatwaifu.chat.core.TokenUsage
import com.chatwaifu.log.AttachmentKind
import com.chatwaifu.log.AttachmentRef
import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.ChatLogRepository
import com.chatwaifu.log.ChatLogRole
import com.chatwaifu.log.MessageSource
import com.chatwaifu.log.MessageStatus
import com.chatwaifu.log.room.RoomChatLogRepository
import com.chatwaifu.mobile.data.attachment.AttachmentStore

/**
 * Description: 聊天记录的落库与恢复。ChatCore 的 [ChatMessage] ↔ Log 的 [ChatLogEntry]。
 *
 * 和老实现（`AssistantMessageManager`）的区别：
 *
 * 1. **不再依赖 ChatGPT 模块的 DTO**。老实现直接吃 `ChatGPTResponseData`，
 *    从 `choices[0].message.content` 和 `usage` 里掏数据，换基座就得改。
 * 2. **恢复历史时用户消息不再被丢掉**。老的 `getSendAssistantList()` 过滤条件是
 *    `!it.sendFromMe`，也就是只把模型自己说过的话回传给模型 ——
 *    模型看到的是一段自己的独白，多轮指代（"她多大了"）根本接不住。
 * 3. **不再漏 Room 实体**，两侧都是领域类型。
 * 4. **多模态附件跟着消息一起进出**，重启不再"忘记"刚看过的图。
 *
 * 上下文裁剪不在这里做，交给 [com.chatwaifu.chat.core.ContextBudget] 按 token 算。
 * 排序也不在这里做，[ChatLogRepository.getRecentChatLog] 已经保证「最近 N 条、时间正序」。
 *
 * **characterId 这道缝**：角色层目前没有稳定 uuid，`CharacterModel.name` 同时是身份、
 * 显示名和 `models/` 目录名，所以这里暂时把 name 当 id 传下去。存储层只把它当不透明键，
 * 将来 meta.json 长出 uuid 时只改这个映射 + 一次数据迁移。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class ChatHistoryStore(
    context: Context,
    private val repository: ChatLogRepository = RoomChatLogRepository(context),
    private val attachments: AttachmentStore = AttachmentStore(context),
) {

    private var currentCharacterId: String = ""

    /**
     * 读出某个角色最近的历史，**时间正序**（老的最前面），
     * 可以直接喂给 [com.chatwaifu.chat.core.ChatSession.restore]。
     *
     * @param providerId 当前基座。thinking block 的签名是绑基座+模型的，
     *   只有当条记录来自同一个基座时才回传，否则丢掉（见 [toCoreMessage]）。
     */
    suspend fun load(characterName: String, providerId: String? = null): List<ChatMessage> {
        currentCharacterId = characterName
        val stored = repository.getRecentChatLog(currentCharacterId, limit = HISTORY_LIMIT)
        Log.d(TAG, "load ${stored.size} messages for $characterName")
        return stored.mapNotNull { it.toCoreMessage(providerId) }
    }

    suspend fun appendUser(
        text: String,
        source: MessageSource = MessageSource.TYPED,
        attachmentRefs: List<AttachmentRef> = emptyList(),
    ) {
        insert(
            role = ChatLogRole.USER,
            text = text,
            source = source,
            attachmentRefs = attachmentRefs,
            // 附件在、文本空也要落库，否则这一轮的图就丢了
            allowBlankText = attachmentRefs.isNotEmpty(),
        )
    }

    /**
     * 流式开始时先占一行 `STREAMING`，返回它的 id。
     *
     * 有这一步，流中途被杀进程时历史里留下的是一条半截回复（能看出发生了什么），
     * 而不是凭空少一轮。收尾调 [finishAssistant]。
     */
    suspend fun beginAssistant(providerId: String?, model: String?): Long = insert(
        role = ChatLogRole.ASSISTANT,
        text = "",
        status = MessageStatus.STREAMING,
        providerId = providerId,
        model = model,
        allowBlankText = true,
    )

    /** 把 [beginAssistant] 占的那行补成最终内容。[messageId] 无效时退化成直接插入。 */
    suspend fun finishAssistant(
        messageId: Long,
        message: ChatMessage,
        usage: TokenUsage? = null,
        providerId: String? = null,
        model: String? = null,
    ) {
        if (currentCharacterId.isEmpty()) {
            Log.e(TAG, "no character selected, drop message")
            return
        }
        val thinking = message.contents.filterIsInstance<ChatContent.Thinking>().firstOrNull()
        val entry = ChatLogEntry(
            id = messageId,
            characterId = currentCharacterId,
            role = ChatLogRole.ASSISTANT,
            text = message.text,
            timeline = System.currentTimeMillis(),
            promptTokens = usage?.promptTokens ?: 0,
            completionTokens = usage?.completionTokens ?: 0,
            providerId = providerId,
            model = model,
            status = MessageStatus.OK,
            thinkingText = thinking?.text,
            thinkingOpaque = thinking?.opaque,
        )
        if (messageId == ChatLogEntry.NO_ID) {
            repository.insertChatLog(entry)
        } else {
            repository.updateChatLog(entry)
        }
    }

    /** 流失败时把占位行标成 `FAILED`，保留已收到的片段。 */
    suspend fun failAssistant(messageId: Long, partialText: String) {
        if (messageId == ChatLogEntry.NO_ID || currentCharacterId.isEmpty()) return
        repository.updateChatLog(
            ChatLogEntry(
                id = messageId,
                characterId = currentCharacterId,
                role = ChatLogRole.ASSISTANT,
                text = partialText,
                timeline = System.currentTimeMillis(),
                status = MessageStatus.FAILED,
            )
        )
    }

    /** 把上次异常退出留下的 `STREAMING` 记录收尾成 `FAILED`。启动时调一次。 */
    suspend fun failDanglingStreams() {
        val fixed = repository.failDanglingStreams()
        if (fixed > 0) Log.w(TAG, "marked $fixed dangling streaming message(s) as failed")
    }

    /**
     * 删掉某个角色的全部记录，**连附件文件一起**。
     * FK cascade 只清行，文件得自己删——所以要先取路径再删记录。
     */
    suspend fun clearCharacter(characterName: String) {
        val paths = repository.attachmentPathsOf(characterName)
        repository.deleteChatLog(characterName)
        attachments.deletePaths(paths)
    }

    /** 回收没有任何记录引用的附件文件。启动时调一次即可。 */
    suspend fun gcAttachments() {
        attachments.gc(repository.referencedAttachmentPaths())
    }

    /**
     * 附件够大就预上传，并把换回来的 file id 记进附件行，之后每轮直接引用。
     *
     * 上传失败**不算错误**：内联是所有基座都走得通的那条路，
     * 一次上传失败不该让用户发不出这条消息。
     *
     * 调用时机是「附件已经落库之后」——上传是优化，落库是正确性，顺序不能反。
     */
    suspend fun uploadIfWorthwhile(provider: ChatProvider, ref: AttachmentRef) {
        if (!MediaUploadPolicy.shouldUpload(ref.byteSize)) return
        val file = attachments.resolve(ref.relPath) ?: return
        val remote = try {
            provider.upload(file.readBytes(), ref.mime, file.name)
        } catch (e: Throwable) {
            Log.w(TAG, "pre-upload failed, will inline instead", e)
            null
        } ?: return
        repository.rememberRemoteFileId(
            relPath = ref.relPath,
            fileId = remote.id,
            providerId = provider.id.key,
        )
    }

    private suspend fun insert(
        role: ChatLogRole,
        text: String,
        promptTokens: Int = 0,
        completionTokens: Int = 0,
        source: MessageSource = MessageSource.TYPED,
        status: MessageStatus = MessageStatus.OK,
        providerId: String? = null,
        model: String? = null,
        attachmentRefs: List<AttachmentRef> = emptyList(),
        allowBlankText: Boolean = false,
    ): Long {
        if (currentCharacterId.isEmpty()) {
            Log.e(TAG, "no character selected, drop message")
            return ChatLogEntry.NO_ID
        }
        if (text.isBlank() && !allowBlankText) return ChatLogEntry.NO_ID
        return repository.insertChatLog(
            ChatLogEntry(
                characterId = currentCharacterId,
                role = role,
                text = text,
                timeline = System.currentTimeMillis(),
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                providerId = providerId,
                model = model,
                source = source,
                status = status,
                attachments = attachmentRefs,
            )
        )
    }

    /**
     * [ChatLogEntry] → core [ChatMessage]。
     *
     * 三条丢弃规则：
     * - `SYSTEM` 不回传（system prompt 是配置不是记录）；`TOOL` 单独恢复会让请求非法
     *   （缺配对的 `ToolCall`）
     * - `FAILED` / `STREAMING` 的行不回传：半截回复喂回去会让模型接着编
     * - thinking 的 [ChatLogEntry.thinkingOpaque] **只在同一个基座下回传**。
     *   签名是绑基座+模型的，跨基座重放会校验失败直接 400
     */
    private fun ChatLogEntry.toCoreMessage(currentProviderId: String?): ChatMessage? {
        if (status != MessageStatus.OK) return null

        val coreRole = when (role) {
            ChatLogRole.USER -> ChatRole.USER
            ChatLogRole.ASSISTANT -> ChatRole.ASSISTANT
            ChatLogRole.SYSTEM, ChatLogRole.TOOL -> return null
        }

        val contents = mutableListOf<ChatContent>()

        // thinking 必须在最前面，这是 Anthropic 的硬要求（和 MessageAccumulator 一致）
        val thinking = thinkingText
        val opaque = thinkingOpaque
        if (!thinking.isNullOrBlank() && opaque != null &&
            providerId != null && providerId == currentProviderId
        ) {
            contents += ChatContent.Thinking(thinking, opaque)
        }

        if (text.isNotBlank()) contents += ChatContent.Text(text)
        contents += attachments.mapNotNull { it.toCoreContent(currentProviderId) }

        return if (contents.isEmpty()) null else ChatMessage(coreRole, contents)
    }

    /**
     * [AttachmentRef] → core [ChatContent]。
     *
     * **文件没了就返回 null**（专属目录的内容用户能在系统设置里清掉），
     * 让这条消息退化成纯文本，而不是把一个读不出来的路径发给基座。
     *
     * 上传过且 file id 还没过期的，优先走 [MediaSource.RemoteFileId]：历史每轮都要重发全量，
     * 内联的话同一张图会被 base64 一遍又一遍地传上去。校验绑了 provider ——
     * file id 是基座本地的，换基座重放必然 404。
     */
    private fun AttachmentRef.toCoreContent(currentProviderId: String?): ChatContent? {
        val remote = currentProviderId
            ?.takeIf { remoteIdValidFor(it, System.currentTimeMillis()) }
            ?.let { MediaSource.RemoteFileId(remoteFileId!!) }

        val source = remote ?: run {
            val file = attachments.resolve(relPath) ?: run {
                Log.w(TAG, "attachment file missing, degraded to text: $relPath")
                return null
            }
            MediaSource.LocalPath(file.absolutePath, mime)
        }
        return when (kind) {
            AttachmentKind.IMAGE -> ChatContent.Image(source, width = width, height = height)
            AttachmentKind.AUDIO -> ChatContent.Audio(
                source = source,
                format = mime.substringAfterLast('/'),
                durationMs = durationMs,
            )
            AttachmentKind.DOC -> ChatContent.Doc(
                source = source,
                fileName = relPath.substringAfterLast('/'),
                mimeType = mime,
            )
            // 视频入库这一环还没做（要转码 + 时间轴），到这里说明数据不该存在
            AttachmentKind.VIDEO -> {
                Log.w(TAG, "video attachment not supported yet, skipped: $relPath")
                null
            }
        }
    }

    companion object {
        private const val TAG = "ChatHistoryStore"

        /** 读这么多条进内存，真正发出去多少由 ContextBudget 按 token 决定。 */
        private const val HISTORY_LIMIT = 200
    }
}
