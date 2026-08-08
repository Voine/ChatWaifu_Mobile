# 媒体管线

附件的**格式兼容层**：用户选中的任意图片/音频/视频/PDF，怎么变成「能发给任何一家基座」
的规范形态。配套文档：[chat-storage.md](chat-storage.md)（存储层）、[chat-core.md](chat-core.md)（聊天基座）。

改造起因：存储层那一轮把「一份字节有归属地」解决了，但**归一化只对图像做了**——
音频/文档是直通（只有一道体积闸门），视频直接拒绝。而各家 API 对格式的要求很硬，
直通落进去的 `.m4a` 发给 OpenAI 必然 400。这一轮把这层补齐。

## 一、断点状态表

| # | 项 | 状态 |
|---|---|---|
| 0 | CMake 版本收拢进 catalog（`libs.versions.cmake`） | ✅ 已提交 `1d87db7` |
| 1 | minSdk 24 → 28 | ✅ 完成 |
| 2 | 阶段 A：probe-before-read、体积闸门前置、alpha 分流 | ✅ 完成 |
| 3 | 阶段 B：Coil 3 接成解码前端 | ✅ 完成 |
| 4 | 阶段 C：音频 → 16k mono WAV（media3 重采样） | ✅ 完成 |
| 5 | 阶段 D：视频抽帧 + `chat_attachment` v4 | ✅ 完成 |
| 6 | 本文档 + chat-storage.md / CLAUDE.md 订正 | ✅ 完成 |
| — | **实机端到端验证** | ❌ 没做，见第八节 |
| — | 附件 UI 入口（选文件、缩略图、进度条） | ❌ 没做，见第八节 |

编译验证：`:Log:compileDebugKotlin` ✅、`:app:compileDebugKotlin` ✅、`assembleDebug` ✅。

## 二、各家能收什么（这一层的全部依据）

| | 图像 | 音频 | 视频 |
|---|---|---|---|
| Anthropic | jpeg / png / gif / webp + PDF | ❌ 不支持 | ❌ |
| OpenAI | jpeg / png / gif / webp + PDF | **只有 wav / mp3** | ❌ |
| Gemini | 常见都收 | wav / mp3 / aac / ogg / flac | ✅ 唯一一家 |

两个结论直接从这张表掉出来，它们是整条管线的两个设计支点：

**1. 音频的规范形态只能是 PCM WAV。** `wav ∩ mp3` 是 OpenAI 划的死线，
而 **Android 平台没有 MP3 编码器**（`MediaCodec` 只有 decoder），要出 mp3 就得引 LAME。
所以规范形态是 16kHz 单声道 16bit WAV。体积上 32KB/s，10 分钟约 19MB，
配合已有的 `upload()` 预上传和 1MB 内联阈值不成问题。

**2. 视频不转码，走抽帧。** 视频输入只有 Gemini 一家收，而这个工程的 Gemini provider
还是 stub。抽帧之后「看视频」在**任何有 vision 的基座上都能用**，且零转码代码。
代价写在明面上：丢掉帧间连续性，「这段视频里发生了什么」够用，
「数一下他挥了几次手」不够。

## 三、为什么 minSdk 抬到 28

`24` → `28`（Android 9）。抬这一档换来的东西正好都在媒体这条线上：

- **HEIF / HEIC 可解**。iPhone 和很多国产相机的默认格式，24~27 上是静默 `decode failed`
- **`ImageDecoder` 可用**，且它自动应用 EXIF 方向
- **`MediaMetadataRetriever.getFramesAtIndex()`**，抽帧不用逐帧 seek

停在 28 不继续往上：29 的 scoped storage 对这个工程没意义（本来就零存储权限），
31 的 AVIF 解码是单格式锦上添花、可以运行时 gate，不值得再砍一截设备。

全工程只有一处 `Build.VERSION.SDK_INT` 分支（`VITS/.../WaveUtils.kt` 的 R/30 判断），
和这次无关，没动。

## 四、选了哪些库，以及否掉了谁

