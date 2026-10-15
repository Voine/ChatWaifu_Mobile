package com.chatwaifu.chat.provider.google

import com.chatwaifu.chat.core.ChatContent
import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatError
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRequest
import com.chatwaifu.chat.core.ChatRole
import com.chatwaifu.chat.core.FinishReason
import com.chatwaifu.chat.core.MediaSource
import com.chatwaifu.chat.core.MessageAccumulator
import com.chatwaifu.chat.core.ModelInfo
import com.chatwaifu.chat.core.ProviderCapabilities
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.chat.core.ReasoningLevel
import com.chatwaifu.chat.core.TokenUsage
import com.chatwaifu.chat.core.capabilitiesFor
import com.chatwaifu.chat.net.HttpSupport
import com.chatwaifu.chat.net.MediaEncoding
import com.chatwaifu.chat.net.SseEvent
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
 * Description: Google Gemini，走 `models/{model}:streamGenerateContent?alt=sse`。
 *
 * ## 为什么不是 Interactions API
 * 2026-06 起 Google 的主接口是 Interactions，`generateContent` 转成 legacy（仍然可用、
 * 不会下线，Batch / 显式缓存 / safetySettings 目前只有它有）。原计划直接对 Interactions，
 * 实测后改了主意：
 * - 我们统一走客户端历史（`store:false`），Interactions 的卖点（服务端会话、agent 步骤）
 *   一个都用不上，剩下的就是一个更新、更少代理支持的 wire format
 * - LiteLLM 一类网关（公司内 AthenAI）对 `generateContent` 是**原样透传**，
 *   对 Interactions 是自己翻译一层（返回的 id 里能看到 `litellm:custom_llm_provider`）
 * - `generateContent` 是 OpenAI 兼容层之外第三方支持最广的 Gemini 协议
 *
 * ## 请求
 * ```
 * x-goog-api-key: <key>        // 官方
 * Authorization: Bearer <key>  // 网关（AthenAI 只认这个）
 * ```
 * 两个都发，理由同 Anthropic。
 * ```json
 * {
 *   "system_instruction": { "parts": [{ "text": "人物设定" }] },
 *   "contents": [{ "role": "user", "parts": [{ "text": "..." }] }],
 *   "generationConfig": {
 *     "maxOutputTokens": N, "temperature": 1.0, "stopSequences": [...],
 *     "thinkingConfig": { "thinkingLevel": "low", "includeThoughts": true }
 *   }
 * }
 * ```
 *
 * ## 内容块映射
 * | ChatContent | Gemini part |
 * |---|---|
 * | Text | `{"text": ...}` |
 * | Image / Audio / Doc（内联） | `{"inline_data": {"mime_type":..., "data": <base64>}}` |
 * | Image / Audio / Doc（Url / 已上传） | `{"file_data": {"mime_type":..., "file_uri":...}}` |
 * | ToolCall | `{"functionCall": {"name":..., "args": {...}}}` |
 * | ToolResult | `{"functionResponse": {"name":..., "response": {...}}}` |
 * | Thinking | 不回传原文；`thoughtSignature` 挂回对应 part |
 *
 * 角色枚举是 `user` / **`model`**（不是 assistant），这是最容易写错的一处。
 *
 * ## 思考 —— 最容易踩的坑
 * Gemini 3.x **默认就会思考，而且思考 token 计入 `maxOutputTokens`**。实测
 * `maxOutputTokens=200` 时 195 个 token 花在思考上，回复只出了 5 个字就 `MAX_TOKENS` 截断。
 * 所以：
 * - [ChatRequest.reasoning] 为 null 时**主动给 `thinkingLevel: "low"`**，陪聊场景不需要深思
 * - [ReasoningLevel.OFF] 也只能映射到 `low`：3.x 的 Pro 不允许关思考，Flash 的 `minimal`
 *   各型号支持不一，用 `low` 是所有 3.x 都认的最低档
 *
 * ## thoughtSignature
 * 3.x 的回复 part 上会挂一个 `thoughtSignature`，多轮里**原样挂回同一个 part**模型才能接上
 * 之前的思路（函数调用时是强制的，缺了直接 400）。我们把它存进
 * [ChatContent.Thinking.opaque]，加 [SIGNATURE_TAG] 前缀和别家的 opaque 区分，
 * 回放时挂到这条 assistant 消息的第一个文本 part 上。
 *
 * ## 为什么值得优先补
 * Gemini 原生支持**音频输入和输出**。将来有机会把「LLM 出中文 → 翻译成日文 → VITS 合成」
 * 这条链路里的翻译和 TTS 一起省掉。当然音色和 Live2D 角色的绑定就没了，属于另一个取舍。
 *
 * Author: Voine
 * Date: 2026/8/6（实现于 2026/10/14）
 */
