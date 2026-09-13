# ChatWaifu_Mobile

Android 版「AI 纸片人聊天器」。LLM 出文本 → 翻译成日文 → 本地 Bert-VITS2 推理出语音 → 驱动 Live2D 模型口型与动作；语音输入走本地 Sherpa-ncnn ASR。

初版写于 2023 年初（GPT-3.5 刚发布），2023 年后基本停止维护，2026 年重新开始迭代。当前开发分支 `feature/v2.0.0`。

## Companion UI 施工入口

下一轮前台 UI 改造的跨机器交接文档是 **[docs/companion-ui-construction-plan.md](docs/companion-ui-construction-plan.md)**。
接手此任务时先完整阅读它和文中指定的设计包，再从第 10 节第一个未完成批次施工；
默认只做 Phase 1 的四态前台壳与隔离 Mock，不直接进入真实链路接线。
本文档记录环境准备、源码落点、验收标准和续接状态，不需要原 Copilot 对话。
当前是规划完成，尚未实施；实际进度以后以施工文档第 12 节为准。

## 技术栈

- Kotlin + Jetpack Compose（UI 全部 Compose，但外壳仍是 Activity + Fragment + Navigation 混合架构）
- MVVM + LiveData / SharedFlow，Retrofit + Gson 做网络，Room 做聊天记录持久化
- Native：Bert-VITS2 + MNN 语音合成（走 `Bert-VITS2-MNN` submodule 打的 AAR，本仓库自己不再有 TTS 的 cpp）、Live2D Cubism SDK(C++) 渲染、meta-lipSync 口型分析
- Sherpa-ncnn 语音识别跑在**独立进程** `:sherpa`，通过 AIDL 通信
- AGP 9.3.1 / Gradle 9.6.1 / Kotlin 2.3.21 / JDK 17 toolchain，全模块字节码目标 Java 11
- `compileSdk` = 37，`targetSdk` = 36，`minSdk` = 29（先为媒体管线从 24 抬到 28，
  理由见 `docs/media-pipeline.md` 第三节；再被 Bert-VITS2-MNN 顶到 29）
- **只出 `arm64-v8a`**：BV2 的 native 只有 arm64 一份。决定 APK 里有哪些 ABI 的是
  `app/build.gradle` 的 `defaultConfig.ndk.abiFilters`——库模块里的 `abiFilters` 只管自己
  CMake 的产物，管不到预置 jniLibs（Sherpa）和 AAR 带进来的 `.so`
- **走 AGP 9 的 built-in Kotlin**：模块不再 apply `org.jetbrains.kotlin.android`，也没有任何 `kotlinOptions {}`
- Room 的注解处理走 **KSP**（2.3.11），已从 kapt 迁走；`kotlin-kapt` 插件全工程不再使用
- 只申请 `RECORD_AUDIO` + `INTERNET`。**没有任何存储权限**，模型走应用专属目录 + SAF 导入

## 模块结构

根工程 `ChatWaifu_Mobile`，8 个模块（见 `settings.gradle`）：

| 模块 | namespace | 职责 |
|---|---|---|
| `app` | `com.chatwaifu.mobile` | 唯一 application 模块，UI + 编排全流程 |
| `ChatCore` | `com.chatwaifu.chat` | 聊天基座抽象层，多 provider（见 `docs/chat-core.md`） |
| `Translate` | `com.chatwaifu.translate` | 翻译抽象 `ITranslate` + 百度翻译实现 |
| `VITS` | `com.chatwaifu.vits` | TTS 门面：包 BV2 的 AAR + 音频播放 + 文件/权限工具（模块名沿用，Bert-VITS2 本身就是 VITS） |
| `Live2D` | `com.chatwaifu.live2d` | Live2D Cubism SDK Native 封装（含 CMake + SDKRoot） |
| `Lipsync` | `com.chatwaifu.lipsync` | meta-lipSync Native 封装（含 CMake） |
| `Sherpa` | `com.k2fsa.sherpa.ncnn` | Sherpa-ncnn ASR，跑在 `:sherpa` 进程，AIDL 暴露 |
| `Log` | `com.chatwaifu.log` | Room 数据库，聊天记录 + 附件引用读写（见 `docs/chat-storage.md`） |

