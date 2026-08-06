# ChatWaifu_Mobile

Android 版「AI 纸片人聊天器」。LLM 出文本 → 翻译成日文 → 本地 VITS 推理出语音 → 驱动 Live2D 模型口型与动作；语音输入走本地 Sherpa-ncnn ASR。

初版写于 2023 年初（GPT-3.5 刚发布），2023 年后基本停止维护，2026 年重新开始迭代。当前开发分支 `feature/v2.0.0`。

## 技术栈

- Kotlin + Jetpack Compose（UI 全部 Compose，但外壳仍是 Activity + Fragment + Navigation 混合架构）
- MVVM + LiveData / SharedFlow，Retrofit + Gson 做网络，Room 做聊天记录持久化
- 三块 Native：VITS(ncnn) 语音合成、Live2D Cubism SDK(C++) 渲染、meta-lipSync 口型分析
- Sherpa-ncnn 语音识别跑在**独立进程** `:sherpa`，通过 AIDL 通信
- AGP 9.3.1 / Gradle 9.6.1 / Kotlin 2.3.21 / JDK 17 toolchain，全模块字节码目标 Java 11
- `compileSdk` = 37，`targetSdk` = 36，`minSdk` = 24
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
| `VITS` | `com.chatwaifu.vits` | VITS-ncnn 语音合成 + 文本清洗 + 音频播放/录制（含 CMake） |
| `Live2D` | `com.chatwaifu.live2d` | Live2D Cubism SDK Native 封装（含 CMake + SDKRoot） |
| `Lipsync` | `com.chatwaifu.lipsync` | meta-lipSync Native 封装（含 CMake） |
| `Sherpa` | `com.k2fsa.sherpa.ncnn` | Sherpa-ncnn ASR，跑在 `:sherpa` 进程，AIDL 暴露 |
| `Log` | `com.chatwaifu.log` | Room 数据库，聊天记录读写 |

依赖方向：`app` → 其余 7 个模块，模块之间**无横向依赖**。

## 核心链路

### 主循环
`ChatActivity` 启动后调用 `ChatActivityViewModel.mainLoop()`（`app/src/main/java/com/chatwaifu/mobile/ChatActivityViewModel.kt`），是一个跑在 `Dispatchers.IO` 上的 `while(true)`，每轮：

1. `fetchInput()` — 用 `suspendCancellableCoroutine` 挂起，等 UI 层调 `sendMessage()` 唤醒（`inputFunc` 回调持有 continuation）
2. `ChatHistoryStore.appendUser()` 写入用户消息（Room）
3. `streamChatRequest()` → `ChatSession.send()`，collect `Flow<ChatDelta>`
4. `TextDelta` 逐字累积后 emit 到 `_chatContentUIFlow` 渲染气泡（`isStreaming = true`），`Completed` 时 emit 最终文本并落库
5. `fetchTranslateIfNeed()` — 默认把回复翻成日文（内置模型都是日语声库）
6. `generateAndPlaySound()` → `SoundGenerateHelper.generateAndPlay()`，VITS 推理出的 `FloatArray` 一路给 `SoundPlayHandler` 播放、一路 `forwardResult` 给 `LipsValueHandler` 驱动口型

`ChatStatus` 枚举（`DEFAULT/FETCH_INPUT/SEND_REQUEST/TRANSLATE/GENERATE_SOUND`）通过 `chatStatusLiveData` 给 UI 显示当前阶段。

### 聊天基座（ChatCore）
业务只认 `ChatMessage` / `ChatDelta`，不认某一家的 `choices[0]`。核心是一个抽象方法
`ChatProvider.chatStream(request): Flow<ChatDelta>`；历史**统一由客户端的 `ChatSession` 持有**
（因为 Anthropic 没有服务端会话，必须重发全量）。已落地 `OpenAICompatProvider`（覆盖代理 /
DeepSeek / Ollama / llama.cpp / vLLM）和 `OpenAIResponsesProvider`；Anthropic / Gemini / 端内
三个是 stub，`chatStream` 抛 `NotImplementedError`，**映射方案写在各自类 KDoc 里**。

设计意图、三家 API 映射表、扩展新 provider 的步骤、进度与待办全在 **`docs/chat-core.md`**，
动这一层之前先看它。

