# ChatCore · 聊天基座抽象层 设计 + 施工日志

分支 `feature/v2.0.0`，起点 `0bf2f99 基座升级`。

这份文件同时是**设计文档**和**施工日志**：上半部分是接口语义和三家 API 的映射表
（后面补 provider 实现时不用重新查一遍文档），下半部分是进度和待办。

## 为什么要做

原来的链路把「OpenAI Chat Completions」这一个具体接口焊死在业务里：

- `ChatGPT` 模块只有 `GhatGPTAPI.sendMsg()` 一个 `POST v1/chat/completions`，DTO 是 2023 年
  `gpt-3.5-turbo` 时代的形状，model / temperature / max_tokens 全硬编码在 `ChatGPTData.kt`
- `ChatGPTNetService` 同时兼了网络、密钥、system role、**历史裁剪**四件事，历史还只是
  `List<String>`（纯 assistant 文本）
- `ChatActivityViewModel.mainLoop()` 直接依赖 `choices[0].message.content`
- 换基座要动 4 个文件，多模态输入无处安放

## 2026 年三家接口的形状

结构高度同源（都是 `role + 内容块列表` + 独立的 system 字段），差异集中在字段名和推理档：

| 基座 | 当前主接口 | system | 历史 | 多模态入 | 推理档 | temperature |
|---|---|---|---|---|---|---|
| OpenAI | `POST /v1/responses`（Chat Completions 已转为兼容层） | `instructions` | `previous_response_id` 可选服务端 | `input_text` / `input_image` / `input_file` | `reasoning.effort` | **gpt-5 推理档拒收** |
| Anthropic | `POST /v1/messages` | 顶层 `system` | **无服务端会话，必须重发全量** | content block：`text` / `image` / `document` | `thinking:{type:"adaptive"}` + `output_config.effort` | 支持，但**范围 0.0–1.0**（默认 1.0） |
| Google | `interactions.create`（2026-06 GA，`generateContent` 转 legacy） | `system_instruction` | `previous_interaction_id` 可选服务端 | `parts[].inline_data` / `file_data` | `generation_config.thinking_level` | 支持 |

> **订正**：立项时的计划里写「`claude-opus-5` / `claude-sonnet-5` 收到 `temperature` 直接 400」，
> 这是错的。Messages API 里 `temperature` 是受支持的可选字段，只是范围收窄成 0.0–1.0（OpenAI 是 0–2）。
> 所以 `AnthropicMessagesProvider.CAPABILITIES.supportsTemperature = true`，
> `supportsTemperature = false` 只对 OpenAI Responses（gpt-5 推理档）成立。

抽象层要做的就是把这张表压成一套接口，让业务只认 `ChatMessage` / `ChatDelta`。

## 四个已确认的设计决策

1. **流式优先**：核心方法是 `chatStream(): Flow<ChatDelta>`，非流式 `chat()` 是扩展函数
2. **历史由抽象层在客户端统一维护**：因为 Anthropic 根本没有服务端会话，只要有一家必须重发全量，
   这一层就得自己持有全量。顺带好处：Room 仍是唯一数据源，切 provider 不丢上下文
3. **新建 `ChatCore` 模块**，provider 作为子包
4. **首批只真正实现** OpenAI Responses + OpenAI 兼容通用；Anthropic / Gemini / 端内只留接口和映射注释

## 模块结构

`namespace = com.chatwaifu.chat`。依赖只用 catalog 里已有的
`retrofit` / `retrofit-converter-gson` / `gson` / `okhttp` / `kotlinx-coroutines-android` / `androidx-annotation`
——**没有新增任何依赖坐标**。