依赖方向：`app` → 其余 7 个模块，模块之间**无横向依赖**。

外加一个 submodule `Bert-VITS2-MNN`（`git@github.com:Voine/Bert-VITS2-MNN.git`，钉在 `master`），
不参与 ChatWaifu 的 Gradle 构建，见下面「端内 TTS」一节。

## 核心链路

### 主循环
`ChatActivity` 启动后调用 `ChatActivityViewModel.mainLoop()`（`app/src/main/java/com/chatwaifu/mobile/ChatActivityViewModel.kt`），是一个跑在 `Dispatchers.IO` 上的 `while(true)`，每轮：

1. `fetchInput()` — 用 `suspendCancellableCoroutine` 挂起，等 UI 层调 `sendMessage()` 唤醒（`inputFunc` 回调持有 continuation）
2. `ChatHistoryStore.appendUser()` 写入用户消息（Room）
3. `beginAssistant()` 先插一行 `STREAMING` 占位，拿到 messageId
4. `streamChatRequest()` → `ChatSession.send()`，collect `Flow<ChatDelta>`；`TextDelta` 逐字累积后 emit 到 `_chatContentUIFlow` 渲染气泡（`isStreaming = true`）。结束时 `finishAssistant()` 把占位行补成最终文本 + usage + thinking + `OK`；失败或断流走 `failAssistant()` 标 `FAILED` 并保留片段
5. `fetchTranslateIfNeed()` — 默认把回复翻成日文（内置模型都是日语声库）
6. `generateAndPlaySound()` → `SoundGenerateHelper.generateAndPlay()`，BV2 **按句**推理出的 `FloatArray` 一路给 `SoundPlayHandler` 播放、一路连同**采样率**一起 `forwardResult` 给 `LipsValueHandler` 驱动口型

`ChatStatus` 枚举（`DEFAULT/FETCH_INPUT/SEND_REQUEST/TRANSLATE/GENERATE_SOUND`）通过 `chatStatusLiveData` 给 UI 显示当前阶段。

### 聊天基座（ChatCore）
业务只认 `ChatMessage` / `ChatDelta`，不认某一家的 `choices[0]`。核心是一个抽象方法
`ChatProvider.chatStream(request): Flow<ChatDelta>`；历史**统一由客户端的 `ChatSession` 持有**
（因为 Anthropic 没有服务端会话，必须重发全量）。已落地 `OpenAICompatProvider`（覆盖代理 /
DeepSeek / Ollama / llama.cpp / vLLM）和 `OpenAIResponsesProvider`；Anthropic / Gemini / 端内
三个是 stub，`chatStream` 抛 `NotImplementedError`，**映射方案写在各自类 KDoc 里**。

设计意图、三家 API 映射表、扩展新 provider 的步骤、进度与待办全在 **`docs/chat-core.md`**，
动这一层之前先看它。

### 聊天存储（Log + 附件）
`Log` 模块只暴露三个 public 类型：`ChatLogEntry`（领域类型）、`AttachmentRef`、
`ChatLogRepository`（接口，suspend + Flow，**无默认实现**）。`room/` 那一包全部 `internal` ——
Room 实体不跨模块边界；`ChatLogEntry` 也刻意不是 `ChatCore` 的 `ChatMessage`
（`Log` 不依赖 `ChatCore`），映射由 app 侧的 `ChatHistoryStore` 做。

