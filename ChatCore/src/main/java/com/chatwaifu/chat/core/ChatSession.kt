package com.chatwaifu.chat.core

import kotlinx.coroutines.CancellationException
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

    /** 每轮附在用户消息上的备注（时间、自动想起的往事）。见 [TurnContextContributor] */
    var turnContext: TurnContextContributor? = null,

    /** 会话自己跑工具循环。null = 不声明工具。见 [ToolHandler] */
    var tools: ToolHandler? = null,
) {

    private val history = mutableListOf<ChatMessage>()

    /**
     * history 下标 → 这条 user 消息生成时附上的备注。和 history 平行存，不混进消息本身：
     * [snapshot] 要给出原话（落库、记忆抽取都不该看到备注），只在组装请求时合进去。
     */
    private val turnNotes = HashMap<Int, String>()

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
        turnNotes.clear()
    }

    fun snapshot(): List<ChatMessage> = history.toList()

    /**
     * 撤掉最后一条（重试前去掉上一次的 user 输入、或者丢掉一条空回复）。
     *
     * 和 `restore(snapshot().dropLast(1))` 的区别：保留其余消息的备注和裁剪起点，
     * 前缀不变，缓存不会因为一次重试整段作废。
     */
    fun dropLast() {
        if (history.isEmpty()) return
        turnNotes.remove(history.lastIndex)
        history.removeAt(history.lastIndex)
        historyStart = historyStart.coerceAtMost(maxOf(0, history.size - 1))
    }

    fun clear() {
        history.clear()
        historyStart = 0
        turnNotes.clear()
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
        // 备注在入历史之前取：取失败（抛异常）不影响这一轮，只是没有备注
        val note = turnContext?.runCatching { turnContext(userMessage) }
            ?.getOrNull()
            ?.takeIf { it.isNotBlank() }
        history.add(userMessage)
        note?.let { turnNotes[history.lastIndex] = it }

        // 记忆先取：它要从 maxPromptTokens 里占掉一块，trim 得知道还剩多少
        val memoryBlock = memory?.runCatching { memoryBlock(budget.memoryTokens) }
            ?.getOrNull()
            ?.takeIf { it.isNotBlank() }

        val trimmed = budget.trim(withNotes(), systemPrompt, historyStart)
        historyStart = trimmed.startIndex
        val system = composeSystemPrompt(memoryBlock, trimmed.truncated)
        val toolSpecs = tools?.runCatching { specs() }?.getOrNull().orEmpty()

        // 工具循环：模型要调工具就执行、回填结果、再问一次。中间这些往返**不进历史**，
        // 历史里只留最终回复——下一轮的前缀和这一轮第一次请求的前缀一致，缓存照样命中
        val exchange = mutableListOf<ChatMessage>()
        val spokenBefore = StringBuilder()
        var usage: TokenUsage? = null
        for (round in 0..MAX_TOOL_ROUNDS) {
            val request = options.toRequest(trimmed.messages + exchange, system)
                .let { if (toolSpecs.isEmpty()) it else it.copy(tools = it.tools + toolSpecs) }

            val accumulator = MessageAccumulator()
            var completed: ChatDelta.Completed? = null
            provider.chatStream(request).collect { delta ->
                accumulator.accept(delta)
                if (delta is ChatDelta.Completed) {
                    completed = delta
                } else if (options.streamResponse) {
                    emit(delta)
                }
            }
            val done = completed ?: return@flow
            // 用 provider 给的完整消息，缺内容时用累加结果兜底
            val message = done.message.takeIf { it.contents.isNotEmpty() } ?: accumulator.build()
            usage = usage plus done.usage

            val handler = tools
            if (handler != null && message.toolCalls.isNotEmpty() && round < MAX_TOOL_ROUNDS) {
                spokenBefore.append(message.text)
                exchange += message
                message.toolCalls.forEach { call ->
                    val (output, failed) = try {
                        handler.call(call.name, call.argumentsJson) to false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        (e.message ?: e::class.simpleName.orEmpty()) to true
                    }
                    exchange += ChatMessage.toolResult(call.id, output, failed)
                }
                continue
            }

            // 调工具前说过的话（「让我想想……」）已经流式发出去了，并进最终消息，
            // 否则落库的文本和用户刚才看到 / 听到的对不上
            val finalMessage = if (spokenBefore.isEmpty()) {
                message
            } else {
                message.copy(
                    contents = listOf(ChatContent.Text(spokenBefore.toString())) +
                        message.contents.filterNot { it is ChatContent.ToolCall },
                )
            }
            history.add(finalMessage)
            emit(done.copy(message = finalMessage, usage = usage))
            return@flow
        }
    }

    /** 把备注合进对应的 user 消息：备注在前，原话在后，同一条消息里的两个文本块 */
    private fun withNotes(): List<ChatMessage> {
        if (turnNotes.isEmpty()) return history
        return history.mapIndexed { index, message ->
            val note = turnNotes[index] ?: return@mapIndexed message
            message.copy(contents = listOf(ChatContent.Text(note)) + message.contents)
        }
    }

    private infix fun TokenUsage?.plus(other: TokenUsage?): TokenUsage? {
        if (this == null) return other
        if (other == null) return this
        fun add(a: Int?, b: Int?) = if (a == null && b == null) null else (a ?: 0) + (b ?: 0)
        return TokenUsage(
            promptTokens = add(promptTokens, other.promptTokens),
            completionTokens = add(completionTokens, other.completionTokens),
            totalTokens = add(totalTokens, other.totalTokens),
            cachedTokens = add(cachedTokens, other.cachedTokens),
        )
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
        /**
         * 一轮最多跑几次工具。到上限那次仍然带着工具声明（Anthropic 要求历史里有 tool_use
         * 时必须声明 tools），但它再要调工具就不执行了，直接用它给的文字收尾
         */
        const val MAX_TOOL_ROUNDS = 3

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
