# ChatWaifu 2.0 收尾路线图

> 写给**接手这份工作的另一台机器上的 agent**。读完这份就能开工，不需要原对话。
>
> 配套文档：[companion-ui-construction-plan.md](companion-ui-construction-plan.md)（Phase 1~2.8 的施工史与验收记录，
> 第 12 节是逐批次的交接日志）、[chat-core.md](chat-core.md)、[chat-storage.md](chat-storage.md)、
> [media-pipeline.md](media-pipeline.md)、[memory.md](memory.md)。
> 根目录 `CLAUDE.md` 是工程约定（构建脚本、依赖管理、Edge-to-edge 等），动手前先读。

---

## 进度总览（2026-10-16 盘点，HEAD `3911955`）★新会话先看这一节

> **这是全工程进度的唯一权威入口。** 之前几次长会话的进度散落在各专项文档里，
> 新会话一度误判成「Companion UI 尚未实施」从头规划。以后每完成一批，**先改这张表**，
> 再去专项文档写细节。

| 任务 | 状态 | 细节在哪 |
|---|---|---|
| Companion 前台（Phase 1 ~ 2.8 + T1 转正） | ✅ 完成，`CompanionActivity` 是正式前台，debug 入口已删 | `companion-ui-construction-plan.md` 第 12 节 |
| T2 按句流水线（首声 38~43s → 18~23s，模拟器） | ✅ 完成 | 本文 T2 |
| T3 翻译并入流水线 | ✅ 完成（选项 C） | 本文 T3 |
| T4 Anthropic / Gemini provider | ✅ 完成，四家真实网关联调过 | `chat-core.md` |
| T5 记忆 L1 日记 / 分层回忆 / 时间感 / 三类事实 / 长程评测 | ✅ 完成（DB v7） | `memory.md` 第七~九节 |
| T6 多模态入口 | ✅ 完成（2026-10-16）：图片 / 视频 / 音频 / PDF 全有入口，按模型能力 gate，换模型后历史重放自动降级 | 本文 T6、`media-pipeline.md` |
| Companion 历史分页 + 流式写库节流 | ✅ 完成（2026-10-16） | `companion-ui-construction-plan.md` 第 12 节 |
| T7 真机验证 | ❌ 未做，全部验收都在模拟器 `CW_API36_4k` + JVM 联调 | 本文 T7 |
| **Live2D 表演力（表情 / 动作 / 情绪 / 真实口型）** | 🔒 **刻意留白**，所有者在单独调研，**不要碰** | 本文前提 2、第 5 节 |

**还能继续优化、且不碰表演课题的余项**（按建议顺序）：

1. T7 真机验证（被小米机的「USB 安装」开关挡着，需要所有者在手机上操作）
2. 记忆在设备上的端到端：`memory.md` 第九节三条手动验证；遗忘打分按 7.8 节暂缓
3. **BV2 遇到英文 / markdown 符号会抛 `IndexOutOfBounds`，长回复里还有一次 native 崩溃**（2026-10-16 实测，见施工日志），属 TTS 前处理，需单独排查
4. 技术债（第 3 节），不专门清 lint

**测试现状**：21 个 JVM 测试文件（app 15 / ChatCore 5 / VITS 1），159 个用例；
联调用例无 `ATHENAI_KEY` 时跳过。**0 个 instrumentation 测试。**

**已过时、仅作历史参考的文档**：`project-handoff.md`（停在 2026-09-13 / Phase 2.6）、
`companion-ui-construction-plan.md` 第 1~11 节（最初的 Phase 1 规划）。

---

## 0. 这份路线图的前提（**非常重要，决定了很多取舍**）

以下四条是项目所有者明确拍板的，**不要自行推翻，也不要"顺手照顾一下"**：

1. **不考虑 1.0 老版本兼容。** 2.0 会作为一个大版本统一发布。1.0 是旧时代开源出去的粗糙作品，
   不值得为它的存量数据和交互习惯付出设计成本。
   → 直接后果：**Room 迁移可以不验老库**、**老 UI 可以直接删而不是并存**、
   **老 SharedPreferences key 可以不迁**。遇到"要不要兼容旧数据"的岔路，一律选不兼容。

2. **暂不做 Behavior / 表演系统。** 角色表演（表情、动作、情绪、真实口型）是**单独开的课题**，
   由另一个 agent 专门负责。
   → 直接后果：**不要碰** `LipsValueHandler` 的 `USE_REAL_LIP_SYNC`、不要动 Live2D renderer、
   不要实现 `behaviorProfileId`、不要往 `CharacterPackage` 里加表现相关字段。
   本路线图里所有任务都必须和那个课题**无交集**，避免两边打架。

