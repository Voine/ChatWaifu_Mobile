# Live2D Companion UI V0.1 施工与跨机器交接

> 给接手 Copilot / GPT-6 的第一指令：先读本文件，再读设计包和本阶段涉及的源码；
> 从第 10 节的第一个未完成批次开始实施，不需要重新输出一份总体规划。
> 不依赖原对话、原机器绝对路径或原 Copilot 会话。默认只实施 Phase 1；
> 完成验收后停止，报告结果，由用户决定是否进入 Phase 2。

## 1. 交接状态与证据边界

- 文档日期：2026-09-13。
- 分析基线：`feature/v2.0.0`，提交 `b2a5619`（`memory p0 p1`）。
- **目前只有规划完成；本文所列 UI 改造、测试、环境恢复均未实施。**
- 已阅读设计包全部 7 份 Markdown、manifest 和 5 张参考图；已检查项目相关
  UI/导航、聊天编排、模型管理、聊天与存储边界、媒体/记忆设计、语音链路及原生
  Live2D 宿主与主要 C++ 集成代码。
- 不是对全部第三方 SDK 的逐行审计；未读取二进制模型内容，也未阅读当前工作区
  尚未初始化的 BV2 子模块源码。不把此前文档中“编译通过”的记录算成本轮验证。
- 本轮没有执行构建或真机验证。记录中的结论是静态代码检查结果或施工目标，
  不是已达成的产品能力。
- 接手时若 HEAD 已变化，先查看本文件涉及路径的差异，保留别人新增的功能。
  **不要 reset 到基线，不要覆盖用户未提交的修改。**

### 1.1 设计包位置与优先级

设计包实际是双层目录，不是包内示例所写的 `design_v01`：

```text
live2d_ai_companion_design_v01\
  live2d_ai_companion_design_v01\
    README_DESIGN.md
    manifest.json
    agent\COPILOT_TASK.md
    specs\UI_SPEC.md
    specs\INTERACTION_SPEC.md
    specs\ARCHITECTURE.md
    specs\IMPLEMENTATION_PLAN.md
    specs\DESIGN_NOTES.md
    references\overview.png
    references\home_idle.png
    references\home_input.png
    references\home_speaking.png
    references\history_sheet.png
```

约束优先级：用户最新明确要求 > 设计包 Markdown 的产品约束 > 本文的工程落地细化 >
参考图的视觉启发。当前代码决定实际接线位置，但不是保留旧 UX 的理由。
若有实质冲突，明确指出并询问，不静默扩大范围。

四张单页参考图含相邻页面的裁切内容；以 overview 理解构图，不把裁切边缘当规格。
总览图中出现的商城、悬浮窗、场景等不等于本期需求。参考图仅作方向参考，
不裁切成产品角色/背景素材，不提交新的未授权版权资源。

### 1.2 最短阅读路线

1. 本文件与上述全部设计包 Markdown、参考图。
2. `CLAUDE.md` 的工程约束、构建约定，以及本文件第 3 节列出的当前实现。
3. Phase 1 开工前完整阅读 `ChatFragment.kt`、`ChatContent.kt`、`UserInput.kt`、
   `ChatFragmentViewModel.kt`、`ChatActivity.kt`、`LoginActivity.kt`、
   `ChannelListFragment.kt`、`ChatActivityViewModel.kt` 的初始化路径，以及原生宿主生命周期。
4. Phase 2 开工前追加 `docs\chat-core.md`、`docs\chat-storage.md`、
   `docs\media-pipeline.md`、`docs\memory.md` 及各接线点源码。

## 2. 交付目标与范围

产品层级：**角色 > 当前发言/表现 > 输入 > 历史**。
目标是角色主导的前台陪伴界面，而非在聊天软件上面放一张人物图。

### Phase 1：本次默认施工范围

- 保留真实 Native Live2D，重做 Compose 覆盖层。
- 完成 Idle、InputExpanded、Speaking、HistoryOpen 四个可演示画面。
- 以隔离的内存 Mock 驱动输入、回复、语音活动指示和历史。
- 输入/历史开合、返回键、加载/失败状态、字体缩放与键盘适配。
- 原有正式聊天入口及角色列表、设置、模型管理、记忆等功能仍可访问。
- 无 API Key、无录音授权、TTS 未初始化也能演示新 UI；无角色内容时 UI 仍可操作。

### Phase 2：需后续明确进入

接回现有文本发送、流式回复、持久化历史、ASR、TTS 播放状态；再将正式启动路径
改为恢复角色直接进入主页。不新建 provider，不重做数据库与记忆系统。

### 不在 V0.1 范围内

Unity 迁移、悬浮气泡、后台陪伴服务、MediaProjection/屏幕识别、视觉接线、
角色商城、复杂场景/换装、新长期记忆后端、真实 viseme 重写、并行对话与语音打断。
现有功能保留，不因为“不新做”而删除。Phase 3 才考虑情绪/意图到角色表情/动作映射；
Phase 4 的后台陪伴另开设计。

## 3. 当前工程事实与复用边界

以下 app 源码短路径均相对 `app\src\main\java\com\chatwaifu\mobile\`。

| 部分 | 当前事实 | 改造边界 |
|---|---|---|
| 应用外壳 | `ui\login\LoginActivity.kt` 是 launcher；`ChatActivity.kt` 创建 Compose 外壳并启动业务循环 | 不全量迁移导航 |
| 导航 | `ui\base\ChatWaifuMainContent.kt` 内嵌 Fragment NavHost；`mobile_navigation.xml` 从角色列表开始 | 历史弹层不作为新导航目的地 |
| 正式入口 | `ChannelListFragment` 调 `initModel()`、申请录音权限；选择角色后等待声库成功再进入聊天 | 演示不能直接复用整条初始化路径 |
| 主页面 | `ChatFragment` 承载 GLSurfaceView，`ChatContent.kt` 已有当前发言区和常驻输入区 | 替换覆盖层，不是从瀑布流全盘重写 |
| UI 数据 | `ChatDialogContentUIState` 有内容、发送方、错误、流式标记；业务 SharedFlow 无 replay | 后续需页面快照，不能仅依靠重订阅旧事件恢复 |
| 编排 | `ChatActivityViewModel` 调 ChatSession、翻译、TTS、ChatHistoryStore、记忆巩固 | 页面 VM 不复制业务会话 |
| ASR | `ChatFragmentViewModel` 绑定 `:sherpa` 服务；当前是按住录音路径 | 保留 IPC 与输入语义 |
| 模型 | `data\model\CharacterRepository` 是来源；meta 指定 Live2D 入口、可选声库、speaker | 不假设目录名等于入口文件名 |
| 历史 | `utils\ChatHistoryStore.kt` 映射 ChatCore 与 Log；Room v5 | 不另建 UI 历史数据库 |
| 记忆 | 已有按角色隔离的 L2 事实、L0 分块淘汰和管理 UI | 不顺手扩建 L1/L3 |
| 多模态 | 归一化、落盘、映射已有基础，聊天 UI/发送入口尚未完整接入 | 视觉按钮仅占位 |

关键文档陈旧点：当前 manifest 的 `ChatActivity` 是 **portrait**，不是部分说明中的横屏；
数据库是 **v5**，不是旧段落的 v3；导航还包含记忆页面；TTS 已是 BV2/MNN，
不是旧 README 中的 ncnn 权重格式。不要按旧文案倒退实现，也不要顺手全面清理历史文档。

### 3.1 Live2D 生命周期必须保持

```text
ChatFragment → GLSurfaceView (OpenGL ES 2, continuous rendering)
             → GLRenderer → JniBridgeJava/JniBridgeC
             → LAppDelegate → LAppView/LAppLive2DManager/LAppModel
