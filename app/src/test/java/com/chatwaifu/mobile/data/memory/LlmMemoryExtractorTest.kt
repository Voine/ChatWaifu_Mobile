package com.chatwaifu.mobile.data.memory

import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRequest
import com.chatwaifu.chat.core.ModelInfo
import com.chatwaifu.chat.core.ProviderCapabilities
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.log.MemorySubject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** L2 抽取结果的解析：删除上限、整批丢弃。不走网络。 */
class LlmMemoryExtractorTest {

    private val extractor = LlmMemoryExtractor(object : ChatProvider {
        override val id = ProviderId.OPENAI_COMPAT
        override val displayName = "unused"
        override val capabilities = ProviderCapabilities()
        override val availableModels = emptyList<ModelInfo>()
        override fun chatStream(request: ChatRequest): Flow<ChatDelta> = emptyFlow()
    })

    @Test
    fun `一批最多删两条，多出的删除丢掉，upsert 照常`() {
        val ops = extractor.parse(
            """
            [{"op":"delete","slot":"a"},{"op":"upsert","slot":"名字","content":"小林","importance":90},
             {"op":"delete","slot":"b"},{"op":"delete","slot":"c"},{"op":"delete","slot":"d"}]
            """.trimIndent()
        )
        assertEquals(
            listOf(
                MemoryOp.Delete("a"),
                MemoryOp.Upsert("名字", "小林", 90),
                MemoryOp.Delete("b"),
            ),
            ops,
        )
        assertEquals(LlmMemoryExtractor.MAX_DELETES, ops.count { it is MemoryOp.Delete })
    }

    @Test
    fun `三类主体和约定日期：日期只对约定生效，坏日期不丢整条，未知类别当 user`() {
        val ops = extractor.parse(
            """
            [{"op":"upsert","about":"promise","slot":"约定：唱歌","content":"唱歌","importance":85,"due":"2026-10-19"},
             {"op":"upsert","about":"self","slot":"我的喜欢的天气","content":"下雨天","importance":50,"due":"2026-10-19"},
             {"op":"upsert","about":"promise","slot":"约定：改天","content":"改天吃饭","importance":60,"due":"下周"},
             {"op":"upsert","about":"pet","slot":"名字","content":"小林","importance":90}]
            """.trimIndent()
        ).filterIsInstance<MemoryOp.Upsert>()
        assertEquals(
            listOf(MemorySubject.PROMISE, MemorySubject.SELF, MemorySubject.PROMISE, MemorySubject.USER),
            ops.map { it.subject },
        )
        assertTrue(ops[0].dueAt != null)
        assertEquals(null, ops[1].dueAt)
        assertEquals(null, ops[2].dueAt)
    }

    @Test
    fun `截断的 JSON 整批丢弃，不做部分应用`() {
        assertTrue(extractor.parse("""[{"op":"delete","slot":"名字"},{"op":"ups""").isEmpty())
    }
}