数据库 `version = 3`，两张表 `chat_message` / `chat_attachment`（FK CASCADE），
`exportSchema = true`，迁移**手写**在 `room/Migrations.kt`，
**刻意没有 `fallbackToDestructiveMigration()`**（聊天记录是用户资产，迁移错了应该崩）。
枚举列存 `name` 不存 ordinal。

附件的**字节**不在 `Log` 里，在 app 的 `data/attachment/AttachmentStore.kt`：
落 `<getExternalFilesDir("attachments")>/<characterId>/<uuid>.<ext>`，
DB 里只存**相对路径**。落盘那份就是模型看到的东西，历史重放要用它。
文件回收是独立的（CASCADE 只清行）：删角色先取路径再删记录再删文件，
启动时另跑一次全库孤儿 GC + `clearTemp()`。

**`AttachmentStore` 只管字节，不懂格式**——格式归一化是独立一层，见下。

`characterId` 目前传的是**角色名**（角色层还没有稳定 uuid），存储层只当它是不透明键——
这是一道有文档的缝，见 `docs/chat-storage.md` 第六节。

设计取舍（为什么继续用 Room、改造前发现的三个 bug、
按模型算的能力 gate、`upload()` 预上传）全在 **`docs/chat-storage.md`**。

### 媒体管线（格式兼容层）
`app/data/attachment/` 里除 `AttachmentStore` 之外的那一堆，负责把用户选中的任意媒体
变成「能发给任何一家基座」的规范形态。入口是 `AttachmentProvider.ingest(context)`：

```
MediaProbe → MediaNormalizer(Image/Audio/Video/Doc) → AttachmentStore.adopt()
```

四条规范形态：图像长边 ≤1568（不透明 JPEG / 带 alpha 走 PNG→WebP）；
音频 **16kHz 单声道 WAV**（mp3 直通）；视频**不转码，抽帧 + 抽音轨**；DOC 只收 PDF。

三条纪律：
- **体积闸门必须在读字节之前**（`OpenableColumns.SIZE`）。整条管线没有 `readBytes()`，
  产物是临时文件 + `renameTo` 接管
- **`alpha` 决定编码格式**。无条件 JPEG 会让透明区域变黑
- **只有派生行进上下文**。视频落「父行 + N 帧 + 音轨」，父行只给 UI 回放，
  规则在 `ChatHistoryStore.modelVisibleAttachments()`

选库依据（为什么是 Coil 3 + `media3-common`，为什么否掉 ffmpeg-kit 和
`media3-transformer`）、各家能收什么的对照表、修掉的三个洞、v4 schema，
全在 **`docs/media-pipeline.md`**，动这一层之前先看它。

### 端内 TTS（Bert-VITS2 + MNN）

2026/9 从 2023 年那套 VITS-ncnn 换过来的。老实现（自己写的 Kotlin 音素化 +
`vitsncnn_jni.cpp` 里的 ncnn 前向 + 302MB `.ncnn.bin` 权重）已整体删除。

**AAR 不入库，从 submodule 现场产出**。首次 clone 或换 submodule commit 后要跑一次：

```bash
git submodule update --init --recursive
git lfs install
git -C Bert-VITS2-MNN lfs pull --include="bertvits2-jni/src/main/assets/bv2_model/jp/*,\
bertvits2-jni/src/main/assets/bert/jp/*,\
openjtalk/src/main/assets/open_jtalk_dic_utf_8-1.11/*"
cd Bert-VITS2-MNN && ./gradlew publishAars \
    -PpublishGroupId=com.chatwaifu.bv2 -PpublishVersion=2.0.0-a45fd76
```

产物落在 `Bert-VITS2-MNN/build/repo`，`settings.gradle` 里已经把它加成第一个 maven 源。
版本号在 `libs.versions.toml` 的 `bertvits2`，格式是 `<上游 versionNameExt>-<submodule commit>`，
**换 commit 时它和 `-PpublishVersion` 要一起改**。

