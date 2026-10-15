# 记忆分层

> 状态：**Phase 0 ~ 3 均已落地**。Phase 0/1 写于 2026/9/11；2026/10/15 补上
> L1 情节摘要、分层回忆（摘要 → 原文）、时间感、Anthropic 显式缓存，见第七、十节。
> 抽取 / 摘要 / 工具循环有打真实网关的回归测试（`MemoryLiveTest` / `ProviderLiveTest`）。
>
> 相关文档：上下文裁剪在 [`chat-core.md`](chat-core.md) 的 `ContextBudget` 一节，
> 存储层约定在 [`chat-storage.md`](chat-storage.md)。动这一层之前先看那两份。

---

## 一、现在是什么样

全工程唯一的「记忆」机制是 `ChatCore/core/ContextBudget.kt` 的 `trim()`：从历史尾部
往前保留，直到撞上 token 预算（默认 `maxPromptTokens` 24000）或条数上限（100 条）。
`ChatHistoryStore.HISTORY_LIMIT = 200` 又在它上面加了一道——**第 200 条之前的对话
对模型完全不存在**，不是「记得模糊」，是根本没进过 prompt。

两个具体后果：

1. **prompt cache 命中率接近 0**。`trim()` 每轮从头部掉一条，前缀每轮都变。
   各家基座的缓存都是按前缀匹配的，前缀一动整段缓存作废。
2. **模型不知道自己忘了**。裁剪是静默的，模型看到的是一段从中间开始的对话，
   于是它会把缺失的部分编出来。

---

## 二、为什么陪伴型和助理型的记忆不是一回事

这决定了后面所有的取舍，先说清楚。

助理型记忆的 KPI 是**事实召回准确率**（"上次会议决定了什么"），所以业界方案普遍是
向量库 + 语义检索。陪伴型的 KPI 是**关系连续性和人设一致性**——她记不记得你叫什么、
你养的猫叫豆豆、你说过讨厌被叫「主人」。

这类信息有三个性质：**量极小**（几百 token 封顶）、**更新极慢**、**必须每轮都在**。

对「必须每轮都在」的东西上向量检索是错配的：检索不到就等于失忆，而且检索式注入
每轮往 prompt 里塞不同内容，如果注在前面就把上面第 1 条的缓存问题又犯一遍。

所以本设计的重心是**一个小而稳定、每轮常驻的事实块**，不是一个大而全的检索库。

---

## 三、四层模型

按「是否每轮都在 prompt 里」划分，不按学术上的长短期。

| 层 | 内容 | 存哪 | 常驻 prompt | 更新时机 | 本轮 |
|---|---|---|---|---|---|
| L0 工作记忆 | 最近 N 轮原文 | `chat_message` | 是 | 每轮 | **Phase 0** |
| L1 情节摘要 | 按 session 切分的段落摘要 | `memory_episode` | 最近几条 | session 结束 | Phase 2 |
| L2 事实/画像 | 结构化槽位 | `memory_fact` | 是（限额） | 抽取时 upsert | **Phase 1** |
| L3 归档 | 全量原文 | `chat_message` | 否，检索命中才注入 | — | Phase 3 |

**L2 是整个方案的重心**，投入产出比最高。L1 之前先把 L2 的抽取 prompt 调好——
情节摘要的质量依赖同一套抽取能力，两边同时做容易两边都不准。

---

## 四、三个已确认的决策

这三条是拍过板的，不是开放问题，重开话题时不要再当选项讨论。

### 4.1 记忆按角色隔离，不跨角色共享

不做「用户画像全局共享 + 关系记忆隔离」的双层 scope。记忆表的 scope 维度就是现有的
`characterId`——它当前传的是**角色名**（角色层还没有稳定 uuid，见
`chat-storage.md` 第六节那道有文档的缝），存储层只当它是不透明键。
将来 `meta.json` 长出 uuid 时，这里和 `chat_message` 一起迁移。

代价要说清楚：同一个用户对三个角色各说一遍自己叫什么。这是刻意的——
角色之间「互相知道你跟别人说过什么」在陪伴场景里是破坏感的。

### 4.2 抽取走接口，模型可单独配置

