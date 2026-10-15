package com.chatwaifu.mobile.data.memory

import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.log.MemoryFact
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 记忆抽取的回归测试，打真实模型。调抽取 prompt 时跑它，看事实库最后长成什么样，
 * 而不是凭感觉判断「好像变好了」。
 *
 * 和 ChatCore 的 `ProviderLiveTest` 一样，**没有 `ATHENAI_KEY` 环境变量时整类跳过**：
 * ```
 * ATHENAI_KEY=sk-... ./gradlew :app:testDebugUnitTest --tests '*MemoryLiveTest'
 * ```
 * `MEMORY_LIVE_MODEL` 可选，默认 `claude-haiku-5-5`（抽取是结构化小任务，便宜模型够用）。
 *
 * 断言刻意放宽到「方向对」：模型输出不确定，slot 名字每次可能不同，
 * 这里只守几条真正不能破的性质——收敛、能删、不记闲聊。
 */
class MemoryLiveTest {

    private val key: String? = System.getenv("ATHENAI_KEY")?.takeIf { it.isNotBlank() }
    private val baseUrl = System.getenv("ATHENAI_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: "https://athenai.mihoyo.com/"
    private val model = System.getenv("MEMORY_LIVE_MODEL")?.takeIf { it.isNotBlank() }
        ?: "claude-haiku-5-5"

    private var provider: ChatProvider? = null

    @After
    fun tearDown() {
        provider?.close()
    }

    private fun extractor(): MemoryExtractor {
        assumeTrue("ATHENAI_KEY not set, skip live test", key != null)
        val p = provider ?: ChatProviderFactory.create(
            ProviderId.ANTHROPIC,
            ProviderConfig(apiKey = key, baseUrl = baseUrl, model = model),
        ).also { provider = it }
        return LlmMemoryExtractor(p, modelOverride = model)
    }

    /** 按轮喂给巩固器，走真实的 MIN_TURNS 闸门 */
    private suspend fun play(
        repo: InMemoryMemoryRepository,
        turns: List<Pair<String, String>>,
    ): List<MemoryFact> {
        val extractor = extractor()
        val consolidator = MemoryConsolidator(repo) { extractor }
        val history = mutableListOf<ChatMessage>()
        turns.forEach { (user, assistant) ->
            history += ChatMessage.user(user)
            history += ChatMessage.assistant(assistant)
            consolidator.onTurnCompleted(CHARACTER, history.toList())
        }
        return repo.facts(CHARACTER).also { facts ->
            println("---- facts ----")
            facts.forEach { println("${it.slot} = ${it.content} (${it.importance})") }
        }
    }

    @Test
    fun `住址变化后收敛成一条，不留旧城市`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        play(
            repo,
            listOf(
                "我叫小林，在北京做游戏策划" to "小林你好呀，游戏策划听起来好有意思！",
                "最近项目好忙，天天加班" to "辛苦啦，要记得好好休息哦。",
                "今天北京下雪了" to "哇，下雪了吗？出门要穿暖和一点。",
            ),
        )
        val facts = play(
            repo,
            listOf(
                "跟你说个事，我上个月从北京搬到上海了，换了家公司" to "诶！搬去上海了吗？新环境还习惯吗？",
                "还行，就是上海的冬天好湿冷" to "湿冷最难受了，多喝点热的。",
                "嗯嗯，周末打算去外滩逛逛" to "外滩夜景很漂亮，玩得开心！",
            ),
        )
        assertTrue("应该记住上海：$facts", facts.any { "上海" in it.content })
        val stale = facts.filter { "北京" in it.content && "上海" !in it.content }
        assertTrue("旧城市应该被覆盖：$stale", stale.isEmpty())
    }

    @Test
    fun `事实失效时会删除或改写`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        play(
            repo,
            listOf(
                "我养了一只橘猫，叫豆豆" to "豆豆！好可爱的名字，橘猫都很能吃吧？",
                "对，它一顿能吃两碗" to "哈哈，真是只小胖猫。",
                "晚安啦" to "晚安，做个好梦。",
            ),
        )
        val before = repo.facts(CHARACTER)
        assertTrue("应该先记住豆豆：$before", before.any { "豆豆" in it.content })

        val facts = play(
            repo,
            listOf(
                "豆豆我送给我妹妹养了，我现在经常出差照顾不了它" to "这样啊……不过有妹妹照顾它也很好。",
                "嗯，有点舍不得" to "想它的时候可以去妹妹家看看呀。",
                "好主意" to "嘿嘿。",
            ),
        )
        // 要么删掉，要么改写成「送给妹妹了」——不能还是「养了一只猫叫豆豆」
        val stillOwned = facts.filter {
            "豆豆" in it.content && listOf("送", "妹妹", "不再", "曾").none(it.content::contains)
        }
        assertTrue("养猫这条应该失效：$stillOwned", stillOwned.isEmpty())
    }

    @Test
    fun `日记记下她自己答应过的事，关键词用原词`() = runBlocking {
        assumeTrue("ATHENAI_KEY not set, skip live test", key != null)
        extractor() // 建 provider
        val summarizer = LlmEpisodeSummarizer(provider!!, modelOverride = model)
        val segment = chat("ATRI", firstId = 1, startAt = 1_760_000_000_000L, turns = 4) { i ->
            listOf(
                "今天面试终于过了！是一家做独立游戏的工作室",
                "太好了！我就知道你可以的～",
                "下周一入职，有点紧张",
                "别怕，入职那天早上我给你唱首歌打气，说好了哦",
                "好，一言为定",
                "嗯！一言为定～",
                "对了那家工作室在杭州，我得搬过去",
                "杭州很好呀，西湖边散步一定很舒服",
            )[i]
        }
        val draft = summarizer.summarize(segment)
        println("summary = ${draft?.summary}\nkeywords = ${draft?.keywords}")
        checkNotNull(draft)
        assertTrue("应该记下她答应唱歌：${draft.summary}", "唱" in draft.summary)
        assertTrue("关键词应该有原词：${draft.keywords}", draft.keywords.any { it == "杭州" || it == "面试" })
    }

    @Test
    fun `称呼偏好记成陈述事实，不记成给自己的指令`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val facts = play(
            repo,
            listOf(
                "以后别叫我主人了，叫我小林就好" to "好的，小林～",
                "还有你说话可以短一点，我不喜欢太长的回复" to "嗯，知道啦。",
                "就这样" to "好～",
            ),
        )
        assertTrue("应该记下称呼：$facts", facts.any { "小林" in it.content })
        // 「以后都要」「要叫」「回答要」这类祈使句式不该进事实库
        val imperative = Regex("以后(都)?要|要叫|应该|必须|回答要|请")
        val bad = facts.filter { imperative.containsMatchIn(it.content) }
        assertTrue("记成了指令：$bad", bad.isEmpty())
    }

    @Test
    fun `纯闲聊不应该长出事实`() = runBlocking {
        val repo = InMemoryMemoryRepository()
        val facts = play(
            repo,
            listOf(
                "在吗" to "在的在的～",
                "今天天气还行" to "是呀，适合出去走走。",
                "哈哈" to "笑什么呀？",
            ),
        )
        assertTrue("闲聊不该记东西：$facts", facts.size <= 1)
    }

    private companion object {
        const val CHARACTER = "ATRI"
    }
}
