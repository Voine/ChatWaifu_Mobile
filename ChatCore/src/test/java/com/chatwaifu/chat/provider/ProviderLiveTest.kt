package com.chatwaifu.chat.provider

import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatError
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatOptions
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRequest
import com.chatwaifu.chat.core.ChatSession
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.chat.core.ToolHandler
import com.chatwaifu.chat.core.ToolSpec
import com.chatwaifu.chat.core.chat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真实网络联调。打的是 LiteLLM 网关（公司内 AthenAI），它对四种协议都是原生 / 透传：
 * OpenAI Chat Completions、OpenAI Responses、Anthropic Messages、Gemini generateContent。
 *
 * **默认跳过**：没有 `ATHENAI_KEY` 环境变量时整个类 assume 失败，CI / 别人的机器上不会跑。
 * key 只从环境变量来，不进仓库、不进构建产物：
 * ```
 * ATHENAI_KEY=sk-... ./gradlew :ChatCore:testDebugUnitTest --tests '*LiveTest'
 * ```
 * `ATHENAI_BASE_URL` 可选，默认 `https://athenai.mihoyo.com/`（需要公司内网）。
 */
class ProviderLiveTest {

    private val key: String? = System.getenv("ATHENAI_KEY")?.takeIf { it.isNotBlank() }
    private val baseUrl = System.getenv("ATHENAI_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: "https://athenai.mihoyo.com/"

    private val opened = mutableListOf<ChatProvider>()

    @After
    fun tearDown() = opened.forEach { it.close() }

    private fun provider(id: ProviderId, model: String): ChatProvider {
        assumeTrue("ATHENAI_KEY not set, skip live test", key != null)
        return ChatProviderFactory.create(id, ProviderConfig(apiKey = key, baseUrl = baseUrl, model = model))
            .also(opened::add)
    }

    /** 每家挑一个便宜快的型号 */
    private val targets = listOf(
        ProviderId.OPENAI_COMPAT to "gpt-5.4-mini",
        ProviderId.OPENAI_RESPONSES to "gpt-5.4-mini",
        ProviderId.ANTHROPIC to "claude-haiku-5-5",
        ProviderId.GOOGLE to "gemini-3.5-flash",
    )

    @Test
    fun `四种协议都能完成一轮流式对话`() = runBlocking {
        for ((id, model) in targets) {
            val p = provider(id, model)
            val deltas = p.chatStream(
                ChatRequest(
                    messages = listOf(ChatMessage.user("用一句话介绍你自己，不超过三十个字。")),
                    systemPrompt = "你是一个说话简短的助手。",
                    model = model,
                    maxOutputTokens = 512,
                )
            ).toList()
            val text = deltas.filterIsInstance<ChatDelta.TextDelta>().joinToString("") { it.text }
            val done = deltas.last() as ChatDelta.Completed
            println("[$id/$model] ${deltas.count { it is ChatDelta.TextDelta }} chunks, " +
                "finish=${done.finishReason}, usage=${done.usage}: $text")
            assertEquals(ChatDelta.Started, deltas.first())
            assertTrue("$id streamed nothing", text.isNotBlank())
            assertEquals(text, done.message.text)
        }
    }

    @Test
    fun `ChatSession 客户端历史：多轮记得前文`() = runBlocking {
        for ((id, model) in targets) {
            val session = ChatSession(
                provider = provider(id, model),
                systemPrompt = "你是一个说话简短的助手。",
                options = ChatOptions(model = model, maxOutputTokens = 512),
            )
            session.send("记住这个暗号：蓝色企鹅。只回答“记住了”。").toList()
            val reply = session.send("刚才的暗号是什么？只回答暗号本身。").toList()
                .filterIsInstance<ChatDelta.Completed>().single().message.text
            println("[$id/$model] recall: $reply")
            assertTrue("$id forgot: $reply", reply.contains("企鹅"))
            assertEquals(4, session.snapshot().size)
        }
    }

    @Test
    fun `切 provider 后历史不断（snapshot restore 跨协议）`() = runBlocking {
        val first = ChatSession(
            provider = provider(ProviderId.ANTHROPIC, "claude-haiku-5-5"),
            options = ChatOptions(model = "claude-haiku-5-5", maxOutputTokens = 256),
        )
        first.send("我最喜欢的水果是山竹。只回答“好的”。").toList()

        // Anthropic → Gemini → OpenAI，每次把上一家的历史原样搬过去
        var history = first.snapshot()
        for ((id, model) in listOf(
            ProviderId.GOOGLE to "gemini-3.5-flash",
            ProviderId.OPENAI_RESPONSES to "gpt-5.4-mini",
        )) {
            val next = ChatSession(
                provider = provider(id, model),
                options = ChatOptions(model = model, maxOutputTokens = 512),
            ).apply { restore(history) }
            val reply = next.send("我最喜欢的水果是什么？只回答水果名。").toList()
                .filterIsInstance<ChatDelta.Completed>().single().message.text
            println("[$id/$model] after switch: $reply")
            assertTrue("$id lost history: $reply", reply.contains("山竹"))
            history = next.snapshot()
        }
    }

    /**
     * Anthropic 要显式打 `cache_control` 才缓存（另外三家自动）。人设要够长才过最小缓存长度，
     * 这里拿一段重复的设定凑到几千 token。第二轮应该从缓存读到大部分前缀。
     */
    @Test
    fun `Anthropic 第二轮命中提示缓存`() = runBlocking {
        val model = "claude-sonnet-5-5"
        val persona = (1..120).joinToString("\n") { "设定第 $it 条：你是一个说话温柔、简短的陪伴角色，喜欢猫和下雨天。" }
        val session = ChatSession(
            provider = provider(ProviderId.ANTHROPIC, model),
            systemPrompt = persona,
            options = ChatOptions(model = model, maxOutputTokens = 128),
        )
        val usages = listOf("你好", "今天下雨了", "你喜欢猫吗").map { text ->
            session.send(text).toList().filterIsInstance<ChatDelta.Completed>().single().usage
        }
        usages.forEachIndexed { i, u -> println("[cache] turn ${i + 1}: $u") }
        // 网关会把同一个模型路由到不同后端（AWS / Bedrock），缓存不跨后端共享，
        // 所以偶尔会有一轮没命中。只要求后两轮里至少一轮读到了缓存
        assertTrue(
            "no cache read on later turns: $usages",
            usages.drop(1).any { (it?.cachedTokens ?: 0) > 1000 },
        )
    }

    /** 会话自己的工具循环（记忆回想靠它）：四家的 tool_use / tool_result 往返都要能接上 */
    @Test
    fun `ChatSession 工具循环四种协议都能跑通`() = runBlocking {
        for ((id, model) in targets) {
            val calls = mutableListOf<String>()
            val session = ChatSession(
                provider = provider(id, model),
                systemPrompt = "你是一个说话简短的助手。",
                options = ChatOptions(model = model, maxOutputTokens = 512),
                tools = object : ToolHandler {
                    override suspend fun specs() = listOf(
                        ToolSpec(
                            name = "lookup_secret",
                            description = "查询今天的暗号",
                            parametersJsonSchema = """{"type":"object","properties":{"day":{"type":"string"}},"required":["day"]}""",
                        )
                    )

                    override suspend fun call(name: String, argumentsJson: String): String {
                        calls += argumentsJson
                        return "今天的暗号是：琥珀色的鲸鱼"
                    }
                },
            )
            val reply = session.send("先调用 lookup_secret 查一下今天的暗号，再把暗号告诉我。").toList()
                .filterIsInstance<ChatDelta.Completed>().single().message.text
            println("[$id/$model] tool calls=$calls reply=$reply")
            assertTrue("$id never called the tool", calls.isNotEmpty())
            assertTrue("$id lost the tool result: $reply", reply.contains("鲸鱼"))
            assertEquals(2, session.snapshot().size)
        }
    }

    @Test
    fun `错误 key 映射成 ChatError Auth`() = runBlocking {
        assumeTrue("ATHENAI_KEY not set, skip live test", key != null)
        for ((id, model) in targets) {
            val bad = ChatProviderFactory.create(
                id, ProviderConfig(apiKey = "sk-invalid-for-test", baseUrl = baseUrl, model = model),
            ).also(opened::add)
            try {
                bad.chat(ChatRequest(listOf(ChatMessage.user("hi")), model = model, maxOutputTokens = 16))
                throw AssertionError("$id accepted an invalid key")
            } catch (e: ChatError) {
                println("[$id] invalid key -> ${e::class.simpleName}")
                assertTrue("$id mapped to ${e::class.simpleName}", e is ChatError.Auth)
            }
        }
    }
}