`MemoryExtractor` 是接口，第一个实现走 LLM，**为后续换端上本地模型预留**。
抽取模型支持单独配置，没配就回落当前主模型。

### 4.3 L3 不上 FTS

`LIKE` 全表扫。理由是数据量——个人聊天记录重度使用几年也就几万行，
现代手机上扫几千到几万行是个位数毫秒，而检索发生在网络请求之前，
这点耗时会被随后的 LLM 往返彻底淹没。

顺带记一笔**排除掉的方案**，省得以后重新论证：SQLite 的 `trigram` tokenizer 要 3.34+，
Android 自带的 SQLite 严重滞后，要保证所有设备都有得把 minSdk 推到 34 左右
（覆盖率约 54%）——升 minSdk 换不到它。真要确定版本得用
`androidx.sqlite:sqlite-bundled` 自带一份。而且 `trigram` 索引三字符序列，
中日文大量是二字词，它对这个工程的语言环境本来就不合适。

---

## 五、Phase 0：让截断不再毁缓存

三个改动，都在 `ChatCore` 里，**不依赖记忆功能本身**，可以先合。

### 5.1 分块淘汰

`ContextBudget.trim()` 现在每轮掉一条。改成**超预算时一次砍掉一块**
（`EVICT_CHUNK = 20`），然后连续 20 轮前缀完全稳定。

这需要 `trim` 记住上一次的起点，所以签名要带上它：

```kotlin
// ContextBudget.kt
data class TrimResult(
    val messages: List<ChatMessage>,
    /** 本次实际使用的起点，调用方要存下来下轮传回 */
    val startIndex: Int,
)

fun trim(
    history: List<ChatMessage>,
    systemPrompt: String?,
    previousStart: Int = 0,
): TrimResult
```

语义：先用 `previousStart` 试算，**塞得下就原样返回**（前缀不变，缓存命中）；
塞不下才把起点按 `EVICT_CHUNK` 往后推，推到塞得下为止。起点**只增不减**。

`ChatSession` 持有 `private var historyStart = 0`，`restore()` 时重置为 0。

两条既有的结构约束不变，仍然要遵守（不满足会被基座直接拒）：
首条必须是 user 消息；`ToolCall` 和配对的 `ToolResult` 不能被切散。
推起点之后仍要跑一遍现有的「丢掉开头不成对的 assistant / tool 残片」。

### 5.2 记忆断层标记

`historyStart > 0` 时，往 system prompt 尾部追加一句
「（更早的对话已省略，必要时依据下面的记忆作答，不要编造）」。

**刻意不带条数**——带了 `N` 就是每次淘汰都改一次 system prompt，
而 system prompt 在所有基座里都是缓存前缀的一部分。不带条数的话，
从「无标记」到「有标记」只变一次，之后永远稳定。

放 system 区而不是插一条伪消息：伪消息会破坏「首条必须是 user」这条约束，
还得额外处理它和 `trim` 的关系。

### 5.3 `MemoryContributor` 钩子

`ChatCore` **不能碰 Room**（它现在对存储一无所知，这个边界要守住）。所以留一个接口，
实现放 app 层：

```kotlin
// ChatCore/core/MemoryContributor.kt
fun interface MemoryContributor {
    /** 返回要注入 system 区的记忆块。每轮请求前调用一次，实现方自己做缓存。 */
    suspend fun memoryBlock(budgetTokens: Int): String?
}
```

`ChatSession` 多一个可空构造参数，默认 `null`（no-op）。`send()` 里在
`options.toRequest(...)` 之前拿一次，拼到 `systemPrompt` 后面。

Phase 0 只落接口和调用点，实现留到 Phase 1。

### 5.4 预算拆分

`ContextBudget` 从「一个总额」变成「按用途分配」，各层超支各自降级而不是互相挤：

```kotlin
class ContextBudget(
    val maxPromptTokens: Int = DEFAULT_MAX_PROMPT_TOKENS,
    val maxMessages: Int = DEFAULT_MAX_MESSAGES,
    /** 记忆块的硬上限。给多了会挤掉工作记忆，给少了记不住事 */
    val memoryTokens: Int = DEFAULT_MEMORY_TOKENS,   // 600
)
```

