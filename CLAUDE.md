# ChatWaifu_Mobile

Android 版「AI 纸片人聊天器」。LLM 出文本 → 翻译成日文 → 本地 VITS 推理出语音 → 驱动 Live2D 模型口型与动作；语音输入走本地 Sherpa-ncnn ASR。

初版写于 2023 年初（GPT-3.5 刚发布），2023 年后基本停止维护，2026 年重新开始迭代。当前开发分支 `feature/v2.0.0`。

## 技术栈

- Kotlin + Jetpack Compose（UI 全部 Compose，但外壳仍是 Activity + Fragment + Navigation 混合架构）
- MVVM + LiveData / SharedFlow，Retrofit + Gson 做网络，Room 做聊天记录持久化
- 三块 Native：VITS(ncnn) 语音合成、Live2D Cubism SDK(C++) 渲染、meta-lipSync 口型分析
- Sherpa-ncnn 语音识别跑在**独立进程** `:sherpa`，通过 AIDL 通信
- AGP 9.3.1 / Gradle 9.5 / Kotlin 2.2.10 / JDK 17 toolchain，全模块字节码目标 Java 11
- `compileSdk` = 34（AGP 9 的下限），`targetSdk` = 34，`minSdk` = 24
- 只申请 `RECORD_AUDIO` + `INTERNET`。**没有任何存储权限**，模型走应用专属目录 + SAF 导入

## 模块结构

根工程 `ChatWaifu_Mobile`，8 个模块（见 `settings.gradle`）：

| 模块 | namespace | 职责 |
|---|---|---|
| `app` | `com.chatwaifu.mobile` | 唯一 application 模块，UI + 编排全流程 |
| `ChatGPT` | `com.chatwaifu.chatgpt` | OpenAI Chat Completions 封装（Retrofit） |
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
2. `AssistantMessageManager` 写入用户消息、拼装上下文
3. `sendChatGPTRequest()` → `ChatGPTNetService.sendChatMessage()`
4. 结果 emit 到 `_chatContentUIFlow` 渲染气泡
5. `fetchTranslateIfNeed()` — 默认把回复翻成日文（内置模型都是日语声库）
6. `generateAndPlaySound()` → `SoundGenerateHelper.generateAndPlay()`，VITS 推理出的 `FloatArray` 一路给 `SoundPlayHandler` 播放、一路 `forwardResult` 给 `LipsValueHandler` 驱动口型

`ChatStatus` 枚举（`DEFAULT/FETCH_INPUT/SEND_REQUEST/TRANSLATE/GENERATE_SOUND`）通过 `chatStatusLiveData` 给 UI 显示当前阶段。

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

角色的**人物设定和 speaker id 是按角色存的**：内置三个沿用 `SAVED_*_SETTING` 老 key（在 Setting 页改），导入模型存 `SAVED_SYSTEM_PROMPT_PREFIX + 名字` 和各自 `meta.json` 的 `speakerId`（在模型管理页改）。以前所有外部模型共用一份设定和一个 speaker id。

## 配置与密钥

- 运行时密钥存 `SharedPreferences`（`Constant.SAVED_STORE`），key 名都在 `app/.../data/Constant.kt`
- debug 构建从 `local.properties` 读 `CHAT_CPT_KEY` / `TRANSLATE_APP_ID` / `TRANSLATE_KEY` 注入 `BuildConfig`（见 `app/build.gradle` 的 `readLocalProperties`）；release 构建注空串
- `local.properties` 不入库

## 依赖版本管理

**所有依赖坐标和版本统一在 `gradle/libs.versions.toml`**，模块里只写 `libs.xxx` 别名，不要再硬编码版本号。原先的 `version.gradle` + `rootProject.ext.*` 方案已删除。

catalog 里除了依赖，还收拢了这些构建参数，模块通过 `libs.versions.xxx.get()` 读：
- `compileSdk` / `minSdk` / `targetSdk`（`.get().toInteger()`），各模块的 `lint {}` 和 `testOptions {}` 也一并从这里取 `targetSdk`
- `jvmTarget`：Java 的 `compileOptions` 和 Kotlin 的 `kotlinOptions.jvmTarget` 都从这里取，**两边必须一致**，否则 AGP 9 会报 `Inconsistent JVM Target Compatibility Between Java and Kotlin Tasks`

新增依赖的流程：先在 toml 里加 `[versions]` 条目（如果是新的版本线）和 `[libraries]` 别名，再在模块 `build.gradle` 里 `implementation libs.your.alias`。

几个坑：
- 别名末段不能是 Groovy 关键字。`material3-window-size-class` 的别名必须去掉尾部 `class`（现为 `compose-material3-window-size`），否则 `libs.xxx.class` 会被解析成 `getClass()`
- Compose 系列库在 `[libraries]` 里**故意不写版本**，由 `androidx-compose-bom` 统一管理
- `settings.gradle` 里的 `foojay-resolver-convention` 插件版本无法走 catalog（settings 插件解析早于 catalog 可用），只能硬编码

收拢时统一了原先各模块不一致的版本（`appcompat` 1.4.1/1.6.1、`material` 1.5.0/1.7.0/1.9.0-beta01、`gson` 2.10/2.10.1）。取的都是 Gradle 原本就会解析到的最高版本，所以 `:app` 的实际依赖图没有变化。

## 已知技术债 / 坑

- `targetSdk` 停在 34。往 35/36 抬会触发**强制 edge-to-edge**（系统不再自动加 status/navigation bar inset），需要逐屏处理 `WindowInsets`，是一次独立的 UI 改造，适合和 UI 迭代一起做
- `compose-bom` 停在 `2023.04.01`，它带的 compose lint 检测器和 AGP 9 的 lint API 不兼容，`MutableCollectionMutableState` 会直接让 `lintAnalyzeDebug` 崩溃，目前在 `app/build.gradle` 里 disable 掉了。升级 compose-bom 后应该删掉那行
- `ChatWaifuApplication.context` 是静态 Context，多处直接引用而非注入
- `ChatGPTNetService` 的模型名/参数写死在 `ChatGPTData.kt` 里，还是 2023 年的 `gpt-3.5` 时代形态
- `mainLoop()` 的 `while(true)` + continuation 唤醒设计，异常和取消路径比较脆
- `material` 依赖曾长期停留在 `1.9.0-beta01`（beta 版）
- `app/build.gradle` 里 `android.applicationVariants.all { ... outputFileName = ... }` 写在 `release {}` 块内，但实际对所有 variant 生效，debug 包也被改名成 `ChatWaifu_<时间戳>.apk`
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
