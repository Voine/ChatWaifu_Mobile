package com.chatwaifu.chat.provider.anthropic

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
 * Description: Anthropic Messages API（`POST {base}/v1/messages`）。
 *
 * ## 请求头
 * ```
 * x-api-key: <key>            // 不是 Authorization: Bearer
 * anthropic-version: 2023-06-01
 * ```
 * 两个都发：官方只认 `x-api-key`；LiteLLM 一类网关（公司内的 AthenAI）两个都认，
 * 只发 Bearer 的话打不了官方，只发 x-api-key 的话某些代理不认。
 *
 * ## 请求体
 * ```json
 * {
 *   "model": "claude-sonnet-5-5",
 *   "max_tokens": 4096,                       // 必填！三家里只有它是必填
 *   "system": "人物设定",
 *   "messages": [ { "role": "user", "content": [ ...content blocks... ] } ],
 *   "stream": true,
 *   "temperature": 1.0,                        // 范围 0..1，不是 OpenAI 的 0..2
 *   "thinking": { "type": "adaptive" },        // 或 {"type":"disabled"}
 *   "output_config": { "effort": "high" },     // low/medium/high/xhigh/max
 * }
 * ```
 *
 * ## 内容块映射（我们的 ChatContent 几乎就是它的 content block）
 * | ChatContent | Anthropic |
 * |---|---|
 * | Text | `{"type":"text","text":...}` |
 * | Image | `{"type":"image","source":{"type":"base64","media_type":...,"data":...}}`，或 `{"type":"url","url":...}` |
 * | Doc | `{"type":"document","source":{"type":"base64",...}}` |
 * | ToolCall | `{"type":"tool_use","id":...,"name":...,"input":{...}}`（input 是 **对象**，不是 JSON 字符串） |
 * | ToolResult | `{"type":"tool_result","tool_use_id":...,"content":...}`，且必须放在 **user** 消息里 |
 * | Thinking | `{"type":"thinking","thinking":...,"signature":...}` ← `opaque` 存 signature |
 *
 * 硬约束：
 * 1. `messages` 首条必须是 user（[com.chatwaifu.chat.core.ContextBudget.trim] 已经保证）
 * 2. 带 thinking 的 assistant 消息，thinking block 必须在最前面
 *    （[MessageAccumulator.build] 已经这么排）
 * 3. 相邻同角色消息要合并 —— TOOL 折叠进 user 之后，很容易出现两条 user 挨着
 * 4. **thinking 签名跨 provider 不能重放**：签名是 Anthropic 签发的，别家产出的
 *    Thinking（opaque 是 OpenAI 的 encrypted reasoning、或者干脆没有）发过来会 400。
 *    所以只回传「有签名」的 thinking；没签名的直接丢，不影响对话正确性
 *
 * ## 缓存断点（`cache_control`）
 * 三家里只有 Anthropic 的 prompt cache 要**显式打标**，OpenAI / Gemini 是按前缀自动缓存的。
 * 不打标的话 [com.chatwaifu.chat.core.ContextBudget] 的分块淘汰（为了前缀稳定）在这一家
 * 的收益是零。所以每个请求固定打两个断点：
 * 1. **system 末尾**：人设 + 记忆块，变化最慢
 * 2. **最后一条消息的最后一块**：滚动断点。下一轮请求的前缀正好包含这一轮写进缓存的全部，
 *    于是每轮只为新增的那一截付「写缓存」的钱，前面的全按「读缓存」计
 *
 * 不够最小长度（Sonnet/Opus 1024、Haiku 2048 token 量级）时 API 会静默不缓存，不报错，
 * 所以不用自己判断长短。效果看 usage 的 `cache_read_input_tokens`（→ [TokenUsage.cachedTokens]）。
 *
 * **有效期用 1 小时，不用默认的 5 分钟**：聊天是人在打字，隔几分钟才回一句很正常，
 * 5 分钟的缓存在两句之间就过期了。1h 的写入价是 2×（5m 是 1.25×），读都是 0.1×，
 * 只要一小时内再聊一句就回本。同 Hermes Agent 对「人在打字的会话」的选择。
 *
 * ## 推理档
 * - [ReasoningLevel.OFF] → `thinking: {"type":"disabled"}`
 * - 其余 → `thinking: {"type":"adaptive"}` + `output_config.effort`
 * - `claude-haiku-4-5` 不支持 adaptive，走老的 `{"type":"enabled","budget_tokens":N}`
 *
 * SSE 事件的映射见 [AnthropicStreamParser]。
 *
 * Author: Voine
 * Date: 2026/8/6（实现于 2026/10/14）
 */