`memoryBlock(budgetTokens = memoryTokens)` 把上限传下去，
超了由实现方按优先级砍（见 6.4），不由 `trim` 来砍。

---

## 六、Phase 1：L2 事实层

### 6.1 schema v5

DB 现在是 **version 4**（上游 3→4 加的是媒体管线的附件列）。记忆表落 **5**。

```sql
CREATE TABLE `memory_fact` (
  `id`              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `characterId`     TEXT    NOT NULL,
  `slot`            TEXT    NOT NULL,
  `content`         TEXT    NOT NULL,
  `importance`      INTEGER NOT NULL DEFAULT 0,
  `pinned`          INTEGER NOT NULL DEFAULT 0,
  `sourceMessageId` INTEGER,
  `createdAt`       INTEGER NOT NULL,
  `updatedAt`       INTEGER NOT NULL,
  FOREIGN KEY(`sourceMessageId`) REFERENCES `chat_message`(`id`) ON DELETE SET NULL
);
CREATE UNIQUE INDEX `index_memory_fact_characterId_slot` ON `memory_fact` (`characterId`, `slot`);
```

四个字段值得单独说：

**`slot`（语义槽位键）+ UNIQUE 约束**是整个设计的关键。它让「upsert」成为**数据库层
强制的**，而不是指望抽取器自觉。用户说「我搬到上海了」，抽取器输出
`UPSERT(slot="用户所在城市", content="上海")`，直接覆盖掉原来的「北京」。
没有这条约束，事实库会随时间长出一堆自相矛盾的行，然后模型开始精分。

**`sourceMessageId` 用 `ON DELETE SET NULL` 而不是 `CASCADE`**——和附件那张表刻意相反。
附件是「这条消息的一部分」，消息没了字节也该没；记忆是「从这条消息**派生**出来的独立事实」，
删掉一条三个月前的聊天记录不该让「用户养了只猫叫豆豆」跟着消失。

**`pinned`** 是用户在记忆页手动固定的行，**抽取器的 DELETE 对它无效**。
这是给用户的兜底：模型判断失误要删掉重要的事时，用户有办法钉住。

**`importance`** 只用于超预算时的排序，不参与检索。范围 0-100，抽取器给。

迁移照 `Migrations.kt` 现有风格手写，追加 `MIGRATION_4_5` 到 `ALL`。
**仍然不加 `fallbackToDestructiveMigration()`**——记忆和聊天记录一样是用户资产。

> 注意 `MIGRATION_3_4` 的 KDoc 里那条教训同样适用：`CREATE TABLE` 里写的
> `DEFAULT 0` 必须和实体上的 `@ColumnInfo(defaultValue = "0")` 一一对应，
> 否则 Room 校验 identityHash 对不上、启动即崩。

### 6.2 `Log` 模块的对外形状

沿用现有约定：`room/` 那一包全部 `internal`，只暴露领域类型和仓库接口，
**没有默认实现**（老 `IChatLogDbApi` 每个方法都有默认空实现，写一半的实现类能编译
通过并静默返回空，排查很痛，这个坑不要再踩）。

```kotlin
// Log/MemoryFact.kt
data class MemoryFact(
    val id: Long = NO_ID,
    val characterId: String,
    val slot: String,
    val content: String,
    val importance: Int = 0,
    val pinned: Boolean = false,
    val sourceMessageId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

// Log/MemoryRepository.kt
interface MemoryRepository {
    /** 按 pinned 降序 → importance 降序 → updatedAt 降序 */
    suspend fun facts(characterId: String): List<MemoryFact>
    fun observeFacts(characterId: String): Flow<List<MemoryFact>>
    /** 按 (characterId, slot) upsert。pinned 行的 content 也会被更新，只有删除受保护 */
    suspend fun upsert(fact: MemoryFact)
    /** @return 实际删除的行数；pinned 行不会被删 */
    suspend fun deleteBySlot(characterId: String, slot: String): Int
    /** 用户在记忆页手动删，无视 pinned */
    suspend fun deleteById(id: Long)
    suspend fun setPinned(id: Long, pinned: Boolean)
    /** 删角色时一起清 */
    suspend fun deleteAll(characterId: String)
}
```