class GeminiProvider(
    private val config: ProviderConfig,
) : ChatProvider {

    override val id: ProviderId = ProviderId.GOOGLE
    override val displayName: String = "Gemini"
    override val capabilities: ProviderCapabilities = CAPABILITIES
    override val availableModels: List<ModelInfo> = MODELS

    private val http = StreamingHttpClient(config) {
        config.apiKey?.takeIf { it.isNotBlank() }
            ?.let { mapOf("x-goog-api-key" to it, "Authorization" to "Bearer $it") }
            ?: emptyMap()
    }

    private fun endpoint(model: String): String = HttpSupport.resolveEndpoint(
        baseUrl = config.baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL,
        path = "v1beta/models/$model:streamGenerateContent",
    ) + "?alt=sse"

    override fun chatStream(request: ChatRequest): Flow<ChatDelta> = flow {
        val model = request.model ?: defaultModel
        val url = endpoint(model)
        val parser = GeminiStreamParser()
        http.postSse(url, buildRequestJson(request)) { event ->
            parser.onEvent(event).forEach { emit(it) }
        }
        emit(parser.finish(url))
    }.flowOn(Dispatchers.IO)

    override fun close() = http.close()

    // ---------------- 请求映射 ----------------

    internal fun buildRequestJson(request: ChatRequest): String {
        val root = JsonObject()
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            root.add("system_instruction", JsonObject().apply {
                add("parts", JsonArray().apply { add(textPart(prompt)) })
            })
        }
        root.add("contents", buildContents(request))

        root.add("generationConfig", JsonObject().apply {
            request.maxOutputTokens?.let { addProperty("maxOutputTokens", it) }
            request.temperature?.let { addProperty("temperature", it) }
            if (request.stopSequences.isNotEmpty()) {
                add("stopSequences", jsonArrayOf(request.stopSequences))
            }
            add("thinkingConfig", JsonObject().apply {
                addProperty("thinkingLevel", (request.reasoning ?: ReasoningLevel.LOW).toThinkingLevel())
                // 要了才会在流里给 thought part（UI 不展示，但会落进 Thinking 记录）
                addProperty("includeThoughts", request.reasoning != null)
            })
        })

        if (request.tools.isNotEmpty()) {
            root.add("tools", JsonArray().apply {
                add(JsonObject().apply {
                    add("functionDeclarations", JsonArray().apply {
                        request.tools.forEach { tool ->
                            add(JsonObject().apply {
                                addProperty("name", tool.name)
                                addProperty("description", tool.description)
                                add("parameters", JsonParser.parseString(tool.parametersJsonSchema))
                            })
                        }
                    })
                })
            })
        }
        mergeExtras(root, request.extras)
        return root.toString()
    }

    private fun buildContents(request: ChatRequest): JsonArray {
        val caps = capabilitiesFor(request.model)
        // functionResponse 要带函数名，而 ToolResult 只有 callId，所以先建索引
        val toolNames = request.messages
            .flatMap { it.toolCalls }
            .associate { it.id to it.name }
        val array = JsonArray()
        var lastRole: String? = null
        request.messages.forEach { message ->
            val role = when (message.role) {
                ChatRole.ASSISTANT -> "model"
                ChatRole.USER, ChatRole.TOOL -> "user"
                ChatRole.SYSTEM -> return@forEach
            }
            val parts = buildParts(message, caps, toolNames)
            if (parts.isEmpty) return@forEach
            if (role == lastRole) {
                array.last().asJsonObject.getAsJsonArray("parts").addAll(parts)
            } else {
                array.add(JsonObject().apply {
                    addProperty("role", role)
                    add("parts", parts)
                })
                lastRole = role
            }
        }
        return array
    }

    private fun buildParts(
        message: ChatMessage,
        caps: ProviderCapabilities,
        toolNames: Map<String, String>,
    ): JsonArray = JsonArray().apply {
        val signature = message.contents
            .filterIsInstance<ChatContent.Thinking>()
            .firstNotNullOfOrNull { it.opaque?.takeIf { o -> o.startsWith(SIGNATURE_TAG) } }
            ?.removePrefix(SIGNATURE_TAG)
        var signaturePlaced = false

        message.contents.forEach { content ->
            when (content) {
                is ChatContent.Text -> if (content.text.isNotEmpty()) {
                    add(textPart(content.text).apply {
                        if (signature != null && !signaturePlaced) {
                            addProperty("thoughtSignature", signature)
                            signaturePlaced = true
                        }
                    })
                }

                is ChatContent.Image -> {
                    if (!caps.imageInput) throw ChatError.CapabilityUnsupported("image input")
                    add(mediaPart(content.source, "image/jpeg"))
                }

                is ChatContent.Audio -> {
                    if (!caps.audioInput) throw ChatError.CapabilityUnsupported("audio input")
                    add(mediaPart(content.source, "audio/${content.format}"))
                }

                is ChatContent.Doc -> {
                    if (!caps.fileInput) throw ChatError.CapabilityUnsupported("file input")
                    add(mediaPart(content.source, content.mimeType ?: "application/pdf"))
                }

                is ChatContent.ToolCall -> add(JsonObject().apply {
                    add("functionCall", JsonObject().apply {
                        addProperty("name", content.name)
                        add(
                            "args",
                            runCatching { JsonParser.parseString(content.argumentsJson) }
                                .getOrNull()?.takeIf { it.isJsonObject }
                                ?: JsonObject(),
                        )
                    })
                    if (signature != null && !signaturePlaced) {
                        addProperty("thoughtSignature", signature)
                        signaturePlaced = true
                    }
                })

                is ChatContent.ToolResult -> add(JsonObject().apply {
                    add("functionResponse", JsonObject().apply {
                        addProperty("name", toolNames[content.callId] ?: content.callId)
                        // response 必须是对象；工具输出本来是 JSON 对象就原样塞，否则包一层
                        val parsed = runCatching { JsonParser.parseString(content.output) }
                            .getOrNull()?.takeIf { it.isJsonObject }
                        add(
                            "response",
                            parsed ?: JsonObject().apply {
                                addProperty(if (content.isError) "error" else "output", content.output)
                            },
                        )
                    })
                })

                // 思考原文不回传，签名已经挂在上面的 part 上
                is ChatContent.Thinking -> Unit
            }
        }
    }

    private fun textPart(text: String) = JsonObject().apply { addProperty("text", text) }

    private fun mediaPart(source: MediaSource, fallbackMime: String): JsonObject =
        JsonObject().apply {
            when (source) {
                is MediaSource.Url -> add("file_data", JsonObject().apply {
                    addProperty("mime_type", fallbackMime)
                    addProperty("file_uri", source.url)
                })

                is MediaSource.RemoteFileId -> add("file_data", JsonObject().apply {
                    addProperty("mime_type", fallbackMime)
                    addProperty("file_uri", source.id)
                })

                else -> {
                    val inline = MediaEncoding.inline(source)
                        ?: throw ChatError.CapabilityUnsupported("media source")
                    add("inline_data", JsonObject().apply {
                        addProperty("mime_type", inline.mimeType)
                        addProperty("data", inline.base64)
                    })
                }
            }
        }

    private fun ReasoningLevel.toThinkingLevel(): String = when (this) {
        // 见类注释：3.x 不保证能关思考，low 是所有型号都认的最低档
        ReasoningLevel.OFF, ReasoningLevel.LOW -> "low"
        ReasoningLevel.MEDIUM -> "medium"
        ReasoningLevel.HIGH, ReasoningLevel.MAX -> "high"
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/"

        /** 见 [com.chatwaifu.chat.provider.anthropic.AnthropicMessagesProvider.SIGNATURE_TAG] */
        internal const val SIGNATURE_TAG = "gemini:"

        val CAPABILITIES = ProviderCapabilities(
            streaming = true,
            imageInput = true,
            audioInput = true,
            audioOutput = true,
            fileInput = true,
            toolCalling = true,
            reasoningLevels = ReasoningLevel.entries.toSet(),
            supportsTemperature = true,
            // generateContent 每次重发 contents[]，和其他家一样走客户端历史
            serverSideHistory = false,
            maxContextTokens = 1_000_000,
        )

        /** 第一个是默认模型 */
        val MODELS = listOf(
            ModelInfo(
                "gemini-3.5-flash", "Gemini 3.5 Flash", 1_000_000,
                supportsImageInput = true, supportsAudioInput = true, note = "默认",
            ),
            ModelInfo(
                "gemini-3.6-flash", "Gemini 3.6 Flash", 1_000_000,
                supportsImageInput = true, supportsAudioInput = true,
            ),
            ModelInfo(
                "gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", 1_000_000,
                supportsImageInput = true, note = "便宜", cheap = true,
            ),
        )
    }
}