```

- `ChatFragment.onStart()` 调 `nativeOnStart()`，验证选中角色的入口文件后请求换模型。
- `onResume()` 恢复 GLSurfaceView；`onPause()` 先暂停它，再调用 native pause。
- `onDestroyView()` 解除加载回调、销毁 lipsync context、解绑 Sherpa、清空 View 引用。
- `onStop()` 调 native stop；`onDestroy()` 调 native destroy。
- C++ `LAppDelegate::OnStart` 创建视图/纹理管理器；`OnSurfaceCreate` 初始化 Cubism/shader；
  `OnSurfaceChanged` 更新 viewport、矩阵和 sprite。
- native pause 保存场景；**native stop 真正释放视图、模型管理器并 Dispose Cubism**；
  native destroy 释放顶层单例。不是可以随意触发的空回调。
- 换模型请求在 `LAppView::Render` 中处理，完成后 `OnLoadDone` 回 Java。
- 当前 `onLoadError()` 为空，必须在新宿主中映射可见的失败状态；GL 线程回调切回主线程
  并受 View 生命周期约束，避免页面销毁后的过期回调修改新页面。
- Amadeus 有 `needRenderBack(false)` 和 `"fix"` 表情处理，重用宿主时不能丢掉；
  切回其他角色需正确恢复对应显示配置。

实施约束：一个活跃 native 宿主；输入、历史、字幕重组不能重建它；
不得同时挂新旧两个渲染页面。优先由现有 Fragment 管生命周期，提取的
`CharacterRendererHost` 只包装现有 View/受控回调，不另造长期运行服务。
Compose 控件不直接调用 JNI。需要调整线程时逐项确认 GL 队列与释放顺序，
不要未经分析就把全部 JNI 调用搬入 `queueEvent`。

### 3.2 不能混淆的就绪和完成状态

- Live2D 加载完成、声库加载完成、业务输入可用是三件事。
- `SoundGenerateHelper.generateAndPlay()` 负责按句推理并排队音频；
  `SoundPlayHandler` 在自己的线程播放，函数返回不代表用户已听完。
- 当前 `GENERATE_SOUND → DEFAULT` 不能直接解释为 Speaking 结束。
- 现有 lipsync 默认是按采样率估算时长的循环动画，Phase 1 不改变这套算法。
- 现有 `mainLoop()` 即使尚未发送请求，也会启动数据库残留流修复及附件 GC；
  为“纯 Mock”而只不点发送按钮，并不足以隔离业务副作用。

## 4. 页面与视觉规格

```text
Box(fillMaxSize)
├── CharacterRendererHost       全屏、稳定 View
├── ReadabilityGradientLayer    顶部及下方局部渐变，不全局压暗
└── CompanionOverlay
    ├── 顶部工具与角色信息
    ├── CurrentUtterancePanel
    ├── InputPanel
    ├── FloatingActions
    └── HistorySheet            同页覆盖层