`ChatHistoryStore.clearCharacter()` 里要补一次 `memory.deleteAll()`——
`memory_fact` 没有到角色的外键（角色层没有表），不会被任何 cascade 带走。

### 6.3 抽取

```kotlin
// app/data/memory/MemoryExtractor.kt
interface MemoryExtractor {
    suspend fun extract(
        recentTurns: List<ChatMessage>,
        existingFacts: List<MemoryFact>,
    ): List<MemoryOp>
}

sealed interface MemoryOp {
    data class Upsert(val slot: String, val content: String, val importance: Int) : MemoryOp
    data class Delete(val slot: String) : MemoryOp
}
```

**必须把 `existingFacts` 一起喂进去**，否则模型只会不断 ADD，产生同义槽位
（「猫的名字」和「宠物名」并存）。让它在已有槽位上做增量操作是唯一能收敛的形态。

`LlmMemoryExtractor` 的实现要点：

- 复用 `ChatCore`——新建一个**不带历史**的 `ChatSession`，`systemPrompt` 是抽取指令，
  一轮问答拿 JSON。零新增网络代码。
- `ChatOptions.model` 用抽取专用模型；没配就用主 session 的 model。
  配置存 SharedPreferences，key 加进 `Constant`（`SAVED_MEMORY_MODEL`），
  沿用 `ChatProviderSettings` 那套按 provider 分开存的形状。
- **解析失败就整批丢弃**，不做部分应用。半个 JSON 解出来的 `Delete` 可能删掉正确的事实。
- 输出的 `slot` 要做归一化（trim + 长度上限），防止模型吐出一个 200 字的 slot 名把索引撑坏。

抽取指令里要写死的几条（否则事实库会被垃圾撑满）：
只记**长期有效**的事实，不记这轮对话的内容本身；不记模型自己说过的话；
不确定就不记；每次操作数上限（比如 5 条）。

### 6.4 注入

注入端是 `MemoryContributor` 的实现，产出的块形如：

```
【关于对方，你记得这些】
- 名字：小明
- 养了一只叫豆豆的猫
- 不喜欢被叫「主人」
```

三条纪律：

1. **用角色第一人称的口吻写**，不要第三人称分析腔（"用户于 X 时间提到职业变动"）。
   分析腔的记忆块注进去会显著拉垮 roleplay 质量，这在陪伴类应用里是能直接听出来的。
2. **超预算按 `pinned` → `importance` → `updatedAt` 砍尾部**，不做摘要压缩
   （压缩要再调一次模型，为 600 token 不值得）。
3. **放 system 区**，跟着 system prompt 一起进缓存前缀。它变化很慢（只在抽取产生
   实际变更时才变），所以放前面是划算的——这点和 L3 的检索片段正好相反，
   那种每轮都不同的东西将来要注在**最后一条 user 消息之前**。

### 6.5 什么时候跑

**TTS 播报窗口为主，每 N 轮为闸门**，两者是「何时」和「是否」的关系：

`CompanionRealResponseDriver` 在回复落库之后、等 TTS 播完之前把抽取扔进独立 scope，
让它和 TTS 推理+播报并发。这个窗口是白送的——一段回复要播好几秒，
这几秒里用户在听，而抽取是网络等待、TTS 是 CPU，两者重叠得很好。**不占首字延迟**。
（2023 年的 `mainLoop()` 已在 T1 删除。）

闸门：`turnsSinceLastRun >= MIN_TURNS`（起步值 3）才真的跑。一轮一次既浪费 token
也没有信息量——单轮对话里能提取的稳定事实很少。

实现放 `app/data/memory/MemoryConsolidator.kt`：

- 持一个 `Mutex`，**同时只允许一次抽取在跑**。主循环比抽取快得多，不加锁会堆叠。
- `runCatching` 全包住。**抽取失败绝不能影响主循环**——记忆是增强，不是正确性。
- 取最近 `MIN_TURNS * 2` 轮喂进去，不是全量历史。

### 6.6 记忆页

新增 `nav_memory`（Fragment + ComposeView，和现有五个页面同构），入口挂在
`ChatWaifuDrawer`。按当前角色 `observeFacts()` 订阅，支持编辑 content、删除、
pin/unpin、手动新增。

