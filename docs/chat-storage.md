# 聊天存储层

`Log` 模块 + app 侧附件管理的设计与改造记录。配套文档：[chat-core.md](chat-core.md)（聊天基座抽象层）。

改造起因：`ChatCore` 把「和哪家基座说话」这件事抽象掉了，接下来要接多模态。
但多模态的落点在存储——一轮对话不再只有文本，还有一份**字节**需要有归属地。
在往上加字段之前，先把这一层的地基修一遍。

## 一、先说结论：Room 还是够用的

2026 年重新评估了一遍，结论是**继续用 Room**，理由不是惯性：

| 候选 | 为什么不换 |
|---|---|
| **Room** 2.8.4 | 官方维护、`Flow` 订阅、`@Relation` 表达一对多、schema 导出 + 迁移校验。这个场景要的就是这些 |
| SQLDelight | 优势是 KMP 和 SQL-first。这个工程不跨平台，换过去要重写全部查询，收益只是「SQL 写在 .sq 文件里」 |
| DataStore | 只适合配置项。聊天记录要按角色分区、按时间倒序取最近 N 条、要 join 附件表——这些都不是 key-value 能干的事 |
| Realm | 已停止维护（MongoDB 2024 年宣布 deprecate），不能选 |
| ObjectBox | 快，但闭源 native、包体积 +1MB 左右。当前瓶颈是 440MB 的模型资源，不是数据库 |
| 裸 SQLite | 就是在手写 Room 生成的那部分代码 |

**未来真正会逼着换的是语义召回**（「我们上次聊的那家店叫什么」——要向量检索而不是时间窗口）。
那也不需要换掉 Room：加 `sqlite-vec` 扩展，或者小数据量下直接把 embedding 存成
`BLOB` 暴力算余弦。**明确不在本轮范围内**。

顺带说清一个边界：**realtime 语音对聊不进这套存储**。它是一个和 `ChatProvider`
并列的接口（有状态会话、`input_audio_buffer.append`、服务端 VAD、打断），
产品形态和回合制天然不同。走本地 ASR 落成文本再进这里，
而不是往库里塞一段原始录音——录音的体积和它的复用价值不成比例。

## 二、改造前发现的问题

按严重程度排：

### bug #1：历史窗口取的是**最老**的 200 条

```sql
-- 老实现
select * from ChatMessage where characterName = :name limit :limit
```

`LIMIT` 没有 `ORDER BY`。SQLite 会按 rowid 顺序返回，也就是**最早**的那些行。
后果：任何一段超过 `HISTORY_LIMIT = 200` 条的对话，喂给模型的永远是最开头那 200 条，
新说的话根本进不了上下文。`ChatHistoryStore` 里那句 `.sortedBy { timeline }` 也救不了——
错误的行早在 SQL 里就选完了。

修法是内层倒序取、外层正序还原：

```sql
select * from (
    select * from chat_message where characterId = :characterId
    order by timeline desc, id desc limit :limit
) order by timeline asc, id asc
```

`id` 作为第二排序键：`timeline` 是毫秒时间戳，同一轮里 user 和 assistant 可能撞上同一毫秒。

### bug #2：分页方向反了

`loadOlderChatMessage` 的条件是 `id > :id`，那是往**更新**的方向翻，
和聊天记录页往上滚要「更老」的语义相反。改成 `id < :beforeId`。

### bug #3：记录页整表加载

`ChatLogViewModel` 用 `getAllChatLog(name).reversed()` —— 一次性把某个角色的全部记录
读进内存再反转。历史长了必然卡。改成订阅 `observeChatLog(limit = 200)`。

### 结构性问题

- **Room 实体漏到 app 层**：`IChatLogDbApi` 直接返回 `@Entity` 类，app 侧到处读它的字段，
  加一列就要动多处
- **接口每个方法都有默认空实现**（`{}` / `emptyList()`），实现类写一半也能编译过，静默返回空
- **全同步 API**，而 UI 是 Compose，想要的是订阅
- **没有索引**：`where characterName = ?` 全表扫
- **没有迁移机制**：`version = 1` 从没升过