3. **暂不为商业化做减法。** 所有者的判断是「先把效果拉到上限，自己用得下去了再谈商业化」。
   → 直接后果：**APK 体积不是当前优先级**。378MB/462MB 不好看，但按需下载会引入模型管理、
   版本 gate、断点续传一整套复杂度，现在做是过早优化。记录在第 5 节「明确暂不做」里。

4. **效果优先于规模。** 判断一个任务要不要做，标准是「它能不能让这个 app 更好用」，
   而不是「它能不能让这个 app 更容易卖」。

---

## 1. 当前状态速览（2026/10）

### 已经做完的（Phase 1 ~ 2.8）

| 能力 | 状态 | 在哪 |
|---|---|---|
| Companion 四态前台壳 | 完成 | `ui/companion/`（main source set） |
| 流式上屏 + 精确播放完成 | 完成 | `CompanionViewModel` + `SoundPlayHandler` 的 marker 机制 |
| Provider / 模型设置 | 完成 | `ui/setting/ModelSettingsSection.kt` |
| 角色管理 / 角色包 | 完成 | `data/model/`，稳定 ID + 切换 generation guard |
| Persona / Voice Profile | 完成 | `data/model/profile/`，含试听 |
| 语音输入 + ASR 抽象 | 完成 | `data/asr/`，已从 Sherpa-ncnn 换成 sherpa-mnn |
| 端内 TTS（BV2+MNN） | 完成 | `VITS` 模块 |
| 聊天存储 / 多模态管线 / 记忆 L0+L2 | 完成 | `Log` 模块 / `data/attachment/` / `data/memory/` |

### 核心矛盾：**做好的东西用户摸不到**（2026-10-14 已由 T1 解决，以下为当时的记录）

这是当前最大的问题，比任何缺失的功能都严重：

- **Companion UI 整套只在 debug 包可达**。正式入口仍是
  `LoginActivity → ChatActivity → ChannelListFragment / ChatFragment`（2023 年的 Fragment 架构）。
  `CompanionDemoActivity` 定义在 `app/src/debug/AndroidManifest.xml`，release 包里不存在。
  → Phase 1/2.1/2.5/2.6/2.7/2.8 的成果，正式包用户**一个都看不到**。
- **多模态管线零用户入口**。探测、归一化、落盘、模型可见性规则全通，但聊天页没有"加图片"按钮。
- **ASR 同理**，刚换完 MNN 并验证通过，但只能从 debug 入口用。

→ 所以 **T1「正式入口迁移」是本路线图的第一优先级**，它一次性释放积压五个 Phase 的价值。

### 已知的、本路线图会处理的问题

- **首声延迟**：`mainLoop()` 是「LLM 完整结束 → 翻译 → TTS」串行，长回复有数秒死寂（见 T2）
- ~~Anthropic / Gemini 仍是 stub~~ → T4 已完成（2026-10-14）；端内 LLM 仍是 stub
- ~~**记忆只有 L0 + L2**~~：T5 已补上 L1 日记和分层回忆
- **翻译是必需的云依赖**：中文 LLM → 百度翻译 → 日语 TTS，每轮一次网络往返（见 T3）

---

## 2. 任务清单

**按建议顺序排列。T1 是其余任务的地基（很多任务要改的正是正式入口那条链路），强烈建议先做。**

每个任务都标了「验收标准」——完成后要能自己验证，而不是"编译过了就算完"。

---

### T1. 正式入口迁移：让 Companion 成为唯一前台 ★最高优先级

> **状态（2026-10-14）：已完成**。完整一轮（流式 → BV2 出声 → 回 Idle）已在 4KB 页模拟器 `CW_API36_4k` 上验过。
> 交接记录见 `companion-ui-construction-plan.md` 第 12 节末条。下面是原始规格，保留作对照。

**目标**：删除老的 Fragment 聊天 UI，Companion 成为正式包的真实入口。

**为什么现在做**：见第 1 节「核心矛盾」。另外越晚做越贵——两套 UI 并存期间，
每个新功能都要考虑"接哪边"，T2~T6 全都会被这个问题拖累。

**因为不考虑 1.0 兼容，这里可以做得很干脆**：直接删老 UI，不要做"新旧并存 + 开关切换"。

#### 现状盘点

```
LoginActivity (launcher)
  └→ ChatActivity (单 Activity + mobile_navigation.xml 六个 Fragment)
       ├─ nav_channel_list  ChannelListFragment   ← 启动页，内部复用 ModelManagerContent
       ├─ nav_chat          ChatFragment          ← 老聊天页（要删）
       ├─ nav_chat_log      ChatLogFragment
       ├─ nav_setting       SettingFragment
       ├─ nav_model_manager ModelManagerFragment
       └─ nav_memory        MemoryFragment

app/src/debug/.../CompanionDemoActivity  ← Companion 的唯一入口，debug-only
```