**这一页不是锦上添花，是 Phase 1 的必要组成**：记忆系统出问题时
（记错了、记重了、该忘的没忘），没有这一页你根本无法定位——
唯一的可观测面就是角色说话变奇怪了。而且对陪伴类应用来说，
「能看见并修改她记得什么」本身就是个卖点。

---

## 七、Phase 2 / 3：情节摘要与分层回忆（2026/10/15 落地）

### 7.1 三条拍板（和用户对齐过，不要再当开放问题）

1. **切段 = 时间 + 条数结合**。间隔超过 3 小时就是两次聊天（日记感）；一段聊到
   40 条还没断就先切一块（兜底 L0 淘汰：工作记忆最多 100 条，被挤出窗口的原文
   一定已经写进了日记，不会出现「窗口里没有、日记里也没有」的空洞）
2. **回忆分层**：摘要够用就不碰原文，需要原话时再顺着摘要去定位原文
3. **她自己说过的话要记**——但记在日记（摘要层）里，不进 L2 事实层。
   她答应过什么、你们的约定，是关系连续性的一部分；她随口编的胡话在摘要这一步
   会被压成一句或者略掉，成本可控。L2 仍然只记对方的事实

### 7.2 L1 情节摘要

`memory_episode` 表（**DB v6**）：`characterId` + 消息区间 `[startMessageId, endMessageId]`
+ 时间 + 第一人称日记 `summary` + 检索用 `keywords`。

- **消息区间是「回到原文的指针」不是外键**：删聊天记录不该连带删日记
- **游标就是 `max(endMessageId)`**，不另存状态。摘要失败不插行，下次自然重试
- **用户删日记是软删除**（summary 置空）：真删最新那行游标会回退，用户想忘掉的那段
  又被重新写回来
- 生成：`LlmEpisodeSummarizer`，和 L2 抽取同一个形状（无历史的一次性 `ChatSession`，
  模型走 `SAVED_MEMORY_MODEL`）。**关键词要求用聊天里的原词**——它以后是拿去匹配用户原话的
- 时机：每轮结束后（和 L2 一起在 TTS 窗口里）+ Companion 准备会话时（隔几小时再打开 App，
  上一段在这时封存）。绝大多数时候没有可封存的段，只是一次本地查询

### 7.3 分层回忆（原 L3）

| 层 | 什么时候 | 怎么进上下文 | 成本 |
|---|---|---|---|
| 常驻 | 每轮 | 最近 3 篇日记跟 L2 事实一起在 system 区 | 进缓存前缀，几乎免费 |
| 自动想起 | 用户原话里出现某篇老日记的关键词 | 附在本轮 user 消息前（最多 2 篇，带「回忆#id」） | 零往返 |
| 搜日记 | 模型觉得该记得但上面都没有 | 工具 `search_memories(query)` | 一次往返 |
| 读原文 | 摘要说不清、需要原话 | 工具 `read_original(episode_id)` | 一次往返 |

**自动想起的匹配方向是反的**：拿日记的关键词去原话里 `contains`，而不是从原话里切词去搜。
中文不分词时从原话切不出靠谱的检索词，而关键词是摘要时用原词挑好的。
仍然是 `LIKE` 级别的子串匹配，不上 FTS（4.3）。

**工具只在需要时才付延迟**。最常见的「她记得上次聊过的事」由前两层覆盖，零往返；
工具那两层模型自己判断要不要用。工具循环在 `ChatSession` 里（`ToolHandler`），
中间的 tool_use / tool_result **不进历史**，下一轮前缀不变。
`read_original` 校验回忆归属当前角色（id 是模型给的，不校验就能读到别的角色的记录）。
模型不支持工具调用时（`capabilitiesFor(model).toolCalling == false`）不声明工具，只剩前两层。

### 7.4 时间感

每轮附在 user 消息前的备注（`CompanionTurnContext`，实现 `ChatCore` 的 `TurnContextContributor`）：

```
（系统备注，不是对方说的话）现在是 10月15日 周三 23:10，距离上次聊天过去了 4 天。
你想起了以前的事：
- [回忆#12 · 9月3日] ……
```