## 三、改造后的形状

### 模块内分层

```
Log/
  ChatLogEntry.kt        领域类型（public）：ChatLogEntry / ChatLogRole /
                         MessageSource / MessageStatus
  AttachmentRef.kt       领域类型（public）：AttachmentRef / AttachmentKind
  ChatLogRepository.kt   接口（public），无默认实现，suspend + Flow
  room/
    ChatMessageEntity.kt      internal @Entity
    AttachmentEntity.kt       internal @Entity
    MessageWithAttachments.kt internal @Embedded + @Relation
    ChatMessageDao.kt         internal @Dao
    ChatDatabase.kt           internal，version = 3
    Migrations.kt             internal，手写 Migration
    RoomChatLogRepository.kt  public 实现
```

Room 那一包全部 `internal`：**持久化类型不跨模块边界**。app 侧只看得到
`ChatLogEntry` / `AttachmentRef` / `ChatLogRepository`。

领域类型也**刻意不是** `ChatCore` 的 `ChatMessage`：`Log` 不依赖 `ChatCore`
（模块之间无横向依赖），两者之间的映射由 app 侧的 `ChatHistoryStore` 做。

### 表结构

`chat_message`（v2 起）：

| 列 | 说明 |
|---|---|
| `id` | 自增主键 |
| `characterId` | 见下节「characterId 这道缝」 |
| `role` | `USER` / `ASSISTANT` / `SYSTEM` / `TOOL`，存**枚举名**不存 ordinal |
| `text` | |
| `timeline` | 毫秒时间戳 |
| `promptTokens` / `completionTokens` | |
| `providerId` / `model` | 哪个基座、哪个模型产出的。thinking 重放要靠它 |
| `source` | `TYPED` / `ASR` / `REALTIME_TRANSCRIPT` |
| `status` | `STREAMING` / `OK` / `FAILED` |
| `thinkingText` / `thinkingOpaque` | 思考过程，以及基座要求原样回传的签名串 |

索引：`(characterId, timeline)`。

`chat_attachment`（v3 起，v4 加了后六列）：

| 列 | 说明 |
|---|---|
| `id` | 自增主键 |
| `messageId` | FK → `chat_message.id`，`ON DELETE CASCADE`，带索引 |
| `kind` | `IMAGE` / `AUDIO` / `VIDEO` / `DOC` |
| `relPath` | **相对**附件根目录的路径，形如 `<characterId>/<uuid>.jpg` |
| `mime` / `byteSize` | |
| `width` / `height` / `durationMs` | 可空，给 token 估算用 |
| `remoteFileId` / `remoteProvider` / `remoteExpiresAt` | 基座侧 file id 的缓存 |
| `sampleRate` / `channels` | v4。音频参数，判「能不能直接发给某家」要用 |
| `sourceRelPath` / `posMs` | v4。**派生关系**（视频抽帧/抽音轨），见 [media-pipeline.md](media-pipeline.md) 第七节 |
| `origMime` / `origByteSize` | v4。用户**原本**给的形态，归一化会改前面那些值 |

**存相对路径不存绝对路径**：应用专属目录的绝对路径会随
「内部存储 ↔ 外部存储」「多用户」变化，写死进库就等于给自己埋一个「重装后全部附件失效」。

**CASCADE 只清行，不清文件**。文件回收是单独一件事，见 GC 那节。

### 枚举存名字不存 ordinal

`role`、`source`、`kind` 全部存 `name`。存 ordinal 的代价是：往枚举中间插一个值，
整张表的语义就错位了，而且**迁移脚本救不回来**（数据里已经分不出谁是谁）。
反序列化用一个带回落的 helper：认不出来的值不抛异常，落到默认值 + 日志。

### 迁移

`exportSchema = true`，导出的 schema 入库，Room 会用 `identityHash` 校验手写的迁移和
实体声明是否一致——对不上直接崩，而不是在运行时因为缺一列而莫名其妙地失败。
（**`Log/schemas/` 目前只有 `1.json` / `3.json` / `4.json`，缺 `2.json`**，
不影响构建，但迁移测试要用它，补上。）

