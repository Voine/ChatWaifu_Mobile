# v2.0.0 基座升级 · 断点记录

这份文件是「基座升级」这件事的施工日志，用来在中途断掉时能直接接上。
全部收尾做完、CLAUDE.md 订正完之后可以删掉。

分支 `feature/v2.0.0`。起点是 `a324535 依赖收拢，架构调整`。

## 升级前已经做完的（上一轮遗留在工作区、未提交）

- catalog：kotlin 2.2.10→2.3.21、新增 ksp 2.3.11、compose-bom 2023.04.01→2026.06.01、
  material 1.9.0-beta01→1.14.0、compileSdk 34→37、targetSdk 34→36，androidx/gson/retrofit 一批
- kapt → KSP：`Log/build.gradle` 换插件，`room.schemaLocation` 从
  `annotationProcessorOptions.arguments` 挪到 `ksp { arg(...) }`；根 `build.gradle` 同步
- 删掉 `app/build.gradle` 里的 `lint { disable 'MutableCollectionMutableState' }`
- API 迁移：`Divider`→`HorizontalDivider`（7 处）、`LinearProgressIndicator` 改 lambda progress、
  `LocalContext`→`LocalResources`、okhttp `RequestBody.create`→`toRequestBody`、
  `FocusRequester` 补 `remember`

验证基线（三条都过）：`:app:compileDebugKotlin`（clean 全量）、`:app:lintDebug`（0 error /
182 warning）、`:app:assembleDebug`（含 CMake native）。

## 施工清单

| # | 项 | 状态 |
|---|---|---|
| 1 | Edge-to-edge 适配（targetSdk 36 已生效） | ✅ 完成 |
| 2 | Gradle 10 兼容：空格赋值 + 弃用 DSL | ✅ 完成 |
| 3 | AGP 9 新 DSL + built-in Kotlin 迁移 | ✅ 完成 |
| 4 | catalog 收拢 okhttp / coroutines | ✅ 完成 |
| 5 | 清理源码 deprecated 调用 | ✅ 完成 |
| 6 | 更新 CLAUDE.md | ✅ 完成 |

**六项全部完成。** 收尾验证：`clean` 之后全量
`:app:assembleDebug` + `:app:assembleRelease` 一次过（含四个 CMake native ABI），
`--warning-mode all` 下零 `w:` / 零 `e:` / 零弃用提示；
`:app:lintDebug` 0 error / 177 warning（纯历史存量）。
产物 `app-debug.apk` 与 `ChatWaifu_202608061021.apk`。
**改动尚未提交**，仍在 `feature/v2.0.0` 工作区。

明确**不在**本轮范围内：lint 那 182 条 warning 的存量清理（62 条 UnusedResources +
27 条 HardcodedText 等历史包袱）、Kotlin 2.4（被 KSP 卡住，见 CLAUDE.md）。

## 施工日志

（每完成一项追加一节：改了什么、为什么、怎么验证的）

### 1. Edge-to-edge 适配 ✅

`targetSdk` 抬到 36 之后强制 edge-to-edge 其实已经在跑了，但适配一行没做。改动：

- `ChatActivity` / `LoginActivity` 显式调用 `enableEdgeToEdge()`。系统在 35+ 本来就强制，
  显式调用是为了 minSdk 24~34 上行为一致，外加把系统栏图标明暗对比交给 androidx
- `LoginActivity` 是 View 布局，edge-to-edge 下拿不到 inset，加了
  `applyWindowInsets()`：`ViewCompat.setOnApplyWindowInsetsListener` 把
  `systemBars | displayCutout | ime` **叠加**到布局原有 padding 上（不能覆盖，
  否则 `activity_login.xml` 里的 `activity_horizontal_margin` 会被冲掉）。
  带上 ime 是因为 `decorFitsSystemWindows=false` 之后窗口不再自动 resize
- `ChatContent.kt`：Scaffold 原本 exclude 了 `navigationBars` + `ime`，注释写着
  「so this can be added by the UserInput composable」，但那个 `UserInput` 压根没接回来
  （注释是从 `Conversation.kt` 抄来的，那边确实接了）。补上
  `Modifier.navigationBarsPadding().imePadding()`，padding 加在 `UserInput` 内层，
  `Surface` 的 tonalElevation 才能铺到导航栏后面