每轮都不同，所以**不能放 system 区**。备注生成后**跟着那条消息留在会话历史里**、以后原样重发：
只在当前轮临时拼上的话，上一轮写进缓存的前缀（带备注）和这一轮（不带）对不上，
滚动缓存每轮被打穿。备注不进 `snapshot()`——落库和记忆抽取看到的仍是原话。

### 7.5 Anthropic 显式缓存

三家里只有 Anthropic 要显式打 `cache_control`，之前一处都没打，**分块淘汰在这一家的收益是零**。
现在每个请求两个断点：system 末尾（人设 + 记忆）+ 最后一条消息末尾（滚动）。
实测（`ProviderLiveTest`，~5k token 人设）：第 2、3 轮 `cachedTokens` ≈ 4.9k。
顺带把 Anthropic 的 `promptTokens` 统一成「含缓存」的口径（它的 `input_tokens` 不含缓存读写）。

### 7.6 对照 Hermes Agent / OpenClaw 补的四条（2026/10/16）

读过两家源码（本地在 `_ref/`，不入库）。它们是助理型，记任务进度 / 决策 / 命令，
检索偏精确字符串，**整体不照搬**；但下面四条工程做法通用，已经落地：

1. **会话内冻结记忆块**（抄 Hermes 的 frozen snapshot）。抽取写入事实后**不再**让记忆块失效，
   只在准备会话、新日记封存时刷新。改之前每 3 轮抽一次就把 system 区改一次，Claude 的缓存
   从头写一遍。刚抽出的事实来自眼前的对话，原文还在工作记忆里，冻结不丢信息。
   模拟器实测（5 轮，第 3 轮后抽取写了 5 条事实）：第 2~5 轮 `cached` 依次
   2035 / 2229 / 2489 / 2730，抽取前后都没断
2. **Claude 缓存有效期 1h**（Hermes 对「人在打字的会话」的选择）。陪伴聊天两句之间
   常超过 5 分钟。写入 2×（原 1.25×），读 0.1× 不变
3. **抽取写陈述事实不写指令**（Hermes 的记忆指引）：「喜欢被叫小林」✓「以后都要叫他小林」✗。
   后者注进 system 区等于变相改人设。`MemoryLiveTest` 守着
4. **单批最多删 2 条**（OpenClaw dreaming 单次最多移除 25%）。删除是唯一不可逆的操作

刻意没借的：OpenClaw 的压缩前 memory flush（我们靠「40 条一段 < 工作记忆 100 条」兜底，
不用多跑一轮）、向量 + BM25 混合检索（陪伴场景「换个说法也要想起」确实是现在关键词匹配的弱点，
但 OpenClaw 自己在没有 embedding 时退化成纯关键词，说明这条路可以以后孤立地加）、
Skills / 群聊隔离 / 多 profile（场景不需要）。

**网关的一个坑**：AthenAI 会把同一个 Claude 模型路由到不同后端（直连 / Bedrock），
缓存不跨后端共享，偶尔会有一轮 `cached=0`。不是我们的 bug，`ProviderLiveTest` 的断言已放宽。

### 7.7 第二轮：换说法召回、约定、自我设定、便宜模型、长程评测（2026/10/16）

**换说法也能想起（便宜版）**：写日记时关键词 = 原词 + 别名 / 上位词
（「豆豆」→「橘猫」「猫」「宠物」），上限 12 → 16。零额外调用，替代向量检索的第一步。

**L2 分三类**（`memory_fact.subject`，**DB v7**，`MIGRATION_6_7` 加 `subject` / `dueAt`）：

| subject | 记什么 | 注入 |
|---|---|---|
| `USER` | 对方的长期事实（原来的 L2） | 【你记得关于对方的这些事】 |
| `PROMISE` | 双方约定、她答应过的事、待跟进；能推算日子的带 `dueAt` | 【你们之间的约定……做完了就不用再提】 |
| `SELF` | 她在聊天里给自己定的设定（「我喜欢晴天」） | 【你自己说过的关于自己的事（和人设冲突时以人设为准）】 |

