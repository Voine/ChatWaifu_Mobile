package com.chatwaifu.chat.provider.local

import com.chatwaifu.chat.core.ChatDelta
import com.chatwaifu.chat.core.ChatProvider
import com.chatwaifu.chat.core.ChatRequest
import com.chatwaifu.chat.core.ModelInfo
import com.chatwaifu.chat.core.ProviderCapabilities
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.chat.provider.compat.OpenAICompatProvider
import kotlinx.coroutines.flow.Flow

/**
 * Description: 端内推理 —— **尚未实现**。
 *
 * 抽象层之所以选「流式优先」，很大一部分就是为了这一条路：端内推理天然是
 * 一个 token 一个 token 出的回调，[Flow] 是它最自然的形状，反倒是非流式要额外攒。
 *
 * ## 两条可选路线
 *
 * ### (a) 本机起一个 OpenAI 兼容 server —— 几乎零成本
 * llama.cpp 的 `llama-server`、MLC、Ollama 都能在端上跑一个 HTTP server。
 * 这种情况**根本不需要这个类**，直接用 [OpenAICompatProvider]：
 * ```kotlin
 * OpenAICompatProvider(
 *     config = ProviderConfig(baseUrl = "http://127.0.0.1:8080/", apiKey = null),
 *     displayName = "端内模型",
 * )
 * ```
 * 代价是要在 App 里拉起并守护一个进程（参考 Sherpa 那样单独一个 `:sherpa` 进程），
 * 而且多个 ncnn/GGML 实例抢内存的问题依旧存在。
 *
 * ### (b) JNI 直连推理库（MNN / llama.cpp / MediaPipe LLM Inference）
 * 这个类要实现的是这条。关键点：
 * 1. 用 `callbackFlow` 把「native token 回调」包成 `Flow<ChatDelta>`：
 *    ```kotlin
 *    override fun chatStream(request: ChatRequest) = callbackFlow {
 *        trySend(ChatDelta.Started)
 *        val handle = nativeStart(renderPrompt(request)) { token ->
 *            trySend(ChatDelta.TextDelta(token))
 *        }
 *        awaitClose { nativeCancel(handle) }   // 取消 collect 必须能中断 native 推理
 *    }.flowOn(inferenceDispatcher)
 *    ```
 * 2. **prompt 模板要自己拼**。这是和云端最大的差异：云端收结构化的
 *    `messages[]`，本地模型收的是一整个字符串，得按模型的 chat template
 *    （ChatML / Llama 3 / Gemma 之类）把 role 和分隔符渲染出来。
 *    这层渲染逻辑应该放在这个包里，不要污染 core。
 * 3. 推理必须跑在单线程的专用 dispatcher 上（native 侧不可重入），
 *    并且**参考 Sherpa 的做法考虑放进独立进程** —— 见 CLAUDE.md：
 *    多个 ncnn 库像是进程独享资源。VITS 和端内 LLM 抢同一份内存/算力时尤其要注意。
 * 4. 模型文件走应用专属目录 + SAF 导入，和 VITS / Live2D 模型同一套布局
 *    （`ModelStorage` / `ModelImporter`），不要申请存储权限。
 *    `content://` URI 喂不进 native，必须先解压落地成真实路径。
 *
 * ## 能力
 * 端内几乎都是纯文本小模型，所以 [CAPABILITIES] 把多模态全关了；
 * 上下文窗口由用户加载的模型决定，所以 `maxContextTokens = null`
 * （[com.chatwaifu.chat.core.ContextBudget] 会退回保守默认值）。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class LocalLlmProvider(
    private val config: ProviderConfig,
) : ChatProvider {

    override val id: ProviderId = ProviderId.LOCAL
    override val displayName: String = "端内模型"
    override val capabilities: ProviderCapabilities = CAPABILITIES
    override val availableModels: List<ModelInfo> = emptyList()

    /** 端内模型没有「模型名」的概念，用户选的是一个文件（[ProviderConfig.localModelPath]）。 */
    override val defaultModel: String = ""

    override fun chatStream(request: ChatRequest): Flow<ChatDelta> =
        throw NotImplementedError("LocalLlmProvider 还没实现，见类注释里的两条路线")

    companion object {
        val CAPABILITIES = ProviderCapabilities(
            streaming = true,
            imageInput = false,
            audioInput = false,
            audioOutput = false,
            fileInput = false,
            toolCalling = false,
            reasoningLevels = emptySet(),
            supportsTemperature = true,
            serverSideHistory = false,
            maxContextTokens = null,
        )
    }
}