关键事实（已核实）：
- `ChannelListFragment` 和 `ModelManagerFragment` **都**渲染 `ModelManagerContent`，
  角色管理/Persona/Voice 已经是 Compose，**不需要重写**，只要换宿主。
- `CompanionRealResponseDriver`（debug source set）已经把
  `ChatProviderFactory` / `ChatSession` / `ChatHistoryStore` / 翻译 / `SoundGenerateHelper` /
  `LipsValueHandler` 全接通了，**它就是正式链路**，只是放错了 source set。
- `ChatActivityViewModel` 和 `CompanionRealResponseDriver` 是**两套平行实现**同一件事
  （`mainLoop()` vs `responses()`）。迁移的核心就是**只保留一套**。

#### 建议做法

1. **把 `CompanionRealResponseDriver` 从 `src/debug` 移到 `src/main`**。
   它是正式链路的实现，不该在 debug source set 里。
   `CompanionMockDriver` 留在 debug（它是隔离演示用的）。

2. **决定保留哪套编排**。两个选项，**倾向第二个**：
   - (a) 保留 `ChatActivityViewModel.mainLoop()`，让 Companion 消费它
   - (b) **保留 `CompanionResponseDriver` 那套，删掉 `mainLoop()`** ←建议
     理由：`mainLoop()` 是 `while(true)` + `suspendCancellableCoroutine` 唤醒的设计，
     CLAUDE.md 自己都标注了"异常和取消路径比较脆"；而 driver 那套是 Flow 驱动的，
     已经在 Phase 2.1 把取消、generation 隔离、Room 收尾都处理过了。
     **注意**：`mainLoop()` 里有 driver 没有的东西——记忆巩固 (`consolidateMemoryInBackground`)，
     迁移时必须搬过去，别丢了。

3. **入口改为 Companion**。`LoginActivity` 之后直接进角色选择，选完进 Companion。
   `CompanionDemoActivity` 的内容提升为正式 Activity（注意它的
   `windowSoftInputMode="adjustResize"` 是真机实测修过的 IME 缺陷，**必须保留**）。

4. **删除老 UI**：`ui/chat/ChatFragment.kt`、`ChatContent.kt`、`ChatFragmentViewModel.kt`、
   `ui/channellist/ChannelListContent.kt`、`ChannelListBean.kt`，
   以及 `mobile_navigation.xml` 里的 `nav_chat`。
   `ui/common/UserInput.kt` 如果只被老页面用也一并删。
   **保留** `ChatLogFragment` / `SettingFragment` / `ModelManagerFragment` / `MemoryFragment`，
   它们仍然有用，只是需要从 Companion 能导航过去（见下）。

5. **补上 Companion 到其余页面的导航**。现在 `CompanionEvent.More` 在 demo 里是
   "释放 renderer 并跳回 ChatActivity"，正式版需要一个真的菜单：设置 / 聊天记录 / 角色 / 记忆。
   注意 renderer 的释放时序——demo 里那套 `releaseRendererForNavigation()` 是有道理的
   （Live2D 的 GL context 不能和新 Activity 抢），照搬。

6. **`ChatFragmentViewModel` 里的触控逻辑要保住**。
   `Live2DTouchManager` 的初始化/保存（`initTouch` / `saveTouch`）是角色摆位，
   Companion 侧目前没有等价物。要么搬进 `CharacterRendererHost`，要么确认 Companion 不需要。
   `LegacyPushToTalkRecorder` 随老页面一起删（Companion 用的是 `AsrEngine`）。

#### 验收标准

- release 包（**不是 debug**）启动后能直接进 Companion，看到 Live2D + 四个操作
- 发一轮真实对话：流式上屏 → TTS 播放 → 回 Idle
- 语音输入可用（麦克风按钮真的录音，不是 demo 开关）
- 能从 Companion 导航到设置 / 角色 / 聊天记录 / 记忆，并能回来
- 切角色后 Live2D、persona、voice、历史都跟着换
- `grep -rn "ChatFragment\|ChannelListContent" app/src` 无结果
- `./gradlew :app:assembleRelease` 成功，release manifest 里**不再**有 `CompanionDemoActivity`

---

### T2. 首声延迟：按句流水线 ★体感收益最大