- 三类共用 `(characterId, slot)` 唯一索引，抽取指令里让 SELF 的 slot 以「我的」开头、
  PROMISE 以「约定：」开头，避免撞名
- 抽取器拿到「今天是哪天」，把「下周一」换算成 `due`；换算不了就不给，不丢整条
- **到期提醒**：`dueAt` 落在 ±36 小时内的约定，在本轮备注里点一句「找个自然的时机提起」，
  **每个约定每次会话只提醒一次**。完成 / 取消后由抽取器 delete（受单批删除上限约束）

**记忆模型默认取便宜档**：设置页的记忆模型空着时，不再跟随主模型，而是用这家标了
`ModelInfo.cheap` 的那一档（Claude → Haiku 5.5、Gemini → Flash Lite、OpenAI → nano、
兼容基座 → `deepseek-v4-flash`）。兼容基座只在主模型本身是 deepseek 时才换——
它对面是什么服务由用户决定，对着 Ollama 发 `deepseek-v4-flash` 只会 404。
`ModelInfo.requestExtras` 让 V4 flash 默认带 `thinking: disabled`（不关的话每次先吐一大段推理）。

**长程评测 `LongHorizonMemoryLiveTest`**：整套记忆组件按 App 的接法串起来，虚拟时钟回放
「12 次会话、跨 45 天」的剧本（自我介绍 → 面试 → 拿 offer → 约好入职唱歌 → 搬家 → 入职），
一周后开新会话抽查 9 项（名字 / 城市 / 宠物 / 换说法召回 / 喜好 / 约定 / 面试日期 / 住处 /
自我设定一致）。全程 `deepseek-v4-flash`，一次约 80 秒。门槛 3/4。

调出来的两个问题：
1. 抽取在改写已有槽位时会把日期弄丢（「9月10号面试」被重写成「9月11号」）→ 指令加
   「更新时保留仍然成立的细节」
2. 隔了一周再来，她会只顾着说「一周没声了」而不回答问题（5~6 / 9）→ 备注里加
   「先回应对方这句话本身，时间是背景不是话题」，之后 7~9 / 9

### 7.8 遗留

**遗忘**：`importance × 时间衰减 × 命中次数` 的综合打分（Stanford generative agents
那套）。等事实库真的长到几百条、`memoryTokens` 明显不够分的时候再说。
现在上等于给一个还没有的问题写一套调不准的启发式。

---

## 八、落地清单

Phase 0（`ChatCore`，不依赖记忆功能）：

- [x] `ContextBudget.trim()` 分块淘汰（`EVICT_CHUNK = 20`），返回 `TrimResult`；
      `ChatSession` 持 `historyStart`，`restore()` / `clear()` 时归零
- [x] `ContextBudget` 拆出 `memoryTokens = 600` 子预算，`trim` 先扣掉它
- [x] 断层标记追加到 system prompt（`ChatSession.composeSystemPrompt`，不带条数）
- [x] `MemoryContributor` 接口 + `ChatSession.memory` 调用点

Phase 1：

- [x] `Log`：`MemoryFact` / `MemoryRepository`（public）+
      `MemoryFactEntity` / `MemoryFactDao` / `RoomMemoryRepository`（`internal`）
- [x] `Log`：`MIGRATION_4_5`，DB 4 → 5，schema 5.json 已导出
- [x] `ChatHistoryStore.clearCharacter()` 补 `memory.deleteAll()`
- [x] `app/data/memory/`：`MemoryExtractor` + `MemoryOp`
- [x] `LlmMemoryExtractor`：无历史 `ChatSession`、`temperature = 0`、
      JSON 围栏宽容解析、slot 截断、整批丢弃
- [x] `MemoryConsolidator`：`tryLock` + `MIN_TURNS = 3` 闸门 + `runCatching`
- [x] `Constant.SAVED_MEMORY_MODEL` + 设置页配置项（空串是有效值 = 跟随主模型）
- [x] `FactMemoryContributor`：读 facts、按预算截断、第一人称块、带缓存
- [x] `mainLoop()` 在 `generateAndPlaySound()` 前 fire-and-forget
- [x] `nav_memory` 页面 + Drawer 入口 + 改写后让注入缓存失效

Phase 2 / 3（2026/10/15）：

