package com.chatwaifu.mobile.data.memory

import android.util.Log
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatOptions
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRole
import com.chatwaifu.chat.core.ChatSession
import com.chatwaifu.log.MemoryFact
import com.chatwaifu.log.MemorySubject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
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
    /** 「今天」是哪天：模型要把「下周一」换算成日期 */
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
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
        appendLine("## 今天是 ${today()}")
        appendLine()
        appendLine("## 已有记忆槽位")
        if (existingFacts.isEmpty()) {
            appendLine("（空）")
        } else {
            existingFacts.forEach { fact ->
                val due = fact.dueAt?.let { "（到期 ${isoDate(it)}）" }.orEmpty()
                appendLine("- [${fact.subject.tag}] ${fact.slot}: ${fact.content}$due")
            }
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
    internal fun parse(raw: String): List<MemoryOp> {
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

        return capDeletes(dtos.take(MAX_OPS).mapNotNull { it.toOp() })
    }

    /**
     * 一批最多删 [MAX_DELETES] 条，多出来的删除丢掉（upsert 照常应用）。
     *
     * 删除是唯一不可逆的操作：模型误判一次「这些都过时了」就能清空半个事实库。
     * 真要删的事实下一批还会再提，删慢一点没有代价。
     * 同 OpenClaw dreaming 的「单次最多移除 25%」，见 docs/memory.md 7.6
     */
    internal fun capDeletes(ops: List<MemoryOp>): List<MemoryOp> {
        var deletes = 0
        return ops.filter { op ->
            op !is MemoryOp.Delete || ++deletes <= MAX_DELETES
        }
    }

    private fun OpDto.toOp(): MemoryOp? {
        // slot 可能是模型吐出来的一整句话，截断以免把唯一索引撑坏
        val cleanSlot = slot?.trim()?.take(MemoryFact.MAX_SLOT_LENGTH)
        if (cleanSlot.isNullOrEmpty()) return null
        return when (op?.lowercase()) {
            "upsert" -> {
                val cleanContent = content?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                val kind = MemorySubject.entries.firstOrNull { it.tag == about?.trim()?.lowercase() }
                    ?: MemorySubject.USER
                MemoryOp.Upsert(
                    slot = cleanSlot,
                    content = cleanContent.take(MAX_CONTENT_LENGTH),
                    importance = (importance ?: DEFAULT_IMPORTANCE).coerceIn(0, 100),
                    subject = kind,
                    // 只有约定才有到期日；解析不了的日期当没有，不丢整条
                    dueAt = due?.takeIf { kind == MemorySubject.PROMISE }?.let(::parseDue),
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

    private fun today(): String {
        val date = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        return "$date ${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)}"
    }

    private fun isoDate(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toString()

    /** `yyyy-MM-dd` → 当天中午（避开时区边界把日期算错一天） */
    private fun parseDue(text: String): Long? = runCatching {
        LocalDate.parse(text.trim()).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    private data class OpDto(
        val op: String? = null,
        val slot: String? = null,
        val content: String? = null,
        val importance: Int? = null,
        /** user / self / promise */
        val about: String? = null,
        /** 约定的到期日 yyyy-MM-dd */
        val due: String? = null,
    )

    companion object {
        private const val TAG = "LlmMemoryExtractor"

        /** 一批最多应用几条。模型偶尔会把整段对话拆成几十条"事实"，挡一下 */
        private const val MAX_OPS = 8
        internal const val MAX_DELETES = 2
        private const val MAX_CONTENT_LENGTH = 200
        private const val DEFAULT_IMPORTANCE = 50

        /**
         * 抽取指令。
         *
         * 几条限制是必须写死的，否则事实库会被垃圾撑满：只记**长期有效**的，
         * 不记这轮对话本身；不记 assistant 自己说过的话（那是它编的，不是用户的事实）；
         * **写陈述事实不写指令**——「以后都要叫他 X」这种条目注进 system 区等于变相改人设，
         * 而且会和真正的人设打架（Hermes Agent 的记忆指引里同样有这一条）；
         * 不确定就不记。宁可漏也不要脏 —— 漏了下次还有机会，脏了会一直喂给模型。
         */
        private val SYSTEM_PROMPT = """
            你是一个陪伴角色（「我」）的记忆整理器。从最近的对话里找出值得长期记住的东西，
            输出对记忆槽位的增删操作。记忆分三类（about 字段）：

            - user：关于对方的长期事实。姓名、称呼偏好、职业、家人宠物、长期爱好、
              重要经历、明确表达过的喜恶和边界。
            - self：「我」在聊天里给自己定下的设定和喜好（「我最喜欢下雨天」「我不会做饭」）。
              只记「我」明确说出口、以后可能再被问到的；客套话、语气词不算。
            - promise：双方的约定、「我」答应过的事、需要以后跟进的事
              （「入职那天早上给对方唱歌」「问问豆豆喜不喜欢新罐头」）。
              能推算出日子的给 due（yyyy-MM-dd，按上面「今天」换算「下周一」「明天」）；
              推算不出就不给。事情已经做完 / 问过了 / 取消了，就 delete 这一条。

            规则：
            1. 不要记这轮对话本身发生了什么（"今天聊了天气"不是事实）。
            2. 已有槽位里的信息发生变化时，用同一个 slot 做 upsert 覆盖，不要新建近义槽位。
               更新时**保留仍然成立的细节**（日期、数字、名字），不要在改写时把它们弄丢或改错。
            3. 事实明确失效时（"猫送人了"）用 delete。
            4. 不确定就不要输出。一次最多 8 条，其中 delete 最多 2 条。没有值得记的就输出空数组。
            5. content 写成陈述事实，不要写成给自己的指令：
               「喜欢被叫小林」✓　「以后都要叫他小林」✗
               「不喜欢长篇大论」✓　「回答要简短」✗
            6. 三类共用一个槽位名空间：self 的 slot 以「我的」开头，promise 的 slot 以「约定：」开头，
               避免和对方的槽位撞名。

            slot 用简短的中文名词短语，比如「名字」「所在城市」「我的喜欢的天气」「约定：入职唱歌」。
            importance 取 0-100，身份类信息和约定高，一般偏好居中。

            只输出 JSON 数组，形如：
            [{"op":"upsert","about":"user","slot":"名字","content":"小明","importance":90},
             {"op":"upsert","about":"self","slot":"我的喜欢的天气","content":"喜欢下雨天","importance":50},
             {"op":"upsert","about":"promise","slot":"约定：入职唱歌","content":"对方入职那天早上我给对方唱歌打气","importance":85,"due":"2026-10-19"},
             {"op":"delete","slot":"养的宠物"}]
        """.trimIndent()
    }
}