```
ChatCore/src/main/java/com/chatwaifu/chat/
  core/
    ChatMessage.kt          ChatRole / MediaSource / ChatContent / ChatMessage
    ChatRequest.kt          ChatRequest / ReasoningLevel / ToolSpec
    ChatDelta.kt            ChatDelta / FinishReason / TokenUsage / ChatResult
    ChatError.kt            统一错误
    ChatProvider.kt         接口 + suspend fun ChatProvider.chat()
    ChatSession.kt          客户端历史 + ChatOptions + MessageAccumulator
    ContextBudget.kt        TokenEstimator + 按 token 预算裁剪
    ProviderCapabilities.kt ProviderCapabilities / ModelInfo
    ProviderConfig.kt       ProviderId / ProviderConfig
  net/
    SseParser.kt            ~40 行手写 SSE 帧解析
    HttpSupport.kt          OkHttp 装配 / endpoint 拼接 / HTTP → ChatError
    MediaEncoding.kt        MediaSource → base64 / data URI
    StreamingHttpClient.kt  唯一的 HTTP 通道（@Streaming POST）
  provider/
    JsonBuilders.kt         Gson JsonObject 的小工具 + extras 合并
    openai/OpenAIResponsesProvider.kt   ← 已落地
    compat/OpenAICompatProvider.kt      ← 已落地
    anthropic/AnthropicMessagesProvider.kt ← stub（映射写在 KDoc）
    google/GeminiProvider.kt               ← stub
    local/LocalLlmProvider.kt              ← stub
  ChatProviderFactory.kt    create(id, config) + descriptors()（给设置页用，不构造实例）
```

## 核心类型语义

### 消息

`role + List<ChatContent>` 是三家的最小公倍数。多模态入口**现在就定义好**，
provider 不支持的类型在映射时抛 `ChatError.CapabilityUnsupported`，而不是静默丢弃。

```kotlin
enum class ChatRole { SYSTEM, USER, ASSISTANT, TOOL }

sealed interface ChatContent {
    data class Text(text)
    data class Image(source, detail = AUTO)
    data class Audio(source, format)
    data class Doc(source, fileName, mimeType)
    data class ToolCall(id, name, argumentsJson)
    data class ToolResult(callId, output, isError)
    /** Claude thinking block / OpenAI reasoning item：opaque 必须原样回传，不可改写 */
    data class Thinking(text, opaque)
}

sealed interface MediaSource { Bytes / Url / RemoteFileId / LocalPath }
```

`MediaSource.Bytes` 自己实现了 `equals` / `hashCode`（data class 对 `ByteArray` 是引用比较）。

### 请求

`systemPrompt` 独立成字段而不是塞进 `messages`——三家都是独立字段。
可空参数由 provider 按 `capabilities` 决定丢弃还是透传，
这样「传这个字段会 400」的情况不需要调用方分支。`extras` 是 provider 专属逃生阀。

### 流式事件

只发**增量**，累加交给 `ChatSession.MessageAccumulator`，避免每个 provider 各写一套拼接：

```kotlin
sealed interface ChatDelta { Started / TextDelta / ThinkingDelta / ToolCallDelta / Completed }
```

失败**不发** `Failed` 事件，直接让 Flow 抛 `ChatError`——`catch {}` 是 Flow 的惯用法，
也避免调用方同时处理两条失败路径。

`MessageAccumulator.build()` 把 thinking block 放在最前面，这是 Anthropic 的硬要求。

### 错误映射

`ChatError`：`Auth` / `RateLimited(retryAfterSeconds)` / `Network` / `Timeout` /
`ContextOverflow` / `ContentFiltered(category)` / `Refused(category)` / `ModelNotFound` /
`CapabilityUnsupported(what)` / `ServerError(httpCode)` / `Unknown`。

`HttpSupport.toChatError()` 的判定顺序：**先看 body 里的 `error.code`**
（`content_filter` / `context_length_exceeded` 这类语义比状态码准），再看状态码
（401/403→Auth、404→ModelNotFound、408→Timeout、413→ContextOverflow、
429（读 `retry-after`）→RateLimited、5xx→ServerError）。

### ContextBudget

`TokenEstimator` 是粗估：CJK 一字约 1 token（`isCjk()` 覆盖假名 / CJK 统一汉字 / 扩展 A /
谚文 / 全角），其余按 `(n+3)/4`；每条消息 +4 overhead，图片 1600、音频 1000、文档 3000。

`trim()` 从后往前走，**永远保留最后一条**，然后把开头的非 USER 消息丢掉——
这样 Anthropic「首条必须是 user」的约束天然成立。
`forContextWindow(contextTokens)` 给输出预留 1/4。

### ChatSession

客户端历史的唯一归属。`send()` 负责：追加 user 消息 → 裁剪 → 调 provider →
`Completed` 时把 assistant 消息追加回历史。`restore()` / `snapshot()` 对接 Room 和 provider 切换。

