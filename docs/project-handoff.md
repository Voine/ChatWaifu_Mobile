# ChatWaifu Mobile：项目架构、当前进展与新会话交接

> **用途**：这是新 Copilot / AI 会话的首要入口。先读本文，再按任务类型阅读第 12 节列出的
> 专项文档。本文记录项目背景、稳定架构、当前工作区状态、已经验证的能力和下一步边界，
> 避免依赖历史对话。
>
> **更新时间**：2026-09-13  
> **当前分支**：`feature/v2.0.0`  
> **记录时 HEAD**：`93f866c`  
> **重要**：工作区包含尚未提交的 Phase 2.5 Provider 与 Phase 2.6 Character 改造，不要 reset、checkout
> 或覆盖这些修改。开始新任务前先执行 `git status --short`。

新 Agent 的 30 秒结论：

- 当前可继续工作的最新节点是 **Phase 2.6 完成、Phase 3 未授权**。
- 正式产品入口仍是 `LoginActivity → ChatActivity → Fragment Navigation`；新版 Companion
  真实链路仍主要通过 debug-only `CompanionDemoActivity` 验证，不能误判为正式入口已经替换。
- Provider 与 Character 两套新边界都已经存在，必须在现有结构上收敛，禁止再建平行抽象。
- 工作区未提交；先审阅 diff，再决定是否继续施工或提交，绝不能从旧 HEAD 重新实现。

## 1. 项目是什么

ChatWaifu Mobile 是 Android 版「AI 纸片人聊天器」：

```text
用户文字
  → 云端或局域网 LLM 流式回复
  → 可选翻译为日文
  → 本地 Bert-VITS2 + MNN 合成语音
  → AudioTrack 播放
  → Live2D 角色口型与动作
```

语音输入使用本地 Sherpa-ncnn ASR，并运行在独立 `:sherpa` 进程。当前开发主线是在保留
原有聊天、Room、TTS、Live2D 能力的基础上，把前台重构为以角色为视觉主体的 Companion UI。

项目初版写于 2023 年，2023 年后长期停更，2026 年恢复迭代。很多旧代码仍带有 Activity +
Fragment + Navigation 的混合结构，新的页面主体则使用 Jetpack Compose。

## 2. 当前状态摘要

| 范围 | 状态 | 说明 |
|---|---|---|
| 基础工程升级 | 已完成 | AGP 9.3.1、Gradle 9.6.1、Kotlin 2.3.21、KSP、版本目录 |
| ChatCore 基座抽象 | 已完成基础架构 | OpenAI Responses 与 OpenAI-compatible 已实现；Anthropic/Gemini/端内为 stub |
| Room 聊天存储 | 已完成代码接线 | Room 是历史权威来源；部分老库迁移仍未真机验证 |
| 媒体归一化管线 | 已完成代码 | UI 尚无附件入口，`ChatSession.send()` 仍主要接文本 |
| 记忆 | Phase 0 + 1 已完成 | L0 分块淘汰与 L2 事实层已落地；L1/L3 未做 |
| Companion Phase 1 | 已完成 | 四态 UI、隔离 Mock、Live2D 共用宿主 |
| Phase 1.5 | 已完成 | 输入条、底部操作、对白、历史玻璃层视觉优化 |
| Phase 2 | 已完成最小链路 | debug Companion 接入真实文本、Room、翻译、TTS |
| Phase 2.1 | 已完成 | debug Companion 流式 UI/Room 增量行、AudioTrack 精确完成通知 |
| Phase 2.5 | **当前未提交工作，已完成** | Provider 分类/配置/能力、模型设置页、连接测试、迁移与会话快照 |
| Phase 2.6 | **当前未提交工作，已完成** | CharacterPackage 稳定身份、角色管理/详情、选择迁移及切换隔离 |
| Phase 3 | 未开始且未授权 | Character Behavior、情绪/表情映射等均不应自行施工 |

