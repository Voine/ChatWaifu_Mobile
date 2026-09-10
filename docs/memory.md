# 记忆分层

> 状态：设计已定，**尚未施工**。本轮范围是 Phase 0 + Phase 1（写到可直接施工的粒度），
> Phase 2 / 3 只列方向。
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

`mainLoop()` 在 `generateAndPlaySound()` **之前**把抽取扔进独立 scope，
让它和 TTS 推理+播报并发。这个窗口是白送的——BV2 单句 RTF≈0.36，一段回复要播好几秒，
这几秒里用户在听、主循环在 `fetchInput()` 挂起，而抽取是网络等待、TTS 是 CPU，
两者重叠得很好。**不占首字延迟**。

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

## 七、Phase 2 / 3 的方向（本轮不做）

**L1 情节摘要**：按时间间隔切 session（gap > 3h 或 App 进后台封一段），
每段一条摘要，带时间戳。同样**用角色第一人称写**，日记体。
最近几条常驻 prompt，更老的走 L3 检索。

**L3 检索**：`LIKE` 扫 `chat_message`，命中的片段注在**最后一条 user 消息之前**
（保住缓存前缀）。到这一步再评估要不要向量——如果那时 `LIKE` 已经慢到能测出来，
`androidx.sqlite:sqlite-bundled` 那条路还在原地等着，而且届时是个孤立的性能改动，
不和记忆功能的正确性纠缠。

**遗忘**：`importance × 时间衰减 × 命中次数` 的综合打分（Stanford generative agents
那套）。等事实库真的长到几百条、`memoryTokens` 明显不够分的时候再说。
现在上等于给一个还没有的问题写一套调不准的启发式。

---

## 八、施工清单

Phase 0（可独立合入，不依赖记忆功能）：

- [ ] `ContextBudget.trim()` 改分块淘汰，返回 `TrimResult`；`ChatSession` 持 `historyStart`
- [ ] `ContextBudget` 拆出 `memoryTokens` 子预算
- [ ] 记忆断层标记追加到 system prompt（不带条数）
- [ ] `MemoryContributor` 接口 + `ChatSession` 调用点（实现留空）

Phase 1：

- [ ] `Log`：`MemoryFactEntity` / `MemoryFactDao` / `RoomMemoryRepository`（全 `internal`）
      + 领域类型 `MemoryFact` + 接口 `MemoryRepository`
- [ ] `Log`：`MIGRATION_4_5`，DB version 4 → 5，追加进 `ALL`
- [ ] `ChatHistoryStore.clearCharacter()` 补 `memory.deleteAll()`
- [ ] `app/data/memory/`：`MemoryExtractor` 接口 + `MemoryOp`
- [ ] `LlmMemoryExtractor`：无历史 `ChatSession` + JSON 解析 + slot 归一化 + 整批丢弃策略
- [ ] `MemoryConsolidator`：Mutex + 轮次闸门 + `runCatching`
- [ ] `Constant.SAVED_MEMORY_MODEL` + 设置页的抽取模型配置项（空 = 跟随主模型）
- [ ] `MemoryContributor` 的实现：读 facts、排序、按预算截断、拼第一人称块
- [ ] `mainLoop()` 在 `generateAndPlaySound()` 前 fire-and-forget 抽取
- [ ] `nav_memory` 页面 + Drawer 入口

## 九、验证

工程目前**无任何单元测试**，所以这一层的验证只能靠手动。三条最小路径：

1. **upsert 收敛**：跟同一个角色说「我在北京」，几轮后说「我搬到上海了」，
   记忆页里应该只有一条「所在城市 = 上海」，不是两条。
2. **缓存前缀稳定**：连续聊 25 轮以上（跨过一次 `EVICT_CHUNK`），
   看 usage 里的 cached token 数——分块淘汰生效的话，20 轮里应该有 19 轮命中。
3. **迁移**：拿一个 version 4 的真机老库升上来，聊天记录一条不少。
   这条尤其要做——`chat-storage.md` 里标着 1→2 的迁移**至今没在真机老库上跑过**，
   4→5 不要再欠一笔。