- `MIGRATION_1_2`：建 `chat_message`，从老 `ChatMessage` 表搬数据。
  `sendFromMe` 布尔列展开成 `role`（`CASE WHEN sendFromMe != 0 THEN 'USER' ELSE 'ASSISTANT' END`），
  `characterName` → `characterId`。**`id` 原样带过去**，这样附件表的外键有稳定的目标。
  然后 drop 老表、建索引
- `MIGRATION_2_3`：建 `chat_attachment` + 索引
- `MIGRATION_3_4`：`chat_attachment` 加六列，纯加列所以走 `ALTER TABLE ADD COLUMN`。
  `origByteSize` 是 NOT NULL，ALTER 必须带 `DEFAULT 0`，实体侧要有配对的
  `@ColumnInfo(defaultValue = "0")` —— 少一边 `identityHash` 就对不上、启动即崩

**没有 `fallbackToDestructiveMigration()`**，这是刻意的：聊天记录是用户资产，
迁移写错了应该崩在开发者脸上，不能静默清库。

自动迁移（`@AutoMigration`）在 1→2 这一步用不了——它只能加/删列，
不能把一个布尔列变换成一个字符串枚举列。

### `@Relation` 而不是 join

```kotlin
internal data class MessageWithAttachments(
    @Embedded val message: ChatMessageEntity,
    @Relation(parentColumn = "id", entityColumn = "messageId")
    val attachments: List<AttachmentEntity>,
)
```

手写 join 会得到笛卡尔积（一条带 3 个附件的消息返回 3 行，文本重复 3 遍），
还得在 Kotlin 里自己 group by。`@Relation` + `@Transaction` 是两次查询后在内存里组装，
省掉这段胶水代码。查询方法必须标 `@Transaction`，否则两次查询之间可能被写入插一脚。

## 四、附件字节：`AttachmentStore`

在 app 模块（`data/attachment/`）而不是 `Log`：它要用 `Context` 和一堆平台媒体 API，
而 `Log` 应该只管数据库。

```
<getExternalFilesDir("attachments")>/<characterId>/<uuid>.jpg
```

和 `ModelStorage` 同一套思路：应用专属外部目录，零权限、卸载自动清理。

### 为什么必须落地成真实文件

SAF 给的 `content://` URI 的授权生命周期绑在那一次 `ACTION_OPEN_DOCUMENT` 上，
进程重启后可能就读不了了；而历史重放要求**任意时刻**都能读到这份字节。
这和 VITS 模型必须导入落地是同一个理由的变体（那边是 ncnn 在 native 层 `fopen`）。

### 为什么归一化而不是原样复制

各家云端 API 对格式、边长、体积、采样率都有硬要求。落盘的这一份副本
**就是模型真正看到的东西**，所以裁剪必须在入库时做完，而不是发送时临时算——
否则历史重放和当初发出去的不是同一份内容。

参数按**最严的 provider** 取：按最严的压，切到任何一家重放都合法；
按宽松的压，切到严格的一家就重放失败。
**只留这一份，不留原件**——留原件能保真，代价是媒体占用翻倍，而 APK 已经 440MB。
（唯一例外是视频：父行存原视频给 UI 回放，模型看的是抽出来的帧。见下。）

### 格式这件事已经搬走了

`AttachmentStore` 现在**只管字节**：落盘（`adopt`）/ 定位（`resolve`）/ 删除 / GC。
它一度同时管图像归一化（解码、降采样、EXIF、JPEG 重编码），加上音视频之后那条路走不通了。

格式知识全在 `MediaProbe` + `MediaNormalizer` 那一族里，**设计和取舍写在
[media-pipeline.md](media-pipeline.md)**，动这一层之前先看它。这里只记结论：

| 类型 | 规范形态 |
|---|---|
| 图像 | 长边 ≤1568，不透明走 JPEG、带 alpha 走 PNG→WebP |
| 音频 | 16kHz 单声道 16bit WAV；mp3 和已经是 16k mono 的 wav 直通 |
| 视频 | **不转码**，抽帧成 N 张图 + 抽音轨成 WAV，父行只给 UI |
| DOC | 只受理 PDF，原样落地 |