当前工作区最后一次完整验证（Phase 2.5 + 2.6）：

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` 成功。
- 共 34 个 JVM 测试通过，`git diff --check` 通过。
- Phase 2.5：API 37 arm64 AVD 验证模型页、三类 Provider、密钥遮罩、Local Network
  空地址阻断、Embedded Local 占位和临时 OpenAI-compatible SSE 连接。
- Phase 2.6：同一 AVD 验证角色首页、详情、ATRI → Amadeus 切换、正式聊天 Live2D 更新、
  进程重启保持当前角色、当前角色重新进入对话，以及 debug Companion 恢复当前角色。
- 没有物理真机验证；没有商业 Cloud 成功请求；没有合法 Imported ZIP 的设备验收。

## 3. 技术基线

- Kotlin + Jetpack Compose，MVVM + LiveData / SharedFlow。
- Retrofit + Gson + OkHttp；Room + KSP。
- `compileSdk = 37`、`targetSdk = 36`、`minSdk = 29`。
- JDK 17 toolchain，Java/Kotlin 字节码目标 Java 11。
- 仅构建 `arm64-v8a`。
- AGP 9 built-in Kotlin：模块不要 apply `org.jetbrains.kotlin.android`，不要写
  `kotlinOptions {}`。
- 所有依赖及构建版本统一在 `gradle/libs.versions.toml`。
- 只申请 `RECORD_AUDIO` 与 `INTERNET`，没有存储权限；模型导入走 SAF 和应用专属目录。

Kotlin 暂停在 2.3.21：Room 依赖注解处理，而 KSP 尚无 2.4.x 对应版本。升级时必须同时检查
Kotlin、KSP、Room metadata 兼容性和 Coil 版本。

## 4. 模块结构与依赖方向

根工程包含 8 个 Gradle 模块：

| 模块 | namespace | 职责 |
|---|---|---|
| `app` | `com.chatwaifu.mobile` | 唯一 application；UI、ViewModel、流程编排、附件和模型管理 |
| `ChatCore` | `com.chatwaifu.chat` | Provider、统一消息、流式增量、ChatSession、上下文预算 |
| `Translate` | `com.chatwaifu.translate` | 翻译抽象与百度翻译 |
| `VITS` | `com.chatwaifu.vits` | BV2 AAR 门面、音频播放、TTS 编排 |
| `Live2D` | `com.chatwaifu.live2d` | Cubism Native 封装与渲染 |
| `Lipsync` | `com.chatwaifu.lipsync` | meta-lipSync Native 封装 |
| `Sherpa` | `com.k2fsa.sherpa.ncnn` | 本地 ASR，独立 `:sherpa` 进程与 AIDL |
| `Log` | `com.chatwaifu.log` | Room 聊天记录、附件引用、记忆事实 |

依赖方向是：

```text
app ──→ ChatCore
    ├─→ Translate
    ├─→ VITS
    ├─→ Live2D
    ├─→ Lipsync
    ├─→ Sherpa
    └─→ Log
```

其余 7 个模块之间没有横向依赖。不要为了复用方便破坏这条边界；跨模块映射留在 `app`。

仓库另有 submodule `Bert-VITS2-MNN`。它不参与主工程 Gradle 构建，只负责现场发布主工程
依赖的本地 AAR 到 `Bert-VITS2-MNN/build/repo`。

### 4.1 应用入口与页面结构

```text
LoginActivity (launcher)
  └─ ChatActivity
      └─ mobile_navigation.xml
          ├─ nav_channel_list   角色页 / 启动页
          ├─ nav_chat           正式聊天页
          ├─ nav_chat_log       历史
          ├─ nav_setting        Provider、翻译等设置
          └─ nav_model_manager  兼容保留的角色管理路由

debug only:
CompanionDemoActivity (第二个 launcher)
  └─ CompanionScreen + CompanionRealResponseDriver