### 口型同步
`LipsValueHandler` 里 `USE_REAL_LIP_SYNC = false` —— meta-lipSync 的真实 viseme 映射因为时长对齐问题效果不好，**当前默认只播一个 0→1→0 的循环假动画**（`playDefaultAnimation`），时长按音频采样数估算。真实逻辑代码还在，改常量即可启用。

### 语音识别
`ChatFragmentViewModel` 用 `bindService` 绑 `SherpaService`（`:sherpa` 进程），拿 `ISherpaAidlInterface`。按住说话 → `startRecord()`，松手 → `finishRecord(callback)` 回传识别文本。多个 ncnn 库像是进程独享资源，所以才拆进程。

### 模型存取（`app/.../data/model/`）

内置模型和用户导入模型走**同一套磁盘布局**，落在应用专属外部目录：

```
<getExternalFilesDir("models")>/<角色名>/
  meta.json
  live2d/    xxx.model3.json, xxx.moc3, ...
  vits/      config.json, *.bin       ← 可选，没有就是无语音角色
```

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

### 其他版本约束

- `compileSdk` 必须 >= 37：`core-ktx` 1.19 / `lifecycle` 2.11 的 AAR metadata 硬性要求。本地缺 platform 37 时 AGP 会自动下载
- `material-icons-extended` 被 compose-bom 钉在 **1.7.8**（这个 artifact 已废弃、停止发版），和 compose 1.11.4 混用。工程里用到了十几个只存在于 extended 里的图标（`AlternateEmail` / `Pinch` / `SettingsVoice` 等），所以暂时不能去掉；将来要么换 core 图标要么内联成 vector drawable
- okhttp 4+ 把 `MediaType.parse` / `RequestBody.create` 的静态形式标成 `DeprecationLevel.ERROR`，必须用 Kotlin 扩展（`toRequestBody()` / `toMediaType()`）
- okhttp 钉在 **4.12.0**（retrofit 3.0.0 自己依赖的版本）。okhttp 5.x 已发布，但它把 `MediaType` / `RequestBody` 一批 API 做了 Kotlin 化重构，是一次独立迁移
- kotlinx-coroutines 钉在 **1.9.0**，和 lifecycle 2.11 传递进来的版本对齐。用 `kotlinx-coroutines-android`（比 `-core` 多带 `Dispatchers.Main` 的 Android 实现）

## Edge-to-edge

`targetSdk` 36 意味着**强制 edge-to-edge**：系统不再自动补 status / navigation bar 的 inset。
已经逐屏适配过，改动模式如下，新增页面照这个来：

- Activity 侧调 `enableEdgeToEdge()`（`ChatActivity` / `LoginActivity`）。系统在 35+ 本来就强制，
  显式调用是为了 minSdk 24~34 行为一致，外加把系统栏图标明暗对比交给 androidx
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
- `mainLoop()` 的 `while(true)` + continuation 唤醒设计，异常和取消路径比较脆
- lint 还有 **177 条 warning / 0 error** 的历史存量：`UnusedResources` 62（含
  `activity_chat.xml` + `app_bar_chat.xml` 两个 Navigation 模板留下的死布局，从来没被 inflate，
  真正的内容是 `content_main.xml`）、`HardcodedText` 27、`Typos` 20、`Autofill`/`TextFields` 各 11
- manifest 里锁死横屏（`DiscouragedApi`）：Android 16 起固定屏幕方向在多数情况下会被忽略
- APK 体积 ~440MB，因为三个内置模型（Live2D + VITS 声库）全打进 `assets`，没有做按需下载
- 无任何单元测试 / instrumentation 测试

## 常用命令

```bash
./gradlew assembleDebug          # 打 debug 包
./gradlew :app:assembleRelease   # release 包，产物名 ChatWaifu_<yyyyMMddHHmm>.apk
./gradlew clean
./gradlew :app:dependencies      # 查依赖树，确认 catalog 收拢后版本解析结果
```

Native 部分由 `externalNativeBuild` 的 CMake 自动构建，需要本地装好 NDK（`abiFilters` 只出 `armeabi-v7a` / `arm64-v8a`）。

## 约束

模型资源禁止商用（见 README 免责声明）。Live2D 模型和 VITS 声库来自第三方作品，不要把新的版权资源提交进仓库。