改造前这里有个会 OOM 的洞：体积闸门写在 `readBytes()` **之后**，
选一个大文件时 OOM 发生在闸门之前。现在体积从 `OpenableColumns.SIZE` 拿，
整条管线再没有 `readBytes()`。

### 文件回收

两条路，都不依赖数据库的 CASCADE：

- **删角色**：先 `attachmentPathsOf(characterId)` 取路径，再删记录，最后删文件。顺序不能反
- **孤儿 GC**：`gc(referenced)` 遍历磁盘，不在引用集合里的文件全删。
  `referenced` 必须是 `referencedAttachmentPaths()`（**全库**）——
  按角色算会把别人的文件误判成孤儿
- **临时文件**：`clearTemp()`。归一化的中间产物落在 `externalCacheDir`，
  **刻意不在 GC 的覆盖范围内**（否则正在转码的文件会被当孤儿删掉），
  所以要单独扫一次

启动时 GC 和 `clearTemp()` 各跑一次，兜住「写完文件但插库失败」和
「转码中途被杀进程」这两类中间态。

## 五、和 `ChatCore` 的接线

### `ChatHistoryStore`（app 侧）

`ChatLogEntry` ↔ `ChatMessage` 的双向映射都在这里。三条丢弃规则：

- `SYSTEM` 不回传（system prompt 是配置不是记录）；`TOOL` 单独恢复会让请求非法（缺配对的 `ToolCall`）
- `status != OK` 的不回传：半截回复喂回去会让模型接着编
- **`thinkingOpaque` 只在同一个基座下回传**。签名绑基座+模型，跨基座重放直接 400

附件映射时**文件没了就跳过**这个内容块，让消息退化成纯文本——
专属目录的内容用户能在系统设置里清掉，不能因此崩掉整轮对话。

### 流式状态落库

```
beginAssistant()  → 插一行 STREAMING，返回 id
  ...收流...
finishAssistant() → 覆盖成最终文本 + usage + thinking + OK
failAssistant()   → 标 FAILED，保留已收到的片段
```

好处是进程被杀时历史里留下的是一条「答到一半断了」的记录，而不是凭空少一轮。
启动时 `failDanglingStreams()` 把上次留下的 `STREAMING` 收尾成 `FAILED`。

还有一个容易漏的分支：流**正常结束但没有 `Completed`**（provider 只发了几个 text 就断），
这时不抛异常，所以也要显式收尾，否则那行永远停在 `STREAMING`。

### token 估算用真实维度

`ContextBudget` 原来对图片/音频套的是平坦常量。但图片 token 数是**分辨率的函数**，
一张大截图和一张缩略图能差一个数量级，而裁历史正是靠这个数决定丢谁。
所以 `ChatContent.Image` 加了 `width` / `height`，`Audio` 加了 `durationMs`
（`AttachmentStore` 落盘时本来就知道这些值）。

公式取 `w*h/750`：三家里在常见尺寸上偏大的那个。**宁可高估**——
低估会让裁剪以为还塞得下，换来基座一个 `ContextOverflow`。

### 能力检查从 provider 级下沉到模型级

`ModelInfo.supportsImageInput` 之前**声明了但没人读**，能力 gate 全看
provider 级的 `capabilities.imageInput`。而 `OpenAICompatProvider` 的
`imageInput = true`（协议层面确实支持），于是用户选了 `deepseek-chat` 再附一张图，
base64 会一路发到对端换回一个语义不明的 400。

现在加了 `ChatProvider.capabilitiesFor(model)`，把 provider 级能力和 `ModelInfo` 取交集。
**模型不在 `availableModels` 里时回落到 provider 级**——清单只是下拉框候选不是白名单
（用户可以手填 `qwen3:8b`），认不出来的只能按「协议允许」放行，由对端去拒绝。

`ModelInfo` 补了 `supportsFileInput`，默认跟随 `supportsImageInput`：
各家的 PDF 支持实质上是「把页面栅格化后当图看」，视觉模型基本都能收，反之一定不能。