```

`nav_channel_list` 与 `nav_model_manager` 当前复用 `ModelManagerContent` 和
`ModelManagerViewModel`。卡片点击只进入详情；非当前角色通过“设为当前角色”切换，当前角色
通过“进入对话”触发正式 Session/TTS 准备并导航聊天。UI 不持有 renderer 或 `ChatSession`。

### 4.2 `app` 源码责任地图

| 路径 | 责任与入口 | 不应承担 |
|---|---|---|
| `data/chat/` | Provider 配置、profile、当前 inference selection | 角色身份、聊天上下文 |
| `data/model/` | CharacterPackage、metadata、导入、当前角色选择 | Provider key/model、Room entity |
| `data/attachment/` | SAF 媒体探测、规范化、附件字节存储 | 消息历史、角色身份 |
| `data/memory/` | app 层记忆贡献与抽取编排 | Room 实体公开、Provider 历史 |
| `ui/companion/` | Companion 状态、覆盖层、共用 renderer host | 新建网络/TTS 实现 |
| `ui/modelmanager/` | “角色”列表、详情、导入/删除/选择 | 直接操作 JNI/renderer |
| `ui/setting/` | Provider/模型设置草稿、连接测试 | 持有或替换现有 ChatSession |
| `utils/ChatHistoryStore.kt` | ChatCore ↔ Log 映射、历史读写 | stable ID 迁移、UI 状态 |
| `ChatActivityViewModel.kt` | 正式入口会话、历史、翻译、TTS 编排 | Provider 具体 HTTP DTO |

核心装配点：

- `ModelProvider`：无 DI 框架下装配 Character repository/importer。
- `ChatProviderFactory`：根据已保存 selection 创建具体 Provider。
- `ChatSession`：唯一对话上下文持有者。
- `CharacterRendererHost`：Compose/Fragment 共用 Live2D 宿主生命周期适配器。

## 5. 核心聊天链路

正式旧入口的主循环位于：

`app/src/main/java/com/chatwaifu/mobile/ChatActivityViewModel.kt`

```text
fetchInput()
  → ChatHistoryStore.appendUser()
  → ChatHistoryStore.beginAssistant(STREAMING)
  → ChatSession.send()
      → ChatProvider.chatStream()
      → ChatDelta.TextDelta 增量
  → 正式入口逐段更新 UI；debug Companion 同时增量更新 Room 同一 assistant 行
  → ChatDelta.Completed
  → ChatHistoryStore.finishAssistant(OK)
  → 可选翻译
  → SoundGenerateHelper.generateAndPlay()
  → AudioTrack 真实播放完成
  → Idle
```

失败或取消时，Room 中的 `STREAMING` 行必须收尾为 `FAILED`，不能遗留永久流式记录。
Room 是历史状态的权威来源；UI 内存列表只负责当前展示。需要注意：debug Companion 已对每个
TextDelta 调 `updateStreamingAssistant()`；正式 `ChatActivityViewModel` 当前仍只在 begin /
finish / fail 时写 Room，流中片段仅在 UI 内存中。这是后续正式入口收敛时要补齐的差异，
不要误写文档或再建第二套网络流。

### 5.1 ChatCore 不变量

- 业务只认 `ChatMessage`、`ChatDelta`、`ChatError`。
- `ChatProvider.chatStream(request)` 是唯一抽象网络入口。
- 非流式 `chat()` 只是收集同一个 Flow，不存在第二套 HTTP 实现。
- **ChatSession 是对话上下文的唯一持有者**，Provider 不保存历史。
- `ChatSession` 收到 `Completed` 后才把完整 assistant 消息加入历史。
- `ContextBudget.trim()` 返回的 `startIndex` 必须保存并传入下一轮，维持缓存前缀粘性。

## 6. Phase 2.5 Provider 架构

当前 Provider 数据流：

```text
ProviderDescriptor
  ├─ ProviderCategory
  ├─ ProviderCapabilities
  ├─ settingFields
  └─ ModelDescriptor / ModelInfo

