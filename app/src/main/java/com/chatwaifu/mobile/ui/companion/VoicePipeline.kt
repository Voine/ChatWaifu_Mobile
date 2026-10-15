package com.chatwaifu.mobile.ui.companion

import android.os.SystemClock
import android.util.Log
import com.chatwaifu.vits.utils.SentenceSplitter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Description: 一轮回复的语音流水线（T2）：
 *
 * ```
 * LLM delta ─▶ SentenceSplitter ─▶ [翻译，可选] ─▶ TTS 推理 ─▶ 排队播放
 *              （调用方线程）         translator 协程     speaker 协程
 * ```
 *
 * 改造前是「LLM 完整结束 → 整段翻译 → 才开始合成」，首声延迟 = 整段 LLM + 一次翻译往返 +
 * 首句推理。现在首句凑齐就进翻译 / 推理，后面的句子边写边排队。
 *
 * 几条约束（都有单测守着）：
 * - **顺序**：翻译和推理各是一个串行协程，用 Channel 串起来，所以播放顺序一定是原文顺序，
 *   不会「谁先翻译回来谁先播」。翻译串行也顺带把百度的并发压到 1，不会因为按句调用被限流
 * - **翻译可选**：[translate] 为 null 就跳过那一级；单句翻译失败回落原文，不丢句子
 * - **取消**：[cancel] 立刻停播、关闭队列；**并且等推理协程真正退出**再返回。
 *   BV2 的 native 推理不可中断，不等的话下一轮会和这一句的残余推理同时碰进程级单例
 * - **收尾**：[finish] 吐出最后半句、等全部播完，返回是否真的出过声
 *
 * 不负责 TTS 的生命周期，只负责「一轮」。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
internal class VoicePipeline(
    scope: CoroutineScope,
    /** null = 不翻译。返回 null 表示这句翻译失败，按原文播 */
    private val translate: (suspend (String) -> String?)?,
    /** 推理并排队一句。阻塞在推理上；返回是否成功入队 */
    private val speak: suspend (String) -> Boolean,
    /** 所有句子都送完之后调：等播放完成，返回是否真的播完了 */
    private val awaitPlayback: suspend () -> Boolean,
    /** 立刻停播（不等推理） */
    private val stopPlayback: () -> Unit,
    /** 第一句成功入队时回调一次，用来把 UI 切到 SPEAKING */
    private val onFirstAudio: () -> Unit,
    private val splitter: SentenceSplitter = SentenceSplitter(
        firstClauseMinChars = SentenceSplitter.DEFAULT_FIRST_CLAUSE_MIN_CHARS,
    ),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    /** 注入是为了单测：JVM 上没有 android.util.Log */
    private val log: (String, String) -> Unit = { tag, msg -> Log.i(tag, msg) },
) {
    private val startedAt = clock()
    private val sentences = Channel<String>(Channel.UNLIMITED)
    private val spoken = Channel<String>(Channel.UNLIMITED)
    private var anyAudio = false
    private var closed = false

    private val translatorJob: Job? = translate?.let { tr ->
        scope.launch {
            for (sentence in sentences) {
                val text = try {
                    tr(sentence)?.takeIf { it.isNotBlank() } ?: sentence
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    log(TAG, "translate failed, speak original: ${e.message}")
                    sentence
                }
                spoken.send(text)
            }
            spoken.close()
        }
    }

    private val speakerJob: Job = scope.launch {
        for (text in spoken) {
            if (speak(text) && !anyAudio) {
                anyAudio = true
                log(LATENCY_TAG, "first_pcm ${clock() - startedAt}")
                onFirstAudio()
            }
        }
    }

    /** LLM 每来一段增量就喂进来。可以在 IO 线程直接调，不会阻塞在推理上。 */
    fun append(delta: String) {
        if (closed) return
        splitter.append(delta).forEach(::enqueue)
    }

    /**
     * LLM 结束：吐出剩下的半句，等推理和播放全部完成。
     * @return 至少一句出声且完整播完
     */
    suspend fun finish(): Boolean {
        if (closed) return false
        splitter.finish().forEach(::enqueue)
        closed = true
        sentences.close()
        if (translatorJob == null) spoken.close()
        translatorJob?.join()
        speakerJob.join()
        return anyAudio && awaitPlayback()
    }

    /**
     * 中途放弃（用户打断 / 切角色 / 出错）。停播，丢弃队列，**等推理协程退出**再返回。
     * 可以重复调用；finish 之后调用也无害。
     */
    suspend fun cancel() {
        closed = true
        stopPlayback()
        sentences.cancel()
        spoken.cancel()
        withContext(NonCancellable) {
            translatorJob?.cancel()
            speakerJob.cancel()
            translatorJob?.join()
            speakerJob.join()
        }
    }

    private fun enqueue(sentence: String) {
        // 已关闭（cancel / finish 之后）时 trySend 直接失败，不需要先判断
        if (!anyAudio && !loggedFirstSentence) {
            loggedFirstSentence = true
            log(LATENCY_TAG, "first_sentence ${clock() - startedAt}")
        }
        val target = if (translatorJob != null) sentences else spoken
        target.trySend(sentence)
    }

    private var loggedFirstSentence = false

    companion object {
        private const val TAG = "VoicePipeline"

        /** 首声延迟的量化日志：`adb logcat -s CWLatency`，见 v2-roadmap T2 */
        const val LATENCY_TAG = "CWLatency"
    }
}