- `ChannelListContent.kt` / `ModelManagerContent.kt`：同样照搬了 exclude 但底部没有输入框，
  改成只 exclude `ime`，导航栏 inset 交回 Scaffold。否则列表最后一项被导航栏盖住
- `ChatLogContent.kt` / `SettingContent.kt`：Scaffold 没覆盖 `contentWindowInsets`，
  用的是默认值（含 systemBars），本来就是对的，只清掉 5 个没用上的 inset import
- `themes.xml`：删掉 `android:statusBarColor`（API 35+ no-op）和随之无用的 `xmlns:tools`。
  顺带干掉一条 `ObsoleteSdkInt` lint

**没动**：`Conversation.kt` 的 exclude 是对的（`UserInput` 自己带
`navigationBarsPadding().imePadding()`），保持原样。

**遗留观察**：`activity_chat.xml` + `app_bar_chat.xml` 是 Navigation 模板留下的死布局
（没有任何地方 inflate，真正的内容是 `content_main.xml`），`fitsSystemWindows` 只出现在
这个死文件里。属于那 62 条 `UnusedResources`，本轮没清。

验证：`:app:compileDebugKotlin` ✅、`:app:lintDebug` ✅（0 error，warning 182→181）。


### 2. Gradle 10 兼容 ✅

原先每次构建都提示 "Deprecated Gradle features were used in this build, making it
incompatible with Gradle 10"。改完之后这行提示消失。

- **空格赋值 → `=`**：Gradle 10 移除 `propName value` 的 Groovy 写法。
  用 `--warning-mode all` 拿到 Gradle 精确标记的 32 个位置再改，没有全局瞎替换 ——
  只有 **property** 受影响，`compileSdk` / `minSdk` / `proguardFiles` / `abiFilters` /
  `sourceCompatibility` / `path` 这些是**方法调用**，不能加 `=`（加了反而报错）。
  实际改的是：`namespace`、`version`（cmake）、`viewBinding`、`compose`、`buildConfig`、
  `aidl`、`useSupportLibrary`，外加 `settings.gradle` 里 11 处 `maven { url ... }`
- **`android.kotlinOptions {}` → 顶层 `kotlin { compilerOptions {} }`**，8 个模块全改。
  注意 `jvmTarget` 的类型从 String 变成枚举，得走
  `JvmTarget.fromTarget(libs.versions.jvmTarget.get())`
- **`packagingOptions {}` → `packaging {}`**（app）
- **根 `build.gradle`**：`task clean(type: Delete)` → `tasks.register('clean', Delete)`，
  `rootProject.buildDir` → `rootProject.layout.buildDirectory`
- **`settings.gradle`** 顺手去重：`maven.aliyun.com/repository/public/` 原本在
  pluginManagement 和 dependencyResolutionManagement 里各写了两遍（`url '...'` 和
  `url'...'` 少个空格所以没看出来）
- **Gradle wrapper 9.5.0 → 9.6.1**（`./gradlew wrapper --gradle-version 9.6.1`）

验证：`:app:assembleDebug --warning-mode all` ✅，且 grep 不到任何
`has been deprecated` / `incompatible with Gradle 10`。
首次跑 9.6.1 因为要重编 native 花了 10m33s，之后增量 9s。


### 3. AGP 9 新 DSL + built-in Kotlin 迁移 ✅

原先 `gradle.properties` 里挂着一排 `android.xxx=false` 的**逃生阀**，把 AGP 9 的新默认行为
按回了 AGP 8 的老样子。逃生阀在 AGP 10 会被移除，所以这轮按官方指引真正迁移掉。

- **built-in Kotlin**：AGP 9 起自带 Kotlin 支持，8 个模块的
  `alias libs.plugins.kotlin.android` 全部删除，catalog 里 `kotlin-android` 别名也删了。
  `kotlin` 这个 version 现在只服务 compose 编译器插件 —— 它会把 AGP 自带的 KGP 2.2.10
  顶到 2.3.21（`buildEnvironment` 里能看到 `2.2.10 -> 2.3.21`），**不会降版本**
