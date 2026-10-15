package com.chatwaifu.vits.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 增量断句的边界：标点落在 delta 中间 / delta 边界 / 无标点长串，
 * 以及「任意拆法喂进去结果都和整段一次性切一样」这条不变量。
 */
class SentenceSplitterTest {

    private fun feed(splitter: SentenceSplitter, vararg deltas: String): List<String> =
        deltas.flatMap { splitter.append(it) } + splitter.finish()

    @Test
    fun `标点在 delta 中间`() {
        val s = SentenceSplitter()
        // 「。」后面的「今」到了才能确认第一句结束
        assertEquals(listOf("こんにちは。"), s.append("こんにちは。今"))
        assertEquals(emptyList<String>(), s.append("日は晴れ。"))
        assertEquals(listOf("今日は晴れ。"), s.finish())
    }

    @Test
    fun `标点正好在 delta 边界`() {
        val s = SentenceSplitter()
        assertEquals(emptyList<String>(), s.append("こんにちは。"))
        assertEquals(listOf("こんにちは。"), s.append("元気"))
        assertEquals(listOf("元気？"), feed(s, "？"))
    }

    @Test
    fun `连续结束符归同一句，不切出孤立标点`() {
        assertEquals(listOf("本当に？！", "うん……。"), SentenceSplitter.split("本当に？！うん……。"))
        assertEquals(listOf("Really?!", "Yes."), SentenceSplitter.split("Really?! Yes."))
    }

    @Test
    fun `无标点长串按 maxChars 硬切`() {
        val long = "あ".repeat(130)
        val out = SentenceSplitter(maxChars = 60).run { append(long) + finish() }
        assertEquals(listOf(60, 60, 10), out.map { it.length })
    }

    @Test
    fun `只有标点和空白的片段不算一句`() {
        assertEquals(emptyList<String>(), SentenceSplitter.split("。。\n\n  "))
        assertEquals(listOf("はい。"), SentenceSplitter.split("\n\nはい。\n\n"))
    }

    @Test
    fun `任意拆法喂进去都和整段切出来一样`() {
        val text = "春が好きです。桜がとてもきれいだから！\n夏は暑すぎて、ちょっと苦手かな？でも海は楽しい。" +
            "Autumn is nice, too. 冬は……寒い。"
        val expected = SentenceSplitter.split(text)
        for (chunk in 1..7) {
            val s = SentenceSplitter()
            val out = feed(s, *text.chunked(chunk).toTypedArray())
            assertEquals("chunk=$chunk", expected, out)
        }
    }

    @Test
    fun `首句在逗号处提前切，之后的句子不受影响`() {
        val s = SentenceSplitter(firstClauseMinChars = 5)
        val out = feed(s, "私が一番好きな季節は、やっぱり春です。夏も好きだけど、暑すぎる。")
        assertEquals(
            listOf("私が一番好きな季節は、", "やっぱり春です。", "夏も好きだけど、暑すぎる。"),
            out,
        )
    }

    @Test
    fun `首句太短时不在逗号处切`() {
        val out = feed(SentenceSplitter(firstClauseMinChars = 10), "えっと、春が好きです。")
        assertEquals(listOf("えっと、春が好きです。"), out)
    }

    @Test
    fun `千分位逗号不是断句点`() {
        val out = feed(SentenceSplitter(firstClauseMinChars = 3), "It costs 1,000 yen, okay.")
        assertEquals(listOf("It costs 1,000 yen,", "okay."), out)
    }

    @Test
    fun `finish 之后复用会重新允许首句提前切`() {
        val s = SentenceSplitter(firstClauseMinChars = 3)
        feed(s, "一つ目の文、続き。")
        assertEquals(listOf("二つ目の段落、", "続き。"), feed(s, "二つ目の段落、続き。"))
    }
}