- [x] `Log`：`MemoryEpisode` / `EpisodeRepository` + `MemoryEpisodeEntity` / `Dao` /
      `RoomEpisodeRepository`；`MIGRATION_5_6`，DB 5 → 6，schema 6.json 已导出；
      `ChatLogRepository` 加 `getChatLogAfter` / `getChatLogRange`
- [x] `EpisodeSegmenter`（时间 + 条数）、`LlmEpisodeSummarizer`、`EpisodeConsolidator`（游标 + 软删除）
- [x] `MemoryBlockContributor`（原 `FactMemoryContributor`）：事实 + 最近 3 篇日记，
      `DEFAULT_MEMORY_TOKENS` 600 → 1200
- [x] `ChatCore`：`TurnContextContributor`（备注随消息留在历史）、`ToolHandler` + 会话内工具循环、
      `ChatSession.dropLast()`（重试不再整段 restore，保住备注和裁剪起点）
- [x] `CompanionTurnContext`（时间感 + 自动想起）、`MemoryRecallTools`（搜日记 / 读原文）
- [x] Anthropic `cache_control` 两个断点
- [x] 记忆页加「日记」一节（改摘要 / 删除）；删角色时连日记一起清

### 施工中做的两个补充决定

**手动添加的事实默认 `pinned = true`**。用户特意敲进来的东西，不该被抽取器
下一轮判定失效就删掉。

**`DELETE` 的 pinned 保护写在 SQL 的 `where` 里**（`deleteBySlotUnpinned`）而不是
先查再判断——后者是两次查询，中间用户正好在记忆页 pin 住就会漏掉。

**`upsert` 手写分支而不是 `@Insert(onConflict = REPLACE)`**：REPLACE 是「删了再插」，
会换掉自增 id、丢掉 `createdAt` 和用户设的 `pinned`，还会触发外键动作。

## 九、验证

**2026/10/15 补的回归**（`ATHENAI_KEY` 环境变量存在时才跑）：

- `app` 的 `MemoryLiveTest`（Haiku 5.5）：北京 → 上海收敛成一条 ✓；养猫事实在「送给妹妹」后失效 ✓；
  纯闲聊不长事实 ✓；日记记下「我答应入职那天给对方唱歌」，关键词含「杭州」「面试」原词 ✓
- `ChatCore` 的 `ProviderLiveTest`：四种协议的会话工具循环都能跑通；Anthropic 第 2 轮起命中缓存
- 不走网络的：`EpisodeMemoryTest`（切段 / 游标 / 软删除不复写 / 自动想起 / 工具不串台）、
  `ChatSessionTest`（备注随历史重发、不进 snapshot、工具循环）

以下是 Phase 1 当时的记录：

已经过的：`assembleDebug` 通过；`:app:lintDebug` **0 error / 196 warning，
和不含本功能的基线完全一致**（stash 后重跑对比过），零新增；
Room 导出了 `schemas/.../5.json`，说明实体和手写迁移是一致的。

**没过的：真机端到端一次都没跑。** 工程目前无任何单元测试，
所以下面三条只能手动验，都还欠着：

1. **upsert 收敛**：跟同一个角色说「我在北京」，几轮后说「我搬到上海了」，
   记忆页里应该只有一条「所在城市 = 上海」，不是两条。
2. **缓存前缀稳定**：连续聊 25 轮以上（跨过一次 `EVICT_CHUNK`），
   看 usage 里的 cached token 数——分块淘汰生效的话，20 轮里应该有 19 轮命中。
3. **抽取真的会跑**：聊 3 轮以上，看 logcat 里 `MemoryConsolidator` 的
   `applied N memory op(s)`。跑不起来最可能的两个原因是 provider 没配好
   （抽取器返回 null 会安静跳过）和模型没按 JSON 格式输出（日志里有原始回复前 200 字）。

> 迁移这条**按用户指示跳过**：这个库还没有实际使用、没有老用户，
> 出现结构性变更也不需要兼容老数据。`MIGRATION_4_5` 仍然写了（纯建表，十行的事），
> 这样开发机上的库能平滑升上来，也不用破坏工程「不加
> `fallbackToDestructiveMigration()`」的既有约定。