**只拉了日文链路的 LFS 权重**（约 190MB）。zh / en / mix 在 submodule 里仍是 130 字节的
LFS 指针文件，会被打进 AAR 但体积可忽略。代价是 **`BertVITS2SimpleInferImpl` 不能用**——
它启动时要遍历全部四个语种的 config.json，会在解析指针文件时挂掉。
我们走的是 `IBertVITS2FullInfer`（`setBertVITS2ModelPath` 收绝对路径），不碰那条路。

**两处硬编码路径**（BV2 的 AAR 里写死的，不是我们能选的）：
- `text-preprocess` 的 `JPBV2Impl` 读 `filesDir/bert/jp/vocab.txt` → 由 `Bv2ModelInstaller` 装
- openjtalk 词典从 **APK assets** 直读 `open_jtalk_dic_utf_8-1.11`（98MB，openjtalk AAR 自带）

好消息是 BV2 四个语种的预处理全是 `by lazy`，日文链路不碰 zh 的 jieba 词典，
所以 `initPreprocessor()`（拷 17MB zh/en 词典）**只在非日文语种才调**。

**采样率是运行时才知道的**（日文底模 44100，老内置模型 22050）。两处以前写死 22050 的地方
已经改成跟着模型走：`SoundPlayHandler` 现在会按需重建 `AudioTrack`（以前 `setTrackData()`
只改字段不重建，等于没生效），`LipsValueHandler.playDefaultAnimation` 用实际采样率算动画时长。

### 记忆分层

设计和落地记录在 **`docs/memory.md`**，动这一层之前先看它。
**Phase 0 + 1 已落地，真机未验证**；L1 情节摘要和 L3 检索还没做。

两件事：

**L0 分块淘汰**（`ContextBudget.trim`）。改造前每轮从头部掉一条，prompt 前缀每轮都变，
各家的缓存都是按前缀匹配的，命中率基本是 0。现在起点粘性、超预算时按
`EVICT_CHUNK = 20` 整块推，换来连续 20 轮前缀不变。`trim` 返回 `TrimResult`，
**调用方必须把 `startIndex` 存下来下轮传回**，否则退化回老行为。

**L2 事实层**。`memory_fact` 表（DB v5），`(characterId, slot)` 上的 UNIQUE 索引
让 upsert 成为**数据库层强制**的——没有它事实库会长出「所在城市=北京」和
「所在城市=上海」两行并存。外键是 `SET NULL` 不是 `CASCADE`（记忆是从消息派生的
独立事实，删聊天记录不该连带删记忆，和附件表刻意相反）。抽取跑在 TTS 播报窗口，
和推理并发，不占首字延迟。注入走 `MemoryContributor`，`ChatCore` 仍然不碰 Room。

三条拍过板的决策不要再当开放问题：**记忆按角色隔离不共享**、
抽取走接口且模型可单独配置（`SAVED_MEMORY_MODEL`，空 = 跟随主模型）、
L3 检索用 `LIKE` 不上 FTS。

### 口型同步
`LipsValueHandler` 里 `USE_REAL_LIP_SYNC = false` —— meta-lipSync 的真实 viseme 映射因为时长对齐问题效果不好，**当前默认只播一个 0→1→0 的循环假动画**（`playDefaultAnimation`），时长按音频采样数估算。真实逻辑代码还在，改常量即可启用。

### 语音识别
`ChatFragmentViewModel` 用 `bindService` 绑 `SherpaService`（`:sherpa` 进程），拿 `ISherpaAidlInterface`。按住说话 → `startRecord()`，松手 → `finishRecord(callback)` 回传识别文本。多个 ncnn 库像是进程独享资源，所以才拆进程。

### 模型存取（`app/.../data/model/`）

内置模型和用户导入模型走**同一套磁盘布局**，落在应用专属外部目录：

