package com.chatwaifu.mobile.ui.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun blankDraftDoesNotSubmit() = runTest(dispatcher) {
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver {
                flowOf(CompanionResponseEvent.Finished)
            }
        )

        viewModel.onEvent(CompanionEvent.OpenInput)
        viewModel.onEvent(CompanionEvent.DraftChanged("   "))
        viewModel.onEvent(CompanionEvent.Submit)

        assertEquals(CompanionRuntimeState.IDLE, viewModel.uiState.value.runtime)
        assertTrue(viewModel.uiState.value.history.isEmpty())
    }

    @Test
    fun successfulReplyTraversesToIdleAndAddsOneTurn() = runTest(dispatcher) {
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver {
                flowOf(
                    CompanionResponseEvent.Reply("mock reply"),
                    CompanionResponseEvent.Finished,
                )
            }
        )

        viewModel.onEvent(CompanionEvent.DraftChanged("hello"))
        viewModel.onEvent(CompanionEvent.Submit)
        advanceUntilIdle()

        assertEquals(CompanionRuntimeState.IDLE, viewModel.uiState.value.runtime)
        assertEquals("mock reply", viewModel.uiState.value.utterance)
        assertEquals(2, viewModel.uiState.value.history.size)
    }

    @Test
    fun streamingReplyUpdatesUtteranceAndSingleHistoryItemBeforeSpeaking() = runTest(dispatcher) {
        val responses = MutableSharedFlow<CompanionResponseEvent>()
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver { responses }
        )

        viewModel.onEvent(CompanionEvent.DraftChanged("hello"))
        viewModel.onEvent(CompanionEvent.Submit)
        runCurrent()

        responses.emit(CompanionResponseEvent.Streaming("first"))
        runCurrent()
        assertEquals(CompanionRuntimeState.THINKING, viewModel.uiState.value.runtime)
        assertEquals("first", viewModel.uiState.value.utterance)
        assertEquals(2, viewModel.uiState.value.history.size)

        responses.emit(CompanionResponseEvent.Streaming("first second"))
        runCurrent()
        assertEquals("first second", viewModel.uiState.value.utterance)
        assertEquals(2, viewModel.uiState.value.history.size)
        assertEquals("first second", viewModel.uiState.value.history.last().text)

        responses.emit(CompanionResponseEvent.Reply("first second"))
        runCurrent()
        assertEquals(CompanionRuntimeState.SPEAKING, viewModel.uiState.value.runtime)
        assertEquals(2, viewModel.uiState.value.history.size)

        responses.emit(CompanionResponseEvent.Finished)
        runCurrent()
        assertEquals(CompanionRuntimeState.IDLE, viewModel.uiState.value.runtime)
    }

    @Test
    fun streamingFailureKeepsPartialAssistantHistory() = runTest(dispatcher) {
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver {
                flowOf(
                    CompanionResponseEvent.Streaming("partial"),
                    CompanionResponseEvent.Failed("disconnected"),
                )
            }
        )

        viewModel.onEvent(CompanionEvent.DraftChanged("hello"))
        viewModel.onEvent(CompanionEvent.Submit)
        advanceUntilIdle()

        assertEquals(CompanionRuntimeState.ERROR, viewModel.uiState.value.runtime)
        assertEquals("partial", viewModel.uiState.value.utterance)
        assertEquals("partial", viewModel.uiState.value.history.last().text)
        assertEquals(2, viewModel.uiState.value.history.size)
    }

    @Test
    fun recoverableFailureEntersRetryableError() = runTest(dispatcher) {
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver {
                flowOf(CompanionResponseEvent.Failed("mock failure"))
            }
        )

        viewModel.onEvent(CompanionEvent.DraftChanged("/error"))
        viewModel.onEvent(CompanionEvent.Submit)
        advanceUntilIdle()

        assertEquals(CompanionRuntimeState.ERROR, viewModel.uiState.value.runtime)
        assertEquals(CompanionOverlayState.NONE, viewModel.uiState.value.overlay)
        assertEquals("mock failure", viewModel.uiState.value.error?.message)
        assertEquals(true, viewModel.uiState.value.error?.canRetry)
        assertEquals(1, viewModel.uiState.value.history.size)
    }

    @Test
    fun duplicateSubmitWhileRequestIsActiveIsIgnored() = runTest(dispatcher) {
        val requests = mutableListOf<CompanionRequest>()
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver { request ->
                flow {
                    requests += request
                    awaitCancellation()
                }
            }
        )

        viewModel.onEvent(CompanionEvent.DraftChanged("hello"))
        viewModel.onEvent(CompanionEvent.Submit)
        runCurrent()
        viewModel.onEvent(CompanionEvent.DraftChanged("hello"))
        viewModel.onEvent(CompanionEvent.Submit)
        runCurrent()

        assertEquals(1, requests.size)
        assertEquals(1, viewModel.uiState.value.history.size)
        assertEquals("正在准备回复，请稍候", viewModel.uiState.value.notice)
    }

    @Test
    fun retryDoesNotAppendDuplicateUserHistory() = runTest(dispatcher) {
        val requests = mutableListOf<CompanionRequest>()
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver { request ->
                requests += request
                if (request.isRetry) {
                    flowOf(
                        CompanionResponseEvent.Reply("recovered"),
                        CompanionResponseEvent.Finished,
                    )
                } else {
                    flowOf(CompanionResponseEvent.Failed("temporary"))
                }
            }
        )

        viewModel.onEvent(CompanionEvent.DraftChanged("hello"))
        viewModel.onEvent(CompanionEvent.Submit)
        advanceUntilIdle()
        viewModel.onEvent(CompanionEvent.RetryResponse)
        advanceUntilIdle()

        assertEquals(listOf(false, true), requests.map { it.isRetry })
        assertEquals(2, viewModel.uiState.value.history.size)
        assertEquals(CompanionAuthor.USER, viewModel.uiState.value.history[0].author)
        assertEquals(CompanionAuthor.ASSISTANT, viewModel.uiState.value.history[1].author)
        assertEquals(CompanionRuntimeState.IDLE, viewModel.uiState.value.runtime)
    }

    @Test
    fun ttsFailureKeepsReplyAndReturnsToIdle() = runTest(dispatcher) {
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver {
                flowOf(
                    CompanionResponseEvent.Reply("text survives"),
                    CompanionResponseEvent.TtsFailed("voice failed"),
                )
            }
        )

        viewModel.onEvent(CompanionEvent.DraftChanged("hello"))
        viewModel.onEvent(CompanionEvent.Submit)
        advanceUntilIdle()

        assertEquals(CompanionRuntimeState.IDLE, viewModel.uiState.value.runtime)
        assertEquals("text survives", viewModel.uiState.value.utterance)
        assertEquals("voice failed", viewModel.uiState.value.notice)
        assertEquals(2, viewModel.uiState.value.history.size)
    }

    @Test
    fun historyDismissUsesLatestRuntimeAndPreservesDraft() = runTest(dispatcher) {
        val responses = MutableSharedFlow<CompanionResponseEvent>()
        val viewModel = CompanionViewModel(
            responseDriver = CompanionResponseDriver { responses }
        )

        viewModel.onEvent(CompanionEvent.DraftChanged("first"))
        viewModel.onEvent(CompanionEvent.Submit)
        runCurrent()
        responses.emit(CompanionResponseEvent.Reply("reply"))
        runCurrent()
        viewModel.onEvent(CompanionEvent.OpenInput)
        viewModel.onEvent(CompanionEvent.DraftChanged("next"))
        viewModel.onEvent(CompanionEvent.OpenHistory)
        responses.emit(CompanionResponseEvent.Finished)
        runCurrent()
        viewModel.onEvent(CompanionEvent.DismissHistory)

        assertEquals(CompanionRuntimeState.IDLE, viewModel.uiState.value.runtime)
        assertEquals(CompanionOverlayState.INPUT, viewModel.uiState.value.overlay)
        assertEquals("next", viewModel.uiState.value.draft)
        assertEquals(false, viewModel.uiState.value.requestInputFocus)
    }

    @Test
    fun changingCharacterCancelsOldResponse() = runTest(dispatcher) {
        val responses = MutableSharedFlow<CompanionResponseEvent>()
        var playbackCancelled = false
        val viewModel = CompanionViewModel(
            responseDriver = object : CompanionResponseDriver {
                override fun responses(request: CompanionRequest) = responses

                override fun cancel() {
                    playbackCancelled = true
                }
            }
        )

        viewModel.onEvent(CompanionEvent.DraftChanged("hello"))
        viewModel.onEvent(CompanionEvent.Submit)
        runCurrent()
        viewModel.setCharacter("imported:test", "New character")
        responses.emit(CompanionResponseEvent.Reply("stale"))
        runCurrent()

        assertEquals("New character", viewModel.uiState.value.characterName)
        assertEquals("", viewModel.uiState.value.utterance)
        assertEquals(CompanionRuntimeState.IDLE, viewModel.uiState.value.runtime)
        assertTrue(playbackCancelled)
    }
}
