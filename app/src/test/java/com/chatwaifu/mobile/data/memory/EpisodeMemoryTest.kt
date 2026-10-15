package com.chatwaifu.mobile.data.memory

import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.MemoryEpisode
import com.chatwaifu.log.MessageStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** L1 / L3：切段、巩固游标、自动回想、回想工具。全部不走网络。 */
class EpisodeMemoryTest {

    private val t0 = 1_760_000_000_000L

    // ---------------- 切段 ----------------

    @Test
    fun `隔了几个小时就是两段，还在进行的那段不封`() {
        val first = chat("A", firstId = 1, startAt = t0, turns = 3)
        val second = chat("A", firstId = 7, startAt = t0 + 5 * HOUR, turns = 2)
        val segments = EpisodeSegmenter.closedSegments(first + second, now = second.last().timeline + MINUTE)
        assertEquals(listOf((1L..6L).toList()), segments.map { s -> s.map { it.id } })
    }

    @Test
    fun `最后一段冷了足够久也会封`() {
        val run = chat("A", firstId = 1, startAt = t0, turns = 2)
        val now = run.last().timeline + EpisodeSegmenter.GAP_MS + MINUTE
        assertEquals(1, EpisodeSegmenter.closedSegments(run, now).size)
    }

    @Test
    fun `聊得太长时按条数先切一块，且不拆开一问一答`() {
        val run = chat("A", firstId = 1, startAt = t0, turns = 30) // 60 条，一直没断
        val segments = EpisodeSegmenter.closedSegments(run, now = run.last().timeline)
        assertEquals(1, segments.size)
        val cut = segments.single()
        assertTrue(cut.size <= EpisodeSegmenter.MAX_MESSAGES)
        // 段尾是回答，下一条是新的提问
        assertEquals(com.chatwaifu.log.ChatLogRole.ASSISTANT, cut.last().role)
    }

    @Test
    fun `正在流式输出的那一条以及之后的不碰`() {
        val run = chat("A", firstId = 1, startAt = t0, turns = 2).toMutableList()
        run[3] = run[3].copy(status = MessageStatus.STREAMING)
        val now = t0 + 10 * HOUR
        val segments = EpisodeSegmenter.closedSegments(run, now)
        assertEquals(listOf(1L, 2L, 3L), segments.single().map { it.id })
    }

    // ---------------- 巩固 ----------------

    private class ScriptedSummarizer(var fail: Boolean = false) : EpisodeSummarizer {
        val calls = mutableListOf<List<Long>>()
        override suspend fun summarize(segment: List<ChatLogEntry>): EpisodeDraft? {
            calls += segment.map { it.id }
            return if (fail) null else EpisodeDraft("我们聊了${segment.size}句", listOf("关键词${segment.first().id}"))
        }
    }

    @Test
    fun `游标推进：写过的段不会再写，摘要失败时游标不动`() = runBlocking {
        val log = chat("A", 1, t0, 3) + chat("A", 7, t0 + 5 * HOUR, 3)
        val episodes = InMemoryEpisodeRepository()
        val summarizer = ScriptedSummarizer(fail = true)
        val now = t0 + 20 * HOUR
        val consolidator = EpisodeConsolidator(FakeChatLogRepository(log), episodes, { summarizer }, { now })

        assertEquals(0, consolidator.consolidate("A"))
        assertEquals(0L, episodes.coveredUntil("A"))

        summarizer.fail = false
        assertEquals(2, consolidator.consolidate("A"))
        assertEquals(12L, episodes.coveredUntil("A"))
        assertEquals(0, consolidator.consolidate("A"))
        // 第一次失败的那段后来写成了，没有重复写
        assertEquals(listOf(1L, 7L), episodes.episodes("A").map { it.startMessageId })
    }

    @Test
    fun `用户删掉的日记不会被重新写回来`() = runBlocking {
        val log = chat("A", 1, t0, 3)
        val episodes = InMemoryEpisodeRepository()
        val summarizer = ScriptedSummarizer()
        val consolidator = EpisodeConsolidator(FakeChatLogRepository(log), episodes, { summarizer }, { t0 + 20 * HOUR })
        consolidator.consolidate("A")
        episodes.deleteById(episodes.episodes("A").single().id)

        assertEquals(0, consolidator.consolidate("A"))
        assertEquals(1, summarizer.calls.size)
        assertFalse(episodes.hasAny("A"))
    }

    // ---------------- 自动回想 + 时间感 ----------------

    private fun episode(id: Long, keywords: List<String>, endedAt: Long = t0, character: String = "A") =
        MemoryEpisode(
            id = id, characterId = character, startMessageId = id * 10, endMessageId = id * 10 + 5,
            startedAt = endedAt, endedAt = endedAt, summary = "日记$id",
            keywords = keywords.joinToString(MemoryEpisode.KEYWORD_SEPARATOR), createdAt = 0, updatedAt = 0,
        )

    @Test
    fun `关键词撞上原话才想起，长词优先，常驻的跳过`() {
        val all = listOf(
            episode(1, listOf("豆豆", "橘猫")),
            episode(2, listOf("猫")),          // 单字不算
            episode(3, listOf("外滩", "上海")),
            episode(4, listOf("豆豆")),         // 常驻 system 区，跳过
        )
        val hit = EpisodeMatcher.match("豆豆最近还好吗，好想去上海", all, exclude = setOf(4), limit = 2)
        assertEquals(listOf(1L, 3L), hit.map { it.id })
    }

