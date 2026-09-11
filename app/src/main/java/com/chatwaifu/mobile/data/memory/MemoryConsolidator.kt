package com.chatwaifu.mobile.data.memory

import android.content.Context
import android.util.Log
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.log.MemoryFact
import com.chatwaifu.log.MemoryRepository
import com.chatwaifu.log.room.RoomMemoryRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Description: 记忆巩固。决定**什么时候**跑抽取，以及把 [MemoryOp] 落库。
 *
 * ## 时机：TTS 播报窗口
 *
 * 调用点在 `mainLoop()` 里 `generateAndPlaySound()` **之前**，fire-and-forget。
 * 这几秒是白送的：BV2 单句 RTF≈0.36，一段回复要播好几秒，期间用户在听、
 * 主循环在 `fetchInput()` 挂起。而抽取是网络等待、TTS 是 CPU 密集，两者重叠得很好。
 * **完全不占首字延迟** —— 这是整个设计里最不该浪费的一个便宜。
 *
 * ## 闸门：每 [MIN_TURNS] 轮才真跑
 *
 * 一轮一次既费 token 又没信息量（单轮对话里能提炼的稳定事实很少）。
 * 攒几轮再抽，模型能看到上下文，判断也准。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
class MemoryConsolidator(
    private val repository: MemoryRepository,
    private val extractorProvider: () -> MemoryExtractor?,
) {

    constructor(context: Context, extractorProvider: () -> MemoryExtractor?) :
        this(RoomMemoryRepository(context), extractorProvider)

    /**
     * 同时只允许一次抽取在跑。主循环比一次网络往返快得多，不加锁会堆叠成一串并发请求，
     * 而它们看到的是几乎相同的历史，纯属浪费。
     */
    private val mutex = Mutex()

    private var turnsSinceLastRun = 0

    /** 切角色时调。轮次计数和角色绑定，换人要清零 */
    fun reset() {
        turnsSinceLastRun = 0
    }

    /**
     * 记一轮，够数就抽。**永远不抛异常**——记忆是增强不是正确性，
     * 抽取失败绝不能影响主循环。
     *
     * @param recentTurns 最近的历史（调用方给 `ChatSession.snapshot()` 即可，内部会取尾部）
     */
    suspend fun onTurnCompleted(characterId: String, recentTurns: List<ChatMessage>) {
        turnsSinceLastRun++
        if (turnsSinceLastRun < MIN_TURNS) return
        if (characterId.isEmpty()) return

        // 拿不到锁说明上一次还在跑，直接跳过这一轮：轮次计数不清零，下一轮还会再试
        if (!mutex.tryLock()) {
            Log.d(TAG, "extraction already running, skip")
            return
        }
        try {
            turnsSinceLastRun = 0
            runCatching { consolidate(characterId, recentTurns) }
                .onFailure { Log.w(TAG, "consolidate failed, will retry next window", it) }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun consolidate(characterId: String, recentTurns: List<ChatMessage>) {
        val extractor = extractorProvider() ?: run {
            Log.d(TAG, "no extractor available (provider not configured?), skip")
            return
        }
        // 只喂尾部：全量历史既超预算又会让模型重复抽已经记过的事
        val window = recentTurns.takeLast(WINDOW_TURNS)
        val existing = repository.facts(characterId)

        val ops = extractor.extract(window, existing)
        if (ops.isEmpty()) {
            Log.d(TAG, "nothing worth remembering this round")
            return
        }
        apply(characterId, ops)
        Log.i(TAG, "applied ${ops.size} memory op(s) for $characterId")
    }

    private suspend fun apply(characterId: String, ops: List<MemoryOp>) {
        val now = System.currentTimeMillis()
        ops.forEach { op ->
            when (op) {
                is MemoryOp.Upsert -> repository.upsert(
                    MemoryFact(
                        characterId = characterId,
                        slot = op.slot,
                        content = op.content,
                        importance = op.importance,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                // pinned 的行删不掉，返回 0，这是预期行为不是失败
                is MemoryOp.Delete -> repository.deleteBySlot(characterId, op.slot)
            }
        }
    }

    companion object {
        private const val TAG = "MemoryConsolidator"

        /** 攒够几轮才抽 */
        private const val MIN_TURNS = 3

        /** 每次喂给抽取器的历史条数（约等于 MIN_TURNS 的两倍轮次，user+assistant 各算一条） */
        private const val WINDOW_TURNS = 12
    }
}
