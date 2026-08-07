package com.chatwaifu.log

/**
 * Description: 聊天记录的领域模型。
 *
 * 和 `room/ChatMessageEntity` 分开：`@Entity` 是落盘细节，不该跨模块边界。
 * 原来 `IChatLogDbApi` 直接返回 Room 实体，导致 app 侧得写
 * `import com.chatwaifu.log.room.ChatMessage as StoredMessage` 来避免和 ChatCore 的
 * `ChatMessage` 撞名——那是个信号，说明抽象漏了。
 *
 * 也不直接复用 `ChatCore` 的 `ChatMessage`：`Log` 模块不依赖 `ChatCore`
 * （依赖方向是 app → 各模块，模块之间无横向依赖），两边的映射由 app 侧的
 * `ChatHistoryStore` 负责。
 *
 * Author: Voine
 * Date: 2026/8/7
 */
data class ChatLogEntry(
    /** 落库后由 Room 回填；未落库时为 [NO_ID]。 */
    val id: Long = NO_ID,

    /**
     * 角色的稳定键。**不要当显示名用**——角色层将来会长出真正的 uuid，
     * 那时这里的值会变，显示名得另外拿（见 docs/chat-storage.md「characterId 这道缝」）。
     */
    val characterId: String,

    val role: ChatLogRole,

    val text: String,

    val timeline: Long,

    val promptTokens: Int = 0,
    val completionTokens: Int = 0,

    /**
     * 产出这条消息的基座与模型。**不是调试字段**：
     * [thinkingOpaque] 是绑模型的，重放给另一个模型非法，需要靠这两个字段 gate。
     */
    val providerId: String? = null,
    val model: String? = null,

    val source: MessageSource = MessageSource.TYPED,

    val status: MessageStatus = MessageStatus.OK,

    val thinkingText: String? = null,

    /**
     * 基座要求原样回传的不透明串（Anthropic thinking block 的 signature、
     * OpenAI 的 encrypted reasoning item）。**不要改写它。**
     */
    val thinkingOpaque: String? = null,

    /**
     * 富媒体附件。只是引用，字节在应用私有空间里，见 [AttachmentRef]。
     *
     * 写入时 [AttachmentRef.messageId] 可以留空，仓库插完消息会回填。
     */
    val attachments: List<AttachmentRef> = emptyList(),
) {
    companion object {
        const val NO_ID = 0L
    }
}

enum class ChatLogRole {
    USER,
    ASSISTANT,

    /** 目前不落库（system prompt 是配置不是记录），留着是为了历史里能表达。 */
    SYSTEM,

    /** 工具执行结果。 */
    TOOL,
}

/**
 * 文本是怎么来的。ASR 出来的文本置信度和手打的不一样，
 * 排查「模型为什么回了我没说过的话」时需要区分。
 */
enum class MessageSource {
    /** 用户手打。 */
    TYPED,

    /** 本地 Sherpa ASR 识别出来的。 */
    ASR,

    /** realtime session 里基座回传的 output transcript。 */
    REALTIME_TRANSCRIPT,
}

/**
 * 落库状态。
 *
 * 有这个字段才能「首个 delta 就插行、`Completed` 时 update」——
 * 流式中途被杀进程时，区别是「历史里有条半截回复」还是「历史凭空少一轮」。
 */
enum class MessageStatus {
    /** 流还没结束。重启后读到这个状态说明上次是异常退出。 */
    STREAMING,
    OK,
    FAILED,
}
