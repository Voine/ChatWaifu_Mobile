package com.chatwaifu.chat.provider

import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatRequest
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.chat.provider.compat.OpenAICompatProvider
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 默认模型 = 清单第一个，而不是一个单独写死、会和清单脱节的字符串。 */
class DefaultModelTest {

    @Test
    fun `每个有清单的 provider 默认模型都是清单第一个`() {
        val config = ProviderConfig(apiKey = "k", baseUrl = "http://localhost/")
        ProviderId.entries
            .filter { ChatProviderFactory.descriptor(it).models.isNotEmpty() }
            .forEach { id ->
                val provider = ChatProviderFactory.create(id, config)
                assertEquals(id.name, provider.availableModels.first().id, provider.defaultModel)
                provider.close()
            }
    }

    @Test
    fun `清单为空且没填模型时不下发 model 字段`() {
        // 局域网 llama.cpp / vLLM：交给服务端用它自己加载的模型
        val provider = OpenAICompatProvider(
            ProviderConfig(baseUrl = "http://10.0.2.2:8080/"),
            availableModels = emptyList(),
        )
        val body = JsonParser.parseString(
            provider.buildRequestJson(ChatRequest(listOf(ChatMessage.user("hi"))))
        ).asJsonObject
        assertEquals("", provider.defaultModel)
        assertFalse(body.has("model"))
        provider.close()
    }
}