## 落地的两个 provider

### OpenAICompatProvider

`POST {base}/v1/chat/completions`。一份实现同时覆盖旧代理、DeepSeek、Ollama、
llama.cpp server、vLLM、LM Studio。它是**行为等价替换现状**的那一步。

几个要点：
- 构造函数把 `displayName` / `availableModels` / `defaultModel` / `capabilities` 开成可覆盖参数，
  所以「端内起本地 server」这条路可以直接 new 一个它出来
- 只在有 key 时才发 `Authorization: Bearer`（本地 server 收到空 Bearer 会 401）
- 纯文本消息用 **string 形式的 `content`**（兼容性最好），有附件才切数组形式
- `stream_options:{include_usage:true}` 才能在流式下拿到 usage
- 200 响应体里也可能嵌 `error` 对象，要解

### OpenAIResponsesProvider

`POST {base}/v1/responses`，`store=false`（历史我们自己管）。

- `systemPrompt` → `instructions`
- `messages` → `input[]`；`ToolCall` / `ToolResult` 要从消息里**拆出来**成独立的
  `function_call` / `function_call_output` item，不是 content part
- user 侧用 `input_text`，assistant 历史侧用 `output_text`（写错会 400）
- `reasoning:{effort}`：OFF→`minimal`，MAX→`high`
- `supportsTemperature = false`
- **mid-stream 错误也是 HTTP 200**，必须解 `error` 事件而不是只看状态码
- `resolveFinishReason()` 读 `incomplete_details.reason`，并检测 `output` 里有没有 `function_call`

### 三个 stub

`capabilities` / `availableModels` 填的是真值，`chatStream` 抛 `NotImplementedError`，
**映射方案全写在类 KDoc 里**：

- `AnthropicMessagesProvider`：`x-api-key` + `anthropic-version: 2023-06-01`；`max_tokens` **必填**；
  顶层 `system`；`thinking` 在 opus-5 / sonnet-5 / fable-5 上用 `{type:"adaptive"}`，
  haiku-4-5 只能用 `{type:"enabled", budget_tokens>=1024}`；
  `stop_reason=refusal` + `stop_details.category` → `ChatError.Refused`；
  SSE 是 `message_start` / `content_block_start` / `content_block_delta` /
  `content_block_stop` / `message_delta` / `message_stop` / `ping` / `error`
- `GeminiProvider`：Interactions vs generateContent 的对照表；`store:false` 会同时关掉
  链式和 background；role 是 `user` / **`model`**（不是 assistant）；
  原生音频入出，将来可能直接替掉「翻译 + VITS」这一环
- `LocalLlmProvider`：两条路——(a) 端内起 OpenAI 兼容 server，直接复用 `OpenAICompatProvider`
  指到 `http://127.0.0.1:<port>/`；(b) 走 JNI（MNN / llama.cpp / MediaPipe）时用
  `callbackFlow` + `awaitClose { nativeCancel() }`，此时 chat template 得在客户端渲染，
  且可能要像 `:sherpa` 一样拆独立进程

## 配置与密钥

按 provider 分开存，切回来不用重填：

```
saved_active_chat_provider                        // ProviderId.key
saved_provider_<id>_key / _base_url / _model
saved_provider_migrated                          // 一次性迁移标记
```

`ChatProviderSettings`（`app/.../data/chat/`）是 app 侧和 ChatCore 的唯一配置桥梁。
`init` 里跑 `migrateLegacyIfNeeded()`：旧的 `saved_chat_key` 搬到 OPENAI_COMPAT 的 key，
开启状态的 proxy url 搬到它的 base_url，然后把 OPENAI_COMPAT 设为激活——
行为等价于原来的代码路径，老用户不用重填。旧 key 保留没删，方便回滚。

**代理地址不再是一个独立开关**，它就是 OpenAI 兼容基座的 Base Url。

## app 层接线

