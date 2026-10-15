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
import com.chatwaifu.log.MemoryRepository
import com.chatwaifu.log.MessageStatus
import com.chatwaifu.log.room.RoomChatLogRepository
import com.chatwaifu.log.room.RoomMemoryRepository
import com.chatwaifu.log.EpisodeRepository
import com.chatwaifu.log.room.RoomEpisodeRepository
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
 * **characterId 这道缝**：角色层已有稳定 ID，但 Room / Memory 本阶段不做身份迁移，
 * 因此这里的 `characterStorageKey` 仍是旧的角色目录键。每次写入都显式传入该键，
 * 避免快速切角色时旧请求把结果写到新角色分区。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class ChatHistoryStore(
    context: Context,
    private val repository: ChatLogRepository = RoomChatLogRepository(context),
    private val attachments: AttachmentStore = AttachmentStore(context),
    private val memory: MemoryRepository = RoomMemoryRepository(context),
    private val episodes: EpisodeRepository = RoomEpisodeRepository(context),
) {

    /**
     * 读出某个角色最近的历史，**时间正序**（老的最前面），
     * 可以直接喂给 [com.chatwaifu.chat.core.ChatSession.restore]。
     *
     * @param providerId 当前基座。thinking block 的签名是绑基座+模型的，
     *   只有当条记录来自同一个基座时才回传，否则丢掉（见 [toCoreMessage]）。
     * @param accepts 当前模型收哪些附件，见 [toModelContents]。
     */
    suspend fun load(
        characterStorageKey: String,
        providerId: String? = null,
        accepts: (AttachmentKind) -> Boolean = { true },
    ): List<ChatMessage> {
        val stored = repository.getRecentChatLog(characterStorageKey, limit = HISTORY_LIMIT)
        Log.d(TAG, "load ${stored.size} messages for $characterStorageKey")
        return stored.mapNotNull { it.toCoreMessage(providerId, accepts) }
    }

    /**
     * 读取 UI 历史最近的 [limit] 条；与模型上下文不同，这里保留带部分文本的 `FAILED` 记录。
     *
     * 和 [load] 分开取：模型上下文要 [HISTORY_LIMIT] 条给 ContextBudget 挑，
     * 而历史面板首屏只需要一屏多一点，更老的滑到顶再用 [loadOlderDisplayHistory] 翻。
     */
    suspend fun loadDisplayHistory(characterStorageKey: String, limit: Int): List<ChatLogEntry> =
        repository.getRecentChatLog(characterStorageKey, limit = limit)

    /** 历史面板往前翻一页：[beforeId] 之前的 [limit] 条，时间正序。 */
    suspend fun loadOlderDisplayHistory(
        characterStorageKey: String,
        beforeId: Long,
        limit: Int,
    ): List<ChatLogEntry> = repository.getOlderChatLog(characterStorageKey, beforeId, limit)

    suspend fun appendUser(
        characterStorageKey: String,
        text: String,
        source: MessageSource = MessageSource.TYPED,
        attachmentRefs: List<AttachmentRef> = emptyList(),
    ) {
        insert(
            role = ChatLogRole.USER,
            text = text,
            source = source,
            attachmentRefs = attachmentRefs,
            characterStorageKey = characterStorageKey,
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
    suspend fun beginAssistant(
        characterStorageKey: String,
        providerId: String?,
        model: String?,
    ): Long = insert(
        role = ChatLogRole.ASSISTANT,
        text = "",
        status = MessageStatus.STREAMING,
        providerId = providerId,
        model = model,
        characterStorageKey = characterStorageKey,
        allowBlankText = true,
    )

    /** 更新同一条 `STREAMING` 占位行，使 Room 始终保存当前已收到的完整片段。 */
    suspend fun updateStreamingAssistant(
        characterStorageKey: String,
        messageId: Long,
        partialText: String,
        providerId: String?,
        model: String?,
    ) {
        if (messageId == ChatLogEntry.NO_ID || characterStorageKey.isEmpty()) return
        repository.updateChatLog(
            ChatLogEntry(
                id = messageId,
                characterId = characterStorageKey,
                role = ChatLogRole.ASSISTANT,
                text = partialText,
                timeline = System.currentTimeMillis(),
                providerId = providerId,
                model = model,
                status = MessageStatus.STREAMING,
            )
        )
    }

    /** 把 [beginAssistant] 占的那行补成最终内容。[messageId] 无效时退化成直接插入。 */
    suspend fun finishAssistant(
        characterStorageKey: String,
        messageId: Long,
        message: ChatMessage,
        usage: TokenUsage? = null,
        providerId: String? = null,
        model: String? = null,
    ) {
        if (characterStorageKey.isEmpty()) {
            Log.e(TAG, "no character selected, drop message")
            return
        }
        val thinking = message.contents.filterIsInstance<ChatContent.Thinking>().firstOrNull()
        val entry = ChatLogEntry(
            id = messageId,
            characterId = characterStorageKey,
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
    suspend fun failAssistant(
        characterStorageKey: String,
        messageId: Long,
        partialText: String,
    ) {
        if (messageId == ChatLogEntry.NO_ID || characterStorageKey.isEmpty()) return
        repository.updateChatLog(
            ChatLogEntry(
                id = messageId,
                characterId = characterStorageKey,
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
     * 删掉某个角色的全部记录，**连附件文件和长期记忆一起**。
     *
     * 两件 cascade 管不到的事：
     * - 附件的**字节**在磁盘上，FK cascade 只清行，所以要先取路径再删记录
     * - `memory_fact` / `memory_episode` 根本没有指向角色的外键（角色层没有表），必须显式删
     */
    suspend fun clearCharacter(characterName: String) {
        val paths = repository.attachmentPathsOf(characterName)
        repository.deleteChatLog(characterName)
        attachments.deletePaths(paths)
        memory.deleteAll(characterName)
        episodes.deleteAll(characterName)
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
        characterStorageKey: String,
        allowBlankText: Boolean = false,
    ): Long {
        if (characterStorageKey.isEmpty()) {
            Log.e(TAG, "no character selected, drop message")
            return ChatLogEntry.NO_ID
        }
        if (text.isBlank() && !allowBlankText) return ChatLogEntry.NO_ID
        return repository.insertChatLog(
            ChatLogEntry(
                characterId = characterStorageKey,
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
    private fun ChatLogEntry.toCoreMessage(
        currentProviderId: String?,
        accepts: (AttachmentKind) -> Boolean,
    ): ChatMessage? {
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
        contents += attachmentContents(modelVisibleAttachments(), currentProviderId, accepts)

        return if (contents.isEmpty()) null else ChatMessage(coreRole, contents)
    }

    /**
     * 本轮刚导入的附件 → 发给模型的内容块。**和历史重放走同一套规则**
     * （[modelVisibleAttachments] 过滤父行、[attachmentContents] 按能力降级、
     * [toCoreContent] 选内联还是 file id），所以「这一轮看到的」和「下一轮重放时看到的」是同一份东西。
     *
     * @param accepts 当前模型收哪些附件。各家 provider 碰到不支持的块是**直接抛错**的，
     *   不过滤的话：在 Gemini 下发过一段语音，换到 Claude 之后每一轮都会因为重放这段历史失败；
     *   视频的音轨在 Claude 下也会把整轮打挂。
     */
    fun toModelContents(
        refs: List<AttachmentRef>,
        providerId: String?,
        accepts: (AttachmentKind) -> Boolean = { true },
    ): List<ChatContent> {
        val parentPaths = refs.mapNotNullTo(mutableSetOf()) { it.sourceRelPath }
        return attachmentContents(refs.filter { it.relPath !in parentPaths }, providerId, accepts)
    }

    /** 模型收不了的附件降级成占位文字，规则见 [ModelVisibility] */
    private fun attachmentContents(
        visible: List<AttachmentRef>,
        providerId: String?,
        accepts: (AttachmentKind) -> Boolean,
    ): List<ChatContent> = ModelVisibility.degrade(visible, accepts) { it.toCoreContent(providerId) }

    /**
     * UI 上要显示的附件：只要**用户直接给的**那几个（视频的抽帧和音轨不单独显示），
     * 每个配一张预览图——图片是它自己，视频是最早的那一帧，音频 / PDF 没有。
     * 文件被清掉的附件整个不返回。
     */
    fun displayAttachments(entry: ChatLogEntry): List<DisplayAttachment> {
        val firstFrameOf = entry.attachments
            .filter { it.kind == AttachmentKind.IMAGE && it.sourceRelPath != null }
            .sortedBy { it.posMs ?: 0L }
            .groupBy { it.sourceRelPath!! }
            .mapValues { (_, frames) -> frames.first().relPath }
        return entry.attachments
            .filter { it.sourceRelPath == null }
            .mapNotNull { ref -> displayAttachment(ref, firstFrameOf[ref.relPath]) }
    }

    /**
     * 刚导入的一组 ref（第一个是父附件，见 `AttachmentIngest.Progress.Success`）→ UI 用的预览。
     * 和 [displayAttachments] 同一套规则，输入条上和历史里看到的是同一个样子。
     */
    fun displayAttachment(refs: List<AttachmentRef>): DisplayAttachment? {
        val parent = refs.firstOrNull() ?: return null
        val firstFrame = refs.drop(1)
            .filter { it.kind == AttachmentKind.IMAGE }
            .minByOrNull { it.posMs ?: 0L }
        return displayAttachment(parent, firstFrame?.relPath)
    }

    private fun displayAttachment(ref: AttachmentRef, firstFramePath: String?): DisplayAttachment? {
        val file = attachments.resolve(ref.relPath) ?: return null
        val preview = when (ref.kind) {
            AttachmentKind.IMAGE -> file
            AttachmentKind.VIDEO -> firstFramePath?.let(attachments::resolve)
            AttachmentKind.AUDIO, AttachmentKind.DOC -> null
        }
        return DisplayAttachment(ref, preview, ref.durationMs)
    }

    /** 一个要显示的附件。[preview] 为 null 时 UI 画类型标签 */
    data class DisplayAttachment(
        val ref: AttachmentRef,
        val preview: java.io.File?,
        val durationMs: Long?,
    )

    /**
     * 过滤掉**派生物的父行**。
     *
     * 视频是一个视频落多行：父行（原视频）+ N 行抽帧 + 1 行音轨，
     * 而模型看的是帧和音轨，父行只给 UI 回放（见 `VideoNormalizer` 的 KDoc）。
     *
     * 判定方式是「这条消息里有没有别的行认它当 source」，而不是硬编码
     * `kind == VIDEO`：以后要是图像也长出派生物（比如超大图切片），这里不用改。
     * 反过来，一个 VIDEO 行**没有**任何派生物时它不会被过滤掉，
     * 会走到 [toCoreContent] 的 VIDEO 分支被拒——那是老数据（v4 之前存进去的视频）
     * 应有的下场。
     */
    private fun ChatLogEntry.modelVisibleAttachments(): List<AttachmentRef> {
        val parentPaths = attachments.mapNotNullTo(mutableSetOf()) { it.sourceRelPath }
        return if (parentPaths.isEmpty()) {
            attachments
        } else {
            attachments.filter { it.relPath !in parentPaths }
        }
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
            // 视频本体不发给基座——只有 Gemini 收视频，而抽出来的帧和音轨已经
            // 作为派生行进上下文了（见 modelVisibleAttachments）。走到这里的只有
            // 两种情况：v4 之前存进去的老视频行，或者抽帧全失败的视频。都跳过。
            AttachmentKind.VIDEO -> {
                Log.w(TAG, "video row without derived frames, skipped: $relPath")
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