> **状态（2026-10-15）：已完成**。`SoundGenerateHelper.openStream()` + `VoicePipeline`
> （翻译和 TTS 各一个串行协程、Channel 串起来保序）+ `SentenceSplitter`（首句可在逗号处提前切）。
> **实测（4KB 页模拟器 / Claude Haiku / 同一提示词各 3 次）**：首声从 **38~43s 降到 18~23s**，
> 首 token 到首句只差 ~0.1s（改造前是「等整段回复 + 一次翻译往返」）。
> 模拟器上这个绝对值没有意义（BV2 的 arm64 `.so` 走 ARM 翻译，单句推理 ~15s），
> 真机应该短得多，但**相对提升是真的**。日志带 `CWLatency` tag。
> 取消路径实测：播放中切角色，ATRI 那一轮立刻停、回到 Companion 后 Yuuka 重新装载并出声，无迟到音频。

**目标**：用户说完话到听见第一个音的时间，从「完整 LLM 回复 + 翻译 + 首句推理」压到「首句 LLM + 首句翻译 + 首句推理」。

**为什么重要**：对话式陪伴里"她多久开始说话"比"她说得多好"更影响体感。
现在一个长回复可能有好几秒死寂。

#### 现状（已核实，`ChatActivityViewModel.kt` 第 234~262 行）

```kotlin
val result = streamChatRequest(...)        // 等 LLM 完整结束
val translateText = fetchTranslateIfNeed(result.text)   // 整段翻译，一次网络往返
consolidateMemoryInBackground()
generateAndPlaySound(needPlayText = translateText, ...)  // 整段喂给 BV2
```

`SoundGenerateHelper.generateAndPlay()` 内部**确实**按句切分逐句推理
（`splitSentences`，句末标点 + 60 字硬切），但它**拿到的是完整文本**，
所以切分带来的只是"推理期间可以边出边播"，省不掉前面两段等待。

#### 建议做法

把「LLM 流 → 句子 → 翻译 → 合成 → 播放」做成流水线：

1. 在 `streamChatRequest` 的 `TextDelta` 累积处做**增量断句**：
   每来一个 delta，检查新增部分里有没有句末标点（复用
   `SoundGenerateHelper.splitSentences` 的那套 `SENTENCE_ENDINGS`，
   建议把它提成公共工具，别复制一份）。
2. 凑满一句就立刻送进一个 `Channel<String>`，由独立协程消费：翻译 → 合成 → 排队播放。
3. `SoundPlayHandler` 已经是 `MODE_STREAM` + 单个 AudioTrack 排队，**天然支持续播**，
   不需要改它。但要确认：多段 PCM 陆续 `sendSound` 时 marker 完成语义仍然正确
   （Phase 2.1 做过，但那是"一轮内多句一次性入队"，现在变成"陆续入队"）。
4. 最后一句要有明确的收尾信号，否则 `awaitPlaybackComplete` 不知道什么时候算完。

#### 坑

- **取消路径**：切角色 / 用户打断时，要同时取消 LLM 流、翻译请求、待合成队列、播放。
  现在的 `characterSwitchGuard` + `cancelPlayback` 是为"单段"设计的，流水线下要重新捋一遍。
- **翻译的并发**：百度翻译按句调用会把请求数放大 N 倍。要么限流，要么合并短句，
  要么（更好）看 T3 能不能把翻译整个干掉。
- **顺序**：翻译是异步的，多句并发翻译时**必须保证播放顺序和原文一致**，不能谁先回来谁先播。
- **不要动 `LipsValueHandler` 的调用契约**：`forwardResult(pcm, sampleRate)` 保持原样，
  那是表演课题的地盘。

#### 验收标准

- 一个 3~5 句的长回复，首声延迟明显小于改造前（**用日志打时间戳量化**，
  记录"用户提交 → 第一帧 PCM 写入 AudioTrack"的毫秒数，改造前后各记一组）
- 多句之间播放连续，没有可感知的断裂或重叠
- 播放中切角色 / 退出页面，所有在途任务都被取消，没有迟到的声音
- 单元测试覆盖：增量断句的边界（标点在 delta 中间 / delta 边界 / 无标点长串）

---

### T3. 重新评估翻译环节 ★架构债，影响 T2

> **状态（2026-10-15）：已决策 —— 选项 C（保留翻译，并进流水线）**。
> 翻译不再是「整段一次网络往返」，而是流水线里的一级：按句、串行（并发恒为 1，
> 不会被百度限流），返回空或失败就回落原文，句子不丢。翻译开关仍然是设置页的
> `SAVED_USE_TRANSLATE`，关掉就整级跳过。选项 A / B（让 LLM 直接说日语 / 换中文声库）
> 仍然开着，但不再是首声延迟的瓶颈，等表演课题那边定了再回头看。

**目标**：决定「中文 LLM → 百度翻译 → 日语 TTS」这条链路要不要保留。