| 文件 | 改动 |
|---|---|
| `ChatActivityViewModel.kt` | `chatGPTNetService` → `ChatProvider` + `ChatSession`；`rebuildChatSession()` 切基座时 close 旧的、用 `snapshot()` 把历史搬到新的；`streamChatRequest()` collect `Flow<ChatDelta>`，`TextDelta` 累积并 emit（带 `isStreaming = true`），`Completed` 后才走翻译 + VITS |
| `utils/ChatHistoryStore.kt`（新，替掉 `AssistantMessageManager`） | Room ↔ core `ChatMessage`，`HISTORY_LIMIT = 200` |
| `utils/ChatErrorMessages.kt`（新） | `ChatError` 各子类 + `NotImplementedError` → string 资源 |
| `ui/common/ChatDialogContentUIState.kt` | 加 `isStreaming` |
| `ui/chat/ChatFragment.kt` | toast 直接用 `errorMsg`（已经翻译过了）；「内容为空」的兜底判断排除 `isStreaming` |
| `ui/setting/SettingContent.kt` | 新增 `ChatProviderSection`：列 `ChatProviderFactory.descriptors()` 供选择，未实现的标注出来；key / baseUrl / model 三个输入包在 `key(selectedId)` 里（`SettingEditText` 内部是 `rememberSaveable`，不加 key 换基座会显示上一个的值） |
| `settings.gradle` / `app/build.gradle` | `:ChatGPT` → `:ChatCore` |

`Log` 模块（Room 实体）**当轮没动**——它存的是纯文本 + token 数，和 provider 无关。
（后续为了接多模态做了一次地基重构，见 [chat-storage.md](chat-storage.md)。）

**顺手修掉的一个现存 bug**：老 `AssistantMessageManager.getSendAssistantList()` 过滤的是
`!it.sendFromMe`，也就是只把**模型自己说过的话**回传给模型，用户说的全丢了——
模型看到的是一段独白而不是对话。`ChatHistoryStore.load()` 现在两个 role 都返回。

## 施工清单

| # | 项 | 状态 |
|---|---|---|
| 1 | `ChatCore` 骨架：core 类型 + `ChatSession` + `ContextBudget` + SSE/HTTP | ✅ 完成 |
| 2 | `OpenAICompatProvider`（行为等价替换现状） | ✅ 完成 |
| 3 | `OpenAIResponsesProvider` | ✅ 完成 |
| 4 | app 层接线 + Setting UI + 配置迁移 | ✅ 完成 |
| 5 | 删除老 `ChatGPT` 模块 | ✅ 完成 |
| 6 | Anthropic / Gemini / Local 三个 stub + 映射注释 | ✅ 完成 |
| 7 | 本文档 + CLAUDE.md 订正 | ✅ 完成 |

验证：`:ChatCore:compileDebugKotlin` ✅、`:app:compileDebugKotlin` ✅、
`./gradlew assembleDebug` ✅（269 tasks，含 CMake native）。
**实机端到端联调本轮没做**——app 整体准备重写，先把接口层和文档立住。改动尚未提交。

## 待办

按优先级：

1. **实机联调**。免密钥的路子最省事：本地 `ollama serve`，Base Url 填 `http://10.0.2.2:11434/`，
   选 OpenAI 兼容基座。要验的点：流式气泡逐字出现 → `Completed` 后翻译 + VITS 出声 →
   Live2D 口型；连问三轮带指代的问题（验证 user 消息不再被丢弃）；填错 key 应弹 `Auth`
   而不是原始 JSON；切 provider 后历史不断
2. **补 `AnthropicMessagesProvider`**。三个 stub 里它最值得先做（KDoc 里映射已经写全了）
3. **`ChatSession.send()` 收附件**。存储层这一半已经做完（见
   [chat-storage.md](chat-storage.md)：附件表、`AttachmentStore`、按模型算的能力 gate、
   `upload()`），缺的是 `send()` 的签名还只收 `String`，以及聊天页的附件入口
4. **流式分句提前合成**。现在 VITS 要等完整文本，`Completed` 之后才出声；
   按句切分可以让首句提前几秒
5. **`reasoning` 档位进设置页**。`capabilities.reasoningLevels` 已经能 gate，UI 还没有
6. `Gemini` / `LocalLlm` 两个 stub

明确**不在**范围内：服务端会话（`previous_response_id` / `previous_interaction_id`）——
Anthropic 没有这东西，客户端历史是必须的，服务端会话只能是纯优化；
tool calling 的 UI 编排（接口层已经有 `ToolSpec` / `ToolCall` / `ToolResult`，但没有执行器）。