```

使用既有字体/主题和 `MessageFormatter`（Text + LinkAnnotation），新增局部 companion
配色，不把设置页/模型管理页一起换皮。不接回废弃的 ClickableText。

起始参数：水平边距 16dp，间距 8/12/16/24dp，发言圆角 20–24dp，输入圆角 22–28dp，
操作按钮视觉尺寸 44–52dp（可点击区域至少 48dp），约 1dp 低透明度边框，
面板 alpha 约 0.62–0.78；按实际背景对比度调整，不当作死值。
冷蓝/浅中性色、少阴影、薄边框，避免大块 Material 卡片。

首轮不做实时毛玻璃：GLSurfaceView 是独立 Surface，Compose blur 不保证能采样其后内容。
以半透明底色和局部 scrim 达成可读性，不引入 PixelCopy 循环、截图模糊或切换 TextureView。
原生仍清屏并绘制自己的背景；不要假定在它下面加 Compose 背景就会可见。

| 画面 | 必须实现 |
|---|---|
| Idle | 角色主导；姓名/简短状态；可选当前发言；浮动动作；输入默认收起 |
| InputExpanded | 点击对话/发言展开；动作区淡出；文本框、麦克风、非空时发送；键盘不压缩整幅角色 |
| Speaking | 当前回复和轻量语音活动指示；输入仍可展开；不显示全量历史 |
| HistoryOpen | 可见历史入口或底部限定区域上滑；初始 55%–65% 高度，可展开至安全区域内全高；独立滚动 |

历史高度以扣除必要系统安全区域后的可用高度测量。优先复用 Material sheet；
先确认所用版本的锚点能力，若默认半展开不满足范围，用局部显式锚点实现，
不靠把半屏内容硬截断伪装半展开，不引入通用弹层框架。

长发言设置相对可用高度的上限，内部阅读/滚动或提供查看完整历史入口；
不能撑满主页，也不能无入口地截掉内容。空历史、空回复、长消息、大字体都要有布局。

建议底部操作为“对话／互动／视觉占位／更多”，历史另有明确入口。
互动暂不接新动作编排，可明确提示未开放；保留旧有模型位置调整/重置入口。
更多仍能到角色、设置、模型管理、记忆等现有页面。
不实现的按钮用清晰说明，不假装成功，不申请额外权限。

动画起始值：底部操作淡入/位移 120–220ms，输入展开 180–260ms，
历史沿用标准物理动画；发言轻量交叉淡入/尺寸变化，不逐字触发重动画。

## 5. 状态、事件与交互规则

页面级 `CompanionViewModel` 持有不可变 `StateFlow`；纯展示 Composable 接 state/event。
业务运行状态与覆盖层分离，而不是只有一个互斥的 `HistoryOpen/Speaking` 枚举：

| 维度 | 建议内容 |
|---|---|
| 运行 | Idle / Listening / Thinking / Speaking |
| 覆盖层 | None / Input / History |
| 展示数据 | 当前发言、发送方、草稿、错误、历史、当前角色标识 |
| 角色加载 | Loading / Ready / Failed（带可读原因） |

示例事件：OpenInput、DismissInput、DraftChanged、Submit、OpenHistory、
DismissHistory、RetryRenderer、OpenSecondaryDestination。
名称可依项目习惯调整，不为每个按钮建独立 use-case 层。

交互契约：

1. 启动演示进入 Idle；点对话或当前发言进入输入。
2. 提交非空草稿后模拟 Thinking，再显示回复并进入模拟 Speaking，完成后 Idle。
3. 打开历史不暂停运行；若期间 Speaking 已结束，关闭后显示最新 Idle，
   **不恢复旧 Speaking 快照**。记录打开历史前的输入层状态，恢复草稿但不强制弹出键盘。
4. 返回优先关闭历史，然后关闭输入/键盘，最后交给原导航/Activity。
   IME 先消费返回的机型不得误退出页面，按实际系统事件验证。
5. 输入层消费 `navigationBarsPadding` 与 `imePadding`；角色层不消费 IME。
   安全区域处理覆盖刘海与系统栏，避免父子重复 padding。
6. 历史手势限于把手/底部区域；输入/历史交互不能穿透触发角色拖动、缩放或 native 命中。
   不启用旧 native 示例中的隐藏 gear/power 热区作为新交互。
7. Speaking 时允许编辑草稿；Phase 2 默认不打断 TTS、不并行发新请求。
   在真正允许下一轮前给出可见忙碌状态，不能静默丢弃提交。
8. 模拟错误保留草稿/可读提示，可重试；渲染不可用也能关闭面板或离开页面。
9. 当前角色变化清理该页临时状态、取消旧任务；旧角色回调不能写入新角色界面。
10. 一次性导航、Toast、请求不得直接放在会反复重组的 Composable 主体中执行。

小体积草稿和展示选择可用 SavedStateHandle/rememberSaveable；不要将全量历史、
View、JNI 对象或大文本列表放进 saved state。进程恢复不恢复虚假的“正在播放”，
Phase 2 以当前 runtime/持久化记录重建。

## 6. Phase 1 Mock 隔离与入口

采用**显式 debug 演示入口**，正式入口保持旧行为直到 Phase 2。
建议 Mock 数据、模拟驱动和入口实现在 `app\src\debug`，生产展示组件位于 main。
main 不能 import debug 类；release 不含演示入口，不增加 release 可导出组件。
具体用 debug Activity 还是 debug route，先根据现有导航选最小实现并记录；
不得让两个宿主并存，也不得通过旧 ChatActivity 的无条件 mainLoop 路径实现隔离。

演示的严格边界：

- 不实例化/启动正式聊天编排，不调用 `refreshAllKeys/mainLoop/selectCharacter` 整条路径。
- 不调用 ChatSession、翻译、记忆抽取、ChatHistoryStore、历史修复或附件 GC。
- 不绑定 Sherpa、不请求麦克风、不初始化 TTS/lipsync、不输出真实音频。
- 只在内存保存模拟会话，使用固定可辨识的演示内容与标识，不显示伪造的真实服务状态。
- 模拟 Listening/Thinking/Speaking 由可取消任务/可测试调度驱动；退出和切角色取消任务。
- 明确的调试操作能达到四态及失败态，不要求连接网络或真实服务。

真实角色可复用已安装 CharacterModel 的读取路径。首次无模型时，
只借用最小的角色资源准备能力；先确认 `CharacterRepository.loadCharacters()`
和安装器会复制哪些共享 BV2 文件。必要时分离“展示资源准备”与“语音初始化”，
不要为了纯 Mock 调完整正式 `initModel()`。
模型准备允许落盘必要资源，但不得产生聊天/记忆写入；不要重写存储布局。
无资源时展示明确占位和重试，不能用静态参考图冒充成功的 Live2D 验收。

宿主抽取需避免隐含依赖 Activity VM 的 lipsync/Sherpa 初始化。
首轮保留正式 ChatFragment 原能力，同时让同一个最小 Live2D 宿主可供演示使用；
仅在页面可见生命周期内拥有该宿主。若无法安全复用，先收紧到宿主适配而非复制 JNI 逻辑。

## 7. Phase 2 真实接线施工要求

### 7.1 发送、流与历史

- 真实状态适配仍由 `ChatActivityViewModel`/现有 runtime 提供；
  Companion VM 只管理展示和交互，不另建 provider、ChatSession 或持久化副本。
- 给当前轮保存可重新订阅的 UI 快照；当前无 replay SharedFlow 不能作为唯一恢复来源。
- 以本轮身份关联流式事件、错误和最终消息；旧请求/旧角色的晚到事件不能串页。
- 助手依旧 `beginAssistant → finishAssistant/failAssistant` 更新同一行；
  用户经现有入口只落一次。最终回复不得再被 UI append 成第二条。
- 保留 STREAMING/FAILED/OK、失败片段、provider/thinking/usage 信息及启动残留流修复。
- 历史面板使用 `ChatLogRepository`，以数据库 ID 为稳定 key，按角色观察近期记录；
  更早记录复用 `getOlderChatLog`，按 ID 去重合并并保留滚动锚点。
- 不修改 schema；不把 ChatSession 裁剪后的上下文当全量聊天历史。
- 当前 assistant 流式片段主要通过 UI Flow 发布，不能假设数据库占位行实时含片段；
  若历史中展示在途文本，按稳定 messageId 临时覆盖展示，落库完成后归并同一行。
  核对现有查询是否过滤 STREAMING，明确实现中采用的行为。
- 保持 `characterId` 当前按角色名隔离；稳定 UUID 是后续独立迁移，不在这里改。

### 7.2 TTS、ASR 与错误

- `SoundPlayHandler` 增加真实播放开始、完成、失败和停止通知，再上报 runtime。
- 完成必须是“本轮生成已结束且最后样本已播放”，不是推理返回、handler 收到数据、
  write 返回或某句音频排队完成；使用 AudioTrack 播放位置/标记等实际播放依据。
- 覆盖多句衔接、采样率变化/AudioTrack 重建、音频队列清空与停止；
  生命周期退出时不能把旧完成事件应用到新轮次。
- 无声库/关闭语音时直接展示回复并回 Idle，不伪造 Speaking。
  翻译/TTS 失败保留已成功生成的文本并明确提示，不撤销成功聊天记录。
- 不用推理估时或固定 delay 冒充实际播放完成；Mock 的模拟时长只能留在 debug。
- ASR 保留按住开始/松手结束；处理权限拒绝、服务尚未连接、断连、
  ACTION_CANCEL、离开页面及回调线程。接线范围内遇到缺失取消能力时最小补齐，
  不重写跨进程边界，也不增加常驻录音。
- 下一轮发送门禁必须覆盖现有 continuation 输入尚未就绪的情况；
  当前发送入口可能在无等待者时不接收，UI 不得显示“发送成功”后丢消息。
- 确保 Activity 重建不重复启动同一个 VM 的 mainLoop，启动修复只按适当生命周期运行。

### 7.3 正式启动与角色切换

- 恢复上次**有效**角色，选中状态经验证后保存；未命中时使用仓库稳定顺序中
  第一个可用内置角色，并明确提示恢复失败，不写死模型路径或修改 speaker 分配。
- 首次安装显示准备进度，无有效模型显示错误/管理入口；不能仅改导航起点为 nav_chat，
  因为当前 ChatFragment 依赖已选角色及资源准备。
- 渲染就绪即可显示角色，不再等待声库成功；语音准备失败不能阻断纯文本页面。
- 无 Key 允许进入角色主页；真实发送时提示去配置，不改变已有 provider 设置迁移逻辑。
- 从“更多”仍可选择角色、查看记录、配置、导入模型、管理记忆。
- 切换前等待/取消旧角色工作并恢复新角色历史，完成后才开放发送；
  不把异步 restore 尚未完成当成可发送。
- 不新增后台存活承诺；离开页面/后台时保持既有生命周期约束，
  只修复此次接线暴露的必要取消/清理问题。

## 8. 文件施工地图

新增文件名允许在不改变边界的前提下调整；开工后在第 12 节记录实际路径。

| 路径（app 源码短路径除外已写全） | 职责 | 阶段 |
|---|---|---|
| `ui\companion\CompanionScreen.kt` | 三层根布局，state/event 接口 | 1 |
| `ui\companion\CompanionUiState.kt` | 运行/覆盖层/加载状态与事件 | 1 |
| `ui\companion\CompanionViewModel.kt` | 页面状态，不拥有业务会话 | 1 |
| `ui\companion\CompanionOverlay.kt` | 姓名、工具、发言、浮动操作 | 1 |
| `ui\companion\CompanionInput.kt` | 草稿、展开、输入与 IME | 1 |
| `ui\companion\CompanionHistorySheet.kt` | 高度锚点与独立历史列表 | 1 |
| `ui\companion\CharacterRendererHost.kt`（必要时） | 复用 View 的薄包装，生命周期仍由宿主管理 | 1 |
| `app\src\debug\...`、debug manifest（必要时） | Mock 驱动、演示入口 | 1 |
| `ui\chat\ChatFragment.kt`、`ChatContent.kt` | 提取共用宿主/覆盖层，保留正式旧路径 | 1/2 |
| `ui\common\MessageFormatter.kt`、`UserInput.kt` | 优先复用，非必要不改共享行为 | 1/2 |
| `ui\login\LoginActivity.kt`、`ChatActivity.kt` | 正式启动及循环门禁 | 2 |
| `ui\channellist\ChannelListFragment.kt`、导航 XML | 角色页降为次级入口 | 2 |
| `ChatActivityViewModel.kt` | runtime 状态、会话恢复与播放事件 | 2 |
| `ui\chat\ChatFragmentViewModel.kt` | ASR 适配 | 2 |
| `VITS\src\main\java\com\chatwaifu\vits\SoundPlayHandler.kt` | 实际播放生命周期 | 2 |
| `VITS\src\main\java\com\chatwaifu\vits\utils\SoundGenerateHelper.kt` | 本轮生成与播放队列关联 | 2 |
| `app\src\main\res\values\strings.xml` | 文案与无障碍描述 | 1/2 |
| `app\src\test\...`、`app\src\androidTest\...` | 状态、交互、隔离测试 | 1/2 |
| `gradle\libs.versions.toml`、`app\build.gradle` | 仅必要测试依赖/调试配置 | 1 |

首轮不改 ChatCore provider、Log schema、媒体 normalizer、Sherpa 协议、
Cubism SDK 算法与模型资源。若 native 真正阻塞宿主正确工作，说明复现和最小修正，
不要以“保持 renderer 不动”为由放任崩溃，也不要顺势升级整套 SDK。

## 9. 另一台机器的准备与跨机器同步

### 9.1 必须先带过去的文件

1. 包含本文件和入口链接的提交。
2. 完整设计包目录（分析时它是 **untracked**，普通 clone/pull 不会带过去）。
3. 原工程正常跟踪的源码、Gradle wrapper 与子模块指针。

**本文写入磁盘不等于已提交或推送。** 当前 Agent 未获提交/推送要求时不要代做；
用户需先提交并推送所需文档/设计包，或明确将它们一同复制到新机器。
不要只复制 `docs` 却漏掉设计包。不要为传资料提交 local.properties、密钥、
构建产物、`.gradle`、AAR 或大模型权重。不得绕过模型资源许可。

新机器 checkout **实际包含交接文档的分支/提交**，不盲信默认 main。
若 Copilot 创建独立 worktree，以该工作区操作，不访问另一个主检出目录完成修改。

### 9.2 工具链与现场产出 AAR

当前基线：AGP 9.3.1、Gradle 9.6.1、Kotlin 2.3.21、KSP 2.3.11；
JDK toolchain 17、字节码目标 11；compileSdk 37 / targetSdk 36 / minSdk 29；
CMake 4.1.2；仅 arm64-v8a。NDK 按实际模块构建配置/AGP要求安装，不猜版本。
不在 UI 任务中顺手升级 Kotlin、Coil 或 AGP。

原 worktree 检查结果：BV2 子模块未初始化，本地 `Bert-VITS2-MNN\build\repo`
及 `local.properties` 不存在。这是该机器当时状态，不代表新机器也缺失。

新机器先检查 Git、Git LFS、JDK、Android SDK/CMake/NDK 与子模块访问权限。
`local.properties` 在本机配置 SDK，演示不需要填写真实 API Key。
确认缺失后执行初始化；子模块 URL 是 SSH，需要该机已有正常授权，不将私钥写入仓库。

以下为 Windows PowerShell 命令，**逐条执行并检查成功后再继续**：

```powershell
git status --short --branch
git log -1 --oneline
git submodule status
git lfs install
git submodule update --init --recursive
git -C .\Bert-VITS2-MNN rev-parse HEAD
git -C .\Bert-VITS2-MNN lfs pull --include="bertvits2-jni/src/main/assets/bv2_model/jp/*,bertvits2-jni/src/main/assets/bert/jp/*,openjtalk/src/main/assets/open_jtalk_dic_utf_8-1.11/*"
```

基线子模块 commit 是 `a45fd76668501aca3b383a3a69a36e84e953f2a4`，
对应 catalog `bertvits2 = "2.0.0-a45fd76"`。**若接手时指针或版本已变，读取实际值，
不要 checkout 旧 commit 或拿新源码冒充旧版本发布。**

在子模块目录产出 AAR：

```powershell
Push-Location .\Bert-VITS2-MNN
.\gradlew.bat publishAars -PpublishGroupId=com.chatwaifu.bv2 -PpublishVersion=2.0.0-a45fd76
Pop-Location
```

检查发布命令成功、`Bert-VITS2-MNN\build\repo` 存在且含对应坐标，再构建主项目。
只需日文 LFS 路径；不要把未拉取的语言指针误认作有效模型，
不要改用需要遍历所有语种 config 的 `BertVITS2SimpleInferImpl`。
其他操作系统使用对应 Gradle wrapper，路径分隔符按系统处理。

## 10. 分批施工清单与依赖

按顺序执行，每批是一个可回归的修改单元；若用户授权提交则按批提交，
否则保留可审阅 diff。提交消息不包含密钥或设备信息。

| 批次 | 状态 | 依赖 | 施工与退出条件 |
|---|---|---|---|
| P0 基线 | 已完成 | 无 | 阅读最新代码；记录 HEAD/环境；恢复缺失 AAR；构建现有工程，区分环境故障与代码故障 |
| P1-A 宿主与状态 | 已完成 | P0 | 提取最小宿主；独立 debug 入口；状态模型/Mock；无 Key 到达真实角色或明确占位 |
| P1-B Idle/Input | 已完成 | P1-A | 局部主题、当前发言、浮动动作、草稿/IME、返回处理；两态稳定往返 |
| P1-C Speaking/History | 已完成 | P1-B | 可取消模拟播报；双高度历史与滚动；覆盖层不停止角色、不丢运行状态 |
| P1-D 收口 | 已完成 | P1-C | 错误/空态、大字体、触控、隔离与生命周期测试；debug/release 构建；更新交接状态 |
| P1.5-A 输入与 Idle | 已完成 | P1-D | 输入改为 IME 上沿轻量条；底部操作缩小并区分主次；debug/release 构建通过 |
| P1.5-B 对白与字体 | 已完成 | P1.5-A | 对白高度随内容增长且设上限；保留角色名分区；系统信息使用现有无衬线字体；debug/release 构建通过 |
| P1.5-C 历史玻璃层 | 已完成 | P1.5-B | 保留 60%/96% 双高度；深蓝灰半透明层与紧凑消息块；debug/release 构建通过 |
| Phase 1 验收门 | 部分完成（真机余项保留） | P1-D | 自动化与构建已通过；API 36 已验证首次 IME 展开稳定，其余第 11 节设备交互与生命周期项仍待验证 |
| P2-M 最小真实链路 | 已完成（debug Companion 入口） | Phase 1 + 用户确认 | 现有 provider/Room/翻译/BV2 接入；明确六态、重试与忙碌门禁；API 37 arm64 虚拟设备冒烟 |
| P2.1 实时表现基础 | 已完成 | P2-M | 现有 provider 的文本 delta 增量上屏/落同一 STREAMING 行；AudioTrack marker 精确完成、取消与过期回调隔离 |
| P2-A 文本与历史 | 部分完成 | P2.1 | 流式片段、最终/失败状态和 DB 消息 ID 已接；历史分页及正式 runtime 复用仍待做 |
| P2-B 语音 | 部分完成（TTS） | P2.1 | BV2、口型、精确播放完成与取消已接；ASR 未做 |
| P2-C 正式入口 | 未开始 | P2-B + 用户确认 | 恢复角色、准备态、无 Key 可看角色、次级导航及旧数据回归 |
| P3/P4 | 范围外 | 独立需求 | 行为编排/后台陪伴另行设计 |

不需要再问用户是否允许开始已经要求的 Phase 1 施工；
需要用户决策时只问真正阻塞的问题，例如要求改变“编辑草稿但不打断播报”的默认行为。
路径命名、组件拆分等局部实现自行按项目风格确定并记录。

## 11. 验证与验收

### 11.1 构建与测试策略

基线/每个完整改动批次执行最小相关编译；最终执行打包、lint 与新增测试。
PowerShell 在仓库根目录：

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:assembleRelease
```

