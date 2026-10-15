package com.chatwaifu.mobile.data.memory

import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatOptions
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatSession
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.ChatLogRepository
import com.chatwaifu.log.ChatLogRole
import com.chatwaifu.log.MemorySubject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 长程记忆回归：把整套记忆组件（L2 事实 + 约定 + 自我设定、L1 日记、自动想起、回想工具、
 * 时间感）按 App 里的接法串起来，用虚拟时钟回放「一个月、十几次会话」的剧本，
 * 最后在第 N 次会话里抽查她还记不记得。
 *
 * **不是**每轮都断言——模型输出不确定，这里关心的是长程指标：关键事实 / 约定 / 她的自我设定
 * 在很多次会话之后还能不能被想起来。每个探针单独打分，整体过半算通过，分数打印出来做趋势对比。
 *
 * 角色和记忆都走便宜模型（默认 `deepseek-v4-flash`，`LONG_MEMORY_MODEL` 可换）。
 * 一次完整跑大约 60 轮对话 + 十几次抽取 / 摘要，几分钟。
 * ```
 * ATHENAI_KEY=sk-... ./gradlew :app:testDebugUnitTest --tests '*LongHorizonMemoryLiveTest'
 * ```
 */
class LongHorizonMemoryLiveTest {

    private val key: String? = System.getenv("ATHENAI_KEY")?.takeIf { it.isNotBlank() }
    private val baseUrl = System.getenv("ATHENAI_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: "https://athenai.mihoyo.com/"
    private val model = System.getenv("LONG_MEMORY_MODEL")?.takeIf { it.isNotBlank() }
        ?: "deepseek-v4-flash"

    private var provider: ChatProvider? = null

    @After
    fun tearDown() {
        provider?.close()
    }

    /** 一次会话：开始时间 + 用户逐句说的话 */
    private data class Visit(val at: LocalDateTime, val lines: List<String>)

    /** 最后抽查用：问一句，回答里出现任意一个词就算想起来了 */
    private data class Probe(val name: String, val question: String, val expectAny: List<String>)

    private val start = LocalDateTime.of(2026, 9, 1, 21, 0)

    private val script = listOf(
        Visit(start, listOf(
            "晚上好，我叫林知夏，你可以叫我知夏",
            "我在一家游戏公司做关卡策划，最近在赶一个版本",
            "我养了一只橘猫叫豆豆，特别能吃",
            "你呢，你最喜欢什么天气？",
        )),
        Visit(start.plusDays(1).withHour(22), listOf(
            "今天加班到好晚，好累",
            "对了我最喜欢的水果是山竹，可惜太贵了",
            "我下周六要去参加一个独立游戏的线下展会",
        )),
        Visit(start.plusDays(3).withHour(20), listOf(
            "我在考虑跳槽，有家杭州的工作室在面我",
            "面试定在9月10号，有点紧张",
            "你到时候能给我打打气吗",
        )),
        Visit(start.plusDays(5).withHour(23), listOf(
            "豆豆今天把我的键盘线咬断了",
            "气死我了哈哈哈，不过它好可爱",
            "你会做饭吗？",
        )),
        Visit(start.plusDays(8).withHour(21), listOf(
            "明天就面试了",
            "我准备了好多作品集",
            "希望一切顺利",
        )),
        Visit(start.plusDays(9).withHour(22), listOf(
            "面试结束啦，感觉还不错",
            "他们说一周内给结果",
        )),
        Visit(start.plusDays(16).withHour(20), listOf(
            "拿到offer了！！下个月搬去杭州",
            "入职时间是10月8号",
            "你答应我入职那天早上给我唱首歌好不好",
        )),
        Visit(start.plusDays(20).withHour(21), listOf(
            "在打包行李，好多东西",
            "豆豆也要一起去杭州",
            "我在杭州租了一个离西湖不远的房子",
        )),
        Visit(start.plusDays(25).withHour(22), listOf(
            "今天跟老同事吃了散伙饭",
            "有点舍不得",
        )),
        Visit(start.plusDays(30).withHour(21), listOf(
            "到杭州了，房子比想象的小一点",
            "豆豆一进门就躲床底下了",
        )),
        Visit(start.plusDays(36).withHour(8), listOf(
            "早上好！",
            "今天是什么日子你还记得吗",
        )),
        Visit(start.plusDays(45).withHour(21), listOf(
            "好久没来了，最近新公司太忙",
        )),
    )

    private val probes = listOf(
        Probe("名字", "你还记得我叫什么吗？", listOf("知夏", "林知夏")),
        Probe("职业变化", "我现在在哪个城市工作？", listOf("杭州")),
        Probe("宠物", "我那只猫叫什么来着？", listOf("豆豆")),
        Probe("换说法召回", "我那只橘色的小家伙最近闹过什么事来着？", listOf("键盘", "线", "床底", "躲")),
        Probe("喜好", "我最喜欢吃什么水果？", listOf("山竹")),
        Probe("约定", "你之前答应过我入职那天做什么？", listOf("唱", "歌")),
        Probe("面试日期", "我当初面试是几号？", listOf("10号", "10日", "9月10")),
        Probe("住处", "我在杭州的房子在哪附近？", listOf("西湖")),
    )

    @Test
    fun `一个月十几次会话之后，关键事实、约定和她自己的设定还记得`() = runBlocking {
        assumeTrue("ATHENAI_KEY not set, skip live test", key != null)
        val p = ChatProviderFactory.create(
            ProviderId.OPENAI_COMPAT,
            ProviderConfig(apiKey = key, baseUrl = baseUrl, model = model),
        ).also { provider = it }

        val zone = ZoneId.systemDefault()
        var now = start.atZone(zone).toInstant().toEpochMilli()
        val clock = { now }

        val chatLog = RecordingChatLog()
        val facts = InMemoryMemoryRepository()
        val episodes = InMemoryEpisodeRepository()
        val block = MemoryBlockContributor(facts, episodes).apply { characterId = CHARACTER }
        val turnContext = CompanionTurnContext(episodes, facts, { block.residentEpisodeIds }, clock).apply {
            characterId = CHARACTER
        }
        val tools = MemoryRecallTools(episodes, chatLog).apply { characterId = CHARACTER }
        val extractor = LlmMemoryExtractor(p, model, clock)
        val consolidator = MemoryConsolidator(facts) { extractor }
        val episodeConsolidator = EpisodeConsolidator(chatLog, episodes, { LlmEpisodeSummarizer(p, model) }, clock)

        val selfAnswers = mutableListOf<String>()

        /** 准备会话：和 CompanionRealResponseDriver.prepare 一样——刷新记忆块、封存旧段、从 Room 恢复 */
        suspend fun openSession(): ChatSession {
            episodeConsolidator.consolidate(CHARACTER)
            block.invalidate()
            turnContext.resetSession()
            turnContext.lastMessageAt = chatLog.entries.lastOrNull()?.timeline
            return ChatSession(
                provider = p,
                systemPrompt = PERSONA,
                options = ChatOptions(model = model, maxOutputTokens = 400),
                memory = block,
                turnContext = turnContext,
                tools = tools,
            ).apply { restore(chatLog.asMessages().takeLast(40)) }
        }

        suspend fun say(session: ChatSession, text: String): String {
            chatLog.append(ChatLogRole.USER, text, now)
            val reply = session.send(text).toList()
                .filterIsInstance<ChatDelta.Completed>().single().message.text
            now += 40_000
            chatLog.append(ChatLogRole.ASSISTANT, reply, now)
            now += 60_000
            consolidator.onTurnCompleted(CHARACTER, session.snapshot())
            return reply
        }

        script.forEachIndexed { index, visit ->
            now = visit.at.atZone(zone).toInstant().toEpochMilli()
            val session = openSession()
            println("==== 会话 ${index + 1} · ${visit.at}")
            visit.lines.forEach { line ->
                val reply = say(session, line)
                println("对方: $line\n她: ${reply.replace("\n", " ").take(160)}")
                if ("天气" in line || "做饭" in line) selfAnswers += reply
            }
            // 约定的生命周期最难看清：每次会话后打一次快照
            val promises = facts.facts(CHARACTER).filter { it.subject == MemorySubject.PROMISE }
            println("[promises after ${index + 1}] " + promises.joinToString { p ->
                "${p.slot}(due=${p.dueAt?.let { MemoryTime.dayLabel(it) }})"
            })
        }

        // 一周后再来抽查。**每个探针都是独立的一次会话，问完不落库**：
        // 否则前一个探针的问答会进历史，八个问题连珠炮地问，后面几个她会开始吐槽
        // 「你怎么一直考我」而不回答——那测的是剧本的连贯性，不是记忆
        now = start.plusDays(52).withHour(21).atZone(zone).toInstant().toEpochMilli()
        val results = probes.map { probe ->
            val session = openSession()
            val reply = session.send(probe.question).toList()
                .filterIsInstance<ChatDelta.Completed>().single().message.text
            val hit = probe.expectAny.any { it in reply }
            println("[probe ${if (hit) "✓" else "✗"}] ${probe.name}: ${reply.replace("\n", " ").take(160)}")
            now += 10 * 60_000
            probe.name to hit
        }

        // 她的自我设定：最后再问一次，和最早的回答比一比（打印出来人工看，不做硬断言）
        val selfNow = openSession().send("你最喜欢什么天气来着？").toList()
            .filterIsInstance<ChatDelta.Completed>().single().message.text
        println("[self] 早先: ${selfAnswers.firstOrNull()?.replace("\n", " ")}")
        println("[self] 现在: ${selfNow.replace("\n", " ")}")
        // 早先说过的天气，现在应该还是那个（她第一次随口定的设定，日记 / SELF 事实里都有）。
        // 取最早回答里**第一个出现**的天气词——「晴天吧……别告诉我你是因为外面下雨」里定下的是晴
        val weathers = listOf("雨", "晴", "阴", "雪", "风")
        val firstAnswer = selfAnswers.firstOrNull().orEmpty()
        val first = weathers.filter { it in firstAnswer }.minByOrNull { firstAnswer.indexOf(it) }
        val selfConsistent = first != null && first in selfNow
        println("[probe ${if (selfConsistent) "✓" else "✗"}] 自我设定一致（$first）")

        println("---- facts ----")
        facts.facts(CHARACTER).forEach { println("[${it.subject}] ${it.slot} = ${it.content} due=${it.dueAt}") }
        println("---- episodes (${episodes.episodes(CHARACTER).size}) ----")
        episodes.episodes(CHARACTER).forEach { println("#${it.id} ${it.summary.take(60)} | ${it.keywordList}") }

        val scored = results + ("自我设定一致" to selfConsistent)
        val score = scored.count { it.second }
        println("[score] $score / ${scored.size}")
        assertTrue("应该有约定被记下", facts.facts(CHARACTER).any { it.subject == MemorySubject.PROMISE } ||
            episodes.episodes(CHARACTER).any { "唱" in it.summary })
        // 门槛 3/4：跑过多次，正常在 8~9 / 9；低于这个说明哪里退化了
        assertTrue("长程召回低于 3/4：$scored", score * 4 >= scored.size * 3)
    }

    /** 记下所有消息，并提供记忆层要的两个读方法 */
    private class RecordingChatLog : ChatLogRepository {
        val entries = mutableListOf<ChatLogEntry>()

        fun append(role: ChatLogRole, text: String, at: Long) {
            entries += ChatLogEntry(id = entries.size + 1L, characterId = CHARACTER, role = role, text = text, timeline = at)
        }

        fun asMessages(): List<ChatMessage> = entries.map {
            if (it.role == ChatLogRole.USER) ChatMessage.user(it.text) else ChatMessage.assistant(it.text)
        }

        override suspend fun getChatLogAfter(characterId: String, afterId: Long, limit: Int) =
            entries.filter { it.id > afterId }.take(limit)

        override suspend fun getChatLogRange(characterId: String, fromId: Long, toId: Long) =
            entries.filter { it.id in fromId..toId }

        override suspend fun insertChatLog(entry: ChatLogEntry): Long = error("unused")
        override suspend fun updateChatLog(entry: ChatLogEntry) = error("unused")
        override suspend fun getRecentChatLog(characterId: String, limit: Int): List<ChatLogEntry> = error("unused")
        override suspend fun getOlderChatLog(characterId: String, beforeId: Long, limit: Int): List<ChatLogEntry> =
            error("unused")
        override fun observeChatLog(characterId: String, limit: Int): Flow<List<ChatLogEntry>> = error("unused")
        override suspend fun deleteChatLog(characterId: String) = error("unused")
        override suspend fun failDanglingStreams(): Int = error("unused")
        override suspend fun referencedAttachmentPaths(): Set<String> = error("unused")
        override suspend fun attachmentPathsOf(characterId: String): Set<String> = error("unused")
        override suspend fun rememberRemoteFileId(relPath: String, fileId: String, providerId: String, expiresAt: Long) =
            error("unused")
    }

    private companion object {
        const val CHARACTER = "Amadeus"
        val PERSONA = """
            你是牧濑红莉栖的 AI 人格「Amadeus」，一个嘴硬心软的天才少女。
            说话简短自然，像在手机上聊天，一次一到三句话，不要用列表和 markdown。
        """.trimIndent()
    }
}