ProviderProfile (app 层 sealed interface)
  ├─ CloudProviderProfile
  ├─ LocalNetworkProviderProfile
  └─ EmbeddedLocalProviderProfile
          ↓
InferenceSelection
  ├─ ProviderId
  ├─ ProviderConfig
  └─ ChatOptions
          ↓
ChatProviderFactory.create()
          ↓
ChatSession
```

关键文件：

- `ChatCore/.../core/ProviderDescriptor.kt`
- `ChatCore/.../core/ProviderCapabilities.kt`
- `ChatCore/.../core/ProviderConfig.kt`
- `ChatCore/.../ChatProviderFactory.kt`
- `app/.../data/chat/ProviderProfile.kt`
- `app/.../data/chat/ChatProviderSettings.kt`
- `app/.../ui/setting/ModelSettingsState.kt`
- `app/.../ui/setting/ModelSettingsSection.kt`

三类产品语义：

| 类型 | 当前实现 |
|---|---|
| Cloud | OpenAI Responses、OpenAI-compatible；Anthropic/Gemini 仍为 stub |
| Local Network | 独立 `ProviderId.LOCAL_NETWORK`，协议复用 `OpenAICompatProvider` |
| Embedded Local | 配置占位与禁用 UI；`LocalLlmProvider` 仍为 stub |

**安全不变量**：Local Network 必须有非空 endpoint。UI 禁止激活空地址，Factory 也会失败关闭；
绝不能让空地址回落到公网 OpenAI 默认 endpoint。

### 6.1 当前模型切换语义

- 设置页持有编辑草稿，不持有 `ChatSession`。
- 点击“设为当前模型”只改变待保存选择。
- Save 后调用 `refreshAllKeys(rebuildSession = false)`。
- 已创建 Session 继续使用创建时的 Provider、模型和 `ChatOptions` 快照。
- 下一次新建 Session 才读取最新保存配置。
- Room 落库的 provider/model 元数据也使用 Session 快照，不能实时读取 SharedPreferences，
  否则旧 Session 的回复会被错误标记为新模型。

### 6.2 配置存储与迁移

- `ChatProviderSettings` 是 Provider 配置的唯一持久化入口。
- SharedPreferences key 以 Provider ID 分区，Cloud 和 Local Network 不会互相覆盖。
- 旧 `saved_chat_key` 与代理 URL 迁移到 OpenAI-compatible profile。
- 当前迁移版本为 2；只补不存在的目标值，原子 commit，可重复执行。
- API key/auth 在 UI 默认遮罩，不进入 `toString()` 或 Gson 调试序列化输出。
- 本阶段没有引入加密依赖，SharedPreferences 静态存储仍是已知限制。

### 6.3 连接测试

连接测试使用设置页当前未保存草稿：

- 创建临时 Provider，发送最多 8 token 的无状态请求。
- 不创建 `ChatSession`，不写 Room，不改变当前模型。
- 完成后关闭临时 Provider。
- 支持成功、网络/超时、鉴权、模型不存在、endpoint 不兼容、未知错误。
- Embedded Local 明确显示未接入。
- Provider/草稿变化后，旧请求结果不得覆盖当前编辑状态。

## 7. Companion UI 状态与接线

Companion UI 的详细施工记录在 `docs/companion-ui-construction-plan.md` 第 10、12 节。
该文档开头的“尚未实施”是最初历史快照，**实际进度以第 10、12 节为准**。

当前支持：

- `Idle`
- `InputExpanded`
- `Thinking`
- `Speaking`
- `HistoryOpen`
- `Error`

Phase 1/1.5 使用 debug-only Companion Demo 和共用 Live2D 宿主完成视觉与交互。
Phase 2/2.1 通过 `CompanionRealResponseDriver` 接入真实 Provider、ChatSession、Room、
翻译和 TTS，但仍是 debug Companion adapter，不代表正式入口已经完全迁移。

正式入口和 debug Companion 的共享边界是 Provider、ChatSession、ChatHistoryStore、翻译、
SoundGenerateHelper、LipsValueHandler 与 CharacterRepository；`CompanionRealResponseDriver`
只是 adapter，不应演化为第二套业务核心。未来 P2-C 应收敛 runtime，而不是复制实现。

流式阶段：

```text
Thinking
  → 首个 TextDelta：utterance 与同一 History/Room STREAMING 行增量更新
  → Completed：写 Room OK
  → Speaking：完整文本进入 TTS
  → AudioTrack marker 真正到达最后播放帧
  → Idle