```
<getExternalFilesDir("models")>/
  <角色名>/
    meta.json
    live2d/    xxx.model3.json, xxx.moc3, ...
    vits/      config.json, *.mnn     ← 可选；导入角色自带的声学模型
  _bv2/                               ← BV2 共享声库，内置角色都指向这里
    bv2_model/<lang>/  config.json + 6 个 *.mnn
    bert/<lang>/       BERT *.mnn
```

**声库为什么要拆共享**：BV2 是单模型多 speaker（config.json 的 `spk2id`），三个内置角色
共用同一份权重、只差一个 speaker id，按角色各拷一份的话磁盘上会躺三份 90MB。
`ModelMeta.sharedVoice` 标记走哪条路，`Bv2ModelInstaller` 负责装共享那份并分配 speaker。
**BERT 永远是共享的**（它是「一个语种的编码器」随包走），所以导入模型只需自带声学模型，
不用背一份 40MB 的 BERT——`CharacterModel` 里 `vitsDir` 和 `bertDir` 因此是两个字段。

日文底模的 `spk2id` 是 `{八重神子_JP:0, 宵宫_JP:1, 椿_JP:2, 野兽先辈_JP:3}`，
按角色在 assets 里的顺序轮着分。**皮套和声音是对不上的**（BV2 的日文角色和
Yuuka/Amadeus/ATRI 没关系），等炼了自己的模型再替换 `_bv2/bv2_model/jp/` 那 6 个 `.mnn`。

- `CharacterRepository`（接口）是角色数据的唯一来源，调用方只看 `CharacterModel`，不关心是内置还是导入
- `ModelImporter`（接口）负责从 zip 导入，`Flow<ImportProgress>` 发进度
- `ModelStorage` 管布局和 `meta.json`；`BuiltInModelInstaller` 把 assets 解出来（assets 实际在 `Live2D` / `VITS` 两个库模块里）
- `ModelProvider` 是没有 DI 框架下的单例入口，接 Hilt 时只改这一个文件

**`meta.json` 干掉了两个老的隐式约定**：live2d 和 vits 目录名不必再一致；`model3.json` 也不必和角色名同名（入口文件名在导入时解析后写进 meta）。

**为什么必须「导入」而不能直接读用户目录**：`VITS/src/main/cpp/vitsncnn_jni.cpp` 里 ncnn 是按目录 fopen 那些 `.bin` 的，`content://` URI 喂不进去，所以 SAF 选中的包必须先解压落地成真实路径。Live2D 反而无所谓——它的文件读取全部汇聚到 `JniBridgeJava.LoadFile(String)` 一个 Java 方法。

内置模型的解包有**版本 gate**（`Constant.BUILT_IN_MODEL_VERSION` ↔ `SAVED_BUILT_IN_MODEL_VERSION`），改动 assets 里的内置模型时要手动 +1，否则新资源不会被解出来。

### 页面
`ChatActivity` 单 Activity + `mobile_navigation.xml` 五个 Fragment（每个 Fragment 内部是 ComposeView）：
`nav_channel_list`（启动页，选角色）、`nav_chat`、`nav_chat_log`、`nav_setting`、`nav_model_manager`。
`LoginActivity` 是 launcher，填 OpenAI Key / 百度翻译 appid+key。

气泡里的富文本走 `ui/common/MessageFormatter.kt`（markdown-lite：`@提及`、URL、`*粗*`、`_斜_`、`~删除~`、`` `code` ``）。
它产出的 `AnnotatedString` 已经把可点击区域以 **`LinkAnnotation` 内嵌**（`Url` 交给 `LocalUriHandler`，
`Clickable` 走传入的 `authorClicked`），所以渲染端直接用 `Text` 就行，不要再用已废弃的
`ClickableText` + `getStringAnnotations` 手工命中测试。

角色的**人物设定和 speaker id 是按角色存的**：内置三个沿用 `SAVED_*_SETTING` 老 key（在 Setting 页改），导入模型存 `SAVED_SYSTEM_PROMPT_PREFIX + 名字` 和各自 `meta.json` 的 `speakerId`（在模型管理页改）。以前所有外部模型共用一份设定和一个 speaker id。

