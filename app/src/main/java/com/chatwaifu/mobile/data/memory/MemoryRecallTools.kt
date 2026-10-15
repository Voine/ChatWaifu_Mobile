package com.chatwaifu.mobile.data.memory

import android.util.Log
import com.chatwaifu.chat.core.ToolHandler
import com.chatwaifu.chat.core.ToolSpec
import com.chatwaifu.log.ChatLogRepository
import com.chatwaifu.log.ChatLogRole
import com.chatwaifu.log.EpisodeRepository
import com.google.gson.JsonParser

/**
 * Description: 分层回忆交给模型自己决定要不要往下挖的那部分。两件工具，对应两层：
 *
 * | 层 | 工具 | 返回 |
 * |---|---|---|
 * | 摘要 | [SEARCH_MEMORIES] | 按关键词搜到的几段日记（带 id） |
 * | 原文 | [READ_ORIGINAL] | 某段日记对应的聊天原话 |
 *
 * 大多数轮次用不到它们：最近的日记常驻 system 区，关键词撞上的老日记会被自动想起来
 * （[CompanionTurnContext]）。只有「摘要说不清、对方追问细节 / 原话」时模型才会调，
 * 这时多一次往返是值得的——平时不付这个延迟。
 *
 * **按角色隔离**：两个工具都只在 [characterId] 名下查。`read_original` 的 id 是模型给的，
 * 不校验归属的话它猜一个数字就能读到别的角色的聊天记录。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
class MemoryRecallTools(
    private val episodes: EpisodeRepository,
    private val chatLog: ChatLogRepository,
) : ToolHandler {

    @Volatile
    var characterId: String = ""

    /** 当前模型不支持工具调用时关掉，specs 返回空，请求里就不声明工具 */
    @Volatile
    var enabled: Boolean = true

    override suspend fun specs(): List<ToolSpec> {
        val id = characterId
        // 一段日记都没有时声明工具只是白占 token，还可能诱导模型去搜一个空库
        if (!enabled || id.isEmpty() || !episodes.hasAny(id)) return emptyList()
        return SPECS
    }

    override suspend fun call(name: String, argumentsJson: String): String {
        val id = characterId
        val args = runCatching { JsonParser.parseString(argumentsJson).asJsonObject }.getOrNull()
        // 记忆页之外唯一能看到「她为什么想起这件事」的地方
        Log.i(TAG, "$name $argumentsJson")
        return when (name) {
            SEARCH_MEMORIES -> {
                val query = args?.get("query")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                val terms = query.split(' ', '，', ',', '、', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                val found = episodes.search(id, terms, SEARCH_LIMIT)
                if (found.isEmpty()) "没有找到相关的回忆。"
                else found.joinToString("\n") { it.recallLine() }
            }

            READ_ORIGINAL -> {
                val episodeId = args?.get("episode_id")?.takeIf { it.isJsonPrimitive }
                    ?.runCatching { asLong }?.getOrNull()
                    ?: return "参数错误：需要 episode_id（回忆编号，数字）。"
                val episode = episodes.find(episodeId)?.takeIf { it.characterId == id }
                    ?: return "没有编号为 $episodeId 的回忆。"
                val lines = chatLog.getChatLogRange(id, episode.startMessageId, episode.endMessageId)
                    .mapNotNull { entry ->
                        val who = when (entry.role) {
                            ChatLogRole.USER -> "对方"
                            ChatLogRole.ASSISTANT -> "我"
                            else -> return@mapNotNull null
                        }
                        entry.text.trim().takeIf { it.isNotEmpty() }?.let { "$who: $it" }
                    }
                if (lines.isEmpty()) "这段聊天的原文已经被删除了，只剩下日记：${episode.summary}"
                else clip(lines)
            }

            else -> "没有这个工具：$name"
        }
    }

    /** 原文可能很长，按字数截。保头保尾，中间省略——开头是起因，结尾是结论 */
    private fun clip(lines: List<String>): String {
        val full = lines.joinToString("\n")
        if (full.length <= MAX_ORIGINAL_CHARS) return full
        val half = MAX_ORIGINAL_CHARS / 2
        return full.take(half) + "\n……（中间省略）……\n" + full.takeLast(half)
    }

    companion object {
        private const val TAG = "MemoryRecallTools"
        const val SEARCH_MEMORIES = "search_memories"
        const val READ_ORIGINAL = "read_original"
        private const val SEARCH_LIMIT = 5
        private const val MAX_ORIGINAL_CHARS = 3_000

        private val SPECS = listOf(
            ToolSpec(
                name = SEARCH_MEMORIES,
                description = "回想以前和对方聊过的事。对方提到你不记得的过去时用它，" +
                    "按关键词搜你的日记，返回带编号的日记摘要。最近几次的日记你已经记得，不用搜。",
                parametersJsonSchema = """
                    {"type":"object","properties":{"query":{"type":"string",
                    "description":"空格分隔的关键词：人名、地名、物品、事件，用对方的原词"}},
                    "required":["query"]}
                """.trimIndent(),
            ),
            ToolSpec(
                name = READ_ORIGINAL,
                description = "读某段回忆当时的聊天原话。只在日记摘要不够、需要确切的原话或细节时用。",
                parametersJsonSchema = """
                    {"type":"object","properties":{"episode_id":{"type":"integer",
                    "description":"回忆编号，就是「回忆#12」里的 12"}},"required":["episode_id"]}
                """.trimIndent(),
            ),
        )
    }
}