```

TTS 不逐 token 合成。页面退出、切角色或取消会使 playback ID 失效，旧 AudioTrack 回调不能
影响新一轮。

## 8. 存储、媒体与记忆

### 8.1 聊天存储

- `Log` 只公开 `ChatLogEntry`、`AttachmentRef`、`ChatLogRepository`。
- Room entity 全部 internal，不跨模块。
- 数据库迁移手写，没有 destructive fallback。
- 枚举按 name 存储，不存 ordinal。
- 附件 DB 行使用 FK CASCADE；附件文件需要 app 层独立回收。

详见 `docs/chat-storage.md`。

### 8.2 媒体管线

入口为 `AttachmentProvider.ingest(context)`：

```text
MediaProbe
  → Image/Audio/Video/Doc Normalizer
  → AttachmentStore.adopt()
```

体积闸门必须在读取字节前；透明图不能无条件 JPEG；视频只有派生帧/音轨进入模型上下文。
当前 UI 没有附件按钮，真实多模态发送仍未完整接线。

详见 `docs/media-pipeline.md`。

### 8.3 记忆

- L0：`ContextBudget.trim()` 分块淘汰，`EVICT_CHUNK = 20`。
- L2：`memory_fact` 表，按 `(characterId, slot)` 唯一 upsert。
- 事实按角色隔离。
- 抽取模型可单独配置，空值跟随主模型。
- L3 将来使用 `LIKE`，不上 FTS。
- L1 情节摘要和 L3 检索尚未实现。

详见 `docs/memory.md`。

## 9. Native、TTS、Live2D 与 ASR

### 9.1 Bert-VITS2 AAR

首次 clone 或 submodule commit 变化后执行：

```bash
git submodule update --init --recursive
git lfs install
git -C Bert-VITS2-MNN lfs pull --include="bertvits2-jni/src/main/assets/bv2_model/jp/*,\
bertvits2-jni/src/main/assets/bert/jp/*,\
openjtalk/src/main/assets/open_jtalk_dic_utf_8-1.11/*"
cd Bert-VITS2-MNN
./gradlew publishAars \
  -PpublishGroupId=com.chatwaifu.bv2 \
  -PpublishVersion=2.0.0-a45fd76
```

版本必须与 `gradle/libs.versions.toml` 的 `bertvits2` 一致。只拉日文链路权重；不要改用会遍历
全部语种配置的 `BertVITS2SimpleInferImpl`。

### 9.2 采样率与播放完成

- BV2 采样率运行时决定，不能写死 22050。
- `SoundPlayHandler` 根据采样率重建 AudioTrack。
- Speaking → Idle 依赖 AudioTrack marker 与 playback head，不使用 PCM 时长估算或轮询。
- `LipsValueHandler` 当前默认 `USE_REAL_LIP_SYNC = false`，使用按真实采样率估时的假动画。

### 9.3 Live2D

不要在普通 UI/Provider 任务中修改 renderer、JNI 或 C++。Compose 覆盖层与
`CharacterRenderLayer` 分离，IME 只推动输入层，不应改变 SurfaceView 根尺寸。

### 9.4 Sherpa ASR

Sherpa 在独立 `:sherpa` 进程，通过 AIDL 绑定。当前 Companion Phase 2 没有接 ASR。

## 10. 角色模型布局

内置和导入模型统一位于应用专属目录：

```text
<getExternalFilesDir("models")>/
  <角色名>/
    meta.json
    live2d/
    vits/                 # 导入角色可选
  _bv2/
    bv2_model/<lang>/
    bert/<lang>/
