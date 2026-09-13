package com.chatwaifu.mobile.data.chat

import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRequest
import com.chatwaifu.chat.core.ChatSession
import com.chatwaifu.chat.core.ProviderCapabilities
import com.chatwaifu.chat.core.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceSelectionSessionTest {

    @Test
    fun existingSessionKeepsCapturedModelAndNewSessionUsesLatestSelection() = runTest {
        val provider = RecordingProvider()
        var selected = CloudProviderProfile(
            providerId = ProviderId.OPENAI_COMPAT,
            modelId = "model-a",
        ).toSelection()
        val existing = ChatSession(provider, options = selected.sessionOptions)

        selected = CloudProviderProfile(
            providerId = ProviderId.OPENAI_COMPAT,
            modelId = "model-b",
        ).toSelection()
        existing.send("first").toList()
        ChatSession(provider, options = selected.sessionOptions).send("second").toList()

        assertEquals(listOf("model-a", "model-b"), provider.requests.map { it.model })
    }

    @Test
    fun disabledStreamingStillUsesProviderFlowAndEmitsOnlyCompletion() = runTest {
        val provider = RecordingProvider()
        val options = CloudProviderProfile(
            providerId = ProviderId.OPENAI_COMPAT,
            streaming = false,
        ).toSelection().sessionOptions

        val events = ChatSession(provider, options = options).send("hello").toList()

        assertEquals(1, provider.requests.size)
        assertFalse(events.any { it is ChatDelta.TextDelta })
        assertTrue(events.single() is ChatDelta.Completed)
    }

    private class RecordingProvider : ChatProvider {
        val requests = mutableListOf<ChatRequest>()

        override val id = ProviderId.OPENAI_COMPAT
        override val displayName = "test"
        override val capabilities = ProviderCapabilities()
        override val availableModels = emptyList<com.chatwaifu.chat.core.ModelInfo>()
        override val defaultModel = "default"

        override fun chatStream(request: ChatRequest): Flow<ChatDelta> {
            requests += request
            return flowOf(
                ChatDelta.Started,
                ChatDelta.TextDelta("ok"),
                ChatDelta.Completed(ChatMessage.assistant("ok")),
            )
        }
    }
}
