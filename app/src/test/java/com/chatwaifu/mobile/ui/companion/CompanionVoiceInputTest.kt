package com.chatwaifu.mobile.ui.companion

import com.chatwaifu.mobile.data.asr.AsrError
import com.chatwaifu.mobile.data.asr.AsrEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

/**
 * Phase 2.8：Companion 语音输入状态机。
 *
 * 全部用 [FakeVoiceInput] —— 状态机不认识 `AsrEngine` 也不认识 Sherpa，
 * 所以这里不需要 Android context、也不需要真的录音。
 * **这本身就是「换 MNN 不用改 Companion」那条约束的可执行证据。**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionVoiceInputTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun partialResultUpdatesDraftWithoutCreatingHistory() = runTest(dispatcher) {
        val events = MutableSharedFlow<AsrEvent>(replay = 8)
        val voice = FakeVoiceInput(events)
        val viewModel = viewModel(voice)

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()
        events.emit(AsrEvent.ListeningStarted)
        events.emit(AsrEvent.PartialResult("你好"))
        events.emit(AsrEvent.PartialResult("你好世界"))
        runCurrent()

        val state = viewModel.uiState.value
        // partial 是「当前完整文本」，直接覆盖而不是拼接
        assertEquals("你好世界", state.draft)
        assertEquals(CompanionRuntimeState.LISTENING, state.runtime)
        assertTrue(state.voice.listening)
        // 中间结果不产生任何 history item
        assertTrue(state.history.isEmpty())
    }

    @Test
    fun finalResultLeavesEditableDraftAndDoesNotAutoSend() = runTest(dispatcher) {
        val events = MutableSharedFlow<AsrEvent>(replay = 8)
        val viewModel = viewModel(FakeVoiceInput(events))

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()
        events.emit(AsrEvent.ListeningStarted)
        events.emit(AsrEvent.FinalResult("今天天气不错"))
        events.emit(AsrEvent.Ended)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("今天天气不错", state.draft)
        // 识别可能有错，用户要有一次修改机会：不自动发送
        assertEquals(CompanionRuntimeState.IDLE, state.runtime)
        assertTrue(state.history.isEmpty())
        assertEquals(CompanionOverlayState.INPUT, state.overlay)
        assertFalse(state.voice.busy)
    }

    @Test
    fun endpointDoesNotStopOrSendOnItsOwn() = runTest(dispatcher) {
        val events = MutableSharedFlow<AsrEvent>(replay = 8)
        val voice = FakeVoiceInput(events)
        val viewModel = viewModel(voice)

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()
        events.emit(AsrEvent.ListeningStarted)
        events.emit(AsrEvent.PartialResult("嗯"))
        events.emit(AsrEvent.EndpointReached)
        runCurrent()

        // endpoint 只是信号，停不停由用户决定
        assertTrue(viewModel.uiState.value.voice.listening)
        assertEquals(0, voice.stopCount)
        assertTrue(viewModel.uiState.value.history.isEmpty())
    }

    @Test
    fun cancelDiscardsTranscriptAndLateResultsDoNotUpdateUi() = runTest(dispatcher) {
        val events = MutableSharedFlow<AsrEvent>(replay = 8)
        val viewModel = viewModel(FakeVoiceInput(events))

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()
        events.emit(AsrEvent.ListeningStarted)
        events.emit(AsrEvent.PartialResult("要丢掉的内容"))
        runCurrent()
        viewModel.onEvent(CompanionEvent.CancelVoiceInput)
        runCurrent()

        // 取消后迟到的结果不能再写进 UI
        events.emit(AsrEvent.PartialResult("迟到的 partial"))
        events.emit(AsrEvent.FinalResult("迟到的 final"))
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("", state.draft)
        assertFalse(state.voice.busy)
        assertEquals(CompanionRuntimeState.IDLE, state.runtime)
    }

    @Test
    fun repeatedTapsDoNotOpenASecondSession() = runTest(dispatcher) {
        val voice = FakeVoiceInput(MutableSharedFlow(replay = 8))
        val viewModel = viewModel(voice)

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()

        assertEquals(1, voice.listenCount)
    }

    @Test
    fun secondTapWhileListeningStopsNormally() = runTest(dispatcher) {
        val events = MutableSharedFlow<AsrEvent>(replay = 8)
        val voice = FakeVoiceInput(events)
        val viewModel = viewModel(voice)

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()
        events.emit(AsrEvent.ListeningStarted)
        runCurrent()
        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()

        // 点击开始、再次点击结束 —— 只有这一种交互
        assertEquals(1, voice.stopCount)
        assertEquals(0, voice.cancelCount)
    }

    @Test
    fun characterSwitchCancelsListening() = runTest(dispatcher) {
        val events = MutableSharedFlow<AsrEvent>(replay = 8)
        val voice = FakeVoiceInput(events)
        val viewModel = viewModel(voice)

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()
        events.emit(AsrEvent.ListeningStarted)
        runCurrent()
        viewModel.setCharacter("builtin:yuuka", "Yuuka")
        runCurrent()

        assertTrue(voice.cancelBlockingCount > 0)
        assertFalse(viewModel.uiState.value.voice.busy)
    }

    @Test
    fun releasingConversationCancelsListening() = runTest(dispatcher) {
        val events = MutableSharedFlow<AsrEvent>(replay = 8)
        val voice = FakeVoiceInput(events)
        val viewModel = viewModel(voice)

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()
        events.emit(AsrEvent.ListeningStarted)
        runCurrent()
        viewModel.releaseConversation()
        runCurrent()

        // 页面退出必须释放录音
        assertTrue(voice.cancelBlockingCount > 0)
        assertFalse(viewModel.uiState.value.voice.busy)
    }

    @Test
    fun startingVoiceWhileSpeakingCancelsTtsFirst() = runTest(dispatcher) {
        val events = MutableSharedFlow<AsrEvent>(replay = 8)
        val voice = FakeVoiceInput(events)
        var ttsCancelled = 0
        val driver = object : CompanionResponseDriver {
            override fun responses(request: CompanionRequest): Flow<CompanionResponseEvent> = flow {
                emit(CompanionResponseEvent.Reply("在说话"))
                awaitCancellation()
            }

            override fun cancel() {
                ttsCancelled++
            }
        }
        val viewModel = CompanionViewModel(driver, voiceInput = voice)

        viewModel.onEvent(CompanionEvent.DraftChanged("说点什么"))
        viewModel.onEvent(CompanionEvent.Submit)
        runCurrent()
        assertEquals(CompanionRuntimeState.SPEAKING, viewModel.uiState.value.runtime)

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()

        // 先停 TTS 再录音：不让 AudioTrack 和 AudioRecord 无控制并发
        assertTrue(ttsCancelled > 0)
        assertEquals(1, voice.listenCount)
        assertFalse(viewModel.uiState.value.runtime == CompanionRuntimeState.SPEAKING)
    }

    @Test
    fun missingPermissionRequestsInsteadOfListening() = runTest(dispatcher) {
        val voice = FakeVoiceInput(MutableSharedFlow(replay = 8), granted = false)
        val viewModel = viewModel(voice)

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()

        assertEquals(1, voice.permissionRequests)
        assertEquals(0, voice.listenCount)
        assertFalse(viewModel.uiState.value.voice.busy)
    }

    @Test
    fun permanentlyDeniedPermissionSurfacesSettingsHint() = runTest(dispatcher) {
        val voice = FakeVoiceInput(MutableSharedFlow(replay = 8), granted = false)
        val viewModel = viewModel(voice)

        viewModel.onEvent(
            CompanionEvent.VoicePermissionResult(granted = false, canAskAgain = false)
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.voice.permissionBlocked)
        assertNotNull(state.notice)
        assertEquals(0, voice.listenCount)
    }

    @Test
    fun grantedPermissionResultStartsListening() = runTest(dispatcher) {
        val voice = FakeVoiceInput(MutableSharedFlow(replay = 8))
        val viewModel = viewModel(voice)

        viewModel.onEvent(
            CompanionEvent.VoicePermissionResult(granted = true, canAskAgain = true)
        )
        runCurrent()

        assertEquals(1, voice.listenCount)
    }

    @Test
    fun asrErrorDoesNotPolluteHistoryOrEnterErrorState() = runTest(dispatcher) {
        val events = MutableSharedFlow<AsrEvent>(replay = 8)
        val viewModel = viewModel(FakeVoiceInput(events))

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()
        events.emit(AsrEvent.Error(AsrError.AudioRecordFailed))
        events.emit(AsrEvent.Ended)
        runCurrent()

        val state = viewModel.uiState.value
        // ASR 失败只是提示；ERROR 态和 canRetry 是留给聊天请求失败的
        assertTrue(state.history.isEmpty())
        assertNull(state.error)
        assertEquals(CompanionRuntimeState.IDLE, state.runtime)
        assertNotNull(state.notice)
    }

    @Test
    fun submittingWhileListeningStopsSessionBeforeSending() = runTest(dispatcher) {
        val events = MutableSharedFlow<AsrEvent>(replay = 8)
        val voice = FakeVoiceInput(events)
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver { flowOf(CompanionResponseEvent.Finished) },
            voiceInput = voice,
        )

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()
        events.emit(AsrEvent.ListeningStarted)
        events.emit(AsrEvent.PartialResult("直接发送"))
        runCurrent()
        viewModel.onEvent(CompanionEvent.Submit)
        runCurrent()

        // 录音必须先结束，否则迟到的 final 会盖掉已经提交的文本
        assertTrue(voice.cancelBlockingCount > 0)
        assertFalse(viewModel.uiState.value.voice.busy)
        assertEquals(1, viewModel.uiState.value.history.count { it.author == CompanionAuthor.USER })
    }

    @Test
    fun viewModelWithoutVoiceInputStaysUsable() = runTest(dispatcher) {
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver { flowOf(CompanionResponseEvent.Finished) },
        )

        viewModel.onEvent(CompanionEvent.ToggleVoiceInput)
        runCurrent()

        // 没接语音的宿主给提示而不是崩
        assertNotNull(viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.voice.busy)
    }

    private fun viewModel(voice: CompanionVoiceInput) = CompanionViewModel(
        responseDriver = CompanionResponseDriver { flowOf(CompanionResponseEvent.Finished) },
        voiceInput = voice,
    )

    /**
     * 状态机测试用的假引擎。**它实现的是 [CompanionVoiceInput]，不是 Sherpa** ——
     * 未来的 `MnnAsrEngine` 对 Companion 而言和这个 fake 是同一种东西。
     */
    private class FakeVoiceInput(
        private val events: MutableSharedFlow<AsrEvent>,
        private val granted: Boolean = true,
    ) : CompanionVoiceInput {
        var listenCount = 0
        var stopCount = 0
        var cancelCount = 0
        var cancelBlockingCount = 0
        var permissionRequests = 0

        override fun hasPermission() = granted

        override fun requestPermission() {
            permissionRequests++
        }

        override fun listen(): Flow<AsrEvent> {
            listenCount++
            return events
        }

        override suspend fun stop() {
            stopCount++
        }

        override suspend fun cancel() {
            cancelCount++
        }

        override fun cancelBlocking() {
            cancelBlockingCount++
        }

        override fun describe(error: AsrError) = "asr-error:$error"
    }
}
