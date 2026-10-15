package com.chatwaifu.mobile.data.memory

import android.util.Log
import com.chatwaifu.log.ChatLogRepository
import com.chatwaifu.log.EpisodeRepository
import com.chatwaifu.log.MemoryEpisode
import kotlinx.coroutines.sync.Mutex

/**
 * Description: L1 巩固：把已经结束的聊天段写成摘要落库。
 *
 * 切段规则在 [EpisodeSegmenter]；这里只管「什么时候跑、跑完怎么推进游标」。
 *
 * ## 什么时候跑
 * - 每轮结束后（和 L2 抽取一起，在 TTS 播报窗口里，不占首字延迟）
 * - Companion 准备会话时（上次聊完隔了几小时再打开 App，上一段就在这时封存）
 *
 * 大多数时候切段结果是空的（这次聊天还没结束、也没超长），这时只有一次本地查询，没有网络。
 *
 * ## 游标
 * 「已覆盖到哪条消息」就是最后一段摘要的 `endMessageId`（[EpisodeRepository.coveredUntil]），
 * 不另存状态。摘要失败就不插行，游标不动，下次自然重试。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
class EpisodeConsolidator(
    private val chatLog: ChatLogRepository,
    private val episodes: EpisodeRepository,
    private val summarizerProvider: () -> EpisodeSummarizer?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /**
     * @return 本次新写了几段。**永远不抛异常**——记忆是增强不是正确性
     */
    suspend fun consolidate(characterId: String): Int {
        if (characterId.isEmpty()) return 0
        if (!mutex.tryLock()) return 0
        return try {
            runCatching { consolidateLocked(characterId) }
                .onFailure { Log.w(TAG, "episode consolidation failed, will retry later", it) }
                .getOrDefault(0)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun consolidateLocked(characterId: String): Int {
        val cursor = episodes.coveredUntil(characterId)
        val pending = chatLog.getChatLogAfter(characterId, cursor, PENDING_LIMIT)
        val segments = EpisodeSegmenter.closedSegments(pending, clock())
            .filter { it.size >= EpisodeSegmenter.MIN_MESSAGES }
            // 第一次打开这个功能时可能积压很多段，一次只写几段，别一口气打出一串请求
            .take(MAX_PER_RUN)
        if (segments.isEmpty()) return 0

        val summarizer = summarizerProvider() ?: return 0
        var written = 0
        for (segment in segments) {
            // 必须按顺序写：游标是 max(endMessageId)，跳过一段去写后面的会让前面那段永远被跳过
            val draft = summarizer.summarize(segment) ?: break
            val now = clock()
            episodes.insert(
                MemoryEpisode(
                    characterId = characterId,
                    startMessageId = segment.first().id,
                    endMessageId = segment.last().id,
                    startedAt = segment.first().timeline,
                    endedAt = segment.last().timeline,
                    summary = draft.summary,
                    keywords = draft.keywords.joinToString(MemoryEpisode.KEYWORD_SEPARATOR),
                    createdAt = now,
                    updatedAt = now,
                )
            )
            written++
        }
        if (written > 0) Log.i(TAG, "wrote $written episode(s) for $characterId")
        return written
    }

    private companion object {
        const val TAG = "EpisodeConsolidator"
        const val PENDING_LIMIT = 400
        const val MAX_PER_RUN = 3
    }
}