class AnthropicMessagesProvider(
    private val config: ProviderConfig,
) : ChatProvider {

    override val id: ProviderId = ProviderId.ANTHROPIC
    override val displayName: String = "Claude"
    override val capabilities: ProviderCapabilities = CAPABILITIES
    override val availableModels: List<ModelInfo> = MODELS

    private val http = StreamingHttpClient(config) {
        config.apiKey?.takeIf { it.isNotBlank() }
            ?.let {
                mapOf(
                    "x-api-key" to it,
                    "Authorization" to "Bearer $it",
                    "anthropic-version" to ANTHROPIC_VERSION,
                )
            }
            ?: mapOf("anthropic-version" to ANTHROPIC_VERSION)
    }

    private val endpoint: String
        get() = HttpSupport.resolveEndpoint(
            baseUrl = config.baseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL,
            path = "v1/messages",
        )

    override fun chatStream(request: ChatRequest): Flow<ChatDelta> = flow {
        val body = buildRequestJson(request)
        val parser = AnthropicStreamParser()
        http.postSse(endpoint, body) { event ->
            parser.onEvent(event).forEach { emit(it) }
        }
        emit(parser.finish(endpoint))
    }.flowOn(Dispatchers.IO)

    override fun close() = http.close()

    // ---------------- 请求映射 ----------------

    internal fun buildRequestJson(request: ChatRequest): String {
        val model = request.model ?: defaultModel
        val root = JsonObject()
        root.addProperty("model", model)
        root.addProperty("max_tokens", request.maxOutputTokens ?: DEFAULT_MAX_TOKENS)
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { system ->
            // 写成 block 数组才能挂 cache_control，见类注释「缓存断点」
            root.add("system", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", system)
                    add("cache_control", ephemeral())
                })
            })
        }
        root.add("messages", buildMessages(request).also(::markRollingBreakpoint))
        root.addProperty("stream", true)

        val thinkingOn = request.reasoning != null && request.reasoning != ReasoningLevel.OFF
        // 开了 thinking 时 temperature 只能是 1，干脆不发
        if (!thinkingOn) {
            request.temperature?.let { root.addProperty("temperature", it.coerceIn(0f, 1f)) }
        }
        request.reasoning?.let { addThinking(root, model, it, root.get("max_tokens").asInt) }

        if (request.stopSequences.isNotEmpty()) {
            root.add("stop_sequences", jsonArrayOf(request.stopSequences))
        }
        if (request.tools.isNotEmpty()) {
            root.add("tools", JsonArray().apply {
                request.tools.forEach { tool ->
                    add(JsonObject().apply {
                        addProperty("name", tool.name)
                        addProperty("description", tool.description)
                        add("input_schema", JsonParser.parseString(tool.parametersJsonSchema))
                    })
                }
            })
        }
        mergeExtras(root, request.extras)
        return root.toString()
    }

    private fun addThinking(root: JsonObject, model: String, level: ReasoningLevel, maxTokens: Int) {
        if (level == ReasoningLevel.OFF) {
            root.add("thinking", JsonObject().apply { addProperty("type", "disabled") })
            return
        }
        if (model.startsWith(LEGACY_THINKING_PREFIX)) {
            // budget 必须 >= 1024 且 < max_tokens；max_tokens 太小时宁可不开
            val budget = when (level) {
                ReasoningLevel.LOW -> 1024
                ReasoningLevel.MEDIUM -> 4096
                else -> 16_000
            }.coerceAtMost(maxTokens - 1)
            if (budget >= 1024) {
                root.add("thinking", JsonObject().apply {
                    addProperty("type", "enabled")
                    addProperty("budget_tokens", budget)
                })
            }
            return
        }
        root.add("thinking", JsonObject().apply { addProperty("type", "adaptive") })
        root.add("output_config", JsonObject().apply {
            addProperty(
                "effort",
                when (level) {
                    ReasoningLevel.LOW -> "low"
                    ReasoningLevel.MEDIUM -> "medium"
                    ReasoningLevel.HIGH -> "high"
                    else -> "max"
                },
            )
        })
    }

    private fun buildMessages(request: ChatRequest): JsonArray {
        val caps = capabilitiesFor(request.model)
        val array = JsonArray()
        var lastRole: String? = null
        request.messages.forEach { message ->
            val role = when (message.role) {
                ChatRole.ASSISTANT -> "assistant"
                // tool_result 在这个协议里属于 user 消息
                ChatRole.USER, ChatRole.TOOL -> "user"
                // system 走顶层字段，历史里残留的 system 消息不下发
                ChatRole.SYSTEM -> return@forEach
            }
            val blocks = buildBlocks(message, caps)
            if (blocks.isEmpty) return@forEach
            if (role == lastRole) {
                // 相邻同角色合并（API 要求 user / assistant 严格交替）
                array.last().asJsonObject.getAsJsonArray("content").addAll(blocks)
            } else {
                array.add(JsonObject().apply {
                    addProperty("role", role)
                    add("content", blocks)
                })
                lastRole = role
            }
        }
        return array
    }

    private fun buildBlocks(message: ChatMessage, caps: ProviderCapabilities): JsonArray =
        JsonArray().apply {
            message.contents.forEach { content ->
                when (content) {
                    is ChatContent.Text -> if (content.text.isNotEmpty()) {
                        add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", content.text)
                        })
                    }

                    is ChatContent.Image -> {
                        if (!caps.imageInput) throw ChatError.CapabilityUnsupported("image input")
                        add(JsonObject().apply {
                            addProperty("type", "image")
                            add("source", mediaSource(content.source, "image"))
                        })
                    }

                    is ChatContent.Doc -> {
                        if (!caps.fileInput) throw ChatError.CapabilityUnsupported("file input")
                        add(JsonObject().apply {
                            addProperty("type", "document")
                            add("source", mediaSource(content.source, "document"))
                            content.fileName?.let { addProperty("title", it) }
                        })
                    }

                    is ChatContent.Audio -> throw ChatError.CapabilityUnsupported("audio input")

                    is ChatContent.ToolCall -> add(JsonObject().apply {
                        addProperty("type", "tool_use")
                        addProperty("id", content.id)
                        addProperty("name", content.name)
                        add(
                            "input",
                            runCatching { JsonParser.parseString(content.argumentsJson) }
                                .getOrNull()?.takeIf { it.isJsonObject }
                                ?: JsonObject(),
                        )
                    })

                    is ChatContent.ToolResult -> add(JsonObject().apply {
                        addProperty("type", "tool_result")
                        addProperty("tool_use_id", content.callId)
                        addProperty("content", content.output)
                        if (content.isError) addProperty("is_error", true)
                    })

                    // 只回传带签名的 thinking，见类注释第 4 条
                    is ChatContent.Thinking -> content.opaque
                        ?.takeIf { it.startsWith(SIGNATURE_TAG) }
                        ?.let { signature ->
                            add(JsonObject().apply {
                                addProperty("type", "thinking")
                                addProperty("thinking", content.text)
                                addProperty("signature", signature.removePrefix(SIGNATURE_TAG))
                            })
                        }
                }
            }
        }

    /**
     * 滚动断点挂在最后一条消息的最后一个可缓存块上。thinking block 不接受 `cache_control`，
     * 跳过它往前找（最后一条正常是 user，这只是防御）。
     */
    private fun markRollingBreakpoint(messages: JsonArray) {
        val blocks = messages.lastOrNull()?.asJsonObject?.getAsJsonArray("content") ?: return
        blocks.lastOrNull { it.asJsonObject.get("type")?.asString != "thinking" }
            ?.asJsonObject
            ?.add("cache_control", ephemeral())
    }

    private fun ephemeral() = JsonObject().apply {
        addProperty("type", "ephemeral")
        addProperty("ttl", CACHE_TTL)
    }

    private fun mediaSource(source: MediaSource, what: String): JsonObject = JsonObject().apply {
        when (source) {
            is MediaSource.Url -> {
                addProperty("type", "url")
                addProperty("url", source.url)
            }

            is MediaSource.RemoteFileId -> {
                addProperty("type", "file")
                addProperty("file_id", source.id)
            }

            else -> {
                val inline = MediaEncoding.inline(source)
                    ?: throw ChatError.CapabilityUnsupported("$what source")
                addProperty("type", "base64")
                addProperty("media_type", inline.mimeType)
                addProperty("data", inline.base64)
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com/"
        const val ANTHROPIC_VERSION = "2023-06-01"

        /** max_tokens 是必填字段，调用方没给时用这个兜底。 */
        const val DEFAULT_MAX_TOKENS = 4096

        /** 提示缓存有效期，见类注释「缓存断点」 */
        internal const val CACHE_TTL = "1h"

        /**
         * 存进 [ChatContent.Thinking.opaque] 时加的前缀，用来认出「这是 Anthropic 签的」。
         * opaque 是各家共用的字段（OpenAI 存 encrypted reasoning），不加标记就分不清，
         * 切 provider 之后会把别家的串当签名发过来，直接 400。
         */
        internal const val SIGNATURE_TAG = "anthropic:"

        /** 只支持老式 `budget_tokens` thinking 的型号前缀 */
        private const val LEGACY_THINKING_PREFIX = "claude-haiku-4"

        val CAPABILITIES = ProviderCapabilities(
            streaming = true,
            imageInput = true,
            audioInput = false,
            audioOutput = false,
            fileInput = true,          // PDF 走 document block
            toolCalling = true,
            reasoningLevels = ReasoningLevel.entries.toSet(),
            // 支持，但范围是 0..1 —— UI 上的滑杆不能照 OpenAI 的 0..2 来
            supportsTemperature = true,
            // Messages API 没有服务端会话，永远是客户端重发全量。
            // 这也是整个抽象层选择「历史放客户端」的直接原因。
            serverSideHistory = false,
            maxContextTokens = 1_000_000,
        )

        /** 第一个是默认模型 */
        val MODELS = listOf(
            ModelInfo("claude-sonnet-5-5", "Claude Sonnet 5.5", 1_000_000, supportsImageInput = true, note = "默认"),
            ModelInfo("claude-opus-5-5", "Claude Opus 5.5", 1_000_000, supportsImageInput = true),
            ModelInfo("claude-fable-5-1", "Claude Fable 5.1", 1_000_000, supportsImageInput = true, note = "最强"),
            ModelInfo("claude-haiku-5-5", "Claude Haiku 5.5", 200_000, supportsImageInput = true, note = "最快", cheap = true),
        )
    }
}

/**
 * Anthropic SSE → [ChatDelta]。和网络层拆开是为了能直接喂录下来的事件做单元测试。
 *
 * ```
 * message_start          → Started，取 message.usage.input_tokens
 * content_block_start    → tool_use 时拿 id / name
 * content_block_delta    → text_delta / thinking_delta / signature_delta / input_json_delta
 * message_delta          → stop_reason + usage.output_tokens
 * message_stop           → 结束
 * ping                   → 心跳，忽略
 * error                  → 抛 ChatError（流内错误，HTTP 码是 200）
 * ```
 */
internal class AnthropicStreamParser {
    private val accumulator = MessageAccumulator()
    private var started = false
    private var stopped = false
    private var finishReason = FinishReason.STOP
    private var inputTokens: Int? = null
    private var outputTokens: Int? = null
    private var cachedTokens: Int? = null
    private val signature = StringBuilder()

    /** content block index → 工具调用序号（MessageAccumulator 按序号拼参数） */
    private val toolIndexByBlock = mutableMapOf<Int, Int>()

    fun onEvent(event: SseEvent): List<ChatDelta> {
        val json = runCatching { JsonParser.parseString(event.data).asJsonObject }.getOrNull()
            ?: return emptyList()
        val out = mutableListOf<ChatDelta>()
        when (event.event ?: json.optString("type")) {
            "message_start" -> {
                json.optObject("message")?.optObject("usage")?.let { usage ->
                    // Anthropic 的 input_tokens **不含**缓存读写的部分，三项加起来才是整个 prompt。
                    // 统一成 OpenAI 的口径（prompt_tokens 包含 cached），聊天记录里的数字才可比
                    val read = usage.optInt("cache_read_input_tokens")
                    val written = usage.optInt("cache_creation_input_tokens")
                    inputTokens = usage.optInt("input_tokens")
                        ?.let { it + (read ?: 0) + (written ?: 0) }
                    cachedTokens = read
                }
                if (!started) {
                    started = true
                    out += ChatDelta.Started
                }
            }

            "content_block_start" -> {
                val block = json.optObject("content_block")
                if (block?.optString("type") == "tool_use") {
                    val toolIndex = toolIndexByBlock.size
                    toolIndexByBlock[json.optInt("index") ?: 0] = toolIndex
                    out += ChatDelta.ToolCallDelta(
                        index = toolIndex,
                        id = block.optString("id"),
                        name = block.optString("name"),
                    )
                }
            }

            "content_block_delta" -> {
                val delta = json.optObject("delta") ?: return emptyList()
                when (delta.optString("type")) {
                    "text_delta" -> delta.optString("text")?.let { out += ChatDelta.TextDelta(it) }
                    "thinking_delta" -> delta.optString("thinking")?.let { out += ChatDelta.ThinkingDelta(it) }
                    "signature_delta" -> delta.optString("signature")?.let { signature.append(it) }
                    "input_json_delta" -> {
                        val toolIndex = toolIndexByBlock[json.optInt("index") ?: 0] ?: 0
                        delta.optString("partial_json")?.let {
                            out += ChatDelta.ToolCallDelta(index = toolIndex, argsFragment = it)
                        }
                    }
                }
            }

            "message_delta" -> {
                json.optObject("delta")?.let { delta ->
                    when (delta.optString("stop_reason")) {
                        "end_turn", "stop_sequence" -> finishReason = FinishReason.STOP
                        "max_tokens" -> finishReason = FinishReason.MAX_TOKENS
                        "tool_use" -> finishReason = FinishReason.TOOL_CALL
                        "refusal" -> finishReason = FinishReason.REFUSAL
                        "model_context_window_exceeded" ->
                            throw ChatError.ContextOverflow("model_context_window_exceeded")
                        null -> Unit
                        else -> finishReason = FinishReason.OTHER
                    }
                }
                json.optObject("usage")?.optInt("output_tokens")?.let { outputTokens = it }
            }

            "message_stop" -> stopped = true

            "error" -> {
                val error = json.optObject("error")
                val type = error?.optString("type")
                val message = error?.optString("message") ?: event.data
                throw when (type) {
                    "overloaded_error", "api_error" -> ChatError.ServerError(529, message)
                    "rate_limit_error" -> ChatError.RateLimited(message = message)
                    "authentication_error", "permission_error" -> ChatError.Auth(message)
                    else -> ChatError.Unknown(message)
                }
            }

            else -> Unit // ping 等
        }
        out.forEach(accumulator::accept)
        return out
    }

    fun finish(endpoint: String): ChatDelta.Completed {
        if (!started) throw ChatError.Unknown("empty stream from $endpoint")
        if (!stopped && finishReason == FinishReason.STOP) {
            // 没有 message_stop 就断流：连接被掐了，不能当成正常结束
            throw ChatError.Network("stream ended before message_stop")
        }
        if (signature.isNotEmpty()) {
            accumulator.setThinkingOpaque(AnthropicMessagesProvider.SIGNATURE_TAG + signature)
        }
        val usage = TokenUsage(
            promptTokens = inputTokens,
            completionTokens = outputTokens,
            totalTokens = if (inputTokens != null && outputTokens != null) {
                inputTokens!! + outputTokens!!
            } else {
                null
            },
            cachedTokens = cachedTokens,
        )
        return ChatDelta.Completed(accumulator.build(), usage, finishReason)
    }
}