**背景**：这是 2023 年"只有日语声库"的历史遗留。现在它导致：
- 一条本地优先的语音链路中间插了**必需的云服务 + 密钥**
- 每轮一次网络往返，直接影响 T2 的首声延迟
- 多一个失败点（翻译挂了要降级回原文，然后日语声库读中文）

**三个选项**，建议先做调研再决定：

| 选项 | 做法 | 代价 |
|---|---|---|
| A. 让 LLM 直接输出日语 | persona 里要求日语回复，去掉翻译 | 用户看到的也变成日语（除非 UI 做双语显示）；LLM 日语质量存疑 |
| B. 换中文声库 | 换 BV2 的中文底模 | 要重新找/炼模型；`spk2id` 和角色映射要重做 |
| C. 保持现状但异步化 | 翻译并进 T2 的流水线 | 不解决根本问题，但成本最低 |

**注意**：这条和 Behavior 课题**有潜在交集**——如果将来口型要和日语音素对齐，
换语言会影响那边。**动手前先确认表演课题那边的假设**，别让两边打架。

#### 验收标准

- 产出一个决策记录（写进本文档或单独的 ADR），说明选了哪条路、为什么
- 如果选 A 或 B：中文输入 → 角色正常发声，端到端可用
- 如果选 C：翻译不再阻塞首声

---

### T4. 补齐 Provider：Anthropic / Gemini

> **状态（2026-10-14）：已完成**。两家都在公司内网 AthenAI（LiteLLM 网关）上真实联调：流式、多轮、
> 跨协议 snapshot/restore、错误 key → Auth 全部通过（`ChatCore/src/test/.../ProviderLiveTest`）。
> Gemini 改走 `streamGenerateContent` 而不是 Interactions，理由见 `docs/chat-core.md`。
> 设置页「连接测试」直接复用 `provider.chat()`，自动覆盖两家。

**目标**：把两个 stub 实现掉。

**为什么值得做**：ChatCore 的抽象就是为多 provider 设计的，但至今只有 OpenAI 系在跑。
补齐它们能**验证抽象是不是真站得住**——就像 2.8 的 ASR 抽象被 MNN 替换验证过那样。
而且 Anthropic/Gemini 的多模态能力比 OpenAI 兼容层更强，和已有的媒体管线是互补的。

**现状**：
- `ChatCore/.../provider/anthropic/AnthropicMessagesProvider.kt` — `chatStream` 抛 `NotImplementedError`
- `ChatCore/.../provider/google/GeminiProvider.kt` — 同上
- **映射方案已经写在各自类的 KDoc 里**，不用从头设计
- `docs/chat-core.md` 有三家 API 的映射对照表

**坑**：
- Anthropic 没有服务端会话，必须重发全量历史——`ChatSession` 本来就是按这个设计的，正好验证
- thinking 签名跨 provider 不能重放，`ChatHistoryStore` 已经记了 `providerId`，恢复历史时要对齐
- 两家的多模态 content block 格式不同，对接 `ChatContent` 时注意

#### 验收标准

- 两家都能完成一轮真实流式对话（需要有效 API key）
- 设置页的连接测试对它们返回正确结果
- 切 provider 后历史不断（`ChatSession` 的 snapshot/restore 正常）
- 单元测试：请求映射、流解析、错误分类

---

### T5. 记忆 L1 / L3

> **状态（2026-10-15）：已完成**。切段按时间 + 条数结合；回忆分层（常驻日记 → 关键词自动想起 →
> 工具搜日记 / 读原文）；日记双方的话都记；顺带做了时间感和 Anthropic 显式缓存。
> 设计与验证见 `docs/memory.md` 第七、九节。验收四条：日记进下一轮 system 区 ✓、
> 按关键词召回（自动 + 工具）✓、记忆页可看可改日记 ✓、按角色隔离（工具校验归属，单测守着）✓。

**目标**：补上情节摘要和检索。

**为什么**：对陪伴类应用，"她记得我们上周聊过什么"是长期粘性的核心。
现在只有 L0（分块淘汰）和 L2（结构化事实，如"所在城市=北京"），
缺的恰恰是"记得发生过什么"。

**设计已经在 `docs/memory.md` 里**，动手前**必读**。三条拍过板的决策不要再当开放问题：
记忆按角色隔离不共享、抽取走接口且模型可单独配置、**L3 检索用 `LIKE` 不上 FTS**。

**坑**：
- Room schema 要加表 → **因为不考虑 1.0 兼容，可以直接改版本号，不用写精细迁移**。
  但仍然**不要**加 `fallbackToDestructiveMigration()`（聊天记录是用户资产这条原则不变）