| 领域 | 选 | 版本 | 理由 |
|---|---|---|---|
| 图像解码 | **Coil 3** (`coil-core`) | 3.4.0 | 见下 |
| 图像编码 | 平台 `Bitmap.compress` | — | 不需要第三方 |
| 重采样 / 声道混合 | **media3-common** 的 `androidx.media3.common.audio` | 1.11.0 | `SonicAudioProcessor` 是 ExoPlayer 用了十年的实现；自己写会得到一个有可听 aliasing 的线性插值版 |
| 解封装 / 解码 | 平台 `MediaExtractor` + `MediaCodec` | — | 它们能吃的就是这台设备能吃的，第三方库绕不过底层编解码器 |
| WAV 封装 | 自己写 44 字节 RIFF 头 | — | 见下 |
| 抽帧 | 平台 `MediaMetadataRetriever` | — | `getScaledFrameAtTime` 在 native 层就按目标尺寸解 |
| 格式探测 | 平台 `MediaMetadataRetriever` / `MediaExtractor` | — | |

### 否掉的

- **ffmpeg-kit**：ARTHENICA 2025 年 1 月宣布退役，Maven 产物已下架。自己编 FFmpeg 是
  每 ABI +20~40MB 加 LGPL/GPL 合规审查，而它的能力 95% 这里用不上，APK 已经 440MB
- **media3-transformer**：会连带 `exoplayer` + `effect` + `muxer` + `datasource` + `container`
  五个包。这个工程只要「音频转成 16k mono PCM」，不要播放器也不要视频合成管线，
  所以只取 `media3-common`
- **`AudioProcessingPipeline`**（能省掉手写的处理器串联）：构造函数只收 Guava 的
  `ImmutableList`，为一个 list 类型把 Guava 拉进来不值得，而串联逻辑本身就是三十行
- **Glide / Fresco**：前者是 Java、和 Compose 集成差；后者自带 native 解码器，体积不划算
- **`VITS` 模块的 `WaveUtils`**（工程里已有的 WAV 写入）：①走 native ②吃 `FloatArray`
  整份进内存 ③写的是 32bit float 格式，三条都不合用

### 为什么是 Coil，且钉在 3.4.0

选它不只是为了入库解码——**附件缩略图那步 UI 本来就需要一个图片加载库**
（加 `coil-compose` 即可）。与其为入库单独维护一套解码代码，不如两边共用同一条 Decoder 链。
它顺带解决的：HEIC/AVIF、动图只取第一帧、EXIF 方向、`content://` 原生支持，
以及**流式读取**（okio `BufferedSource`，不会把整个文件读成 `ByteArray`）。

**版本停在 3.4.0 而不是最新的 3.5.0**：3.5.0 依赖 `kotlin-stdlib 2.4.0`，
比本工程的编译器（2.3.21，被 KSP 卡住，见 CLAUDE.md）新，会让每次编译都带一条
`Runtime JAR ... is newer than the compiler` 警告。3.4.0 依赖 stdlib 2.3.10，正好在下面。
**这是「Kotlin 卡在 2.3.21」那条依赖链的延伸**，KSP 出 2.4.x 之后一起抬。

副作用一条：Coil 全线依赖 `kotlinx-coroutines 1.10.2`，所以 catalog 里的 coroutines
从 1.9.0 跟着抬到 1.10.2 —— 不抬的话解析结果会是「声明 1.9.0 实际拿到 1.10.2」
这种最难查的状态。

`androidx-exifinterface` 的**显式声明已删除**：方向处理交给了 Coil 的
`ExifOrientationStrategy.RESPECT_ALL`（Coil 自己会把 exifinterface 作为传递依赖带进来）。

## 五、代码形状

```
app/data/attachment/
  MediaLimits.kt        所有闸门和目标参数，一处改
  MediaProbe.kt         content:// → MediaInfo（读字节之前）
  MediaNormalizer.kt    接口 + NormalizedMedia / NormalizeResult / Progress / Error
  ImageNormalizer.kt    Coil 解码 → BitmapEncoder
  AudioNormalizer.kt    直通判定 → PcmDecoder
  VideoNormalizer.kt    原视频直通 + 抽帧 + 抽音轨
  DocNormalizer.kt      体积闸门 + 原样落地
  BitmapEncoder.kt      Bitmap → 临时文件（图像和抽帧共用）
  PcmDecoder.kt         MediaCodec + media3 处理器链 → WavFileWriter
  WavFileWriter.kt      44 字节 RIFF 头，流式
  AttachmentIngest.kt   编排：probe → 派活 → 落盘 → AttachmentRef
  AttachmentProvider.kt 单例入口（和 ModelProvider 同套路）
  AttachmentStore.kt    只管字节：落盘 / 定位 / 删除 / GC
```

