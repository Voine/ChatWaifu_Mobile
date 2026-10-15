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
import com.chatwaifu.chat.provider.google.GeminiProvider
import com.chatwaifu.chat.provider.google.GeminiStreamParser
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * Gemini 的请求映射和流解析。SSE 样本来自 AthenAI 网关透传的真实 `streamGenerateContent` 帧。
 */
class GeminiProviderTest {

    private val provider = GeminiProvider(ProviderConfig(apiKey = "test"))

    private fun body(request: ChatRequest): JsonObject =
        JsonParser.parseString(provider.buildRequestJson(request)).asJsonObject

    @Test
    fun `角色是 model 不是 assistant，system 走 system_instruction`() {
        val json = body(
            ChatRequest(
                messages = listOf(ChatMessage.user("a"), ChatMessage.assistant("b"), ChatMessage.user("c")),
                systemPrompt = "人设",
            )
        )
        val roles = json.getAsJsonArray("contents").map { it.asJsonObject.get("role").asString }
        assertEquals(listOf("user", "model", "user"), roles)
        assertEquals(
            "人设",
            json.getAsJsonObject("system_instruction").getAsJsonArray("parts")[0].asJsonObject.get("text").asString,
        )
    }

    @Test
    fun `没指定推理档时主动压到 low，且不要思考原文`() {
        // 3.x 默认思考且计入 maxOutputTokens，不压的话短回复会被思考吃光
        val thinking = body(ChatRequest(listOf(ChatMessage.user("a"))))
            .getAsJsonObject("generationConfig").getAsJsonObject("thinkingConfig")
        assertEquals("low", thinking.get("thinkingLevel").asString)
        assertFalse(thinking.get("includeThoughts").asBoolean)

        val high = body(ChatRequest(listOf(ChatMessage.user("a")), reasoning = ReasoningLevel.MAX))
            .getAsJsonObject("generationConfig").getAsJsonObject("thinkingConfig")
        assertEquals("high", high.get("thinkingLevel").asString)
    }

    @Test
    fun `thoughtSignature 挂回文本 part，别家的 opaque 不发`() {
        fun history(opaque: String) = ChatRequest(
            listOf(
                ChatMessage.user("a"),
                ChatMessage(ChatRole.ASSISTANT, listOf(ChatContent.Thinking("", opaque), ChatContent.Text("b"))),
                ChatMessage.user("c"),
            )
        )
        val ours = body(history("gemini:SIG")).getAsJsonArray("contents")[1].asJsonObject
            .getAsJsonArray("parts")[0].asJsonObject
        assertEquals("SIG", ours.get("thoughtSignature").asString)

        val foreign = body(history("anthropic:SIG")).getAsJsonArray("contents")[1].asJsonObject
            .getAsJsonArray("parts")[0].asJsonObject
        assertNull(foreign.get("thoughtSignature"))
    }

    @Test
    fun `functionResponse 用调用时的函数名`() {
        val json = body(
            ChatRequest(
                listOf(
                    ChatMessage.user("天气"),
                    ChatMessage(ChatRole.ASSISTANT, listOf(ChatContent.ToolCall("weather#0", "weather", "{}"))),
                    ChatMessage.toolResult("weather#0", "晴"),
                )
            )
        )
        val response = json.getAsJsonArray("contents")[2].asJsonObject.getAsJsonArray("parts")[0]
            .asJsonObject.getAsJsonObject("functionResponse")
        assertEquals("weather", response.get("name").asString)
        assertEquals("晴", response.getAsJsonObject("response").get("output").asString)
    }

    private fun parse(vararg frames: String): Pair<List<ChatDelta>, ChatDelta.Completed> {
        val parser = GeminiStreamParser()
        val deltas = frames.flatMap { parser.onEvent(SseEvent(null, it)) }
        return deltas to parser.finish("test")
    }

    @Test
    fun `思考和正文分流，签名和 usage 收进结果`() {
        val (deltas, done) = parse(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"**Calculating**","thought":true}]}}],"usageMetadata":{"trafficType":"ON_DEMAND"}}""",
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"1加1等于2。"}]}}]}""",
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"","thoughtSignature":"AY89"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":6,"candidatesTokenCount":6,"totalTokenCount":213,"thoughtsTokenCount":201}}""",
        )
        assertEquals(ChatDelta.Started, deltas.first())
        assertEquals("**Calculating**", deltas.filterIsInstance<ChatDelta.ThinkingDelta>().single().text)
        assertEquals("1加1等于2。", done.message.text)
        assertEquals("gemini:AY89", (done.message.contents.first() as ChatContent.Thinking).opaque)
        assertEquals(FinishReason.STOP, done.finishReason)
        assertEquals(6, done.usage?.promptTokens)
        assertEquals(207, done.usage?.completionTokens)
    }

    @Test
    fun `MAX_TOKENS 和安全拦截的映射`() {
        val (_, cut) = parse("""{"candidates":[{"content":{"parts":[{"text":"你好"}]},"finishReason":"MAX_TOKENS"}]}""")
        assertEquals(FinishReason.MAX_TOKENS, cut.finishReason)

        val (_, blocked) = parse("""{"candidates":[{"finishReason":"SAFETY"}]}""")
        assertEquals(FinishReason.CONTENT_FILTER, blocked.finishReason)

        try {
            parse("""{"promptFeedback":{"blockReason":"PROHIBITED_CONTENT"}}""")
            fail("expected ContentFiltered")
        } catch (e: ChatError.ContentFiltered) {
            assertEquals("PROHIBITED_CONTENT", e.category)
        }
    }

    @Test
    fun `函数调用造出稳定 id 并标 TOOL_CALL`() {
        val (_, done) = parse(
            """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"weather","args":{"city":"上海"}}}]},"finishReason":"STOP"}]}"""
        )
        val call = done.message.toolCalls.single()
        assertEquals("weather#0", call.id)
        assertEquals("""{"city":"上海"}""", call.argumentsJson)
        assertEquals(FinishReason.TOOL_CALL, done.finishReason)
    }

    @Test
    fun `没有 finishReason 就断流算网络错误`() {
        val parser = GeminiStreamParser()
        parser.onEvent(SseEvent(null, """{"candidates":[{"content":{"parts":[{"text":"半"}]}}]}"""))
        try {
            parser.finish("test")
            fail("expected ChatError.Network")
        } catch (_: ChatError.Network) {
        }
    }
}
