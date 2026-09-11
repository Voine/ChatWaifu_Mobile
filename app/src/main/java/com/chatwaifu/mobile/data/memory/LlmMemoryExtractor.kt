package com.chatwaifu.mobile.data.memory

import android.util.Log
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatOptions
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRole
import com.chatwaifu.chat.core.ChatSession
import com.chatwaifu.log.MemoryFact
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.toList

/**
 * Description: 用 LLM 做记忆抽取。
 *
 * 复用 `ChatCore` 本身：新建一个**不带历史**的 [ChatSession]，一轮问答拿 JSON。
 * 零新增网络代码，也自动继承了基座抽象——换 provider 时抽取跟着换。
 *
 * 关键是 `memory = null`：这个 session 绝不能再注入记忆，否则就成了
 * 「拿记忆去抽记忆」，模型会把已有事实原样复述一遍当成新发现。
 *
 * @param modelOverride 抽取专用模型。null / 空表示跟随主模型。抽取是个不要求文采的
 *   结构化任务，便宜模型通常就够，但默认不替用户做这个决定。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
class LlmMemoryExtractor(
    private val provider: ChatProvider,
    private val modelOverride: String? = null,
) : MemoryExtractor {

    private val gson = Gson()

    override suspend fun extract(
        recentTurns: List<ChatMessage>,
        existingFacts: List<MemoryFact>,
    ): List<MemoryOp> {
        if (recentTurns.isEmpty()) return emptyList()

        val session = ChatSession(
            provider = provider,
            systemPrompt = SYSTEM_PROMPT,
            options = ChatOptions(
                model = modelOverride?.ifBlank { null },
                temperature = 0f,
            ),
        )

        val raw = session.send(buildUserMessage(recentTurns, existingFacts))
            .toList()
            .filterIsInstance<ChatDelta.Completed>()
            .firstOrNull()
            ?.message
            ?.text
            .orEmpty()

        return parse(raw)
    }

    private fun buildUserMessage(
        recentTurns: List<ChatMessage>,
        existingFacts: List<MemoryFact>,
    ): String = buildString {
        appendLine("## 已有记忆槽位")
        if (existingFacts.isEmpty()) {
            appendLine("（空）")
        } else {
            existingFacts.forEach { appendLine("- ${it.slot}: ${it.content}") }
        }
        appendLine()
        appendLine("## 最近的对话")
        recentTurns.forEach { message ->
            val who = when (message.role) {
                ChatRole.USER -> "对方"
                ChatRole.ASSISTANT -> "我"
                else -> return@forEach
            }
            val text = message.text.takeIf { it.isNotBlank() } ?: return@forEach
            appendLine("$who: $text")
        }
    }

    /**
     * 解析模型输出。
     *
     * **解析失败整批丢弃**，不做部分应用：一个截断的 JSON 里解出来的 `delete`
     * 可能删掉一条完全正确的事实，而我们没有任何办法判断它是不是模型的本意。
     * 抽取本来就是「有则更好」的增强，丢掉一批的代价远小于删错一条。
     */
    private fun parse(raw: String): List<MemoryOp> {
        val json = raw.extractJsonArray() ?: run {
            Log.w(TAG, "no json array in response: ${raw.take(200)}")
            return emptyList()
        }
        val dtos = try {
            gson.fromJson(json, Array<OpDto>::class.java)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "malformed json, dropping whole batch", e)
            return emptyList()
        } ?: return emptyList()

        return dtos.take(MAX_OPS).mapNotNull { it.toOp() }
    }

    private fun OpDto.toOp(): MemoryOp? {
        // slot 可能是模型吐出来的一整句话，截断以免把唯一索引撑坏
        val cleanSlot = slot?.trim()?.take(MemoryFact.MAX_SLOT_LENGTH)
        if (cleanSlot.isNullOrEmpty()) return null
        return when (op?.lowercase()) {
            "upsert" -> {
                val cleanContent = content?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                MemoryOp.Upsert(
                    slot = cleanSlot,
                    content = cleanContent.take(MAX_CONTENT_LENGTH),
                    importance = (importance ?: DEFAULT_IMPORTANCE).coerceIn(0, 100),
                )
            }

            "delete" -> MemoryOp.Delete(cleanSlot)
            else -> null
        }
    }

    /**
     * 从回复里抠出 JSON 数组。模型经常会加 ```json 围栏或者前后写几句解释，
     * 与其在 prompt 里反复强调「只输出 JSON」，不如在这里宽容一点。
     */
    private fun String.extractJsonArray(): String? {
        val start = indexOf('[')
        val end = lastIndexOf(']')
        return if (start in 0..<end) substring(start, end + 1) else null
    }

    private data class OpDto(
        val op: String? = null,
        val slot: String? = null,
        val content: String? = null,
        val importance: Int? = null,
    )

    companion object {
        private const val TAG = "LlmMemoryExtractor"

        /** 一批最多应用几条。模型偶尔会把整段对话拆成几十条"事实"，挡一下 */
        private const val MAX_OPS = 8
        private const val MAX_CONTENT_LENGTH = 200
        private const val DEFAULT_IMPORTANCE = 50

        /**
         * 抽取指令。
         *
         * 几条限制是必须写死的，否则事实库会被垃圾撑满：只记**长期有效**的，
         * 不记这轮对话本身；不记 assistant 自己说过的话（那是它编的，不是用户的事实）；
         * 不确定就不记。宁可漏也不要脏 —— 漏了下次还有机会，脏了会一直喂给模型。
         */
        private val SYSTEM_PROMPT = """
            你是一个对话记忆抽取器。从最近的对话里找出**关于对方（用户）的长期事实**，
            输出对记忆槽位的增删操作。

            规则：
            1. 只记长期有效的事实：姓名、称呼偏好、职业、家人宠物、长期爱好、重要经历、
               明确表达过的喜恶和边界。
            2. 不要记这轮对话本身发生了什么（"今天聊了天气"不是事实）。
            3. 不要记「我」（assistant）说过的话，那不是对方的事实。
            4. 已有槽位里的信息发生变化时，用同一个 slot 做 upsert 覆盖，不要新建近义槽位。
            5. 事实明确失效时（"猫送人了"）用 delete。
            6. 不确定就不要输出。一次最多 8 条。没有值得记的就输出空数组。

            slot 用简短的中文名词短语，比如「名字」「所在城市」「养的宠物」「不喜欢的称呼」。
            importance 取 0-100，身份类信息高，一般偏好居中。

            只输出 JSON 数组，形如：
            [{"op":"upsert","slot":"名字","content":"小明","importance":90},
             {"op":"delete","slot":"养的宠物"}]
        """.trimIndent()
    }
}