- **`jvmTarget` 的 8 个显式声明全部删掉**。built-in Kotlin 下 Kotlin 的 jvmTarget 默认
  跟随 `android.compileOptions.targetCompatibility`，两边不可能再不一致。
  也就是说 CLAUDE.md 里长期记着的那条「两边必须一致否则报
  `Inconsistent JVM Target Compatibility`」的坑，**从此不存在了**。
  验证方式是直接读 class 文件头：app / Log / VITS 的产物都是 `major version: 55`（Java 11）
- **旧 variant API → `androidComponents`**：`android.applicationVariants.all {}` 换成
  `androidComponents.onVariants(selector().withBuildType('release'))`。
  踩到一个点：新 API 里 `outputFileName` 是 `Property<String>`，直接赋值会报
  `Cannot set readonly property: outputFileName`，得用 `.set()`
  - 顺带修了个老 bug：那段重命名代码虽然写在 `buildTypes.release {}` 里，但
    `applicationVariants` 是全局的，实际对 debug 也生效。现在 release 出
    `ChatWaifu_202608051854.apk`，debug 老老实实叫 `app-debug.apk`
- **移除的逃生阀**（共 9 条）：`builtInKotlin`、`newDsl`、`nonFinalResIds`、
  `defaults.buildfeatures.resvalues`、`sdk.defaultTargetSdkToCompileSdkIfUnset`、
  `enableAppCompileTimeRClass`、`usesSdkInManifest.disallowed`、
  `r8.optimizedResourceShrinking`、`dependency.useConstraints`。
  删之前先 grep 确认三件事都不存在：manifest 里的 `uses-sdk`（无）、
  gradle 里的 `resValue`（无）、Java 里的 `case R.id.xxx`（无，只有 Kotlin `when`）
- **`android.dependency.useConstraints=true` 也删了**：AGP 先提示
  `excludeLibraryComponentsFromConstraints` "should be enabled to improve performance"，
  但那个开关本身又是 deprecated / AGP 10 移除。这是给「超大工程」的性能建议，
  不值得为它改依赖解析语义。删掉 `useConstraints=true` 后对比
  `debugRuntimeClasspath` 的 md5（`c2e62d236f2d3273a4dd5e4abfeb3133`）完全一致，
  那条性能提示也一并消失了

**保留**的两条：`android.uniquePackageNames=false`、
`android.r8.strictFullModeForKeepRules=false`。试删过，构建都不受影响
（8 个模块 namespace 本来就唯一；strictFullMode 只在 R8 full mode + `minifyEnabled true`
下才生效，本工程 release 是 `minifyEnabled false`，眼下是惰性的）。
删了等于悄悄改动未来开混淆时的 R8 行为，收益为零，所以留着 —— 等真要上混淆时再一起决策。

验证：`:app:assembleDebug` / `:app:assembleRelease` / `:app:lintDebug` 三条都过，
`--warning-mode all` 下零 Gradle-10、零 AGP 弃用警告（只剩第 5 项要清的源码级 `w:`）。
KSP 2.3.11 正常生成 Room 实现。


### 4. catalog 收拢 okhttp / coroutines ✅

依赖收拢那轮漏了两个：okhttp 和 kotlinx-coroutines 从来没在 catalog 里出现过，
但源码里到处直接 import。这是典型的 **undeclared dependency** —— 代码依赖 A，
构建脚本只声明了 B，靠 B 传递带进 A。谁哪天升了 B、或者 B 换了实现，编译直接炸。

- catalog 新增 `okhttp = "4.12.0"` / `kotlinx-coroutines = "1.9.0"`，两个 library 别名。
  **版本刻意选的就是原先传递解析出来的那两个值**，收拢动作本身不带任何版本变化
- coroutines 选 `kotlinx-coroutines-android` 而非 `-core`：前者多带 `Dispatchers.Main`
  的 Android 实现，工程里到处在用
