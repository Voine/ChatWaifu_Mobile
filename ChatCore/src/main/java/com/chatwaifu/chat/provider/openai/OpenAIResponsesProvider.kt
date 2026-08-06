package com.chatwaifu.chat.provider.openai

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
import com.chatwaifu.chat.core.ReasoningLevel
import com.chatwaifu.chat.core.TokenUsage
import com.chatwaifu.chat.net.HttpSupport
import com.chatwaifu.chat.net.MediaEncoding
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
 * Description: OpenAI Responses API（`POST {base}/v1/responses`）。
 *
 * 这是 OpenAI 当前的主接口，Chat Completions 已经退成兼容层。和它的差异：
 * - system 走顶层 `instructions`，不是 messages 里的一条
 * - 输入是 `input[]` 的 **item 列表**（message / function_call / function_call_output /
 *   reasoning / compaction），内容块用 `input_text` / `input_image` / `input_file`
 * - 推理档是 `reasoning: { effort }`
 * - 支持服务端会话（`previous_response_id`）和服务端上下文压缩（`context_management`）
 *
 * **`store` 一律传 false**：历史由 [com.chatwaifu.chat.core.ChatSession] 在客户端维护，
 * 不需要 OpenAI 替我们存 30 天。
 *
 * 一个容易踩的坑：**流中途出错时 HTTP 状态码仍然是 200**，错误从 `event: error` 帧里来。
 * 所以下面必须显式处理 `error` 事件，只看状态码会把失败当成「回复为空」。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class OpenAIResponsesProvider(
    private val config: ProviderConfig,
) : ChatProvider {

    override val id: ProviderId = ProviderId.OPENAI_RESPONSES
    override val displayName: String = "OpenAI"
    override val capabilities: ProviderCapabilities = CAPABILITIES
    override val availableModels: List<ModelInfo> = MODELS
    override val defaultModel: String = DEFAULT_MODEL

    private val http = StreamingHttpClient(config) {
        val key = config.apiKey?.takeIf { it.isNotBlank() }
            ?: throw ChatError.Auth("OpenAI api key is missing")
        mapOf("Authorization" to "Bearer $key")
    }

    private val endpoint: String
        get() = HttpSupport.resolveEndpoint(
            baseUrl = config.baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL,
            path = "v1/responses",
        )

    override fun chatStream(request: ChatRequest): Flow<ChatDelta> = flow {
        val accumulator = MessageAccumulator()
        var usage: TokenUsage? = null
        var finishReason = FinishReason.STOP
        var started = false
        var completed = false

        http.postSse(endpoint, buildRequestJson(request)) { sse ->
            val payload = runCatching { JsonParser.parseString(sse.data).asJsonObject }.getOrNull()
                ?: return@postSse
            // data 里的 type 和 SSE 的 event 字段是一致的，取 data 更稳
            val type = payload.optString("type") ?: sse.event ?: return@postSse

            if (!started && type.startsWith("response.")) {
                started = true
                emit(ChatDelta.Started)
            }

            when {
                // 流中途的错误也是 HTTP 200，只能从这里捞
                type == "error" -> throw HttpSupport.toChatError(200, payload.toString())

                type == "response.output_text.delta" ->
                    payload.optString("delta")?.let {
                        val delta = ChatDelta.TextDelta(it)
                        accumulator.accept(delta)
                        emit(delta)
                    }

                // 推理模型只回摘要，原始 CoT 不下发
                type == "response.reasoning_summary_text.delta" ->
                    payload.optString("delta")?.let {
                        val delta = ChatDelta.ThinkingDelta(it)
                        accumulator.accept(delta)
                        emit(delta)
                    }

                type == "response.refusal.delta" -> {
                    finishReason = FinishReason.REFUSAL
                    payload.optString("delta")?.let {
                        val delta = ChatDelta.TextDelta(it)
                        accumulator.accept(delta)
                        emit(delta)
                    }
                }

                // 工具调用的 id / name 在 item 加入时给，参数是后续流式拼出来的
                type == "response.output_item.added" -> {
                    val item = payload.optObject("item") ?: return@postSse
                    if (item.optString("type") == "function_call") {
                        val delta = ChatDelta.ToolCallDelta(
                            index = payload.optInt("output_index") ?: 0,
                            id = item.optString("call_id") ?: item.optString("id"),
                            name = item.optString("name"),
                        )
                        accumulator.accept(delta)
                        emit(delta)
                    }
                }

                type == "response.function_call_arguments.delta" -> {
                    val delta = ChatDelta.ToolCallDelta(
                        index = payload.optInt("output_index") ?: 0,
                        argsFragment = payload.optString("delta"),
                    )
                    accumulator.accept(delta)
                    emit(delta)
                }

                type == "response.completed" || type == "response.incomplete" -> {
                    completed = true
                    val response = payload.optObject("response")
                    usage = response?.optObject("usage")?.toTokenUsage()
                    finishReason = resolveFinishReason(response, finishReason)
                }

                type == "response.failed" ->
                    throw HttpSupport.toChatError(200, payload.optObject("response")?.toString())

                else -> Unit
            }
        }

        if (!completed && !started) {
            throw ChatError.Unknown("empty stream from $endpoint")
        }
        emit(ChatDelta.Completed(accumulator.build(), usage, finishReason))
    }.flowOn(Dispatchers.IO)

    override fun close() = http.close()

    // ---------------- 请求映射 ----------------

    private fun buildRequestJson(request: ChatRequest): String {
        val root = JsonObject()
        root.addProperty("model", request.model ?: defaultModel)
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            root.addProperty("instructions", it)
        }
        root.add("input", buildInputItems(request.messages))
        root.addProperty("stream", true)
        // 历史我们自己管，不让 OpenAI 存
        root.addProperty("store", false)

        request.maxOutputTokens?.let { root.addProperty("max_output_tokens", it) }
        // gpt-5 推理档收到 temperature 会 400，所以 capabilities 里声明了不支持，这里恒不下发
        request.temperature?.takeIf { capabilities.supportsTemperature }
            ?.let { root.addProperty("temperature", it) }
        request.reasoning?.let { level ->
            root.add("reasoning", JsonObject().apply { addProperty("effort", level.toEffort()) })
        }
        if (request.stopSequences.isNotEmpty()) {
            root.add("stop", jsonArrayOf(request.stopSequences))
        }
        if (request.tools.isNotEmpty()) {
            root.add("tools", JsonArray().apply {
                request.tools.forEach { tool ->
                    add(JsonObject().apply {
                        // 注意：Responses API 的 function 声明是**平铺**的，
                        // 不像 Chat Completions 那样再套一层 "function": {...}
                        addProperty("type", "function")
                        addProperty("name", tool.name)
                        addProperty("description", tool.description)
                        add("parameters", JsonParser.parseString(tool.parametersJsonSchema))
                    })
                }
            })
        }
        mergeExtras(root, request.extras)
        return root.toString()
    }

    private fun buildInputItems(messages: List<ChatMessage>): JsonArray = JsonArray().apply {
        messages.forEach { message ->
            // 工具调用和工具结果在 Responses API 里是独立的 item，不是消息的一部分
            message.contents.filterIsInstance<ChatContent.ToolCall>().forEach { call ->
                add(JsonObject().apply {
                    addProperty("type", "function_call")
                    addProperty("call_id", call.id)
                    addProperty("name", call.name)
                    addProperty("arguments", call.argumentsJson)
                })
            }
            message.contents.filterIsInstance<ChatContent.ToolResult>().forEach { result ->
                add(JsonObject().apply {
                    addProperty("type", "function_call_output")
                    addProperty("call_id", result.callId)
                    addProperty("output", result.output)
                })
            }

            val content = buildContentParts(message)
            if (content.size() > 0) {
                add(JsonObject().apply {
                    addProperty("type", "message")
                    addProperty("role", message.role.toWireRole())
                    add("content", content)
                })
            }
        }
    }

    private fun buildContentParts(message: ChatMessage): JsonArray = JsonArray().apply {
        // 输出侧和输入侧的文本块类型名不同：assistant 历史必须回传 output_text
        val textType = if (message.role == ChatRole.ASSISTANT) "output_text" else "input_text"

        message.contents.forEach { content ->
            when (content) {
                is ChatContent.Text -> add(JsonObject().apply {
                    addProperty("type", textType)
                    addProperty("text", content.text)
                })

                is ChatContent.Image -> add(JsonObject().apply {
                    addProperty("type", "input_image")
                    when (val source = content.source) {
                        is MediaSource.RemoteFileId -> addProperty("file_id", source.id)
                        else -> addProperty(
                            "image_url",
                            MediaEncoding.asUrlOrDataUri(source, "image"),
                        )
                    }
                    if (content.detail != ImageDetail.AUTO) {
                        addProperty("detail", content.detail.name.lowercase())
                    }
                })

                is ChatContent.Doc -> add(JsonObject().apply {
                    addProperty("type", "input_file")
                    when (val source = content.source) {
                        is MediaSource.RemoteFileId -> addProperty("file_id", source.id)
                        else -> {
                            val inline = MediaEncoding.inline(source)
                                ?: throw ChatError.CapabilityUnsupported("file input by url")
                            addProperty("filename", content.fileName ?: "attachment")
                            addProperty("file_data", inline.dataUri)
                        }
                    }
                })

                // Responses API 目前没有音频内容块（音频走 Realtime API），显式报错而不是静默丢
                is ChatContent.Audio -> throw ChatError.CapabilityUnsupported("audio input")

                // reasoning item 需要原样回传才能在多轮里保持思考连续性。
                // 但 store=false 时拿不到 encrypted_content（要额外声明 include），
                // 当前没有多轮工具调用需求，先跳过。
                is ChatContent.Thinking -> Unit

                is ChatContent.ToolCall, is ChatContent.ToolResult -> Unit
            }
        }
    }

    private fun ChatRole.toWireRole(): String = when (this) {
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
        ChatRole.SYSTEM -> "system"
        // tool 结果已经在上面转成 function_call_output item 了，走不到这里
        ChatRole.TOOL -> "user"
    }

    /** OpenAI 的档位是 minimal / low / medium / high。 */
    private fun ReasoningLevel.toEffort(): String = when (this) {
        ReasoningLevel.OFF -> "minimal"
        ReasoningLevel.LOW -> "low"
        ReasoningLevel.MEDIUM -> "medium"
        ReasoningLevel.HIGH, ReasoningLevel.MAX -> "high"
    }

    // ---------------- 响应映射 ----------------

    private fun resolveFinishReason(response: JsonObject?, current: FinishReason): FinishReason {
        response ?: return current
        response.optObject("incomplete_details")?.optString("reason")?.let { reason ->
            return when (reason) {
                "max_output_tokens" -> FinishReason.MAX_TOKENS
                "content_filter" -> FinishReason.CONTENT_FILTER
                else -> FinishReason.OTHER
            }
        }
        // 有工具调用时 status 是 completed，但语义上这一轮没结束
        val hasToolCall = response.optArray("output")?.any {
            it.isJsonObject && it.asJsonObject.optString("type") == "function_call"
        } ?: false
        if (hasToolCall) return FinishReason.TOOL_CALL
        return current
    }

    private fun JsonObject.toTokenUsage() = TokenUsage(
        promptTokens = optInt("input_tokens"),
        completionTokens = optInt("output_tokens"),
        totalTokens = optInt("total_tokens"),
        cachedTokens = optObject("input_tokens_details")?.optInt("cached_tokens"),
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/"

        private const val DEFAULT_MODEL = "gpt-5.4-mini"

        val CAPABILITIES = ProviderCapabilities(
            streaming = true,
            imageInput = true,
            audioInput = false,      // 音频要走 Realtime API，不是这个接口
            audioOutput = false,
            fileInput = true,
            toolCalling = true,
            reasoningLevels = setOf(
                ReasoningLevel.OFF,
                ReasoningLevel.LOW,
                ReasoningLevel.MEDIUM,
                ReasoningLevel.HIGH,
            ),
            // 不是「不支持采样」，是**传了会 400**（gpt-5 推理档拒绝该参数）
            supportsTemperature = false,
            serverSideHistory = true,
            maxContextTokens = 400_000,
        )

        /** 设置页下拉框的默认候选，用户可以手填任意模型名。 */
        val MODELS = listOf(
            ModelInfo("gpt-5.4-mini", "GPT-5.4 mini", 400_000, supportsImageInput = true, note = "默认"),
            ModelInfo("gpt-5.4", "GPT-5.4", 400_000, supportsImageInput = true),
            ModelInfo("gpt-5.6-luna", "GPT-5.6 luna", 400_000, supportsImageInput = true),
            ModelInfo("gpt-5.6-sol", "GPT-5.6 sol", 400_000, supportsImageInput = true, note = "最强"),
            ModelInfo("gpt-4o-mini", "GPT-4o mini", 128_000, supportsImageInput = true, note = "便宜"),
        )
    }
}