### 预上传

`ChatProvider.upload(bytes, mimeType, fileName): MediaSource.RemoteFileId?`，
**默认返回 null** = 这个基座没有 Files API，调用方继续内联。不抛异常是刻意的：
内联是所有基座都走得通的那条路，上传只是优化。当前只有 `OpenAIResponsesProvider`
真的实现了（`POST /v1/files`，`purpose=user_data`）。

为什么需要它：内联会把附件塞进**每一轮**请求体（历史要重发全量），
一张 3MB 的图 base64 后 4MB，聊十轮就传了 40MB。阈值 1MB
（`MediaUploadPolicy.INLINE_MAX_BYTES`）。

file id 缓存在 `chat_attachment` 的三个 remote 列里，
**按 `relPath` 更新而不是按附件 id**——`updateChatLog` 是「删掉旧附件行再插新的」，
自增 id 不稳定，`relPath` 才是这份字节的身份。

顺手修了 `HttpSupport.HeaderInterceptor`：它原先无条件 `header("Content-Type", "application/json")`,
会把 multipart 的 boundary 一起覆盖掉，上传必然失败。改成只在请求体自己没声明类型时补。

## 六、characterId 这道缝

角色层**没有稳定 uuid**：`CharacterModel.name` 同时是身份、显示名和 `models/` 目录名。
改这件事要动 `meta.json`、内置模型的 `SAVED_*_SETTING` 老 key、导入流程，
不该和存储层重构混在一起。

所以存储层只把 `characterId` 当**不透明键**，app 侧暂时传 `name`。
这是一道**有文档的缝**：将来 `meta.json` 长出 uuid 时，
只改 `ChatHistoryStore` 的映射 + 一次数据迁移，`Log` 模块一行不用动。

（顺带一提：改名会丢历史。这是现状本来就有的行为，不是本次引入的。）

## 七、施工清单

| # | 项 | 状态 |
|---|---|---|
| 1 | 修 `ORDER BY` bug（历史窗口取错行） | ✅ 完成 |
| 2 | `Log` 地基：`characterId` / 索引 / suspend+Flow / 领域类型 / 迁移机制 | ✅ 完成 |
| 3 | `chat_attachment` 表 + `AttachmentStore`（压缩、相对路径、GC、缺文件降级） | ✅ 完成 |
| 4 | 接回 `ChatCore`：多模态落库与恢复、thinking gate、流式 status、真实维度估算 | ✅ 完成 |
| 5 | 能力检查下沉到模型级 + `ChatProvider.upload()` | ✅ 完成 |
| 6 | 本文档 + CLAUDE.md 订正 | ✅ 完成 |
| 7 | 媒体格式兼容层（v4 / 音频转码 / 视频抽帧） | ✅ 完成，见 [media-pipeline.md](media-pipeline.md) |

验证：`:ChatCore:compileDebugKotlin` ✅、`:Log:compileDebugKotlin` ✅、
`:app:compileDebugKotlin` ✅、`assembleDebug` ✅。
**实机端到端联调还没做**，和 `ChatCore` 一样。

## 八、待办

1. **附件 UI 入口**。存储、归一化、映射都通了，但聊天页还没有「加图片」这个按钮。
   接线点：`AttachmentProvider.ingest(context).ingest(characterId, uri)`
   → `Progress.Success.refs` → `ChatHistoryStore.appendUser(text, attachmentRefs = ...)`
   → `uploadIfWorthwhile()`
2. **`ChatSession.send()` 收附件**。现在只收 `String`，这是发送路径上最后一道缺口
3. **迁移的实机验证**。1→2 的数据搬迁、3→4 的加列，都要在真机老库上跑一遍
4. **补 `Log/schemas/2.json`**。见第三节「迁移」
5. **语义召回**。见第一节结尾

~~音频转码~~、~~视频~~ 已经做完，搬到 [media-pipeline.md](media-pipeline.md) 了。

明确**不在**范围内：realtime 会话（另立接口，见第一节）；给角色层加真 uuid（见第六节）；
流媒体格式（见 media-pipeline.md 第八节）。