- L1 摘要的触发时机：抄 L2 的做法，放在 TTS 播报窗口里跑，不占首字延迟
- 注入要走 `MemoryContributor`，别让 `ChatCore` 碰 Room

#### 验收标准

- 多轮对话后能产出情节摘要，且摘要进了下一轮的 system 区
- L3 检索能按关键词召回历史片段
- 记忆页能看到/编辑 L1 内容
- 切角色后记忆隔离（不串台）

---

### T6. 多模态 UI 入口

> **状态（2026-10-15）：图片链路已完成并实机验过**。输入条的「+」和「视觉」是同一个入口：
> 系统照片选择器（`PickVisualMedia`，**不申请任何存储权限**）→ `ingestImage()` →
> 待发送缩略图（可删）→ 随 `CompanionRequest.images` 发出 → `ChatSession.send(ChatMessage)`。
> 能力 gate 按**选中的模型**算，不支持时给提示而不是打开选择器。
> **2026-10-16 补齐**：「+」展开类型菜单（图片·视频 / 音频 / PDF），「视觉」直开相册（图片 + 视频）。

**目标**：让已经建好的媒体管线真的能用。

**现状（已核实）**：
- `ChatMessage.user(text, vararg attachments: ChatContent)` **已经存在**
- `ChatSession.send(ChatMessage)` **已经收多模态**
- `data/attachment/` 的探测 / 归一化 / 落盘 / 模型可见性规则全通
- **唯一缺的是 UI 入口和 driver 接线**

所以这个任务比看起来轻。接线点见 `docs/media-pipeline.md` 第八节。

**建议**：Companion 输入条加一个"+"，走 SAF 选图 →
`AttachmentProvider.ingest()` → 作为 `ChatContent` 附到 `ChatMessage`。

**坑**：
- 能力 gate 按模型算（`ProviderCapabilities`），不支持视觉的模型要禁用入口或给提示
- 只有派生行进上下文（视频 = 父行 + N 帧 + 音轨），规则在 `ChatHistoryStore.modelVisibleAttachments()`
- 体积闸门必须在读字节之前（`OpenableColumns.SIZE`），别破坏这条纪律

#### 验收标准

- 能从 Companion 发一张图并得到基于图片的回复
- 历史里能看到缩略图，重开 app 后仍在
- 不支持视觉的模型下入口有明确反馈
- 超大文件被闸门挡住且提示清晰

---

### T7. 真机验证（贯穿性任务）

**目标**：把"只在模拟器验过"的结论换成真机结论。

**背景**：到 2.8 为止，**几乎所有验收都在模拟器完成**。（2026-10-16 盘点：T1~T6 + 记忆第二轮仍然如此，本任务未开始。）
开发机上的物理设备（小米 24129PN74C / MIUI）一直被
`INSTALL_FAILED_USER_RESTRICTED` 挡住，需要在开发者选项里打开"USB 安装"。

**真机才能验的东西**：
- **真实麦克风的 ASR**：partial transcript 的实际质量、长静音 endpoint 触发、
  不同环境噪声。模拟器只有静音，2.8 的 ASR 结论全部来自 WAV 注入
  （`debug.chatwaifu.asr_wav` 这个 debug-only 口子，见下）
- **TTS 播放中点麦克风**的听觉验证（AudioTrack / AudioRecord 切换有没有串音）
- Live2D 首帧、不同 GPU、不同 Audio HAL
- 蓝牙/有线耳机切换、来电打断、音频焦点
- 低端机性能（MNN ASR + BV2 TTS + Live2D 同时跑）

**现成的验证工具**（2.8 留下的，可以继续用）：
```bash
# 把 WAV 当麦克风输入，绕开"模拟器没有真实麦克风"
adb push some_16k_mono.wav /data/local/tmp/asr_test.wav
adb shell setprop debug.chatwaifu.asr_wav /data/local/tmp/asr_test.wav
# 清掉就回到真麦克风
adb shell setprop debug.chatwaifu.asr_wav ""
```
样本来源：`Bert-VITS2-MNN/third_party/MNN/apps/iOS/.../LocalBatchTest/Audios/*.wav`
（AISHELL 中文朗读，16k mono，带标准答案，适合做正确性基准）。
`Bert-VITS2-MNN/wav_sample/*.wav` 是 BV2 合成的中日语音，44.1kHz，**需要重采样到 16k 才能用**。

---

## 3. 技术债清单（不紧急，但别让它们继续长）

