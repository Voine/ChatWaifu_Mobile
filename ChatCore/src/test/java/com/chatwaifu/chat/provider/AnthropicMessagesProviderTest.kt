package com.chatwaifu.chat.provider

import com.chatwaifu.chat.core.ChatContent
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatError
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatRequest
import com.chatwaifu.chat.core.ChatRole
import com.chatwaifu.chat.core.FinishReason
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ReasoningLevel
import com.chatwaifu.chat.net.SseEvent
import com.chatwaifu.chat.provider.anthropic.AnthropicMessagesProvider
import com.chatwaifu.chat.provider.anthropic.AnthropicStreamParser
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Anthropic 的请求映射和流解析。SSE 样本是从 AthenAI 网关录下来的真实帧（裁掉了无关字段）。
 */
class AnthropicMessagesProviderTest {

    private val provider = AnthropicMessagesProvider(ProviderConfig(apiKey = "test"))

    private fun body(request: ChatRequest): JsonObject =
        JsonParser.parseString(provider.buildRequestJson(request)).asJsonObject

    @Test
    fun `system 走顶层字段，max_tokens 兜默认值`() {
        val json = body(ChatRequest(messages = listOf(ChatMessage.user("hi")), systemPrompt = "人设"))
        assertEquals("人设", json.getAsJsonArray("system")[0].asJsonObject.get("text").asString)
        assertEquals(AnthropicMessagesProvider.DEFAULT_MAX_TOKENS, json.get("max_tokens").asInt)
        assertEquals(AnthropicMessagesProvider.MODELS.first().id, json.get("model").asString)
        assertTrue(json.get("stream").asBoolean)
    }

    @Test
    fun `缓存断点打在 system 末尾和最后一条消息的最后一块`() {
        val json = body(
            ChatRequest(
                messages = listOf(
                    ChatMessage.user("第一句"),
                    ChatMessage.assistant("回答"),
                    ChatMessage(
                        ChatRole.USER,
                        listOf(ChatContent.Text("备注"), ChatContent.Text("第二句")),
                    ),
                ),
                systemPrompt = "人设",
            )
        )
        val system = json.getAsJsonArray("system").single().asJsonObject
        assertEquals("ephemeral", system.getAsJsonObject("cache_control").get("type").asString)
        // 人在打字的会话两句之间常超过 5 分钟，用 1h
        assertEquals("1h", system.getAsJsonObject("cache_control").get("ttl").asString)

        val messages = json.getAsJsonArray("messages")
        val marked = messages.flatMap { m ->
            m.asJsonObject.getAsJsonArray("content").filter { it.asJsonObject.has("cache_control") }
        }
        // 只有一个滚动断点，且在最后一块上：前面的块挂了会浪费 4 个断点名额里的一个
        assertEquals(1, marked.size)
        assertEquals("第二句", marked.single().asJsonObject.get("text").asString)
    }

    @Test
    fun `tool 结果折进 user，相邻同角色合并`() {
        val json = body(
            ChatRequest(
                messages = listOf(
                    ChatMessage.user("查天气"),
                    ChatMessage(
                        ChatRole.ASSISTANT,
                        listOf(ChatContent.ToolCall("call_1", "weather", """{"city":"上海"}""")),
                    ),
                    ChatMessage.toolResult("call_1", "晴"),
                    ChatMessage.user("那明天呢"),
                ),
            )
        )
        val messages = json.getAsJsonArray("messages")
        assertEquals(listOf("user", "assistant", "user"), messages.map { it.asJsonObject.get("role").asString })
        val toolUse = messages[1].asJsonObject.getAsJsonArray("content")[0].asJsonObject
        // input 必须是对象，不能是 JSON 字符串
        assertEquals("上海", toolUse.getAsJsonObject("input").get("city").asString)
        val merged = messages[2].asJsonObject.getAsJsonArray("content")
        assertEquals("tool_result", merged[0].asJsonObject.get("type").asString)
        assertEquals("text", merged[1].asJsonObject.get("type").asString)
    }

    @Test
    fun `只回传 Anthropic 签的 thinking，别家的 opaque 丢弃`() {
        fun assistantWith(opaque: String?) = ChatMessage(
            ChatRole.ASSISTANT,
            listOf(ChatContent.Thinking("想了想", opaque), ChatContent.Text("好")),
        )
        val ours = body(
            ChatRequest(listOf(ChatMessage.user("a"), assistantWith("anthropic:SIG"), ChatMessage.user("b")))
        ).getAsJsonArray("messages")[1].asJsonObject.getAsJsonArray("content")
        assertEquals("thinking", ours[0].asJsonObject.get("type").asString)
        assertEquals("SIG", ours[0].asJsonObject.get("signature").asString)

        val foreign = body(
            ChatRequest(listOf(ChatMessage.user("a"), assistantWith("gemini:XYZ"), ChatMessage.user("b")))
        ).getAsJsonArray("messages")[1].asJsonObject.getAsJsonArray("content")
        assertEquals(1, foreign.size())
        assertEquals("text", foreign[0].asJsonObject.get("type").asString)
    }

