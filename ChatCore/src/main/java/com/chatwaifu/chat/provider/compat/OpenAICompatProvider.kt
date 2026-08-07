package com.chatwaifu.chat.provider.compat

import com.chatwaifu.chat.core.ChatContent
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatError
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRequest
import com.chatwaifu.chat.core.ChatRole
import com.chatwaifu.chat.core.FinishReason
import com.chatwaifu.chat.core.ImageDetail
import com.chatwaifu.chat.core.MediaSource
import com.chatwaifu.chat.core.MessageAccumulator
import com.chatwaifu.chat.core.ModelInfo
import com.chatwaifu.chat.core.ProviderCapabilities
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.chat.core.TokenUsage
import com.chatwaifu.chat.core.capabilitiesFor
import com.chatwaifu.chat.net.HttpSupport
import com.chatwaifu.chat.net.MediaEncoding
import com.chatwaifu.chat.net.SSE_DONE
import com.chatwaifu.chat.net.StreamingHttpClient
import com.chatwaifu.chat.provider.JsonBuilders.jsonArrayOf
import com.chatwaifu.chat.provider.JsonBuilders.mergeExtras
import com.chatwaifu.chat.provider.JsonBuilders.optArray
import com.chatwaifu.chat.provider.JsonBuilders.optInt
import com.chatwaifu.chat.provider.JsonBuilders.optObject
import com.chatwaifu.chat.provider.JsonBuilders.optString
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Description: OpenAI Chat Completions 协议的通用实现（`POST {base}/v1/chat/completions`）。
 *
 * 这一份实现同时覆盖：OpenAI 官方兼容层、各种第三方代理（改造前 `CHATGPT_DEAFULT_PROXY_URL`
 * 那条路）、DeepSeek、Kimi、智谱、Ollama、llama.cpp server、vLLM、LM Studio……
 * 因为它们全都对齐了这个协议。**性价比最高的一个 provider**：一份代码，几十家服务。
 *
 * 端内推理如果走「本机起一个兼容 server」的方案，也直接复用这里，baseUrl 填
 * `http://127.0.0.1:<port>/` 即可（见 `provider/local/LocalLlmProvider`）。
 *
 * 与 [com.chatwaifu.chat.provider.openai.OpenAIResponsesProvider] 的分工：
 * 这里是「通用兼容」，那边是「OpenAI 最新能力」。gpt-5.x 的推理档、服务端 compaction
 * 只有 Responses API 有。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class OpenAICompatProvider(
    private val config: ProviderConfig,
    override val displayName: String = "OpenAI 兼容",
    override val availableModels: List<ModelInfo> = DEFAULT_MODELS,
    override val defaultModel: String = FALLBACK_MODEL,
    override val capabilities: ProviderCapabilities = DEFAULT_CAPABILITIES,
) : ChatProvider {

    override val id: ProviderId = ProviderId.OPENAI_COMPAT

    private val http = StreamingHttpClient(config) {
        // 本机推理服务通常不校验 key，没填就不发这个头，免得某些实现直接 401
        config.apiKey?.takeIf { it.isNotBlank() }
            ?.let { mapOf("Authorization" to "Bearer $it") }
            ?: emptyMap()
    }

    private val endpoint: String
        get() = HttpSupport.resolveEndpoint(
            baseUrl = config.baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL,
            path = "v1/chat/completions",
        )

    override fun chatStream(request: ChatRequest): Flow<ChatDelta> = flow {
        val body = buildRequestJson(request, stream = true)
        val accumulator = MessageAccumulator()
        var usage: TokenUsage? = null
        var finishReason = FinishReason.STOP
        var started = false
        var completed = false

        http.postSse(endpoint, body) { event ->
            if (event.data == SSE_DONE) return@postSse

            val chunk = runCatching { JsonParser.parseString(event.data).asJsonObject }.getOrNull()
                ?: return@postSse

            // 有些代理会把错误塞进 200 的流里，而不是走 HTTP 状态码
            chunk.optObject("error")?.let { throw HttpSupport.toChatError(200, chunk.toString()) }

            chunk.optObject("usage")?.let { usage = it.toTokenUsage() }

            val choice = chunk.optArray("choices")?.firstOrNull()?.asJsonObject ?: return@postSse
            choice.optString("finish_reason")?.let {
                finishReason = it.toFinishReason()
                completed = true
            }

            val delta = choice.optObject("delta") ?: return@postSse

            if (!started) {
                started = true
                emit(ChatDelta.Started)
            }

            // DeepSeek-R1 系列把思维链放在 reasoning_content；OpenAI 兼容层用 reasoning
            (delta.optString("reasoning_content") ?: delta.optString("reasoning"))?.let {
                val d = ChatDelta.ThinkingDelta(it)
                accumulator.accept(d)
                emit(d)
            }

            delta.optString("content")?.let {
                val d = ChatDelta.TextDelta(it)
                accumulator.accept(d)
                emit(d)
            }

            delta.optArray("tool_calls")?.forEach { element ->
                val call = element.asJsonObject
                val d = ChatDelta.ToolCallDelta(
                    index = call.get("index")?.asInt ?: 0,
                    id = call.optString("id"),
                    name = call.optObject("function")?.optString("name"),
                    argsFragment = call.optObject("function")?.optString("arguments"),
                )
                accumulator.accept(d)
                emit(d)
            }
        }

        if (!completed && !started) {
            // 一个 chunk 都没收到：多半是服务端不支持 stream，或者返回了空流
            throw ChatError.Unknown("empty stream from $endpoint")
        }
        emit(ChatDelta.Completed(accumulator.build(), usage, finishReason))
    }.flowOn(Dispatchers.IO)

    override fun close() = http.close()

    // ---------------- 请求映射 ----------------

    private fun buildRequestJson(request: ChatRequest, stream: Boolean): String {
        val root = JsonObject()
        root.addProperty("model", request.model ?: defaultModel)
        root.add("messages", buildMessages(request))

        request.maxOutputTokens?.let { root.addProperty("max_tokens", it) }
        // 只有 capabilities 声明支持时才下发，否则 gpt-5 推理档 / 部分服务会直接 400
        request.temperature?.takeIf { capabilities.supportsTemperature }
            ?.let { root.addProperty("temperature", it) }
        if (request.stopSequences.isNotEmpty()) {
            root.add("stop", jsonArrayOf(request.stopSequences))
        }
        if (request.tools.isNotEmpty()) {
            root.add("tools", buildTools(request))
        }
        if (stream) {
            root.addProperty("stream", true)
            // 不加这个大多数实现不会在流里回 usage
            root.add("stream_options", JsonObject().apply { addProperty("include_usage", true) })
        }
        mergeExtras(root, request.extras)
        return root.toString()
    }

    private fun buildMessages(request: ChatRequest): JsonArray {
        val array = JsonArray()
        // 按本次请求实际用的模型算一次能力，别在每条消息里重复算
        val caps = capabilitiesFor(request.model)

        // system 是这个协议里的一条普通消息（和 Responses / Messages API 不同）
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            array.add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", it)
            })
        }

        request.messages.forEach { message ->
            when (message.role) {
                // tool 结果在这个协议里必须一条一条发，且要带 tool_call_id
                ChatRole.TOOL -> message.contents
                    .filterIsInstance<ChatContent.ToolResult>()
                    .forEach { result ->
                        array.add(JsonObject().apply {
                            addProperty("role", "tool")
                            addProperty("tool_call_id", result.callId)
                            addProperty("content", result.output)
                        })
                    }

                else -> array.add(buildMessage(message, caps))
            }
        }
        return array
    }

    private fun buildMessage(message: ChatMessage, caps: ProviderCapabilities): JsonObject = JsonObject().apply {
        addProperty(
            "role",
            when (message.role) {
                ChatRole.USER -> "user"
                ChatRole.ASSISTANT -> "assistant"
                ChatRole.SYSTEM -> "system"
                ChatRole.TOOL -> "tool"
            },
        )

        val attachments = message.contents.filter {
            it is ChatContent.Image || it is ChatContent.Audio || it is ChatContent.Doc
        }
        if (attachments.isEmpty()) {
            // 纯文本用字符串形式，兼容性最好 —— 一些本地实现不认数组形式的 content
            addProperty("content", message.text)
        } else {
            add("content", buildMultimodalContent(message, caps))
        }

        val toolCalls = message.toolCalls
        if (toolCalls.isNotEmpty()) {
            add("tool_calls", JsonArray().apply {
                toolCalls.forEachIndexed { index, call ->
                    add(JsonObject().apply {
                        addProperty("index", index)
                        addProperty("id", call.id)
                        addProperty("type", "function")
                        add("function", JsonObject().apply {
                            addProperty("name", call.name)
                            addProperty("arguments", call.argumentsJson)
                        })
                    })
                }
            })
        }
    }

    /**
     * @param caps **按模型算过**的能力（见 [capabilitiesFor]）。不能直接读 [capabilities] ——
     *   那是 provider 级的，对 OpenAI 兼容基座来说等于「协议支持」，
     *   和用户当前选的模型支持什么是两件事。
     */
    private fun buildMultimodalContent(
        message: ChatMessage,
        caps: ProviderCapabilities,
    ): JsonArray = JsonArray().apply {
        message.contents.forEach { content ->
            when (content) {
                is ChatContent.Text -> add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", content.text)
                })

                is ChatContent.Image -> {
                    if (!caps.imageInput) throw ChatError.CapabilityUnsupported("image input")
                    add(JsonObject().apply {
                        addProperty("type", "image_url")
                        add("image_url", JsonObject().apply {
                            addProperty("url", MediaEncoding.asUrlOrDataUri(content.source, "image"))
                            if (content.detail != ImageDetail.AUTO) {
                                addProperty("detail", content.detail.name.lowercase())
                            }
                        })
                    })
                }

                is ChatContent.Audio -> {
                    if (!caps.audioInput) throw ChatError.CapabilityUnsupported("audio input")
                    val inline = MediaEncoding.inline(content.source)
                        ?: throw ChatError.CapabilityUnsupported("audio input by url")
                    add(JsonObject().apply {
                        addProperty("type", "input_audio")
                        add("input_audio", JsonObject().apply {
                            addProperty("data", inline.base64)
                            addProperty("format", content.format)
                        })
                    })
                }

                is ChatContent.Doc -> {
                    if (!caps.fileInput) throw ChatError.CapabilityUnsupported("file input")
                    add(JsonObject().apply {
                        addProperty("type", "file")
                        add("file", JsonObject().apply {
                            when (val source = content.source) {
                                is MediaSource.RemoteFileId -> addProperty("file_id", source.id)

                                else -> {
                                    val inline = MediaEncoding.inline(source)
                                        ?: throw ChatError.CapabilityUnsupported("file input by url")
                                    content.fileName?.let { addProperty("filename", it) }
                                    addProperty("file_data", inline.dataUri)
                                }
                            }
                        })
                    })
                }

                // 思考块和工具调用不放在 content 数组里
                is ChatContent.Thinking, is ChatContent.ToolCall, is ChatContent.ToolResult -> Unit
            }
        }
    }

    private fun buildTools(request: ChatRequest): JsonArray = JsonArray().apply {
        request.tools.forEach { tool ->
            add(JsonObject().apply {
                addProperty("type", "function")
                add("function", JsonObject().apply {
                    addProperty("name", tool.name)
                    addProperty("description", tool.description)
                    add("parameters", JsonParser.parseString(tool.parametersJsonSchema))
                })
            })
        }
    }

    // ---------------- 响应映射 ----------------

    private fun JsonObject.toTokenUsage() = TokenUsage(
        promptTokens = optInt("prompt_tokens"),
        completionTokens = optInt("completion_tokens"),
        totalTokens = optInt("total_tokens"),
        cachedTokens = optObject("prompt_tokens_details")?.optInt("cached_tokens"),
    )

    private fun String.toFinishReason(): FinishReason = when (this) {
        "stop" -> FinishReason.STOP
        "length" -> FinishReason.MAX_TOKENS
        "tool_calls", "function_call" -> FinishReason.TOOL_CALL
        "content_filter" -> FinishReason.CONTENT_FILTER
        else -> FinishReason.OTHER
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/"

        /** 改造前 ChatGPTNetService 里内置的那个代理地址，迁过来保持可用。 */
        const val LEGACY_PROXY_BASE_URL = "https://api.openai-proxy.com/"

        private const val FALLBACK_MODEL = "gpt-4o-mini"

        /**
         * 这个 provider 的对端是什么服务完全由用户决定，所以能力只能按「协议保证的部分」声明。
         * 想开图片输入之类的，构造时自己传 [capabilities] 覆盖。
         */
        val DEFAULT_CAPABILITIES = ProviderCapabilities(
            streaming = true,
            imageInput = true,
            audioInput = false,
            audioOutput = false,
            fileInput = false,
            toolCalling = true,
            reasoningLevels = emptySet(),
            supportsTemperature = true,
            serverSideHistory = false,
            maxContextTokens = 128_000,
        )

        /**
         * 只是设置页下拉框的默认候选，不是白名单 —— 用户可以直接手填任意模型名。
         * 本地服务的模型名（`qwen3:8b` 之类）不可能穷举。
         */
        val DEFAULT_MODELS = listOf(
            ModelInfo("gpt-4o-mini", "gpt-4o-mini", 128_000, supportsImageInput = true, note = "便宜"),
            ModelInfo("gpt-4o", "gpt-4o", 128_000, supportsImageInput = true),
            ModelInfo("deepseek-chat", "DeepSeek Chat", 64_000),
        )
    }
}