    @Test
    fun `备注带当前时间和间隔，并在下一轮更新间隔`() = runBlocking {
        val repo = InMemoryEpisodeRepository()
        repo.insert(episode(0, listOf("山竹")))
        var now = t0 + 4 * 24 * HOUR
        val context = CompanionTurnContext(repo, InMemoryMemoryRepository(), residentIds = { emptySet() }, clock = { now })
        context.characterId = "A"
        context.lastMessageAt = t0

        val first = context.turnContext(ChatMessage.user("我还是最喜欢山竹"))
        assertTrue(first, first.startsWith("（系统备注，不是对方说的话，只作背景）现在是"))
        assertTrue(first, first.contains("距离上次聊天过去了 4 天"))
        assertTrue(first, first.contains("[回忆#1 · "))

        now += 2 * MINUTE
        val second = context.turnContext(ChatMessage.user("嗯嗯"))
        assertFalse("刚聊过不提间隔：$second", second.contains("距离上次"))
        assertFalse(second.contains("回忆#"))
    }

    @Test
    fun `到期的约定在备注里提醒一次，同一会话不重复，没到期的不提`() = runBlocking {
        val now = t0 + 10 * 24 * HOUR
        val facts = InMemoryMemoryRepository()
        fun promise(slot: String, due: Long?) = com.chatwaifu.log.MemoryFact(
            characterId = "A", slot = slot, content = "内容-$slot", importance = 80,
            subject = com.chatwaifu.log.MemorySubject.PROMISE, dueAt = due, createdAt = 0, updatedAt = 0,
        )
        facts.upsert(promise("约定：唱歌", now + 2 * HOUR))
        facts.upsert(promise("约定：下个月", now + 30 * 24 * HOUR))
        facts.upsert(promise("约定：没日期", null))
        val context = CompanionTurnContext(InMemoryEpisodeRepository(), facts, residentIds = { emptySet() }, clock = { now })
        context.characterId = "A"

        val first = context.turnContext(ChatMessage.user("早"))
        assertTrue(first, first.contains("内容-约定：唱歌"))
        assertFalse(first, first.contains("下个月") || first.contains("没日期"))
        assertFalse(context.turnContext(ChatMessage.user("嗯")).contains("唱歌"))

        context.resetSession()
        assertTrue(context.turnContext(ChatMessage.user("又来了")).contains("唱歌"))
    }

    @Test
    fun `记忆块按类别分节，约定带日期`() = runBlocking {
        val facts = InMemoryMemoryRepository()
        fun fact(slot: String, subject: com.chatwaifu.log.MemorySubject, due: Long? = null) =
            com.chatwaifu.log.MemoryFact(
                characterId = "A", slot = slot, content = "c-$slot", importance = 50,
                subject = subject, dueAt = due, createdAt = 0, updatedAt = 0,
            )
        facts.upsert(fact("名字", com.chatwaifu.log.MemorySubject.USER))
        facts.upsert(fact("我的喜欢的天气", com.chatwaifu.log.MemorySubject.SELF))
        facts.upsert(fact("约定：唱歌", com.chatwaifu.log.MemorySubject.PROMISE, due = t0))
        val block = MemoryBlockContributor(facts, InMemoryEpisodeRepository()).apply { characterId = "A" }
            .memoryBlock(1_200)!!
        val user = block.indexOf("【你记得关于对方的这些事】")
        val promise = block.indexOf("【你们之间的约定")
        val self = block.indexOf("【你自己说过的关于自己的事")
        assertTrue(block, user in 0 until promise && promise < self)
        assertTrue(block, block.contains("- 约定：唱歌：c-约定：唱歌（"))
        assertTrue(block, block.substring(self).contains("我的喜欢的天气"))
    }

    // ---------------- 回想工具 ----------------

    @Test
    fun `读原文只能读当前角色名下的回忆`() = runBlocking {
        val log = chat("A", 1, t0, 2) { "A的第${it}句" } + chat("B", 100, t0, 2) { "B的秘密$it" }
        val episodes = InMemoryEpisodeRepository()
        val idA = episodes.insert(episode(0, listOf("x")).copy(startMessageId = 1, endMessageId = 4))
        val idB = episodes.insert(episode(0, listOf("y"), character = "B").copy(startMessageId = 100, endMessageId = 103))
        val tools = MemoryRecallTools(episodes, FakeChatLogRepository(log)).apply { characterId = "A" }

        val own = tools.call(MemoryRecallTools.READ_ORIGINAL, """{"episode_id":$idA}""")
        assertTrue(own, own.contains("对方: A的第0句") && own.contains("我: A的第1句"))

        val other = tools.call(MemoryRecallTools.READ_ORIGINAL, """{"episode_id":$idB}""")
        assertFalse("串台了：$other", other.contains("秘密"))
    }

    @Test
    fun `没有日记时不声明工具；搜索按关键词返回带编号的摘要`() = runBlocking {
        val episodes = InMemoryEpisodeRepository()
        val tools = MemoryRecallTools(episodes, FakeChatLogRepository(emptyList())).apply { characterId = "A" }
        assertTrue(tools.specs().isEmpty())

        val id = episodes.insert(episode(0, listOf("外滩", "上海")))
        assertEquals(2, tools.specs().size)
        val found = tools.call(MemoryRecallTools.SEARCH_MEMORIES, """{"query":"外滩 夜景"}""")
        assertTrue(found, found.contains("回忆#$id"))
        assertEquals("没有找到相关的回忆。", tools.call(MemoryRecallTools.SEARCH_MEMORIES, """{"query":"北京"}"""))

        tools.enabled = false
        assertTrue(tools.specs().isEmpty())
    }
}