```

- `CharacterRepository` 是角色数据唯一来源。
- `CharacterPackage.id` 是产品稳定身份；`storageKey` 仅兼容 Room、Memory、legacy persona/
  touch 和现有磁盘目录。本阶段禁止混用，也没有迁移 Room/Memory identity。
- Builtin ID 为确定性 `builtin:<key>`；Imported ID 为 `imported:<uuid>`，老 metadata
  首次读取时补 ID 并原子回写。
- `CurrentCharacterSelectionStore` 持久化稳定 ID，可从旧 `SAVED_CHAT_NAME` 迁移，并在资源
  丢失时回退到可用 Builtin。
- `ModelStorage` 管目录和 `meta.json`。
- `BuiltInModelInstaller` 解包 assets。
- `ModelProvider` 是无 DI 框架下的单例装配点。
- 内置角色共享 `_bv2` 声学模型；BERT 始终共享。
- 修改内置 assets 时必须提升 `Constant.BUILT_IN_MODEL_VERSION`。

角色数据流：

```text
assets / imported ZIP
  → BuiltInModelInstaller / ZipModelImporter
  → ModelStorage + meta.json
  → CharacterRepository
  ├─→ ModelManagerViewModel → 角色列表 / 详情
  ├─→ CurrentCharacterSelectionStore → stable id
  ├─→ ChatActivityViewModel → Session / TTS / renderer
  └─→ CompanionDemoActivity → debug Companion
```

角色切换会创建新的 `ChatSession`，不复制旧角色历史，并显式读取新角色 persona；无 persona
时传 empty/default，不保留上一角色 prompt。generation token 隔离迟到的历史、流式 UI 和
TTS 结果，TTS 初始化/推理共用 Mutex。`ChatHistoryStore` 写入始终显式携带本轮 storageKey，
防止旧请求收尾写进新角色分区。

## 11. 当前工作区的未提交 Phase 2.5–2.6 文件

不要把本文的文件清单当成 `git status` 的替代品；工作区仍在变化，命令输出才是事实来源。
当前未提交修改按责任可分为：

| 范围 | 代表文件 |
|---|---|
| Phase 2.5 Provider core | `ProviderDescriptor.kt`、`ProviderConfig.kt`、`ChatProviderFactory.kt` |
| Phase 2.5 app settings | `ProviderProfile.kt`、`ChatProviderSettings.kt`、`ModelSettings*.kt` |
| Phase 2.6 Character domain | `CharacterModel.kt`、`ModelStorage.kt`、`CharacterRepository*` |
| Phase 2.6 selection/race | `CurrentCharacterSelectionStore.kt`、`CharacterSwitchGuard.kt`、`ChatActivityViewModel.kt` |
| Phase 2.6 UI | `ChannelListFragment.kt`、`ModelManagerContent.kt`、`ModelManagerViewModel.kt` |
| 共用链路调整 | `ChatHistoryStore.kt`、Companion adapter/activity、Chat/Memory/History 调用点 |
| 测试与交接 | `app/src/test/...`、README、本文、Companion 施工文档 |

两阶段都已经构建、测试和模拟器验收，但尚未提交。后续 Agent 必须保留现有修改，不要从
`93f866c` 重新实现。若任务只涉及其中一层，也不要顺手回退另一层。

## 12. 文档导航

| 文档 | 开始任务前何时必读 |
|---|---|
| `docs/project-handoff.md` | 所有新会话首先阅读 |
| `docs/companion-ui-construction-plan.md` | Companion UI、Phase 1–2.6 状态与设备证据 |
| `docs/chat-core.md` | Provider、ChatSession、API 映射、上下文 |
| `docs/chat-storage.md` | Room、附件引用、删除/迁移 |
| `docs/media-pipeline.md` | 图像/音频/视频/PDF 归一化 |
| `docs/memory.md` | L0/L2 记忆及后续 L1/L3 |
| `docs/v2-base-upgrade.md` | 工具链、依赖和基础升级历史 |

## 13. 构建与验证

常用命令：

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:dependencies
```

