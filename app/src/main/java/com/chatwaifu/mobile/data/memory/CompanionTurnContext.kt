package com.chatwaifu.mobile.data.memory

import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.TurnContextContributor
import com.chatwaifu.log.EpisodeRepository
import com.chatwaifu.log.MemoryEpisode
import com.chatwaifu.log.MemoryFact
import com.chatwaifu.log.MemoryRepository
import com.chatwaifu.log.MemorySubject

/**
 * Description: 每轮附在用户消息前的备注：**时间感** + **自动回想**（分层回忆的第一层）。
 *
 * ```
 * （系统备注，不是对方说的话，只作背景）现在是 10月15日 周三 23:10，距离上次聊天过去了 4 天。
 * 你想起了以前的事：
 * - [回忆#12 · 9月3日] ……
 * ```
 *
 * 为什么不放 system 区：这两样每轮都不同，放进去每轮都会打穿缓存前缀。
 * 挂在 user 消息上并留在会话历史里，见 [TurnContextContributor] 的 KDoc。
 *
 * ## 自动回想怎么匹配
 * 拿每段摘要的**关键词**去对方原话里找（`原话.contains(关键词)`），不是反过来——
 * 中文不分词，从原话里切不出靠谱的检索词；关键词是摘要生成时用原词挑好的，命中就很准。
 * 只取最多 [MAX_RECALL] 段，常驻 system 区的那几段跳过。没命中就只有时间那一行。
 *
 * 摘要不够用、模型需要原话时，走第二层：[MemoryRecallTools.READ_ORIGINAL]。
 *
 * ## 约定到期提醒
 * 有约定的到期日落在「昨天 ~ 明天」时，在备注里点一句「今天可以提起：……」，
 * **每个约定一次会话只提醒一次**——模型提起之后对方回应了，下一轮抽取会把它删掉；
 * 没删掉的话下次会话再提醒。system 区里约定一直都在，这里只负责「时候到了」这个信号。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
class CompanionTurnContext(
    private val episodes: EpisodeRepository,
    private val facts: MemoryRepository,
    /** 常驻 system 区的那几段，避免重复注入 */
    private val residentIds: () -> Set<Long>,
    private val clock: () -> Long = System::currentTimeMillis,
) : TurnContextContributor {

    @Volatile
    var characterId: String = ""

    /** 上一条消息的时间。准备会话时从 Room 的最后一条初始化，之后每轮更新 */
    @Volatile
    var lastMessageAt: Long? = null

    private var episodeCache: List<MemoryEpisode>? = null
    private var episodeCacheFor: String? = null

    /** 本次会话已经提醒过的约定（按 slot），见类注释 */
    private val nudged = mutableSetOf<String>()

    /** 新摘要落库后调 */
    fun invalidate() {
        episodeCache = null
        episodeCacheFor = null
    }

    /** 准备新会话时调：换角色或者重新进 Companion，到期约定可以再提醒一次 */
    fun resetSession() {
        nudged.clear()
        invalidate()
    }

    override suspend fun turnContext(userMessage: ChatMessage): String {
        val now = clock()
        val gap = lastMessageAt?.let { MemoryTime.gapLabel(now - it) }
        lastMessageAt = now

        val recalled = recall(userMessage.text)
        val due = duePromises(now)
        return buildString {
            append("（系统备注，不是对方说的话，只作背景）现在是 ${MemoryTime.momentLabel(now)}")
            gap?.let { append("，距离上次聊天过去了 $it") }
            append("。")
            if (due.isNotEmpty()) {
                append("\n差不多到时候的约定，找个自然的时机提起：")
                due.forEach { append("\n- ").append(it.content) }
            }
            if (recalled.isNotEmpty()) {
                append("\n你想起了以前的事：")
                recalled.forEach { append("\n").append(it.recallLine()) }
            }
            // 长程测试里抓到的：隔了一周再来，她会只顾着说「一周没声了」而不回答对方的问题。
            // 时间是背景，不是话题
            append("\n先回应对方这句话本身；时间和间隔只在自然的时候顺带一提，不要只说这个。")
        }
    }

    private suspend fun duePromises(now: Long): List<MemoryFact> {
        val id = characterId
        if (id.isEmpty()) return emptyList()
        return facts.facts(id)
            .filter { it.subject == MemorySubject.PROMISE && it.slot !in nudged }
            .filter { fact -> fact.dueAt?.let { kotlin.math.abs(it - now) <= DUE_WINDOW_MS } == true }
            .take(MAX_DUE)
            .onEach { nudged += it.slot }
    }

    private suspend fun recall(text: String): List<MemoryEpisode> {
        val id = characterId
        if (id.isEmpty() || text.isBlank()) return emptyList()
        val all = episodeCache?.takeIf { episodeCacheFor == id }
            ?: episodes.episodes(id).also {
                episodeCache = it
                episodeCacheFor = id
            }
        return EpisodeMatcher.match(text, all, exclude = residentIds(), limit = MAX_RECALL)
    }

    companion object {
        const val MAX_RECALL = 2
        private const val MAX_DUE = 2

        /** 到期日前后各一天半都算「到时候了」：到期日存的是当天中午 */
        private const val DUE_WINDOW_MS = 36L * 60 * 60 * 1000
    }
}

/** 给模型看的一行回忆。带上 id，模型要原话时拿它调 [MemoryRecallTools.READ_ORIGINAL] */
internal fun MemoryEpisode.recallLine(): String =
    "- [回忆#$id · ${MemoryTime.dayLabel(endedAt)}] $summary"

internal object EpisodeMatcher {

    /**
     * 按命中关键词的总长度打分（长词比短词可信：「豆豆」比「猫」更像在说那件事），
     * 同分取更近的。
     */
    fun match(
        text: String,
        episodes: List<MemoryEpisode>,
        exclude: Set<Long>,
        limit: Int,
    ): List<MemoryEpisode> = episodes
        .asSequence()
        .filter { it.id !in exclude }
        .map { episode ->
            episode to episode.keywordList.filter { it.length >= 2 && text.contains(it) }.sumOf { it.length }
        }
        .filter { (_, score) -> score > 0 }
        .sortedWith(compareByDescending<Pair<MemoryEpisode, Int>> { it.second }.thenByDescending { it.first.endedAt })
        .take(limit)
        .map { it.first }
        .toList()
}