- 声明位置按实际 import 来，不搞一把梭：
  - okhttp → `ChatGPT`（`OkHttpClient` / `Interceptor` / `Request` / `Response` /
    `RequestBody` / `MediaType`，4 个文件）、`Translate`（`OkHttpClient`）
  - coroutines → `ChatGPT`、`Translate`、`Sherpa`（`SherpaHelper` 用
    `CoroutineScope` / `Dispatchers` / `launch`）、`app`（12 个文件）
  - `VITS` / `Live2D` / `Lipsync` / `Log` 没有直接 import，不加

**okhttp 为什么不升 5.x**：retrofit 3.0.0 自己依赖的就是 4.12.0；okhttp 5 把
`MediaType` / `RequestBody` 一批 API 做了 Kotlin 化重构（4.x 时代的
`DeprecationLevel.ERROR` 那批静态方法在 5 里彻底没了），是独立的迁移动作。

验证：`:app` / `:ChatGPT` / `:Translate` / `:Sherpa` 四个 `assembleDebug` 全量过（222 task）。
`debugRuntimeClasspath` 里 okhttp 仍是 `4.12.0`、coroutines-android 仍是 `1.9.0`
（只有 lifecycle 带的 `1.8.1 -> 1.9.0` 一处正常上调，和收拢前一致），
确认这一步**没有改动任何实际解析版本**。


### 5. 清理源码 deprecated 调用 ✅

目标是把编译期的 `w:` 清零。改完 `compileDebugKotlin` 一条警告都不剩。

**`ClickableText` → `Text` + `LinkAnnotation`（2 处，牵动 `MessageFormatter.kt`）**

这项不是换个 API 名字就完事。原来的写法是 `messageFormatter` 产出带
`addStringAnnotation` 的 `AnnotatedString`，`ClickableText.onClick(offset)` 里再手工
`getStringAnnotations(start, end)` 做命中测试，按 tag 分发到 `uriHandler` / `authorClicked`。
新做法把「哪段可点、点了干什么」直接内嵌进文本：

- `SymbolAnnotation` 从 `Pair<AnnotatedString, StringAnnotation?>` 改成
  `Pair<AnnotatedString, LinkAnnotation?>`；`@username` → `LinkAnnotation.Clickable`
  （listener 里回调 `authorClicked`），`http(s)://` → `LinkAnnotation.Url`
  （不用自己接 `UriHandler`，`Text` 会用 `LocalUriHandler` 打开）
- 构建时用 `withLink(link) { append(...) }` 包裹
- `messageFormatter` 因此多一个 `authorClicked` 参数；两个调用点本来就有这个回调
- `SymbolAnnotationType` 枚举（PERSON / LINK）、`StringAnnotation` typealias、
  两个调用点的 `LocalUriHandler` 全部随之删除 —— 类型信息现在由 `LinkAnnotation` 的子类承载

**顺带修掉一个存量 bug**：老代码 `addStringAnnotation(start = matchResult.range.first, ...)`
用的是**原始文本**下标，但 `*bold*` / `` `code` `` 这类 token 在构建 AnnotatedString 时
被 trim 掉了包裹符，实际长度变短。也就是说一条消息里只要在链接**前面**出现过一个这种 token，
链接的可点击范围就会整体右移。`withLink` 锚的是实际 append 进去的内容，这个错位自然消失。

**其余几项**

- `Resources.getColor(int)` → `ContextCompat.getColor(context, int)`（`ChatContent.kt` 3 个
  preview 里）。老 API 从 API 23 起废弃，因为它不带 Theme
- `AudioFormat.CHANNEL_CONFIGURATION_MONO/DEFAULT`（`RecordingUtils.kt`）。
  **这里也藏着一个 bug**：`CHANNEL_CONFIGURATION_MONO` 的值是 2，等于 `CHANNEL_OUT_MONO`，
  是**输出**声道掩码，喂给 `AudioRecord` 没有意义（录音侧单声道是 `CHANNEL_IN_MONO` = 16，
  正是主路径 `channelConfig` 已经在用的值）。换成输入侧常量后第一级 fallback 和主路径重合，
  于是两级 fallback 塌成一级 `CHANNEL_IN_DEFAULT`（值 1，和废弃的
  `CHANNEL_CONFIGURATION_DEFAULT` 同值，行为不变）
