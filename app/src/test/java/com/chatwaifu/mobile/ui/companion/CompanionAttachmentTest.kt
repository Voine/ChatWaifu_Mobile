package com.chatwaifu.mobile.ui.companion

import com.chatwaifu.mobile.data.model.CharacterPackage
import com.chatwaifu.mobile.data.model.ModelSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** T6：附件入口的能力 gate、类型菜单、导入、发送、删除、失败提示、流水线开口进 SPEAKING。 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionAttachmentTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val character = CharacterPackage(
        id = "c1",
        displayName = "ATRI",
        storageKey = "ATRI",
        source = ModelSource.BUILT_IN,
        live2dDir = "/x",
        live2dEntryFileName = "a.model3.json",
        vitsDir = null,
        bertDir = null,
        speakerId = 0,
        language = 0,
    )

    private val image = PendingAttachment("ATRI/a.jpg", AttachmentType.IMAGE, "/abs/ATRI/a.jpg")
    private val visionOnly = AttachmentSupport(image = true, doc = true)

    private inner class FakeDriver(
        private val support: AttachmentSupport,
        private val ingest: suspend (String, AttachmentType?) -> AttachmentIngestResult = { _, _ ->
            AttachmentIngestResult.Success(image)
        },
        private val reply: (CompanionRequest) -> Flow<CompanionResponseEvent> = {
            flowOf(CompanionResponseEvent.Reply("ok"), CompanionResponseEvent.Finished)
        },
    ) : CompanionResponseDriver {
        val requests = mutableListOf<CompanionRequest>()
        val discarded = mutableListOf<PendingAttachment>()
        val ingestedAs = mutableListOf<AttachmentType?>()

        override fun responses(request: CompanionRequest): Flow<CompanionResponseEvent> {
            requests += request
            return reply(request)
        }

        override suspend fun prepare(character: com.chatwaifu.mobile.data.model.CharacterModel) =
            CompanionPreparation(attachmentSupport = support)

        override suspend fun ingestAttachment(uri: String, expected: AttachmentType?): AttachmentIngestResult {
            ingestedAs += expected
            return ingest(uri, expected)
        }

        override fun discardAttachment(attachment: PendingAttachment) {
            discarded += attachment
        }
    }

    private fun kotlinx.coroutines.test.TestScope.ready(driver: FakeDriver): CompanionViewModel =
        CompanionViewModel(driver).apply {
            setCharacter(character.id, character.displayName)
            prepareConversation(character)
            advanceUntilIdle()
        }

    /** 收集期间 [block] 里发出的选择器请求。收集协程跑在测试调度器上，结束时取消 */
    private fun kotlinx.coroutines.test.TestScope.collectPicks(
        vm: CompanionViewModel,
        block: () -> Unit,
    ): List<PickRequest> {
        val picks = mutableListOf<PickRequest>()
        val job = launch { vm.pickRequests.collect { picks += it } }
        advanceUntilIdle()
        block()
        advanceUntilIdle()
        job.cancel()
        return picks
    }

    @Test
    fun `模型不支持看图时视觉入口只提示，不打开选择器`() = runTest(dispatcher) {
        val vm = ready(FakeDriver(AttachmentSupport.NONE))
        val picks = collectPicks(vm) { vm.onEvent(CompanionEvent.Vision) }
        assertTrue(picks.isEmpty())
        assertTrue(vm.uiState.value.notice!!.contains("不支持看图"))
    }

    @Test
    fun `支持看图时视觉入口打开相册`() = runTest(dispatcher) {
        val vm = ready(FakeDriver(visionOnly))
        val picks = collectPicks(vm) { vm.onEvent(CompanionEvent.Vision) }
        assertEquals(listOf(PickRequest(null)), picks)
    }

    @Test
    fun `什么附件都收不了时加号不展开菜单`() = runTest(dispatcher) {
        val vm = ready(FakeDriver(AttachmentSupport.NONE))
        vm.onEvent(CompanionEvent.ToggleAttachMenu)
        assertFalse(vm.uiState.value.attachments.pickerMenuOpen)
        assertTrue(vm.uiState.value.notice!!.contains("不支持附件"))
    }

    @Test
    fun `菜单里选模型收不了的类型只提示，收得了的打开对应选择器`() = runTest(dispatcher) {
        val vm = ready(FakeDriver(visionOnly))
        vm.onEvent(CompanionEvent.ToggleAttachMenu)
        assertTrue(vm.uiState.value.attachments.pickerMenuOpen)

        val rejected = collectPicks(vm) { vm.onEvent(CompanionEvent.PickAttachment(AttachmentType.AUDIO)) }
        assertTrue(rejected.isEmpty())
        assertTrue(vm.uiState.value.notice!!.contains("音频"))
        assertFalse(vm.uiState.value.attachments.pickerMenuOpen)

        val opened = collectPicks(vm) { vm.onEvent(CompanionEvent.PickAttachment(AttachmentType.DOC)) }
        assertEquals(listOf(PickRequest(AttachmentType.DOC)), opened)
    }

    @Test
    fun `菜单选的类型传给 driver 校验，相册入口不限定类型`() = runTest(dispatcher) {
        val driver = FakeDriver(AttachmentSupport(image = true, audio = true, doc = true))
        val vm = ready(driver)

        vm.onEvent(CompanionEvent.PickAttachment(AttachmentType.AUDIO))
        vm.onEvent(CompanionEvent.AttachmentPicked("content://a"))
        advanceUntilIdle()
        // 菜单里的「图片 / 视频」和相册是同一个入口，两种都收
        vm.onEvent(CompanionEvent.PickAttachment(AttachmentType.IMAGE))
        vm.onEvent(CompanionEvent.AttachmentPicked("content://b"))
        advanceUntilIdle()

        assertEquals(listOf(AttachmentType.AUDIO, null), driver.ingestedAs)
    }

    @Test
    fun `只发附件不打字也能发送，附件随请求带出并进历史`() = runTest(dispatcher) {
        val driver = FakeDriver(visionOnly)
        val vm = ready(driver)
        vm.onEvent(CompanionEvent.AttachmentPicked("content://picker/1"))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.attachments.pending.size)
        assertEquals(CompanionOverlayState.INPUT, vm.uiState.value.overlay)

        vm.onEvent(CompanionEvent.Submit)
        advanceUntilIdle()

        val request = driver.requests.single()
        assertEquals("", request.input)
        assertEquals(listOf("ATRI/a.jpg"), request.attachments.map { it.relPath })
        assertTrue(vm.uiState.value.attachments.pending.isEmpty())
        val userItem = vm.uiState.value.history.first { it.author == CompanionAuthor.USER }
        assertEquals(listOf(image), userItem.attachments)
    }

    @Test
    fun `导入失败给出原因，不留待发送项`() = runTest(dispatcher) {
        val vm = ready(
            FakeDriver(visionOnly, ingest = { _, _ ->
                AttachmentIngestResult.Failed("文件太大（120 MB），最大支持 100 MB")
            })
        )
        vm.onEvent(CompanionEvent.AttachmentPicked("content://picker/1"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.attachments.pending.isEmpty())
        assertFalse(vm.uiState.value.attachments.importing)
        assertTrue(vm.uiState.value.notice!!.contains("太大"))
    }

    @Test
    fun `删除待发送的附件会让 driver 清文件`() = runTest(dispatcher) {
        val driver = FakeDriver(visionOnly)
        val vm = ready(driver)
        vm.onEvent(CompanionEvent.AttachmentPicked("content://picker/1"))
        advanceUntilIdle()
        vm.onEvent(CompanionEvent.RemovePendingAttachment("ATRI/a.jpg"))
        assertTrue(vm.uiState.value.attachments.pending.isEmpty())
        assertEquals(listOf("ATRI/a.jpg"), driver.discarded.map { it.relPath })
    }

    @Test
    fun `导入还没完成时不能发送`() = runTest(dispatcher) {
        val gate = CompletableDeferred<AttachmentIngestResult>()
        val driver = FakeDriver(visionOnly, ingest = { _, _ -> gate.await() })
        val vm = ready(driver)
        vm.onEvent(CompanionEvent.AttachmentPicked("content://picker/1"))
        vm.onEvent(CompanionEvent.DraftChanged("看这个"))
        advanceUntilIdle()
        vm.onEvent(CompanionEvent.Submit)
        advanceUntilIdle()
        assertTrue(driver.requests.isEmpty())
        assertNotNull(vm.uiState.value.notice)
        gate.complete(AttachmentIngestResult.Success(image))
        advanceUntilIdle()
        vm.onEvent(CompanionEvent.Submit)
        advanceUntilIdle()
        assertEquals(1, driver.requests.single().attachments.size)
    }

    @Test
    fun `失败后重试带上同一批附件`() = runTest(dispatcher) {
        var calls = 0
        val driver = FakeDriver(visionOnly, reply = {
            calls++
            if (calls == 1) flowOf(CompanionResponseEvent.Failed("boom"))
            else flowOf(CompanionResponseEvent.Reply("ok"), CompanionResponseEvent.Finished)
        })
        val vm = ready(driver)
        vm.onEvent(CompanionEvent.AttachmentPicked("content://picker/1"))
        advanceUntilIdle()
        vm.onEvent(CompanionEvent.Submit)
        advanceUntilIdle()
        vm.onEvent(CompanionEvent.RetryResponse)
        advanceUntilIdle()
        assertEquals(2, driver.requests.size)
        assertTrue(driver.requests[1].isRetry)
        assertEquals(driver.requests[0].attachments, driver.requests[1].attachments)
    }

    @Test
    fun `流水线先开口：VoiceStarted 进 SPEAKING，之后的流式文本不退回 THINKING`() = runTest(dispatcher) {
        val driver = FakeDriver(AttachmentSupport.NONE, reply = {
            flowOf(
                CompanionResponseEvent.Streaming("一句目。"),
                CompanionResponseEvent.VoiceStarted,
                CompanionResponseEvent.Streaming("一句目。二句目"),
            )
        })
        val vm = ready(driver)
        vm.onEvent(CompanionEvent.DraftChanged("hi"))
        vm.onEvent(CompanionEvent.Submit)
        advanceUntilIdle()
        assertEquals(CompanionRuntimeState.SPEAKING, vm.uiState.value.runtime)
        assertEquals("一句目。二句目", vm.uiState.value.utterance)
    }

    // ---- 历史分页 ----

    private fun item(id: Long) = CompanionHistoryItem(id, CompanionAuthor.USER, "m$id", "历史")

    private inner class PagingDriver(
        private val first: CompanionHistoryPage,
        private val older: suspend (Long) -> CompanionHistoryPage,
    ) : CompanionResponseDriver {
        val cursors = mutableListOf<Long>()
        override fun responses(request: CompanionRequest): Flow<CompanionResponseEvent> =
            flowOf(CompanionResponseEvent.Reply("ok"), CompanionResponseEvent.Finished)

        override suspend fun prepare(character: com.chatwaifu.mobile.data.model.CharacterModel) =
            CompanionPreparation(history = first)

        override suspend fun loadOlderHistory(beforeId: Long): CompanionHistoryPage {
            cursors += beforeId
            return older(beforeId)
        }
    }

    @Test
    fun `滑到顶往前翻页，游标用 Room 游标而不是过滤后的第一条`() = runTest(dispatcher) {
        // 首页最老的那条（id 9）被 UI 滤掉了，游标仍然要是 9
        val driver = PagingDriver(
            first = CompanionHistoryPage(listOf(item(10), item(11)), olderCursor = 9, hasOlder = true),
            older = { CompanionHistoryPage(listOf(item(7), item(8)), olderCursor = 7, hasOlder = false) },
        )
        val vm = CompanionViewModel(driver).apply {
            setCharacter(character.id, character.displayName)
            prepareConversation(character)
        }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasOlderHistory)

        vm.onEvent(CompanionEvent.LoadOlderHistory)
        advanceUntilIdle()

        assertEquals(listOf(9L), driver.cursors)
        assertEquals(listOf(7L, 8L, 10L, 11L), vm.uiState.value.history.map { it.id })
        assertFalse(vm.uiState.value.hasOlderHistory)
        assertFalse(vm.uiState.value.loadingOlderHistory)

        // 到底了就不再请求
        vm.onEvent(CompanionEvent.LoadOlderHistory)
        advanceUntilIdle()
        assertEquals(1, driver.cursors.size)
    }

    @Test
    fun `翻页进行中重复触发只发一次请求，结果按 id 去重`() = runTest(dispatcher) {
        val gate = CompletableDeferred<CompanionHistoryPage>()
        val driver = PagingDriver(
            first = CompanionHistoryPage(listOf(item(10)), olderCursor = 10, hasOlder = true),
            older = { gate.await() },
        )
        val vm = CompanionViewModel(driver).apply {
            setCharacter(character.id, character.displayName)
            prepareConversation(character)
        }
        advanceUntilIdle()
        vm.onEvent(CompanionEvent.LoadOlderHistory)
        vm.onEvent(CompanionEvent.LoadOlderHistory)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loadingOlderHistory)
        gate.complete(CompanionHistoryPage(listOf(item(9), item(10)), olderCursor = 9, hasOlder = true))
        advanceUntilIdle()

        assertEquals(1, driver.cursors.size)
        assertEquals(listOf(9L, 10L), vm.uiState.value.history.map { it.id })
        assertTrue(vm.uiState.value.hasOlderHistory)
    }

    @Test
    fun `翻页失败不进错误态，下次还能再翻`() = runTest(dispatcher) {
        var fail = true
        val driver = PagingDriver(
            first = CompanionHistoryPage(listOf(item(10)), olderCursor = 10, hasOlder = true),
            older = {
                if (fail) error("db closed")
                CompanionHistoryPage(listOf(item(9)), olderCursor = 9, hasOlder = false)
            },
        )
        val vm = CompanionViewModel(driver).apply {
            setCharacter(character.id, character.displayName)
            prepareConversation(character)
        }
        advanceUntilIdle()
        vm.onEvent(CompanionEvent.LoadOlderHistory)
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.loadingOlderHistory)

        fail = false
        vm.onEvent(CompanionEvent.LoadOlderHistory)
        advanceUntilIdle()
        assertEquals(listOf(9L, 10L), vm.uiState.value.history.map { it.id })
    }
}