| 债务 | 说明 | 建议 |
|---|---|---|
| **两份 `libMNN.so`** | BV2 的 AAR 带一份（`MNN_SEP_BUILD=ON`，Express 拆在 `libMNN_Express.so`），SherpaMnn 带一份（`SEP_BUILD=OFF` 合并版），**都编自 `Bert-VITS2-MNN/third_party/MNN`**。APK 里只能有一个同名文件，`pickFirsts` 选了后者（符号超集）—— **所以 TTS 和 ASR 运行时加载的其实是同一份 `libMNN.so`**（BV2 那份根本没进包），TTS 只额外从 `libMNN_Express.so` 取 Express 符号。**换 BV2 或重编 MNN 时必须重新验符号**，方法见下 | 中期考虑让 sherpa-mnn 也用 SEP_BUILD=ON，或说服 BV2 侧统一 |
| `ChatWaifuApplication.context` 静态 Context | 多处直接引用而非注入 | 接 Hilt 时一并解决 |
| **`libOVRLipSync.so` 未做 16KB 页对齐** | 2026-10-15 已修掉 `libMNN.so`（按第六节重编，16KB 页模拟器上 TTS / ASR 都验过）。**剩这一个**：Meta 的闭源预编译库，没源码不能重编。16KB 设备上 `liblipsync` 加载失败 → 口型整体不可用（连假动画也没有，`LipsValueHandler` 构造时就碰了 JNI），TTS / 对话不受影响 | 属于表演课题。可选：换 Meta 新版 SDK；或让 `liblipsync` 改 `dlopen` 懒加载 OVR，至少保住假动画 |
| 无任何单元测试以外的测试 | 72 个 JVM 测试，0 个 instrumentation 测试 | T1 之后补关键路径的 UI 测试 |
| `Log/schemas/2.json` 缺失 | 不影响构建，影响迁移测试 | 不考虑 1.0 兼容后，重要性下降 |
| lint 220 warning | 历史存量，0 error | 不用专门清，别新增就行 |
| manifest 锁死横屏 | `DiscouragedApi`，Android 16 起可能被忽略 | 观察 |
| **APK 462MB(debug) / 328MB(release)** | assets: SherpaMnn 100MB + Live2D 25MB + BV2 AAR 里的 openjtalk 词典等 | **按前提 3，当前不做**。将来做按需下载 |

### 验证两份 libMNN 符号兼容的方法

```bash
NM=$ANDROID_NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-nm
unzip -o -q app/build/outputs/apk/debug/app-debug.apk "lib/*" -d /tmp/apk
L=/tmp/apk/lib/arm64-v8a
cat <($NM -D --defined-only $L/libMNN.so) <($NM -D --defined-only $L/libMNN_Express.so) \
  | awk '{print $NF}' | sort -u > /tmp/have.txt
for f in libbertvits2.so libMNN_Express.so libsherpa-mnn-jni.so; do
  $NM -D --undefined-only "$L/$f" | awk '{print $NF}' | grep -E "^_ZN3MNN|^MNN" | sort -u > /tmp/need.txt
  echo "$f missing: $(comm -23 /tmp/need.txt /tmp/have.txt | wc -l)"   # 必须全是 0
done
```

---

## 4. 从零搭环境（新机器第一次开工）

```bash
# 1. BV2 的 AAR 不入库，必须现场产出
git submodule update --init --recursive
git lfs install
git -C Bert-VITS2-MNN lfs pull --include="bertvits2-jni/src/main/assets/bv2_model/jp/*,\
bertvits2-jni/src/main/assets/bert/jp/*,\
openjtalk/src/main/assets/open_jtalk_dic_utf_8-1.11/*"
cd Bert-VITS2-MNN && ./gradlew publishAars \
    -PpublishGroupId=com.chatwaifu.bv2 -PpublishVersion=2.0.0-a45fd76 && cd ..

# 2. 正常构建
./gradlew assembleDebug
```

**ASR 的 native 产物和模型已经在仓库里**（`SherpaMnn/src/main/jniLibs/` 和
`SherpaMnn/src/main/assets/`），不需要重新编译。
万一要重编（换模型 / 升 MNN），步骤记录在第 6 节。

---

## 5. 明确暂不做（**不要自作主张去做**）

- **Behavior / 表情 / 动作 / 情绪 / 真实口型** — 单独课题，另有 agent 负责。
  **不要碰** `USE_REAL_LIP_SYNC`、Live2D renderer、`behaviorProfileId`
- **APK 体积优化 / 按需下载** — 见前提 3
- **1.0 数据兼容 / Room 老库迁移验证** — 见前提 1
- **端内 LLM（`LocalLlmProvider`）** — stub 保留，优先级低于云端 provider
- **TTS Provider 抽象** — 现在只有 BV2 一家，抽象没有第二个实现来验证，是空转
- **ASR 设置页 / 多 runtime 切换** — `AsrProvider` 内部可切已经够了，没有用户需求
- **Wake word / 后台常驻监听 / 云端 ASR**
- **Persona / Voice 市场、AI 生成人格**