新增测试依赖只走 catalog，版本与当前 Compose BOM/协程版本兼容；
没有测试源时不要声称空 test task 验证了行为。新增后运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

设备测试需明确选择 arm64 设备/模拟器；x86 设备不能验证只带 arm64 的 native。
不必每批 clean 或运行全套；文档修改本身不需要 Android 构建。
构建阻塞时记录准确命令与失败阶段，不能删除 BV2 依赖来伪造通过。
已有 lint warning 数量只是历史记录，本次以实际基线比较，不承诺旧数字。

建议新增的最小自动化覆盖：

- 状态：Idle→输入→Thinking→Speaking→Idle；空白不提交；错误回输入且保留草稿。
- 覆盖层：Speaking 打开历史，播报结束后关闭得到 Idle；输入草稿开关历史不丢失。
- 取消：退出/切角色后旧模拟回调不更新状态。
- Compose：四态可达、返回优先级、占位/失败可操作、长消息布局与历史滚动。
- 隔离：Mock 路径没有 provider/DB/语音调用；release 不注册 debug 入口。

### 11.2 Phase 1 硬验收

- [ ] 四态可手动进入并退出，进入方式写入第 12 节。
- [ ] 主页仅当前发言，无全量历史瀑布流；角色仍为视觉主体。
- [x] 无 API Key、拒绝录音授权也可演示；不弹无关权限请求。
- [ ] 真实 Live2D 成功显示；占位只能覆盖缺资源场景，不能替代此项。
- [ ] 角色不可用时错误可见，可重试/离开，输入与历史仍能操作。
- [x] 输入弹出时 Surface 不因 UI 根布局 IME padding 被缩成半屏；关键面部尽量可见。
- [ ] 历史初始高度在可用高度的 55%–65%，可展开；长历史独立滚动。
- [ ] 开关历史不触发 native stop/reload；帧更新继续；手势不穿透。
- [ ] 返回顺序正确，草稿保留，关闭历史显示最新运行状态。
- [ ] 长发言有完整阅读入口，空列表/长单条/大字体均不遮挡退出和发送。
- [ ] 系统栏、刘海、键盘、手势导航均可用；按钮有无障碍标签与足够触摸区域。
- [ ] 导航往返、前后台、View/Activity 重建不重复持有 native 单例，不持续黑屏。
- [x] 模拟无网络、无录音、无真实音频、无聊天/记忆写入；退出取消模拟任务。
- [ ] 正式旧入口和设置/角色/模型管理/记忆页面仍可访问。
- [x] 新增测试通过，debug/release 均可构建，release 无演示入口。

