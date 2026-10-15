package com.chatwaifu.mobile.data.memory

import android.content.Context
import com.chatwaifu.chat.core.MemoryContributor
import com.chatwaifu.chat.core.TokenEstimator
import com.chatwaifu.log.EpisodeRepository
import com.chatwaifu.log.MemoryFact
import com.chatwaifu.log.MemoryRepository
import com.chatwaifu.log.MemorySubject
import com.chatwaifu.log.room.RoomEpisodeRepository
import com.chatwaifu.log.room.RoomMemoryRepository

/**
 * Description: 拼注入 system 区的记忆块。[MemoryContributor] 的实现。四节：
 *
 * ```
 * 【你记得关于对方的这些事】       ← L2 事实（subject = USER）
 * - 名字：小林
 * 【你们之间的约定和要跟进的事】   ← L2 约定（PROMISE），带到期日
 * - 约定：入职唱歌：……（10月19日）
 * 【你自己说过的关于自己的事】     ← L2 自我设定（SELF），和人设冲突时以人设为准
 * - 我的喜欢的天气：喜欢下雨天
 * 【最近几次聊天（你的日记）】     ← L1 最近几段摘要
 * - [回忆#12 · 10月12日] ……
 * ```
 *
 * 两层都**变得很慢**（事实只在抽取改了东西时变，日记只在一段聊天结束时多一条），
 * 所以放 system 区、进缓存前缀。更老的摘要不常驻，靠自动回想（[CompanionTurnContext]）
 * 和工具（[MemoryRecallTools]）按需取。
 *
 * ## 为什么用第一人称写
 *
 * 块的措辞是有讲究的，不是随便排版。写成第三人称分析腔
 * （"用户于 X 时间提到职业变动"）会明显拉垮 roleplay —— 角色开始用报告语气说话，
 * 这在陪伴类应用里是能直接听出来的退化。所以用「你记得这些」的口吻，
 * 让它读起来像角色自己的记忆而不是一份档案。
 *
 * ## 超预算怎么办
 *
 * 事实优先，最多占 [FACT_SHARE] 的额度；剩下的给日记，**从最近的往前**塞。
 * 事实内部按 `pinned → importance → updatedAt` 砍尾部（排序由 [MemoryRepository.facts]
 * 在 SQL 里保证）。**不做摘要压缩** —— 压缩要再调一次模型，而且本身会引入失真。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
class MemoryBlockContributor(
    private val facts: MemoryRepository,
    private val episodes: EpisodeRepository,
) : MemoryContributor {

    constructor(context: Context) : this(RoomMemoryRepository(context), RoomEpisodeRepository(context))

    /** 当前角色。切角色时由调用方改，同时会让缓存失效 */
    var characterId: String = ""
        set(value) {
            if (field != value) invalidate()
            field = value
        }

    private var cached: String? = null
    private var cachedFor: String? = null

    /** 常驻 system 区的日记 id。自动回想要避开它们，不然同一段会注入两遍 */
    @Volatile
    var residentEpisodeIds: Set<Long> = emptySet()
        private set

    /**
     * 让下一轮重新读库。**只在两种时候调**：准备会话（切角色 / 从二级页面回来）、
     * 新日记封存。抽取写入事实后**不调**——会话内记忆块保持冻结，缓存前缀才稳得住
     * （见 `CompanionRealResponseDriver.consolidateMemoryInBackground`）。
     *
     * 有缓存是因为这个方法**每轮请求都会被调**，每轮查一次库纯属浪费。
     */
    fun invalidate() {
        cached = null
        cachedFor = null
    }

    override suspend fun memoryBlock(budgetTokens: Int): String? {
        val id = characterId
        if (id.isEmpty()) return null
        cached?.takeIf { cachedFor == id }?.let { return it }

        val sections = mutableListOf<String>()
        var used = 0

        // 三类共用一份额度，按仓库给的优先级（pinned → importance → updatedAt）取，取完再分节
        val kept = mutableListOf<MemoryFact>()
        var factUsed = SUBJECT_HEADERS.values.sumOf { TokenEstimator.estimate(it) }
        for (fact in facts.facts(id)) {
            val cost = TokenEstimator.estimate(fact.line())
            if (factUsed + cost > budgetTokens * FACT_SHARE) break
            kept += fact
            factUsed += cost
        }
        SUBJECT_HEADERS.forEach { (subject, header) ->
            val lines = kept.filter { it.subject == subject }.map { it.line() }
            if (lines.isNotEmpty()) sections += (listOf(header) + lines).joinToString("\n")
        }
        if (kept.isNotEmpty()) used += factUsed

        // 从最近的往前塞，塞不下就停，然后再翻回时间正序
        val diaryLines = ArrayDeque<String>()
        val resident = mutableSetOf<Long>()
        var diaryUsed = TokenEstimator.estimate(DIARY_HEADER)
        for (episode in episodes.recentEpisodes(id, RECENT_EPISODES).asReversed()) {
            val line = episode.recallLine()
            val cost = TokenEstimator.estimate(line)
            if (used + diaryUsed + cost > budgetTokens) break
            diaryLines.addFirst(line)
            resident += episode.id
            diaryUsed += cost
        }
        if (diaryLines.isNotEmpty()) {
            sections += (listOf(DIARY_HEADER) + diaryLines).joinToString("\n")
        }
        residentEpisodeIds = resident

        if (sections.isEmpty()) return null
        val block = sections.joinToString("\n\n")
        cached = block
        cachedFor = id
        return block
    }

    private fun MemoryFact.line(): String {
        val due = dueAt?.takeIf { subject == MemorySubject.PROMISE }
            ?.let { "（${MemoryTime.dayLabel(it)}）" }.orEmpty()
        return "- $slot：$content$due"
    }

    companion object {
        /** 顺序就是注入顺序。约定排在对方的事后面：它和「对方是谁」一样重要，又比自我设定急 */
        private val SUBJECT_HEADERS = linkedMapOf(
            MemorySubject.USER to "【你记得关于对方的这些事】",
            MemorySubject.PROMISE to "【你们之间的约定和要跟进的事（做完了就不用再提）】",
            MemorySubject.SELF to "【你自己说过的关于自己的事（和人物设定冲突时以人物设定为准）】",
        )
        private const val DIARY_HEADER = "【最近几次聊天（你的日记）】"

        /** 常驻的日记条数。再老的靠回想 */
        const val RECENT_EPISODES = 3

        /** 事实最多占多少额度，剩下的归日记 */
        private const val FACT_SHARE = 0.6
    }
}