## 配置与密钥

- 运行时密钥存 `SharedPreferences`（`Constant.SAVED_STORE`），key 名都在 `app/.../data/Constant.kt`
- 聊天基座的 key / base_url / model 是**按 provider 分开存**的（`saved_provider_<id>_*`），
  统一走 `ChatProviderSettings`，它的 `init` 里带一次性迁移（老的 `saved_chat_key` + proxy url → OpenAI 兼容基座）
- debug 构建从 `local.properties` 读 `CHAT_CPT_KEY` / `TRANSLATE_APP_ID` / `TRANSLATE_KEY` 注入 `BuildConfig`（见 `app/build.gradle` 的 `readLocalProperties`）；release 构建注空串
- `local.properties` 不入库

## 依赖版本管理

**所有依赖坐标和版本统一在 `gradle/libs.versions.toml`**，模块里只写 `libs.xxx` 别名，不要再硬编码版本号。原先的 `version.gradle` + `rootProject.ext.*` 方案已删除。

catalog 里除了依赖，还收拢了这些构建参数，模块通过 `libs.versions.xxx.get()` 读：
- `compileSdk` / `minSdk` / `targetSdk`（`.get().toInteger()`），各模块的 `lint {}` 和 `testOptions {}` 也一并从这里取 `targetSdk`
- `jvmTarget`：**只被 `android.compileOptions` 的 `source/targetCompatibility` 读**。built-in Kotlin 下 Kotlin 的 jvmTarget 默认跟随 `targetCompatibility`，所以模块里不需要（也没有）任何显式 `jvmTarget` 声明，`Inconsistent JVM Target Compatibility Between Java and Kotlin Tasks` 这个坑已经不存在了

新增依赖的流程：先在 toml 里加 `[versions]` 条目（如果是新的版本线）和 `[libraries]` 别名，再在模块 `build.gradle` 里 `implementation libs.your.alias`。

几个坑：
- 别名末段不能是 Groovy 关键字。`material3-window-size-class` 的别名必须去掉尾部 `class`（现为 `compose-material3-window-size`），否则 `libs.xxx.class` 会被解析成 `getClass()`
- Compose 系列库在 `[libraries]` 里**故意不写版本**，由 `androidx-compose-bom` 统一管理
- `settings.gradle` 里的 `foojay-resolver-convention` 插件版本无法走 catalog（settings 插件解析早于 catalog 可用），只能硬编码
- **依赖用到就要显式声明**，别靠传递依赖。okhttp 和 kotlinx-coroutines 都是后来补声明的（源码直接 import，但构建脚本只声明了 retrofit / lifecycle）。这种 undeclared dependency 在上游换实现时会突然编译不过

## 构建脚本约定

- **Groovy 属性赋值必须写 `=`**（`namespace = '...'`）。Gradle 10 移除 `propName value` 的空格写法。但要分清对象：`compileSdk` / `minSdk` / `proguardFiles` / `abiFilters` / `sourceCompatibility` / cmake 的 `path` 这些是**方法调用**，加了 `=` 反而报错。拿不准就跑 `--warning-mode all`，Gradle 会精确指出行号
- 不要用 `android.kotlinOptions {}`（AGP 9 已无此 DSL），也不要 apply `org.jetbrains.kotlin.android`
- 不要用旧 variant API `android.applicationVariants.all {}`（AGP 10 移除，且和新 DSL 不兼容），用 `androidComponents.onVariants(selector()...)`。注意新 API 里 `outputFileName` 是 `Property<String>`，只能 `.set()`
- `gradle.properties` 里**没有** `android.builtInKotlin` / `android.newDsl` 等逃生阀，别加回去——它们在 AGP 10 会被移除
- release APK 重命名逻辑在 `app/build.gradle` 底部的 `androidComponents` 块，用 selector 限定了只作用于 release（debug 保持 `app-debug.apk`）