调用方只认 `AttachmentProvider.ingest(context).ingest(characterId, uri)`，
拿到 `Flow<AttachmentIngest.Progress>`，`Success.refs` 直接交给
`ChatHistoryStore.appendUser(text, attachmentRefs = ...)`。

### `AttachmentStore` 退成只管字节

它原先同时管落盘和图像归一化（解码、降采样、EXIF、JPEG 重编码）。加上音视频之后
那条路走不通：转码要 `MediaCodec`、抽帧要 `MediaMetadataRetriever`，
每种媒体的参数和失败模式都不一样。

入口改成 `adopt(characterId, media, orig, sourceRelPath)`：接管一个**已经归一化好的临时文件**，
`renameTo` 而不是复制。所以字节从头到尾只写一次，也从来没有整份进过内存。

临时目录刻意放 `externalCacheDir` 而不是 `cacheDir`：

- 和附件根目录**同卷**，`renameTo` 才是零拷贝（跨卷 rename 会失败，退化成整份复制一遍，
  一个 200MB 的视频就是白写 200MB）
- **不在附件根目录底下**，因为 `gc()` 会把根目录里没被引用的文件全删，
  而正在转码的中间产物恰好符合「没被引用」。代价是 `tempDir` 不在 GC 覆盖范围内，
  所以另加了 `clearTemp()`，启动时和 `gc()` 一起调

## 六、修掉的三个洞

### 洞 #1：体积闸门在读字节之后（会 OOM）

```kotlin
// 老实现
val bytes = readBytes(uri) ?: return null          // ← 整个文件进 ByteArray
if (bytes.size > Limits.GENERIC_MAX_BYTES) return null   // ← 闸门在这
```

选一个 500MB 的视频，OOM 发生在闸门之前，闸门根本没机会拦。

修法是体积必须从**元数据**拿而不是从字节数拿：`MediaProbe` 查
`OpenableColumns.SIZE`，退路是 `openAssetFileDescriptor().length`。
整条管线之后再没有 `readBytes()`——归一化写临时文件，落盘靠 rename，
直通用 `copyTo` 流式复制。

### 洞 #2：带 alpha 的图透明变黑

老实现无条件 `compress(JPEG)`。JPEG 没有 alpha 通道，于是带透明区域的 PNG 截图
落盘后透明处变成黑色——**喂给视觉模型的图和用户看到的不是一张**。

现在按 `Bitmap.hasAlpha()` 分两条阶梯，两条都在三家的公共子集里：

| 源 | 阶梯 |
|---|---|
| 不透明 | JPEG，质量 88 → 55 |
| 带 alpha | PNG（无损，一次）→ 超预算则 WebP 有损，质量同样往下退 |

带 alpha 时**不退回 JPEG**：那等于把「保住透明度」这个目的丢掉。
WebP 有损是唯一既能压又保 alpha 的公共格式。（`WEBP_LOSSY` 是 API 30 的枚举值，
28~29 上用已废弃的 `WEBP`，它在 quality < 100 时本来就是有损，行为一致。）

### 洞 #3：HEIC 静默失败

minSdk 24 时 `BitmapFactory` 解不了 HEIF，返回 null，日志一行 `decode failed`。
现在 minSdk 28 + Coil 的 Decoder 链，HEIC 正常解；真解不开的（比如 28~30 上的 AVIF）
会得到 `NormalizeError.DecodeFailed`，UI 上要说的是「你这台设备不行」
而不是「这个格式不行」——所以它和 `UnsupportedFormat` 是两个分支。

## 七、schema v4 与派生附件

`chat_attachment` 加六列，全部纯加列，所以 `MIGRATION_3_4` 用
`ALTER TABLE ADD COLUMN` 而不必重建表：

| 列 | 用途 |
|---|---|
| `sampleRate` / `channels` | 音频参数。「能不能直接发给某家」是它俩的函数，每次重新 probe 是白花 IO |
| `sourceRelPath` | 派生来源。null = 用户直接给的 |
| `posMs` | 派生物在源里的时间位置（抽帧的时间戳） |
| `origMime` / `origByteSize` | 用户**原本**给的形态，UI 上要能说「12MB HEIC → 380KB JPEG」 |

