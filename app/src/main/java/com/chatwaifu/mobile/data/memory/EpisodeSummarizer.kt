package com.chatwaifu.mobile.data.memory

import android.util.Log
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatOptions
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatSession
import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.ChatLogRole
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.toList

/**
 * Description: 把一段对话写成角色第一人称的日记摘要 + 检索关键词。L1 的生成端。
 *
 * 和 [MemoryExtractor] 同一个理由做成接口：端上小模型是下一步。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
interface EpisodeSummarizer {

    /** @return null 表示这次没写成（网络 / 解析失败），调用方下次再试，**不推进游标** */
    suspend fun summarize(segment: List<ChatLogEntry>): EpisodeDraft?
}

data class EpisodeDraft(val summary: String, val keywords: List<String>)

/**
 * 走 LLM 的实现。复用 `ChatCore`，一个不带历史、不带记忆的一次性 [ChatSession]，
 * 和 [LlmMemoryExtractor] 同一个形状。
 *
 * **双方说的话都进摘要**：她答应过什么、你们开过什么玩笑，恰恰是陪伴感的来源。
 * 担心她说过的胡话污染记忆？不用逐句记——摘要本身就是压缩，客套和废话在这一步
 * 会被压成一句或者直接略掉；真要原话时再顺着消息区间回原文去查（分层回忆）。
 */
class LlmEpisodeSummarizer(
    private val provider: ChatProvider,
    private val modelOverride: String? = null,
) : EpisodeSummarizer {

    private val gson = Gson()

    override suspend fun summarize(segment: List<ChatLogEntry>): EpisodeDraft? {
        val transcript = buildTranscript(segment)
        if (transcript.isBlank()) return null
        val session = ChatSession(
            provider = provider,
            systemPrompt = SYSTEM_PROMPT,
            options = ChatOptions(model = modelOverride?.ifBlank { null }, temperature = 0.3f),
        )
        val raw = session.send(transcript).toList()
            .filterIsInstance<ChatDelta.Completed>()
            .firstOrNull()?.message?.text.orEmpty()
        return parse(raw)
    }

    private fun buildTranscript(segment: List<ChatLogEntry>): String = buildString {
        val first = segment.firstOrNull() ?: return@buildString
        appendLine("## 这段对话发生在 ${MemoryTime.dayLabel(first.timeline)}")
        segment.forEach { entry ->
            val who = when (entry.role) {
                ChatLogRole.USER -> "对方"
                ChatLogRole.ASSISTANT -> "我"
                else -> return@forEach
            }
            val text = entry.text.trim().takeIf { it.isNotEmpty() }
                ?: if (entry.attachments.isNotEmpty()) "（发了一张图）" else return@forEach
            appendLine("$who: ${text.take(MAX_LINE_CHARS)}")
        }
    }

    private fun parse(raw: String): EpisodeDraft? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start !in 0..<end) {
            Log.w(TAG, "no json object in response: ${raw.take(200)}")
            return null
        }
        val dto = try {
            gson.fromJson(raw.substring(start, end + 1), Dto::class.java)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "malformed json", e)
            return null
        } ?: return null
        val summary = dto.summary?.trim()?.take(MAX_SUMMARY_CHARS)?.takeIf { it.isNotEmpty() }
            ?: return null
        val keywords = dto.keywords.orEmpty()
            .map { it.trim().take(MAX_KEYWORD_CHARS) }
            .filter { it.length >= 2 }
            .distinct()
            .take(MAX_KEYWORDS)
        return EpisodeDraft(summary, keywords)
    }

    private data class Dto(val summary: String? = null, val keywords: List<String>? = null)

    companion object {
        private const val TAG = "LlmEpisodeSummarizer"
        private const val MAX_LINE_CHARS = 300
        private const val MAX_SUMMARY_CHARS = 300
        private const val MAX_KEYWORD_CHARS = 16
        private const val MAX_KEYWORDS = 16

        /**
         * 摘要指令。两条最要紧：**第一人称日记体**（第三人称分析腔注进 prompt 会把
         * roleplay 拉成报告语气，和 [FactMemoryContributor] 同一个教训）；
         * **关键词 = 原词 + 别名 / 上位词**：以后是拿它去 `contains` 用户的原话，只有原词的话
         * 用户换个说法（「豆豆」→「那只橘猫」）就匹配不上。让模型在写日记时顺带把别的叫法列出来，
         * 零额外调用补上「换说法」的召回——这是不上向量检索的便宜版，见 docs/memory.md 7.7。
         */
        private val SYSTEM_PROMPT = """
            你在帮一个陪伴角色（「我」）写日记。读下面这段「我」和「对方」的聊天，
            用「我」的口吻写一段简短的日记，再给出几个以后回想时用的关键词。

            日记要记：
            - 聊了什么、对方的近况和情绪
            - 我答应过对方的事、我们之间的约定和计划（这些一定要记）
            - 让人印象深的玩笑、称呼、重要时刻
            不要记：客套话、没有信息量的闲聊、我自己随口编的细节（压成一句或略过）。
            2~5 句，150 字以内。称呼对方用聊天里出现过的名字，没有就叫「对方」，
            **不要用「他」「她」**（你不知道对方的性别）。不用写日期，系统会另外标注。

            关键词 5~15 个，以后对方提起这件事时可能用到的说法都要覆盖：
            - 聊天里出现的原词：人名、地名、物品、事件（「豆豆」「杭州」「面试」）
            - 同一样东西的别的叫法和上位词（「豆豆」→「橘猫」「猫」「猫咪」「宠物」；
              「面试」→「找工作」「工作」「入职」）
            每个关键词 2~6 个字，不要整句。

            只输出 JSON：{"summary":"……","keywords":["……","……"]}
        """.trimIndent()
    }
}
