package com.chatwaifu.chat.core

/**
 * Description: 与基座无关的会话消息模型。
 *
 * 2026 年 OpenAI / Anthropic / Google 三家的接口形状已经各自演进，但结构是同源的：
 * **一条消息 = 一个角色 + 一串内容块**，system 独立成字段。这里就取这个最小公倍数，
 * 具体 provider 负责把它翻译成自家的 wire format：
 *
 * | 这里 | OpenAI Responses | Anthropic Messages | Gemini |
 * |---|---|---|---|
 * | [ChatContent.Text] | `input_text` / `output_text` | `{"type":"text"}` | `{"text":...}` |
 * | [ChatContent.Image] | `input_image` | `{"type":"image"}` | `inline_data` / `file_data` |
 * | [ChatContent.Doc] | `input_file` | `{"type":"document"}` | `inline_data` |
 * | [ChatContent.ToolCall] | `function_call` item | `{"type":"tool_use"}` | `function_call` step |
 * | [ChatContent.ToolResult] | `function_call_output` item | `{"type":"tool_result"}` | `function_result` step |
 * | [ChatContent.Thinking] | reasoning item | `{"type":"thinking"}` | thought step |
 *
 * Author: Voine
 * Date: 2026/8/6
 */
enum class ChatRole {
    /**
     * 仅用于历史里的兼容表示。真正下发时 system 走 [ChatRequest.systemPrompt]，
     * 因为三家都是独立字段（`instructions` / `system` / `system_instruction`）而不是一条消息。
     */
    SYSTEM,
    USER,
    ASSISTANT,

    /** 工具执行结果所在的消息。Anthropic 把它放在 user 消息里，由 provider 自行折叠。 */
    TOOL,
}

/** 图像输入的精度档，对应 OpenAI 的 `detail`。其他基座没有对应概念，直接忽略。 */
enum class ImageDetail { LOW, HIGH, AUTO }

/**
 * 多模态数据的来源。分四种是因为各家接受的形式不同，且转换成本差异很大：
 * base64 内联会撑大请求体，能用 file id 就别内联。
 */
sealed interface MediaSource {
    /** 内存里的字节，provider 转 base64 内联（`data:` URI / `inline_data`）。 */
    data class Bytes(val bytes: ByteArray, val mimeType: String) : MediaSource {
        // ByteArray 的 equals 是引用比较，data class 自动生成的实现会给出违反直觉的结果
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bytes) return false
            return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
    }

    /** 公网可达的 URL。OpenAI 直接吃，Anthropic 走 `source.type=url`，Gemini 需要先落 Files API。 */
    data class Url(val url: String) : MediaSource

    /** 已上传到基座侧的文件 id（OpenAI `/v1/files`、Gemini Files API）。 */
    data class RemoteFileId(val id: String) : MediaSource

    /**
     * 本地文件路径，由 provider 决定是读成字节内联还是先上传。
     * 注意不能用 `content://` URI —— 调用方需要先落地成真实路径，
     * 和 VITS 模型导入是同一个理由（见 ModelImporter）。
     */
    data class LocalPath(val path: String, val mimeType: String) : MediaSource
}

/** 一条消息里的一个内容块。 */
sealed interface ChatContent {

    data class Text(val text: String) : ChatContent

    data class Image(
        val source: MediaSource,
        val detail: ImageDetail = ImageDetail.AUTO,
    ) : ChatContent

    /**
     * 音频输入/输出。入口先留好：Gemini 原生支持音频进出，OpenAI 有 Realtime，
     * 将来这条路能直接替掉「翻译 → VITS」中的一环。
     *
     * @param format 形如 `wav` / `mp3` / `pcm16`，各家的枚举名不同，provider 自己映射。
     */
    data class Audio(val source: MediaSource, val format: String) : ChatContent

    /** PDF 之类的文档输入。 */
    data class Doc(
        val source: MediaSource,
        val fileName: String? = null,
        val mimeType: String? = null,
    ) : ChatContent

    /** 模型要求调用工具。[argumentsJson] 是原始 JSON 字符串，不在这一层解析。 */
    data class ToolCall(
        val id: String,
        val name: String,
        val argumentsJson: String,
    ) : ChatContent

    /** 工具执行结果，[callId] 必须回填成对应 [ToolCall.id]。 */
    data class ToolResult(
        val callId: String,
        val output: String,
        val isError: Boolean = false,
    ) : ChatContent

    /**
     * 模型的思考过程。
     *
     * [opaque] 存基座要求原样回传的不透明串（Anthropic thinking block 的 signature、
     * OpenAI 的 encrypted reasoning item）。**不要改写它**，否则多轮里签名校验会失败。
     */
    data class Thinking(val text: String, val opaque: String? = null) : ChatContent
}

/**
 * 一条完整消息。
 *
 * 历史统一由 [ChatSession] 在客户端维护（而不是靠 `previous_response_id` 之类的服务端会话），
 * 这样换 provider、清缓存都不会断上下文，也和 Room 里的聊天记录天然对齐。
 */
data class ChatMessage(
    val role: ChatRole,
    val contents: List<ChatContent>,
) {
    /** 把所有文本块拼起来。绝大多数调用方只关心这个。 */
    val text: String
        get() = contents.filterIsInstance<ChatContent.Text>().joinToString(separator = "") { it.text }

    val toolCalls: List<ChatContent.ToolCall>
        get() = contents.filterIsInstance<ChatContent.ToolCall>()

    companion object {
        fun user(text: String): ChatMessage =
            ChatMessage(ChatRole.USER, listOf(ChatContent.Text(text)))

        fun user(text: String, vararg attachments: ChatContent): ChatMessage =
            ChatMessage(ChatRole.USER, listOf(ChatContent.Text(text)) + attachments)

        fun assistant(text: String): ChatMessage =
            ChatMessage(ChatRole.ASSISTANT, listOf(ChatContent.Text(text)))

        fun toolResult(callId: String, output: String, isError: Boolean = false): ChatMessage =
            ChatMessage(ChatRole.TOOL, listOf(ChatContent.ToolResult(callId, output, isError)))
    }
}
