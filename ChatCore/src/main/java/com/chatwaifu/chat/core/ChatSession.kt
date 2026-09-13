package com.chatwaifu.chat.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Description: 一个角色的会话。历史的唯一归属地。
 *
 * 为什么历史放客户端而不是用服务端会话（`previous_response_id` /
 * `previous_interaction_id`）：Anthropic 压根没有服务端会话，必须重发全量；
 * 而只要有一家必须重发，客户端就得留着全量历史。既然如此就统一走这条路 ——
 * 换 provider、换模型、清缓存都不断上下文，也和 Room 里的聊天记录天然一致。
 * 服务端会话留给将来做「省 token / 提高缓存命中」的可选优化
 * （[ProviderCapabilities.serverSideHistory] 已经标好了谁支持）。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class ChatSession(
    private val provider: ChatProvider,

    /** 人物设定，可以随时改（切角色时）。 */
    var systemPrompt: String? = null,

    var options: ChatOptions = ChatOptions(),

    private val budget: ContextBudget =
        ContextBudget.forContextWindow(provider.capabilities.maxContextTokens),

    /**
     * 长期记忆。null 表示不注入（`ChatCore` 自己不知道记忆是怎么来的，见
     * [MemoryContributor] 的 KDoc）。
     */
    var memory: MemoryContributor? = null,
) {

    private val history = mutableListOf<ChatMessage>()

    /**
     * 上一轮裁剪用的历史起点，喂回 [ContextBudget.trim] 让前缀保持粘性。
     * 不持久化：重启后从 0 重新开始，无非是第一轮多算一次淘汰。
     */
    private var historyStart = 0

    /** 从 Room 恢复历史。会覆盖当前内存里的记录。 */
    fun restore(messages: List<ChatMessage>) {
        history.clear()
        history.addAll(messages)
        historyStart = 0
    }

    fun snapshot(): List<ChatMessage> = history.toList()

    fun clear() {
        history.clear()
        historyStart = 0
    }

    /**
     * 发一轮对话。
     *
     * 返回的流会把 provider 的增量原样转出去，同时在内部累加成完整消息：
     * - 用户消息在流开始时就进历史（失败也留着，和 Room 里已落库的记录保持一致）
     * - assistant 消息在收到 [ChatDelta.Completed] 后进历史
     *
     * 失败时抛 [ChatError]，历史里不会留下半截 assistant 消息。
     */
    fun send(userMessage: ChatMessage): Flow<ChatDelta> = flow {
        history.add(userMessage)

        // 记忆先取：它要从 maxPromptTokens 里占掉一块，trim 得知道还剩多少
        val memoryBlock = memory?.runCatching { memoryBlock(budget.memoryTokens) }
            ?.getOrNull()
            ?.takeIf { it.isNotBlank() }

        val trimmed = budget.trim(history, systemPrompt, historyStart)
        historyStart = trimmed.startIndex

        val request = options.toRequest(
            messages = trimmed.messages,
            systemPrompt = composeSystemPrompt(memoryBlock, trimmed.truncated),
        )

        val accumulator = MessageAccumulator()
        provider.chatStream(request).collect { delta ->
            accumulator.accept(delta)
            if (delta is ChatDelta.Completed) {
                // 用 provider 给的完整消息，缺内容时用累加结果兜底
                val finalMessage = delta.message.takeIf { it.contents.isNotEmpty() }
                    ?: accumulator.build()
                history.add(finalMessage)
                emit(delta.copy(message = finalMessage))
            } else if (options.streamResponse) {
                emit(delta)
            }
        }
    }

    /** 便捷入口：纯文本一问一答。 */
    fun send(text: String): Flow<ChatDelta> = send(ChatMessage.user(text))

    /**
     * 人设 + 记忆 + 断层标记拼成最终的 system prompt。
     *
     * 顺序是刻意的：**变得越慢的放越前面**。人设几乎不变，记忆变得很慢，
     * 断层标记只会从「无」翻成「有」一次。这三块合起来就是缓存前缀，
     * 越稳定命中率越高。
     *
     * 断层标记**刻意不带被丢掉的条数**——带了就等于每次淘汰都改写一次 system prompt，
     * 而它是缓存前缀的一部分。不带条数的话整个会话生命周期里只会变一次。
     */
    private fun composeSystemPrompt(memoryBlock: String?, truncated: Boolean): String? {
        if (memoryBlock == null && !truncated) return systemPrompt
        return buildString {
            systemPrompt?.takeIf { it.isNotBlank() }?.let { append(it) }
            memoryBlock?.let {
                if (isNotEmpty()) append("\n\n")
                append(it)
            }
            if (truncated) {
                if (isNotEmpty()) append("\n\n")
                append(TRUNCATION_NOTICE)
            }
        }.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TRUNCATION_NOTICE =
            "（更早的对话已从上下文中省略。需要时依据上面记录的记忆作答，不要凭空编造。）"
    }
}

