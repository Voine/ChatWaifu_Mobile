package com.chatwaifu.mobile.ui.companion

import com.chatwaifu.vits.utils.SentenceSplitter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按句流水线（T2）：顺序、翻译可选、单句失败回落、首声时机、取消必须等推理退出。
 * 用虚拟时间跑，翻译和推理的耗时都是 delay 模拟的。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineTest {

    private class Fake {
        val spoken = mutableListOf<String>()
        var stopped = false
        var firstAudioAt: Long? = null
        var playbackResult = true
    }

    private fun kotlinx.coroutines.test.TestScope.pipeline(
        fake: Fake,
        translate: (suspend (String) -> String?)?,
        speakMs: Long = 100,
    ) = VoicePipeline(
        scope = backgroundScope,
        translate = translate,
        speak = { text ->
            delay(speakMs)
            fake.spoken += text
            true
        },
        awaitPlayback = { fake.playbackResult },
        stopPlayback = { fake.stopped = true },
        onFirstAudio = { fake.firstAudioAt = testScheduler.currentTime },
        splitter = SentenceSplitter(),
        clock = { testScheduler.currentTime },
        log = { _, _ -> },
    )

    @Test
    fun `不翻译时边写边播，首句不等 LLM 结束`() = runTest {
        val fake = Fake()
        val voice = pipeline(fake, translate = null)
        voice.append("一句目。二")
        // backgroundScope 里的 delay 要显式推进虚拟时间才会到期
        advanceTimeBy(101)
        runCurrent()
        // LLM 还没结束，第一句已经推完入队
        assertEquals(listOf("一句目。"), fake.spoken)
        assertEquals(100L, fake.firstAudioAt)
        voice.append("句目。")
        assertTrue(voice.finish())
        assertEquals(listOf("一句目。", "二句目。"), fake.spoken)
    }

    @Test
    fun `翻译耗时不同也按原文顺序播`() = runTest {
        val fake = Fake()
        // 第一句翻得最慢：如果并发翻译、谁先回来谁先播，顺序就会乱
        val cost = mapOf("A。" to 500L, "B。" to 10L, "C。" to 10L)
        val voice = pipeline(fake, translate = { s -> delay(cost.getValue(s)); "jp:$s" })
        voice.append("A。B。C。")
        assertTrue(voice.finish())
        assertEquals(listOf("jp:A。", "jp:B。", "jp:C。"), fake.spoken)
    }

    @Test
    fun `翻译串行，同一时刻只有一个请求在飞`() = runTest {
        val fake = Fake()
        var inFlight = 0
        var maxInFlight = 0
        val voice = pipeline(fake, translate = { s ->
            inFlight++; maxInFlight = maxOf(maxInFlight, inFlight)
            delay(50); inFlight--
            s
        })
        voice.append("一。二。三。四。五。")
        voice.finish()
        assertEquals(1, maxInFlight)
        assertEquals(5, fake.spoken.size)
    }

    @Test
    fun `单句翻译失败或返回空时按原文播，不丢句子`() = runTest {
        val fake = Fake()
        val voice = pipeline(fake, translate = { s ->
            when (s) {
                "坏。" -> error("network down")
                "空。" -> null
                else -> "jp:$s"
            }
        })
        voice.append("好。坏。空。好。")
        voice.finish()
        assertEquals(listOf("jp:好。", "坏。", "空。", "jp:好。"), fake.spoken)
    }

    @Test
    fun `一句都没推出来时 finish 返回 false 且不等播放`() = runTest {
        val fake = Fake()
        var awaited = false
        val voice = VoicePipeline(
            scope = backgroundScope,
            translate = null,
            speak = { false },
            awaitPlayback = { awaited = true; true },
            stopPlayback = {},
            onFirstAudio = { error("no audio expected") },
            splitter = SentenceSplitter(),
            clock = { 0L },
            log = { _, _ -> },
        )
        voice.append("a。b。")
        assertFalse(voice.finish())
        assertFalse(awaited)
    }

    @Test
    fun `取消要等正在推的那一句真正退出`() = runTest {
        // 模拟 BV2：推理不可中断，被取消后也要跑完这一句
        val inferenceDone = CompletableDeferred<Unit>()
        var inferenceFinishedAt = -1L
        val voice = VoicePipeline(
            scope = backgroundScope,
            translate = null,
            speak = {
                withContext(NonCancellable) { delay(1_000) }
                inferenceFinishedAt = testScheduler.currentTime
                inferenceDone.complete(Unit)
                true
            },
            awaitPlayback = { true },
            stopPlayback = {},
            onFirstAudio = {},
            splitter = SentenceSplitter(),
            clock = { testScheduler.currentTime },
            log = { _, _ -> },
        )
        voice.append("長い文章。次")
        runCurrent()
        val cancelReturnedAt = async { voice.cancel(); testScheduler.currentTime }
        advanceTimeBy(1_001)
        runCurrent()
        assertTrue(inferenceDone.isCompleted)
        // cancel 不能在推理还在跑时就返回，否则下一轮会和它同时碰 BV2 单例
        assertTrue(cancelReturnedAt.await() >= inferenceFinishedAt)
    }

    @Test
    fun `取消后不再推新句子，并停掉播放`() = runTest {
        val fake = Fake()
        val voice = pipeline(fake, translate = null, speakMs = 100)
        voice.append("一。二。三。四。")
        runCurrent()
        launch { voice.cancel() }
        advanceUntilIdle()
        assertTrue(fake.stopped)
        assertTrue("spoken=${fake.spoken}", fake.spoken.size <= 1)
        // 取消之后再喂也不会再推
        voice.append("五。六。")
        advanceUntilIdle()
        assertTrue(fake.spoken.size <= 1)
    }
}