真机建议覆盖 API 29 与 35/36 的可用设备，小屏手机、大字体；
大屏/横向窗口用来检查稳健性，不借此重做整套自适应产品设计。
记录测过的设备/API 与未覆盖场景；不能把模拟器截图推断成所有真机可用。

### 11.3 Phase 2 追加验收

真实 provider 单轮/多轮流式回复、失败片段；每条消息只落一次；
历史分页无重复/跳动，角色隔离；进程恢复残留流正确收尾；
无声库仍可文字聊天；实际最后样本播完才退出 Speaking；
录音拒权/取消/断连可恢复；角色切换无串回复；
未配置 Key 可看角色且发送时提示配置；既有数据不清空。
不在本 UI 任务中声称修复了全部历史数据库迁移问题。

## 12. 接手记录模板（施工时持续更新）

当前记录：

- 最后完成批次：P2.1 实时表现基础；流式文本和 AudioTrack 精确完成通知已接入，
  并完成 API 37 arm64 虚拟设备验证。
- 下一批次：停止施工；P2-C、ASR、历史分页及 Phase 3 均需用户另行授权。
- 实际新增/修改的应用源码：`ui/companion/` 状态、ViewModel、宿主与屏幕入口；
  `src/debug` 演示 Activity/Mock/真实 adapter/manifest；模型仓库准备路径；
  `ChatFragment` 改用共用宿主。
- 产品侧阻塞决定：最小 Phase 2 范围已完成；正式入口、ASR、流式 UI、历史分页、
  AudioTrack 精确完成通知及 Phase 3 均不在本批范围。

### 2026-09-13 / P0 基线

- 起始 HEAD / 完成 commit（未提交写“未提交”）：`f5506ba` / 未提交。
- 实际新增与修改文件：仅本施工文档。
- 实现选择与偏离原方案的理由：无实现偏离。确认本机 Android 37 平台目录名为
  `android-37.0`，不是检查脚本最初假定的 `android-37`；AGP 可正常识别。
- 调试入口和四态操作步骤：本批次尚未新增入口。
- 验证命令及结果：`./gradlew :app:compileDebugKotlin`，成功；JDK 17.0.20、
  CMake 4.1.2、NDK 28.2.13676358、arm64 BV2 子模块 `a45fd76` 与
  `2.0.0-a45fd76` 六个本地 AAR 均已就绪。
- 设备/API、场景与观察：未连接设备；仅完成宿主机和编译基线。
- 未覆盖项 / 阻塞原因：尚未做 UI、真机、lint 或 release 验证。
- 下一批次及第一个动作：P1-A；拆分仅准备 Live2D 展示资源的仓库入口，
  避免 Mock 演示安装或初始化 BV2/ASR/正式聊天链路。

### 2026-09-13 / P1-A 宿主与状态

- 起始 HEAD / 完成 commit（未提交写“未提交”）：`f5506ba` / 未提交。
- 实际新增与修改文件：新增 `ui/companion/CompanionUiState.kt`、
  `CompanionViewModel.kt`、`CharacterRendererHost.kt`、`CompanionScreen.kt`；
  新增 `src/debug/.../CompanionDemoActivity.kt`、`CompanionMockDriver.kt` 和 debug manifest；
  修改 `Constant.kt`、`CharacterRepository.kt`、`CharacterRepositoryImpl.kt`、
  `BuiltInModelInstaller.kt`、`ChatFragment.kt`。