    @Test
    fun `推理档映射，开 thinking 时不发 temperature`() {
        val adaptive = body(
            ChatRequest(listOf(ChatMessage.user("a")), reasoning = ReasoningLevel.HIGH, temperature = 0.7f)
        )
        assertEquals("adaptive", adaptive.getAsJsonObject("thinking").get("type").asString)
        assertEquals("high", adaptive.getAsJsonObject("output_config").get("effort").asString)
        assertNull(adaptive.get("temperature"))

        val legacy = body(
            ChatRequest(
                listOf(ChatMessage.user("a")),
                model = "claude-haiku-4-5",
                reasoning = ReasoningLevel.LOW,
                maxOutputTokens = 8000,
            )
        )
        assertEquals("enabled", legacy.getAsJsonObject("thinking").get("type").asString)
        assertEquals(1024, legacy.getAsJsonObject("thinking").get("budget_tokens").asInt)

        val off = body(ChatRequest(listOf(ChatMessage.user("a")), reasoning = ReasoningLevel.OFF, temperature = 1.5f))
        assertEquals("disabled", off.getAsJsonObject("thinking").get("type").asString)
        // Anthropic 的 temperature 上限是 1
        assertEquals(1.0f, off.get("temperature").asFloat)
    }

    private fun parse(vararg frames: Pair<String, String>): Pair<List<ChatDelta>, ChatDelta.Completed> {
        val parser = AnthropicStreamParser()
        val deltas = frames.flatMap { (event, data) -> parser.onEvent(SseEvent(event, data)) }
        return deltas to parser.finish("test")
    }

    @Test
    fun `文本流：增量、usage、结束原因`() {
        val (deltas, done) = parse(
            "message_start" to """{"type":"message_start","message":{"usage":{"input_tokens":23,"cache_read_input_tokens":0}}}""",
            "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            "ping" to """{"type":"ping"}""",
            "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"你"}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"好"}}""",
            "content_block_stop" to """{"type":"content_block_stop","index":0}""",
            "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":5}}""",
            "message_stop" to """{"type":"message_stop"}""",
        )
        assertEquals(ChatDelta.Started, deltas.first())
        assertEquals("你好", deltas.filterIsInstance<ChatDelta.TextDelta>().joinToString("") { it.text })
        assertEquals("你好", done.message.text)
        assertEquals(FinishReason.STOP, done.finishReason)
        assertEquals(23, done.usage?.promptTokens)
        assertEquals(28, done.usage?.totalTokens)
    }

    @Test
    fun `thinking 与签名进 Thinking 块，且排在最前`() {
        val (_, done) = parse(
            "message_start" to """{"type":"message_start","message":{"usage":{"input_tokens":1}}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"嗯"}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"abc"}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"2"}}""",
            "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""",
            "message_stop" to """{"type":"message_stop"}""",
        )
        val first = done.message.contents.first() as ChatContent.Thinking
        assertEquals("嗯", first.text)
        assertEquals("anthropic:abc", first.opaque)
    }

    @Test
    fun `工具调用的参数按块拼接`() {
        val (_, done) = parse(
            "message_start" to """{"type":"message_start","message":{}}""",
            "content_block_start" to """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"tu_1","name":"weather"}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"city\":"}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"北京\"}"}}""",
            "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}""",
            "message_stop" to """{"type":"message_stop"}""",
        )
        val call = done.message.toolCalls.single()
        assertEquals("tu_1", call.id)
        assertEquals("""{"city":"北京"}""", call.argumentsJson)
        assertEquals(FinishReason.TOOL_CALL, done.finishReason)
    }

    @Test
    fun `拒答映射为 REFUSAL，流内 error 抛 ChatError`() {
        val (_, refused) = parse(
            "message_start" to """{"type":"message_start","message":{}}""",
            "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"refusal"}}""",
            "message_stop" to """{"type":"message_stop"}""",
        )
        assertEquals(FinishReason.REFUSAL, refused.finishReason)

        try {
            parse("error" to """{"type":"error","error":{"type":"overloaded_error","message":"busy"}}""")
            fail("expected ChatError")
        } catch (e: ChatError.ServerError) {
            assertEquals("busy", e.message)
        }
    }

    @Test
    fun `没有 message_stop 就断流算网络错误`() {
        val parser = AnthropicStreamParser()
        parser.onEvent(SseEvent("message_start", """{"type":"message_start","message":{}}"""))
        parser.onEvent(
            SseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"半"}}""")
        )
        try {
            parser.finish("test")
            fail("expected ChatError.Network")
        } catch (e: ChatError.Network) {
            assertFalse(e.message.isNullOrBlank())
        }
    }
}