/**
 * 除消息和 system prompt 之外的生成参数。和 [ChatRequest] 分开是因为它们的生命周期不同：
 * 这些是「会话级配置」（用户在设置页改一次），messages 是每轮都变的。
 */
data class ChatOptions(
    val model: String? = null,
    val maxOutputTokens: Int? = null,
    val temperature: Float? = null,
    val reasoning: ReasoningLevel? = null,
    val tools: List<ToolSpec> = emptyList(),
    val stopSequences: List<String> = emptyList(),
    val streamResponse: Boolean = true,
    val extras: Map<String, Any?> = emptyMap(),
) {
    fun toRequest(messages: List<ChatMessage>, systemPrompt: String?): ChatRequest = ChatRequest(
        messages = messages,
        systemPrompt = systemPrompt,
        model = model,
        maxOutputTokens = maxOutputTokens,
        temperature = temperature,
        reasoning = reasoning,
        tools = tools,
        stopSequences = stopSequences,
        extras = extras,
    )
}

/**
 * 把增量事件拼回一条完整消息。
 *
 * 放在 core 里而不是各 provider 里：provider 只负责「把自家 SSE 翻译成增量」，
 * 拼接这件事所有基座都一样，写一遍就够。provider 内部也可以直接复用它来生成
 * [ChatDelta.Completed] 的 message。
 */
class MessageAccumulator {
    private val text = StringBuilder()
    private val thinking = StringBuilder()
    private var thinkingOpaque: String? = null
    private val toolCalls = sortedMapOf<Int, ToolCallBuilder>()

    fun accept(delta: ChatDelta) {
        when (delta) {
            is ChatDelta.TextDelta -> text.append(delta.text)
            is ChatDelta.ThinkingDelta -> thinking.append(delta.text)
            is ChatDelta.ToolCallDelta -> {
                val builder = toolCalls.getOrPut(delta.index) { ToolCallBuilder() }
                delta.id?.let { builder.id = it }
                delta.name?.let { builder.name = it }
                delta.argsFragment?.let { builder.args.append(it) }
            }

            ChatDelta.Started, is ChatDelta.Completed -> Unit
        }
    }

    fun setThinkingOpaque(opaque: String?) {
        thinkingOpaque = opaque
    }

    fun build(): ChatMessage {
        val contents = mutableListOf<ChatContent>()
        // thinking 放在最前面：Anthropic 要求 thinking block 必须是 assistant 消息的首块
        if (thinking.isNotEmpty() || thinkingOpaque != null) {
            contents += ChatContent.Thinking(thinking.toString(), thinkingOpaque)
        }
        if (text.isNotEmpty()) {
            contents += ChatContent.Text(text.toString())
        }
        toolCalls.values.forEach { builder ->
            contents += ChatContent.ToolCall(
                id = builder.id.orEmpty(),
                name = builder.name.orEmpty(),
                argumentsJson = builder.args.toString(),
            )
        }
        return ChatMessage(ChatRole.ASSISTANT, contents)
    }

    private class ToolCallBuilder {
        var id: String? = null
        var name: String? = null
        val args = StringBuilder()
    }
}