/**
 * Gemini SSE → [ChatDelta]。每一帧都是一个完整的 `GenerateContentResponse`
 * （没有 `event:` 字段），增量体现在 `candidates[0].content.parts[]` 里。
 */
internal class GeminiStreamParser {
    private val accumulator = MessageAccumulator()
    private var started = false
    private var finished = false
    private var finishReason = FinishReason.STOP
    private var usage: TokenUsage? = null
    private var signature: String? = null
    private var toolIndex = 0

    fun onEvent(event: SseEvent): List<ChatDelta> {
        val json = runCatching { JsonParser.parseString(event.data).asJsonObject }.getOrNull()
            ?: return emptyList()
        json.optObject("error")?.let { throw HttpSupport.toChatError(200, json.toString()) }

        val out = mutableListOf<ChatDelta>()
        if (!started) {
            started = true
            out += ChatDelta.Started
        }

        // prompt 本身被拦：没有 candidates，只有 promptFeedback.blockReason
        json.optObject("promptFeedback")?.optString("blockReason")?.let { reason ->
            throw ChatError.ContentFiltered(category = reason, message = "prompt blocked: $reason")
        }

        json.optObject("usageMetadata")?.let { meta ->
            val prompt = meta.optInt("promptTokenCount")
            if (prompt != null) {
                // 思考 token 也是计费的输出，并进 completion
                val completion = (meta.optInt("candidatesTokenCount") ?: 0) +
                    (meta.optInt("thoughtsTokenCount") ?: 0)
                usage = TokenUsage(
                    promptTokens = prompt,
                    completionTokens = completion,
                    totalTokens = meta.optInt("totalTokenCount"),
                    cachedTokens = meta.optInt("cachedContentTokenCount"),
                )
            }
        }

        val candidate = json.optArray("candidates")?.firstOrNull()?.asJsonObject
        candidate?.optObject("content")?.optArray("parts")?.forEach { element ->
            val part = element.asJsonObject
            part.optString("thoughtSignature")?.let { if (signature == null) signature = it }
            val isThought = part.get("thought")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
            part.optString("text")?.let {
                out += if (isThought) ChatDelta.ThinkingDelta(it) else ChatDelta.TextDelta(it)
            }
            part.optObject("functionCall")?.let { call ->
                val index = toolIndex++
                out += ChatDelta.ToolCallDelta(
                    index = index,
                    // Gemini 的函数调用没有 id，用名字 + 序号造一个，ToolResult 回传时靠它对上
                    id = "${call.optString("name")}#$index",
                    name = call.optString("name"),
                    argsFragment = call.optObject("args")?.toString() ?: "{}",
                )
            }
        }

        candidate?.optString("finishReason")?.let { reason ->
            finished = true
            finishReason = when (reason) {
                "STOP" -> if (toolIndex > 0) FinishReason.TOOL_CALL else FinishReason.STOP
                "MAX_TOKENS" -> FinishReason.MAX_TOKENS
                "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII" ->
                    FinishReason.CONTENT_FILTER
                else -> FinishReason.OTHER
            }
        }

        out.forEach(accumulator::accept)
        return out
    }

    fun finish(endpoint: String): ChatDelta.Completed {
        if (!started) throw ChatError.Unknown("empty stream from $endpoint")
        if (!finished) throw ChatError.Network("stream ended without finishReason")
        signature?.let { accumulator.setThinkingOpaque(GeminiProvider.SIGNATURE_TAG + it) }
        return ChatDelta.Completed(accumulator.build(), usage, finishReason)
    }
}