- 实现选择与偏离原方案的理由：采用 debug-only 第二 launcher Activity，避免经过
  `ChatActivity.mainLoop()`；宿主仍是同一 GLSurfaceView/JNI 生命周期适配器，正式
  `ChatFragment` 同步复用。新增 `loadCharactersForDisplay()`，只解 Live2D，不碰共享
  BV2/BERT；这是检查原 `loadCharacters()` 后确认必须做的隔离。
- 调试入口和四态操作步骤：debug 安装后从桌面选择 `Companion UI Demo`；本批仅提供
  角色、对话和历史入口骨架，完整四态操作在 P1-C 记录。
- 验证命令及结果：`./gradlew :app:compileDebugKotlin`，成功。
- 设备/API、场景与观察：未连接设备；真实 Live2D 显示和生命周期仍待真机验证。
- 未覆盖项 / 阻塞原因：覆盖层仍是临时骨架；输入、Speaking、历史面板尚未施工。
- 下一批次及第一个动作：P1-B；实现冷色半透明 Idle 覆盖层、可读当前发言和
  独立消费 IME/navigation bar inset 的输入层。

### 2026-09-13 / P1-B Idle/Input

- 起始 HEAD / 完成 commit（未提交写“未提交”）：`f5506ba` / 未提交。
- 实际新增与修改文件：新增 `CompanionOverlay.kt`、`CompanionInput.kt`；重写
  `CompanionScreen.kt`；修改 `CompanionUiState.kt`、`CompanionViewModel.kt` 和
  `strings.xml`。
- 实现选择与偏离原方案的理由：使用半透明实色面板和局部上下渐变，不对
  GLSurfaceView 做实时 blur；输入层自身消费 `navigationBarsPadding`/`imePadding`，
  renderer 根层尺寸不随 IME padding 改变。
- 调试入口和四态操作步骤：进入 `Companion UI Demo` 即 Idle；点“对话”或当前发言
  进入 InputExpanded；返回键先关闭输入并保留草稿。
- 验证命令及结果：`./gradlew :app:compileDebugKotlin`，成功。
- 设备/API、场景与观察：未连接设备；键盘不压缩 Surface 的结论来自布局边界，
  仍需真机观察关键面部位置。
- 未覆盖项 / 阻塞原因：Speaking 指示和历史双高度面板尚未施工。
- 下一批次及第一个动作：P1-C；增加可取消 Mock 回复的 Speaking 指示，
  实现可在 60% 与安全区全高间切换且独立滚动的历史覆盖层。

### 2026-09-13 / P1-C Speaking/History

- 起始 HEAD / 完成 commit（未提交写“未提交”）：`f5506ba` / 未提交。
- 实际新增与修改文件：新增 `CompanionHistorySheet.kt`；修改
  `CompanionScreen.kt`、`CompanionOverlay.kt`、debug `CompanionDemoActivity.kt`
  和 `strings.xml`。
- 实现选择与偏离原方案的理由：Material 默认半展开锚点不能保证目标比例，采用页面局部
  显式双锚点（安全可用高度 60% / 96%），支持把手点击和上下拖动；未引入通用弹层框架。
  Mock 回复由 ViewModel scope 内可取消 Flow 驱动，历史层只改变 Compose overlay。
- 调试入口和四态操作步骤：Idle 点“对话”进入 InputExpanded；提交任意非空文本后
  依次进入 Thinking、Speaking、Idle；点右上历史进入 HistoryOpen，点击/拖动把手切换
  60% 与全高，返回或关闭按钮退出。输入 `/error` 可进入可恢复模拟错误。
- 验证命令及结果：`./gradlew :app:compileDebugKotlin`，成功。
- 设备/API、场景与观察：未连接设备；锚点比例和手势仍需设备验证。
- 未覆盖项 / 阻塞原因：自动化测试、renderer 可见错误面板、最终 debug/release/lint
  验证尚未完成。
- 下一批次及第一个动作：P1-D；修正视觉-only 安装与已有完整模型共存的边界，
  增加状态/取消测试、错误可操作 UI，并执行最终构建和 lint。

### 2026-09-13 / P1-D 收口与 Phase 1 自动化验收

- 起始 HEAD / 完成 commit（未提交写“未提交”）：`f5506ba` / 未提交。
- 实际新增与修改文件：新增 `app/src/test/.../CompanionViewModelTest.kt`；修改
  `BuiltInModelInstaller.kt`、`CharacterRepositoryImpl.kt`、`CharacterRendererHost.kt`、
  `CompanionInput.kt`、`CompanionHistorySheet.kt`、`CompanionOverlay.kt`、
  `app/build.gradle`、`gradle/libs.versions.toml` 和 `strings.xml`。
- 实现选择与偏离原方案的理由：模型准备在仓库单例内用 Mutex 串行化，视觉更新仅原子
  替换 `live2d/` 并保留完整声库 meta，且保留崩溃恢复 backup；从演示进入原功能前显式
  pause/stop/destroy 旧宿主再启动 `ChatActivity`，避免两个 Activity 争用 Native 单例。
  输入和历史面板消费全屏触控，避免事件穿透到 GLSurfaceView。
- 调试入口和四态操作步骤：安装 debug APK 后选择 `Companion UI Demo`；启动为 Idle；
  点当前发言或“对话”进入 InputExpanded；提交非空文本，经约 0.7 秒进入 Speaking，
  约 2.6 秒后回 Idle；任意时刻点右上历史进入 HistoryOpen，点击或上下拖动把手切换
  60%/96% 高度。输入 `/error` 验证错误后恢复输入和草稿；“更多”进入原功能。
- 验证命令及结果：
  `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleRelease`
  成功；新增 5 个测试全部通过；lint 0 error / 199 个历史 warning，新增 companion 路径
  无 warning；debug APK 359MB、release APK 332MB；合并后的 debug manifest 含
  `CompanionDemoActivity`，release manifest 不含该 Activity；`git diff --check` 通过。
- 设备/API、场景与观察：`adb devices -l` 无连接设备，本轮没有真机或模拟器 UI 观察。
- 未覆盖项 / 阻塞原因：真实 Live2D 首帧、API 29 与 35/36、IME/手势导航/刘海、
  大字体和小屏布局、历史拖动、前后台与 Activity 重建、正式旧入口导航回归均需
  arm64 设备验证；因此第 11.2 节其余设备相关项保持未勾选。
- 下一批次及第一个动作：Phase 1 验收门；在 arm64 设备安装
  `app/build/outputs/apk/debug/app-debug.apk`，按上述四态步骤逐项核对第 11.2 节。
  未经用户明确授权不得开始 Phase 2。

### 2026-09-13 / Phase 1 真机 IME 缺陷修复

- 起始 HEAD / 完成 commit（未提交写“未提交”）：`f5506ba` / 未提交。
- 实际新增与修改文件：修改 `app/src/debug/AndroidManifest.xml`，为
  `CompanionDemoActivity` 显式声明 `android:windowSoftInputMode="adjustResize"`。
- 实现选择与偏离原方案的理由：运行时确认 Activity 未显式配置时被系统解析为
  `adjustPan`，首次 IME 从 0 变为实际 inset 前先平移整个窗口；GLSurfaceView 尺寸没有变化。
  仅修正 debug 演示 Activity 的窗口策略，不改 Compose 层级、Insets、焦点时序或视觉设计。
- 调试入口和四态操作步骤：强制停止并重新启动 `Companion UI Demo`，首次从 Idle 点“对话”
  进入 InputExpanded，再关闭并重复打开。