- 三处 unnecessary safe call：`ChatActivityViewModel.kt:266`（`isNullOrEmpty()` 已让
  `response` 智能转换成非空）、`ChatFragment.kt:98`、`LoginActivity.kt:52`
- `FakeData.kt` 的 `@DrawableRes` 加 `@param:` 限定符 —— Kotlin 2.x 警告未限定的注解
  将来会同时落到 field 上
- `ChatListSampleCode.kt` 从 material(M2) 统一到 material3，消掉 3 条
  `UsingMaterialAndMaterial3Libraries`。映射：`colors.*` → `colorScheme.*`
  （M3 无 `secondaryVariant`，取语义最近的 `secondary`）、
  `typography.subtitle2/body2` → `titleSmall/bodyMedium`、
  `Surface.elevation` → `shadowElevation`
- `ChatActivityViewModel.onCleared()` 去掉 `super.onCleared()`（父类实现为空，
  一条 `EmptySuperCall`）

验证：`:app:compileDebugKotlin` / `:VITS:compileDebugKotlin` **零 warning**、
`:app:assembleDebug` ✅、`:app:lintDebug` 0 error，warning 181 → 177。

**没动**的 lint 存量（本轮声明的范围外）：`UnusedResources` 62、`HardcodedText` 27、
`Typos` 20、`Autofill`/`TextFields` 各 11、`UseKtx` 3、`AutoboxingStateCreation` 1 等。
另外 `DiscouragedApi`（manifest 里锁死横屏，Android 16 起会被忽略）和
`OldTargetApi` 这两条是产品/适配决策，不属于基座收尾。


### 6. 更新 CLAUDE.md ✅

订正了三处**已经变成假话**的描述：

- 「`targetSdk` 停在 34，往 35/36 抬会触发强制 edge-to-edge……适合和 UI 迭代一起做」——
  已经是 36 且适配完了。换成一节 **Edge-to-edge**，写清新增页面该怎么写
  （Compose 默认别动 `contentWindowInsets`；只有底部挂 `UserInput` 才 exclude 并在
  `UserInput` 上补回 padding；View 布局自己监听且必须叠加不能覆盖）
- 「`compose-bom` 停在 `2023.04.01`，`MutableCollectionMutableState` 让 lint 崩溃，
  在 `app/build.gradle` 里 disable 掉了」—— bom 已经是 2026.06.01，那行 disable 上一轮就删了
- 「`applicationVariants` 写在 `release {}` 里但对所有 variant 生效，debug 包也被改名」——
  第 3 项已修

新增的部分：

- 技术栈补一条「走 AGP 9 built-in Kotlin」，Gradle 版本号 9.5 → 9.6.1
- 新增 **构建脚本约定** 一节：Groovy 属性必须 `=`（并说清哪些是方法调用不能加）、
  禁止 `kotlinOptions` / `kotlin.android` 插件 / 旧 variant API、
  别把 `builtInKotlin` / `newDsl` 逃生阀加回来
- `jvmTarget` 那条从「Java 和 Kotlin 两边必须一致否则报
  `Inconsistent JVM Target Compatibility`」改成「Kotlin 侧默认跟随 `targetCompatibility`，
  这个坑已不存在」
- 依赖管理补一条「用到就显式声明，别靠传递依赖」，并说明 okhttp 4.12.0 /
  coroutines 1.9.0 为什么钉在这两个版本
- 页面一节补 `MessageFormatter.kt` 的约定：富文本的可点击区域以 `LinkAnnotation` 内嵌，
  渲染端用 `Text`，不要再用 `ClickableText` + `getStringAnnotations`
- 技术债列表里把 lint 存量写成具体数字和构成（177 条，含两个死布局文件），
  并补上 manifest 锁死横屏在 Android 16 会被忽略这条

`material-icons-extended` 钉在 1.7.8 那条**复核后仍然成立**（实测解析结果就是 1.7.8，
和 compose 1.11.4 混用），保留。