### Kotlin 为什么卡在 2.3.21

Kotlin 最新是 **2.4.10**，但这个工程只能用 2.3.21，原因是一条硬依赖链：

`Room 需要注解处理器` → `KSP 至今没有 2.4.x 版本（最新 2.3.11）` → `Kotlin 只能停在 2.3.x`

退回 kapt 也不行：Kotlin 2.4 产出的 metadata 版本是 2.4.0，而 Room 2.8.4 内置的 `kotlin-metadata-jvm`
最高只认 2.3.0，`kaptDebugKotlin` 会直接抛
`Provided Metadata instance has version 2.4.0, while maximum supported version is 2.3.0`。

**等 KSP 发 2.4.x 之后，把 `kotlin` 和 `ksp` 两个版本一起往上抬即可。**

这条链还牵着一个第三方库：**Coil 钉在 3.4.0 而不是最新的 3.5.0**，
因为 3.5.0 依赖 `kotlin-stdlib 2.4.0`（比编译器新，每次编译都会警告
`Runtime JAR ... is newer than the compiler`），3.4.0 依赖 2.3.10 正好在下面。
抬 Kotlin 的时候顺手把它一起抬。

### 其他版本约束

- `compileSdk` 必须 >= 37：`core-ktx` 1.19 / `lifecycle` 2.11 的 AAR metadata 硬性要求。本地缺 platform 37 时 AGP 会自动下载
- `material-icons-extended` 被 compose-bom 钉在 **1.7.8**（这个 artifact 已废弃、停止发版），和 compose 1.11.4 混用。工程里用到了十几个只存在于 extended 里的图标（`AlternateEmail` / `Pinch` / `SettingsVoice` 等），所以暂时不能去掉；将来要么换 core 图标要么内联成 vector drawable
- okhttp 4+ 把 `MediaType.parse` / `RequestBody.create` 的静态形式标成 `DeprecationLevel.ERROR`，必须用 Kotlin 扩展（`toRequestBody()` / `toMediaType()`）
- okhttp 钉在 **4.12.0**（retrofit 3.0.0 自己依赖的版本）。okhttp 5.x 已发布，但它把 `MediaType` / `RequestBody` 一批 API 做了 Kotlin 化重构，是一次独立迁移
- kotlinx-coroutines 钉在 **1.10.2**：Coil 3.x 全线依赖这个版本，不跟着抬就会变成「catalog 声明 1.9.0、实际解析到 1.10.2」这种最难查的状态。用 `kotlinx-coroutines-android`（比 `-core` 多带 `Dispatchers.Main` 的 Android 实现）
- media3 **只取 `media3-common`**，为的是 `androidx.media3.common.audio` 那一包（重采样 / 声道混合）。不要顺手换成 `media3-transformer`：它会连带 exoplayer + effect + muxer + datasource + container 五个包，而这里只需要「音频转 16k mono PCM」。那一包的 API 全是 `@UnstableApi`，用的地方要 `@androidx.annotation.OptIn(UnstableApi::class)`（**androidx 的 OptIn，不是 kotlin 的**——kotlin.OptIn 认不出 androidx 的 `@RequiresOptIn`，会警告「has no effect」）
- `androidx-exifinterface` **已删除**：EXIF 方向交给 Coil 的 `ExifOrientationStrategy.RESPECT_ALL`（`ImageNormalizer.defaultLoader()` 里那一行是唯一开关，删了会静默回归成「躺着的图」）

## Edge-to-edge

`targetSdk` 36 意味着**强制 edge-to-edge**：系统不再自动补 status / navigation bar 的 inset。
已经逐屏适配过，改动模式如下，新增页面照这个来：

- Activity 侧调 `enableEdgeToEdge()`（`ChatActivity` / `LoginActivity`）。系统在 35+ 本来就强制，
  显式调用是为了 minSdk 29~34 行为一致，外加把系统栏图标明暗对比交给 androidx