- 验证命令及结果：`./gradlew :app:assembleDebug` 成功；合并 manifest 包含
  `adjustResize`；安装 APK 后 `dumpsys window windows` 显示
  `sim={adjust=resize forwardNavigation}`。
- 设备/API、场景与观察：arm64 真机 24129PN74C / API 36；首次及后续展开 IME 时
  Live2D 未再整页上移，仅输入控件移动到键盘上方。
- 未覆盖项 / 阻塞原因：仍未覆盖 API 29、小屏/大字体、历史拖动、前后台与 Activity 重建、
  正式旧入口导航回归等 Phase 1 真机验收项。
- 下一批次及第一个动作：继续 Phase 1 arm64 真机验收；不得进入 Phase 2。

### 2026-09-13 / Phase 1.5 Visual Polish

- 起始 HEAD / 完成 commit（未提交写“未提交”）：`f5506ba` / 未提交。
- 实际新增与修改文件：修改 `CompanionInput.kt`、`CompanionOverlay.kt`、
  `CompanionHistorySheet.kt` 和本施工文档；未修改 renderer、JNI/C++、业务链路或角色列表。
- 实现选择与偏离原方案的理由：删除输入态的大标题和双层纵向面板，改为只响应 IME inset
  的单行半透明输入条；关闭按钮保留 48dp 点击区但缩小图标并降低 alpha。Idle 四个动作保留
  48dp 点击区，视觉圆形缩为 38–42dp，仅“对话”保留较高对比度。对白面板不设固定高度，
  短句包裹内容，长句自然增长到 172dp 后内部滚动；角色名继续独立成段，并以低透明分隔线
  保留 Galgame 对白层级。历史仍使用原 60%/96% 高度和拖动逻辑，只替换为深蓝灰半透明底、
  低 alpha 边框及更紧凑的消息块。系统状态、操作标签、历史标题和时间显式使用项目现有
  `FontFamily.SansSerif`，未新增字体资源或依赖。
- 调试入口和四态操作步骤：安装 debug APK 后进入 `Companion UI Demo`；Idle 点“对话”
  检查 IME 上沿输入条；关闭后点右上历史检查半高玻璃层，原把手点击/拖动仍切换双高度。
- 验证命令及结果：每组修改后均分别执行
  `./gradlew :app:assembleDebug :app:assembleRelease`，三次均成功；`git diff --check`
  通过。
- 设备/API、场景与观察：arm64 真机 24129PN74C / API 36；Idle 中角色与当前对白仍为主体，
  四个操作视觉尺寸和非主操作对比度均降低；首次 InputExpanded 仅输入条移动到 IME 上方，
  无大标题且角色上半身完整可见；HistoryOpen 半高状态可透过深蓝灰层感知背景角色。
- 布局/性能风险：未增加 blur、PixelCopy、图片采样或新动画，只有透明色、尺寸和字体样式
  调整，无新增持续渲染负担。长对白达到 172dp 后仍依赖内部滚动；极端字体缩放可能减少
  首屏可见行数。输入条在超大字体或三行草稿时会向上增长，但 renderer 根尺寸不随之改变。
  仓库没有内置字体文件，`FontFamily.SansSerif` 会尊重 OEM/用户系统字体替换；API 36 真机
  启用了系统手写字体，因此无法在不新增字体资源的前提下保证这些文案始终呈现固定字形。
- 未覆盖项 / 阻塞原因：尚未真机覆盖超长对白、三行输入、长历史独立滚动、96% 历史高度、
  API 29、小屏、系统超大字体和横向/大屏窗口。
- 下一批次及第一个动作：继续 Phase 1 真机验收；未经用户明确授权不得开始 Phase 2。

### 2026-09-13 / P2-M 最小真实聊天链路

- 起始 HEAD / 完成 commit（未提交写“未提交”）：`f5506ba` / 未提交。
- 实际新增与修改文件：新增 debug-only
  `app/src/debug/java/com/chatwaifu/mobile/ui/companion/CompanionRealResponseDriver.kt`；
  修改 debug `CompanionDemoActivity.kt`，以及 main 中的 `CompanionViewModel.kt`、
  `CompanionUiState.kt`、`CompanionScreen.kt`、`CompanionInput.kt`、
  `CompanionOverlay.kt`、`strings.xml` 和 `CompanionViewModelTest.kt`；
  修改 `VITS/.../SoundPlayHandler.kt`，使终止时丢弃排队 PCM、释放轨道并退出工作线程；
  `app/build.gradle` 同时停止向构建日志打印 `local.properties` 密钥值。
- 实现选择与偏离原方案的理由：本批只接 debug Companion Demo，未替换正式
  `ChatActivityViewModel` 或导航入口。真实 adapter 复用 `ChatProviderFactory`、
  `ChatSession`、`ChatHistoryStore`、百度翻译、`SoundGenerateHelper` 和
  `LipsValueHandler`，以保持现有网络/TTS 实现不变；这不同于第 7.1 节最终架构所要求的
  “Companion VM 只消费正式 runtime”，因此只能视为最小接线，不是 P2-C 正式迁移。
  Activity 配置重建时保留同一 ViewModel 会话：只按角色名重新挂载新 renderer，不重复
  `setCharacter/prepare`，避免清空草稿、错误、历史或取消进行中的请求。
- 真实数据流：`CompanionInput` → `CompanionViewModel` →
  `CompanionRealResponseDriver` → `ChatProviderFactory/ChatSession.send()` →
  `ChatHistoryStore`；最终 assistant 文本上屏后，可选百度翻译，再进入
  `SoundGenerateHelper.generateAndPlay()` 与 `LipsValueHandler`。provider 内部仍按流读取，
  但本批只把最终文本交给 Companion UI，没有逐 delta 上屏。
- UI 状态与错误：支持 Idle、InputExpanded、Thinking、Speaking、HistoryOpen、Error；
  非空提交只追加一次 user，活动请求期间拒绝重复提交。聊天失败保留原 user 并提供重试；
  重试复用同一 UI/Room user 轮次，新建 assistant 占位。取消或异常会把 Room
  `STREAMING` 行收尾为 `FAILED`。翻译失败回退原文继续 TTS；TTS 失败保留 assistant
  文本和历史，以轻提示回 Idle。
- TTS 边界：现有 `SoundGenerateHelper` 没有 AudioTrack 硬件播放完成回调。本批按每段
  PCM 样本数/实际采样率累计预计播放结束时间后退出 Speaking，覆盖了多段排队，但不满足
  第 7.2 节要求的精确最后样本完成语义；完整 P2-B 仍需在现有播放层补正式通知。
- 验证命令及结果：
  `./gradlew :app:testDebugUnitTest --tests 'com.chatwaifu.mobile.ui.companion.CompanionViewModelTest' :app:assembleDebug :app:assembleRelease`
  成功；重复提交、失败重试、TTS 降级、历史恢复和切角色取消等测试通过。
- 设备/API、场景与观察：arm64 虚拟设备
  `sdk_gphone16k_arm64` / API 37。当前已配置 OpenAI 云端请求真实到达 provider，
  但账户额度耗尽，只完成 Thinking → 可重试 Error 验证。成功路径使用临时本机
  OpenAI-compatible SSE 端点经 `adb reverse` 验证同一 provider 流解析、Room 持久化、
  assistant 上屏、BV2 44100Hz 两段 PCM/口型、Speaking → Idle 与 History；临时端点、
  端口映射和设备配置随后均已清理。截图位于 `screen_shot/p2/virtual-thinking.png`、
  `virtual-error.png`、`virtual-speaking.png`、`virtual-idle-after-tts.png` 和
  `virtual-history.png`。
