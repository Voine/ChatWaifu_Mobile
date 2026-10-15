package com.chatwaifu.chat.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本轮备注（时间感 / 自动回忆）和工具循环。provider 是假的，只记录收到的请求。 */
class ChatSessionTest {

    /** 按脚本逐次回复；每次请求都记下来 */
    private class ScriptedProvider(private val replies: List<ChatMessage>) : ChatProvider {
        override val id = ProviderId.OPENAI_COMPAT
        override val displayName = "fake"
        override val capabilities = ProviderCapabilities(toolCalling = true)
        override val availableModels = emptyList<ModelInfo>()
        val requests = mutableListOf<ChatRequest>()

        override fun chatStream(request: ChatRequest): Flow<ChatDelta> = flow {
            val reply = replies[requests.size]
            requests += request
            emit(ChatDelta.Started)
            reply.text.takeIf { it.isNotEmpty() }?.let { emit(ChatDelta.TextDelta(it)) }
            emit(ChatDelta.Completed(reply, TokenUsage(promptTokens = 10, completionTokens = 1)))
        }
    }

    private fun ChatRequest.lastUserTexts(): List<String> =
        messages.last { it.role == ChatRole.USER }.contents
            .filterIsInstance<ChatContent.Text>().map { it.text }

    @Test
    fun `备注挂在本轮 user 消息前，之后每轮原样重发，但不进 snapshot`() = runBlocking {
        val provider = ScriptedProvider(listOf(ChatMessage.assistant("a1"), ChatMessage.assistant("a2")))
        var n = 0
        val session = ChatSession(
            provider,
            turnContext = TurnContextContributor { "note${++n}" },
        )
        session.send("u1").toList()
        session.send("u2").toList()

        assertEquals(listOf("note1", "u1"), provider.requests[0].lastUserTexts())
        // 第二轮请求里，第一条 user 消息仍然带着 note1：前缀和上一轮一致，缓存才命中
        val second = provider.requests[1].messages
        assertEquals(listOf("note1", "u1"), second[0].contents.map { (it as ChatContent.Text).text })
        assertEquals(listOf("note2", "u2"), provider.requests[1].lastUserTexts())
        // 原话给 Room / 记忆抽取，不能混进备注
        assertEquals(listOf("u1", "a1", "u2", "a2"), session.snapshot().map { it.text })
    }

    @Test
    fun `dropLast 只撤最后一条，其余备注保留`() = runBlocking {
        val provider = ScriptedProvider(
            listOf(ChatMessage.assistant("a1"), ChatMessage.assistant("a2"), ChatMessage.assistant("a3"))
        )
        var n = 0
        val session = ChatSession(provider, turnContext = TurnContextContributor { "note${++n}" })
        session.send("u1").toList()
        session.send("u2").toList()
        session.dropLast() // a2
        session.dropLast() // u2
        session.send("u2 again").toList()
        val first = provider.requests[2].messages[0]
        assertEquals("note1", (first.contents[0] as ChatContent.Text).text)
    }

    @Test
    fun `工具循环：执行、回填、再问；历史只留最终回复，调工具前说的话并进去`() = runBlocking {
        val provider = ScriptedProvider(
            listOf(
                ChatMessage(
                    ChatRole.ASSISTANT,
                    listOf(
                        ChatContent.Text("让我想想……"),
                        ChatContent.ToolCall("c1", "read_original", """{"episode_id":3}"""),
                    ),
                ),
                ChatMessage.assistant("想起来了，是山竹。"),
            )
        )
        val calls = mutableListOf<String>()
        val session = ChatSession(
            provider,
            tools = object : ToolHandler {
                override suspend fun specs() = listOf(ToolSpec("read_original", "", "{}"))
                override suspend fun call(name: String, argumentsJson: String): String {
                    calls += "$name $argumentsJson"
                    return "对方: 我最喜欢山竹"
                }
            },
        )
        val deltas = session.send("我最喜欢什么水果来着").toList()

        assertEquals(listOf("""read_original {"episode_id":3}"""), calls)
        assertEquals(2, provider.requests.size)
        assertTrue(provider.requests.all { r -> r.tools.any { it.name == "read_original" } })
        // 第二次请求带上了 tool_use 和 tool_result
        val tail = provider.requests[1].messages.takeLast(2)
        assertEquals(listOf(ChatRole.ASSISTANT, ChatRole.TOOL), tail.map { it.role })

        val done = deltas.filterIsInstance<ChatDelta.Completed>().single()
        assertEquals("让我想想……想起来了，是山竹。", done.message.text)
        assertTrue(done.message.toolCalls.isEmpty())
        assertEquals(20, done.usage?.promptTokens)
        // 历史：user + 最终 assistant，中间的工具往返不留
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), session.snapshot().map { it.role })
    }

    @Test
    fun `工具抛异常时把错误交给模型，不中断这一轮`() = runBlocking {
        val provider = ScriptedProvider(
            listOf(
                ChatMessage(ChatRole.ASSISTANT, listOf(ChatContent.ToolCall("c1", "boom", "{}"))),
                ChatMessage.assistant("查不到呢"),
            )
        )
        val session = ChatSession(
            provider,
            tools = object : ToolHandler {
                override suspend fun specs() = listOf(ToolSpec("boom", "", "{}"))
                override suspend fun call(name: String, argumentsJson: String): String = error("db closed")
            },
        )
        val done = session.send("hi").toList().filterIsInstance<ChatDelta.Completed>().single()
        assertEquals("查不到呢", done.message.text)
        val result = provider.requests[1].messages.last().contents.single() as ChatContent.ToolResult
        assertTrue(result.isError)
        assertEquals("db closed", result.output)
    }
}