Live2D/Lipsync Native 由 externalNativeBuild 自动构建，需要 NDK 与 CMake。TTS `.so` 来自
BV2 AAR。不要通过删除 BV2 依赖来绕过本地 AAR 缺失。

设备验证必须使用 arm64 设备或 arm64 AVD。报告要区分：

- 已实现
- 自动化/构建已验证
- 模拟器已验证
- 物理真机仍未验证

## 14. 已知风险与未完成事项

- Anthropic、Gemini、Embedded Local Provider 仍是 stub。
- SharedPreferences 中的凭据没有静态加密；当前只保证 UI/日志/调试输出不泄漏。
- ChatCore、Room 多次迁移和 BV2 整链路仍缺充分物理真机覆盖。
- 正式入口仍是旧 Activity/Fragment 混合架构；Companion 真实链路主要在 debug adapter。
- Companion 未接 ASR、附件、Vision、后台陪伴、行为编排或情绪映射。
- Room/Memory 的角色分区仍使用 legacy storageKey；稳定 Character ID 的全量数据迁移未做。
- Imported preview 尚无 ZIP 约定；当前角色页使用已有内置头像或统一 fallback。
- 正式 `ChatActivityViewModel` 尚未像 debug Companion 一样把每个 TextDelta 增量写回 Room。
- debug Companion 当前每个文本 delta 都会写 Room，长回复的写入频率未来可合并节流。
- `mainLoop()` 仍是 `while(true)` + continuation，取消和异常路径较脆。
- APK 体积约 350MB，模型和词典仍随包。
- Activity 锁定竖屏；Android 16 对固定方向的限制是已知技术债。
- 项目历史 lint warning 较多，不要把无关清理混入功能任务。

## 15. 后续新会话的最短启动流程

1. 阅读本文。
2. 执行 `git status --short`，确认 Phase 2.5–2.6 未提交修改是否仍在。
3. 根据任务阅读第 12 节对应专项文档。
4. 若继续 Companion，查看 `docs/companion-ui-construction-plan.md` 第 10、12 节最新记录。
5. 若继续 Provider，复用 `ProviderDescriptor → ProviderProfile → InferenceSelection →
   ChatProviderFactory → ChatSession`，不新建平行网络层。
6. 若开始 Embedded Local，接现有 `EmbeddedLocalProviderProfile` 与
   `LocalLlmProvider` stub；不要把 Local 简化成 URL，也不要改 ChatSession 历史职责。
7. 若继续角色系统，稳定身份只用 `CharacterPackage.id`；旧聊天/记忆/persona/touch 只用
   `storageKey`，除非另开有迁移方案的独立任务。
8. 修改前先跑最小编译；完成后执行相关测试、debug/release 构建，并明确设备证据。
9. 未经用户授权，不进入 Phase 3，不修改 Live2D renderer/JNI/C++，不扩展 ASR/TTS Provider。

接手任务时按领域守住以下不变量：

| 如果任务涉及 | 开工前确认 | 禁止顺手做 |
|---|---|---|
| Provider | `docs/chat-core.md` + 本文第 6 节 | 第二套 HTTP/Session、Local=URL |
| Character | 本文第 10 节 + P2.6 施工记录 | 混用 id/storageKey、Room identity 偷迁移 |
| Companion | 施工文档第 10、12 节 | 直接进入 Phase 3、复制业务 runtime |
| History/附件 | `docs/chat-storage.md`、`docs/media-pipeline.md` | destructive migration、绕过体积闸门 |
| Memory | `docs/memory.md` | 改成跨角色共享、擅自上 FTS |
| TTS/Live2D | 本文第 9 节 | 修改 renderer/JNI/C++ 或写死采样率 |