- 布局/性能风险：未修改 renderer/JNI/C++。聊天、Room、翻译和 native TTS 串在同一请求
  互斥区内，避免退出时提前释放资源，但长 TTS 期间不会并发下一轮。配置重建会重新加载
  角色清单和 renderer，不会重建业务会话。虚拟设备首次出现 Android 16KB page-size
  compatibility 警告，属于已有 native 库兼容风险。
- 未覆盖项 / 阻塞原因：未用有效额度完成真实云端成功回复；未做逐字流式 UI、历史分页/
  数据库稳定 ID、进程恢复、无声库设备路径、多角色切换、AudioTrack 精确完成回调、ASR、
  正式入口或 API 29 真机回归。Phase 1 其余真机项也不因本次虚拟设备冒烟而自动完成。
- 下一批次及第一个动作：停在 Phase 2；如用户授权继续，先决定完整 P2-A 是复用正式
  runtime 还是提升当前 adapter，再补流式快照和数据库稳定 ID。不得自行进入 P2-C 或
  Phase 3。

### 2026-09-13 / P2.1 实时表现基础

- 起始 HEAD / 完成 commit（未提交写“未提交”）：`f5506ba` / 未提交。
- 实际新增与修改文件：修改 `VITS/.../SoundPlayHandler.kt`、
  `VITS/.../SoundGenerateHelper.kt`、`app/.../utils/ChatHistoryStore.kt`、
  `CompanionRealResponseDriver.kt`、`CompanionUiState.kt`、`CompanionViewModel.kt`
  和 `CompanionViewModelTest.kt`；未修改 VITS native 推理、Live2D renderer 或 JNI/C++。
- AudioTrack 实现：每轮 TTS 分配独立 playback id，PCM 仍按 BV2 逐句生成并依次写入同一个
  MODE_STREAM AudioTrack。所有 PCM 写消息之后排入 End；End 以实际累计 frame 设置
  `setNotificationMarkerPosition`，并读取一次 `playbackHeadPosition` 关闭“设置 marker 前
  已经播完”的竞态。尚未播完时只等待 `OnPlaybackPositionUpdateListener`，没有轮询、
  样本时长 delay 或固定超时；continuation 与 playback id 一次性结算，旧 track 回调必须
  同时匹配当前 track 和当前轮次。
- 取消与释放：页面退出、ViewModel 清理和切角色先调用独立 `cancel()`，立即停止当前
  AudioTrack 并使其 playback id 失效；native 单句推理不被修改，推理返回后通过
  `ensureActive()` 阻止继续入队。提前取消的 tombstone 会由对应 Begin 消费；
  尚未被 Handler 消费的 completion 另行登记，release 时也会结算，避免悬挂或过期完成。
  工作线程退出前清空 PCM/marker 消息，下一轮使用新 track 和新 id。native 资源关闭改由
  独立 IO cleanup Job 等待请求互斥锁，不再从 Activity/ViewModel 生命周期回调
  `runBlocking` 主线程；进入旧页面时异步等待该 Job 完成后再启动，避免资源交叠。
- 流式文本：继续使用 `ChatSession.send()` 的原始 `ChatDelta.TextDelta`，没有第二套网络。
  每个 delta 累加成当前完整片段，经 `CompanionResponseEvent.Streaming` 更新 utterance
  及同一条 UI history item，同时 `ChatHistoryStore.updateStreamingAssistant()` 覆盖
  `beginAssistant()` 创建的同一条 Room `STREAMING` 行。Completed 后以原
  `finishAssistant()` 写完整 message/usage/thinking 并进入 Speaking；TTS 只处理完整文本。
  失败或取消以最后片段收尾为 `FAILED`；provider 已 Completed 后的最终 Room `OK` 写入放在
  `NonCancellable` 区间，assistant 占位行本身也在受保护区内创建，整轮 `finally` 会兜底
  标记 `FAILED`，关闭取消发生在插入、请求和最终持久化各阶段时遗留 STREAMING 的窗口。
- 历史恢复：Companion 展示历史改读原始 `ChatLogEntry`，使用数据库 id，并保留有文本的
  FAILED 记录；模型上下文仍走原 `load()`，继续过滤 FAILED/STREAMING，二者职责不混用。
- 自动化验证：
  `./gradlew :app:testDebugUnitTest --tests 'com.chatwaifu.mobile.ui.companion.CompanionViewModelTest' :app:assembleDebug :app:assembleRelease`
  成功，新增覆盖多次 Streaming 只更新一个 assistant item、首块到达后仍为 Thinking、
  完整 Reply 才进入 Speaking、流式失败保留部分文本，以及切角色触发播放取消；
  `git diff --check` 通过。
- 设备/API、场景与观察：arm64 虚拟设备
  `sdk_gphone16k_arm64` / API 37。临时 OpenAI-compatible SSE 每 2 秒发送一段，
  UI 依次显示“こんにちは。”→“こんにちは。リアルタイムで”→完整句，
  HistoryOpen 中同一 assistant 消息也原位增长。44100Hz BV2 生成两段 PCM 后，
  日志记录 `frames=155648 head=153549` 时仍保持 Speaking，约 92ms 后 marker 到达，
  仅记录一次 completion 并回 Idle；第二轮同样只完成一次。
- 取消验证：在下一轮 TTS 播放中退出页面，未收到该轮 marker completion，也未发生崩溃；
  重新进入后新 SoundPlayHandler 的 playback id 从独立轮次开始并正常完成，证明旧回调
  未污染新会话。最终实现再次覆盖了 native 推理期间返回桌面：Activity 未等待推理完成、
  无 ANR/崩溃，数据库状态统计为 `FAILED=1 / OK=13 / STREAMING=0`。临时 SSE、
  `adb reverse` 和 SharedPreferences 已恢复/清理。
- 截图：`screen_shot/p21/virtual-stream-1.png`、
  `virtual-stream-2.png`、`virtual-history-stream-1.png`、
  `virtual-history-stream-2.png`、`virtual-speaking.png`、
  `virtual-idle-after-marker.png`。
- 未覆盖项 / 风险：当前没有连接物理真机，因此精确完成、取消和流式 UI 的运行验证仅覆盖
  API 37 arm64 Virtual Device；仍需物理设备核对不同 Audio HAL、蓝牙/有线输出切换及来电/
  音频焦点中断。Room 当前每个文本 delta 都更新一次，长回复的写入频率后续可在不改变
  最终权威状态的前提下做合并节流；本批未做历史分页、ASR 或正式入口迁移。
- 下一批次及第一个动作：按用户要求停止，不进入 Phase 3。

每完成一批在本节追加：

```text
日期 / 批次：
起始 HEAD / 完成 commit（未提交写“未提交”）：
实际新增与修改文件：
实现选择与偏离原方案的理由：
调试入口和四态操作步骤：
验证命令及结果：
设备/API、场景与观察：
未覆盖项 / 阻塞原因：
下一批次及第一个动作：
```

交付报告必须分开写“已实现”“已验证”“仍未验证”。
对只有代码没有设备结果的项不要勾选真机验收完成。
无需另建重复规划文件，把实际落点和剩余任务更新到这里，确保再次换机器仍能接续。