---

## 6. 附录：ASR native 重新编译步骤（仅在换模型或升 MNN 时需要）

2.8 把 Sherpa-ncnn 换成了 sherpa-mnn，产物已入库。若要重新编译：

```bash
export ANDROID_NDK=$HOME/Library/Android/sdk/ndk/27.0.12077973
export PATH=$HOME/Library/Android/sdk/cmake/3.22.1/bin:$PATH
MNN=Bert-VITS2-MNN/third_party/MNN

# 1. 编 MNN Android 库（必须 make install，sherpa-mnn 要 include/ 和 lib/ 布局）
mkdir -p $MNN/project/android/build_64 && cd $MNN/project/android/build_64
cmake ../../../ -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DCMAKE_BUILD_TYPE=Release -DANDROID_ABI=arm64-v8a -DANDROID_STL=c++_static \
  -DANDROID_NATIVE_API_LEVEL=android-29 -DMNN_LOW_MEMORY=ON -DMNN_SEP_BUILD=OFF \
  -DCMAKE_INSTALL_PREFIX=. -DNATIVE_LIBRARY_OUTPUT=. -DNATIVE_INCLUDE_OUTPUT=.   -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON   "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"
make -j8 && make install
# ↑ 最后两个参数是 16KB 页对齐（Android 15+ / Google Play 要求），**不能省**：
#   2026-09 那份没加，16KB 设备上 dlopen 直接拒，TTS 和 ASR 一起挂。
# 放进 APK 前 strip（未 strip 是 70MB+）并确认对齐：
#   llvm-strip --strip-unneeded libMNN.so
#   llvm-readelf -lW libMNN.so | grep LOAD     # Align 列必须是 0x4000
# 2026-10-15 那份：NDK 28.2.13676358（和 BV2 的 app_version.gradle 一致）、MNN 8d9f6131a

# 2. 编 sherpa-mnn JNI（关掉用不到的 TTS/diarization，显著减体积）
cd $MNN/apps/frameworks/sherpa-mnn
export MNN_LIB_DIR=$(cd $MNN/project/android/build_64 && pwd)
export SHERPA_MNN_ENABLE_TTS=OFF SHERPA_MNN_ENABLE_SPEAKER_DIARIZATION=OFF \
       SHERPA_MNN_ENABLE_BINARY=OFF SHERPA_MNN_ENABLE_JNI=ON \
       SHERPA_MNN_ENABLE_16K_PAGE_SIZE=ON
bash build-android-arm64-v8a.sh
# 产物：build-android-arm64-v8a/install/lib/libsherpa-mnn-jni.so

# 3. 转模型（host 版 MNNConvert，FP32 输入 + 8bit 量化，不要转 int8 的 onnx）
#    模型：huggingface csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20
./MNNConvert -f ONNX --modelFile encoder-epoch-99-avg-1.onnx \
  --MNNModel encoder.mnn --weightQuantBits=8 --weightQuantBlock=64
# decoder / joiner 同理；连同 tokens.txt 一起放进
# SherpaMnn/src/main/assets/sherpa-mnn-streaming-zipformer-zh-en/
```

**注意**：sherpa-mnn 的 `target_link_libraries(sherpa-mnn-core MNN)` 写死只连一个库，
所以 MNN 必须用 `MNN_SEP_BUILD=OFF`（Express 合并进 libMNN）。
这正是产生"两份 libMNN"那条技术债的原因。

**ASR 语言能力**：当前模型是 **zipformer 中英双语，不含日语**。
和换之前的 Sherpa-ncnn 持平（那个也不支持日语），不是回退。
角色说日语靠 BV2 TTS，用户输入走中英。

---

## 7. 给接手 agent 的工作方式建议

1. **先读 `CLAUDE.md`**，它记录了很多反直觉的工程约定
   （Groovy 赋值要写 `=`、Kotlin 为什么卡在 2.3.21、Compose 库故意不写版本等），
   不读会踩坑。
2. **每个任务开工前先做只读调查**，确认文档描述和代码现状一致。
   这份路线图写于 2026/10，代码可能已经变了。
3. **验收要真的验**。这个项目历史上积累了大量"只过了编译"的结论，
   不要再往上加。能在模拟器验的就验，需要真机的标注清楚。
4. **完成一批就往 `companion-ui-construction-plan.md` 第 12 节追加交接记录**
   （那里有固定格式），保持"换一台机器也能接续"。
5. **遇到范围爆炸就停下来报告**，不要为了"架构更优雅"做无关重构。
   这条在前几个 Phase 的规格里反复强调过，是有效的。