`origByteSize` 是 NOT NULL，所以 ALTER 里必须带 `DEFAULT 0`，
而实体侧要有对应的 `@ColumnInfo(defaultValue = "0")` ——
这两个是一对，改一个必须改另一个，否则 Room 的 `identityHash` 对不上、启动即崩。

### 为什么派生关系用 relPath 而不是自引用外键

一个视频落多行：

```
chat_attachment
  ├─ VIDEO  <uuid>.mp4   sourceRelPath = null            ← 父行，只给 UI 回放
  ├─ IMAGE  <uuid>.jpg   sourceRelPath = ↑, posMs = 0
  ├─ IMAGE  <uuid>.jpg   sourceRelPath = ↑, posMs = 2000
  │  ...
  └─ AUDIO  <uuid>.wav   sourceRelPath = ↑               ← 抽出来的音轨
```

外键能买到引用完整性，但这里本来就有：派生行和父行永远属于同一条消息、
永远一起插一起删（`updateWithAttachments` 是「删光重插」）。
而外键要求「先插父拿到自增 id 再插子」，插入顺序就成了隐式契约。
`relPath` 是 uuid 文件名、天然唯一，而且 `rememberRemoteFileId` 早就在拿它
当这份字节的身份了。

GC 不用动——它是「遍历磁盘 ∖ 全库引用集合」，派生文件天然被覆盖。

### 只有派生行进上下文

**父行模型看不到**，规则在 `ChatHistoryStore.modelVisibleAttachments()`：
判定方式是「这条消息里有没有别的行认它当 source」，而不是硬编码 `kind == VIDEO`——
以后要是图像也长出派生物（比如超大图切片），这里不用改。

反过来，一个 VIDEO 行**没有**任何派生物时不会被过滤，会走到 VIDEO 分支被拒。
那是老数据（v4 之前存进去的视频）应有的下场。

这是整个存储层唯一一处「落盘的不等于模型看到的」，所以在
`VideoNormalizer` 的 KDoc 和这里各说了一遍。

## 八、待办

1. **实机端到端验证**。整条管线只过了编译。要真机上跑的：
   HEIC 导入、带 alpha 的 PNG、m4a 转码、mp3 直通、一个带音轨的 mp4 抽帧、
   以及 **v3 → v4 迁移在老库上跑一遍**（v1 → v2 那次的验证同样还欠着，见 chat-storage.md）
2. **附件 UI 入口**。管线通了但聊天页还没有「加图片」按钮。接线点：
   `AttachmentProvider.ingest(context).ingest(characterId, uri)` → `Progress.Success.refs`
   → `ChatHistoryStore.appendUser(text, attachmentRefs = ...)` → `uploadIfWorthwhile()`。
   进度条直接用 `Progress.Working(percent, stage)`。这一步要加 `coil-compose`
3. **`ChatSession.send()` 收附件**。现在只收 `String`，这是发送路径上最后一道缺口
4. **`Log/schemas/2.json` 缺失**。目录里只有 `1.json` / `3.json` / `4.json`，
   而 chat-storage.md 声称 1/2/3 都入库了。不影响构建，但 Room 的迁移测试要用它
5. **`AttachmentKind.DOC` 只受理 PDF**。docx/xlsx 要先自己解析成文本或图片，另一件事
6. **原生视频输入**。等 Gemini provider 从 stub 变成实现之后再评估，
   届时的选择是 `media3-transformer`（转封装 + 裁剪），不是 FFmpeg

明确**不在**范围内：

- **流媒体（HLS / DASH / 远端 URL）**。附件的定义是「一份确定的、任意时刻可读的字节」，
  适配流是另一个产品功能。判定规则简单化：`MediaExtractor` 打不开就拒绝
- **realtime 语音对聊**。它是和 `ChatProvider` 并列的接口，不进这套存储（见 chat-storage.md 第一节）
- **超时长音视频的截断**。截断会静默吃掉用户后半段的话，所以是明确拒绝 + 告知上限

## 九、顺手记一笔

`app/build.gradle` 的 `readLocalProperties` 会把 `CHAT_CPT_KEY` 明文
`println` 到构建日志里。`local.properties` 本身不入库，但任何一次 CI 构建日志都会带上它。
建议把那行 println 删掉或只打印键名。