- **Compose 页面**：默认不要动 `Scaffold.contentWindowInsets`。只有底部真的挂了 `UserInput`
  才 `exclude(navigationBars)`，并且**必须**在 `UserInput` 上补
  `Modifier.navigationBarsPadding().imePadding()`（padding 加在内层，`Surface` 的
  tonalElevation 才能铺到导航栏后面）。`ChatContent.kt` / `Conversation.kt` 是这个形态；
  `ChannelListContent` / `ModelManagerContent` 底部没输入框，所以只 `exclude(ime)`
- **View 布局页面**（只有 `LoginActivity`）：自己 `ViewCompat.setOnApplyWindowInsetsListener`，
  取 `systemBars | displayCutout | ime`，并且是**叠加**到布局原有 padding 上而不是覆盖，
  否则 xml 里的 margin 会被冲掉。`ime` 必须带上——`decorFitsSystemWindows=false` 之后
  窗口不再自动 resize，不处理键盘会挡住输入框
- `themes.xml` 里不要再写 `android:statusBarColor`，API 35+ 是 no-op

## 已知技术债 / 坑

- `ChatWaifuApplication.context` 是静态 Context，多处直接引用而非注入
- `ChatCore` 只联调过编译，**实机端到端还没验证**；Anthropic / Gemini / 端内三个 provider 是 stub
- 存储层同样只过了编译：**Room 1→2 和 3→4 的数据迁移都还没在真机老库上跑过**
- 多模态**存储、归一化、映射都通了，但 UI 没有附件入口**：`ChatSession.send()` 还只收
  `String`，聊天页也没有「加图片」按钮。接线点见 `docs/media-pipeline.md` 第八节
- `Log/schemas/` 缺 `2.json`（只有 1/3/4），不影响构建但迁移测试要用
- `mainLoop()` 的 `while(true)` + continuation 唤醒设计，异常和取消路径比较脆
- lint 还有 **195 条 warning / 0 error** 的历史存量（其中 `ChromeOsAbiSupport` 那条是
  只出 arm64 的直接后果）：`UnusedResources` 66（含
  `activity_chat.xml` + `app_bar_chat.xml` 两个 Navigation 模板留下的死布局，从来没被 inflate，
  真正的内容是 `content_main.xml`）、`HardcodedText` 27、`Typos` 20、`Autofill`/`TextFields` 各 11
- manifest 里锁死横屏（`DiscouragedApi`）：Android 16 起固定屏幕方向在多数情况下会被忽略
- APK 体积 ~354MB（换 BV2 后从 ~440MB 降下来的）。大头是 openjtalk 词典 98MB +
  BV2 日文声学模型 49MB + 日文 BERT 43MB + zh/en 预处理词典 17MB + Live2D 22MB，
  全打进 `assets`，没有做按需下载
- **BV2 的端到端还没在真机上验过**：整条链路只过了编译和打包
- 无任何单元测试 / instrumentation 测试

## 常用命令

```bash
# 首次 clone 后必须先产出 BV2 的 AAR，见上面「端内 TTS」一节
./gradlew assembleDebug          # 打 debug 包
./gradlew :app:assembleRelease   # release 包，产物名 ChatWaifu_<yyyyMMddHHmm>.apk
./gradlew clean
./gradlew :app:dependencies      # 查依赖树，确认 catalog 收拢后版本解析结果
```

Live2D / Lipsync 的 Native 由 `externalNativeBuild` 的 CMake 自动构建，需要本地装好 NDK。
TTS 的 `.so` 来自 BV2 的 AAR，不在本仓库构建。全工程只出 `arm64-v8a`。

## 约束

模型资源禁止商用（见 README 免责声明）。Live2D 模型和 VITS 声库来自第三方作品，不要把新的版权资源提交进仓库。
