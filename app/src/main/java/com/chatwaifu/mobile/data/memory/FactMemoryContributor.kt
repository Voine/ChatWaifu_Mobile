package com.chatwaifu.mobile.data.memory

import android.content.Context
import com.chatwaifu.chat.core.MemoryContributor
import com.chatwaifu.chat.core.TokenEstimator
import com.chatwaifu.log.MemoryRepository
import com.chatwaifu.log.room.RoomMemoryRepository

/**
 * Description: 把 L2 事实拼成注入 system 区的记忆块。[MemoryContributor] 的实现。
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
 * 按 `pinned → importance → updatedAt` 砍尾部（排序由
 * [MemoryRepository.facts] 在 SQL 里保证）。**不做摘要压缩** ——
 * 压缩要再调一次模型，为几百 token 不值得，而且压缩本身会引入失真。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
class FactMemoryContributor(
    private val repository: MemoryRepository,
) : MemoryContributor {

    constructor(context: Context) : this(RoomMemoryRepository(context))

    /** 当前角色。切角色时由调用方改，同时会让缓存失效 */
    var characterId: String = ""
        set(value) {
            if (field != value) invalidate()
            field = value
        }

    private var cached: String? = null
    private var cachedFor: String? = null

    /**
     * 记忆被改写时调（抽取落库后、用户在记忆页编辑后）。
     *
     * 有缓存是因为这个方法**每轮请求都会被调**，而记忆的变化频率比对话轮次低一两个数量级，
     * 每轮查一次库纯属浪费。
     */
    fun invalidate() {
        cached = null
        cachedFor = null
    }

    override suspend fun memoryBlock(budgetTokens: Int): String? {
        val id = characterId
        if (id.isEmpty()) return null
        cached?.takeIf { cachedFor == id }?.let { return it }

        val facts = repository.facts(id)
        if (facts.isEmpty()) return null

        val lines = mutableListOf<String>()
        // 标题也要算进预算，否则刚好卡在边界时会超
        var used = TokenEstimator.estimate(HEADER)
        for (fact in facts) {
            val line = "- ${fact.slot}：${fact.content}"
            val cost = TokenEstimator.estimate(line)
            if (used + cost > budgetTokens) break
            lines += line
            used += cost
        }
        if (lines.isEmpty()) return null

        val block = (listOf(HEADER) + lines).joinToString("\n")
        cached = block
        cachedFor = id
        return block
    }

    companion object {
        private const val HEADER = "【你记得关于对方的这些事】"
    }
}
