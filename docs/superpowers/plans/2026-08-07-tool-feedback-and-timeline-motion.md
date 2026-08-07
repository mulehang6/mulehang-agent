# 顶栏反馈与工具时间线动效 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让分支复制反馈跟随鼠标，并以语义化图标、动作文本和缓慢过渡提升工具时间线的可读性。

**Architecture:** 在 `ConversationTimeline.kt` 中保留事件分组规则，新增纯展示规则来解析当前运行工具的标题、图标和动效类别；Compose 组件只消费这些展示规则。标题栏把复制反馈提升为带可选指针坐标的轻量状态，由 `ChatScreen` 根层负责定位 toast。

**Tech Stack:** Kotlin、Compose Multiplatform Desktop、Material 3、kotlin.test、JUnit 5。

## Global Constraints

- 只修改 `desktopApp`；不得改变 `shared` 的工具事件协议、会话持久化或 Markdown 解析语义。
- 保留当前工作区中用户已有的未提交改动，并只做与本功能直接相关的增量改动。
- 每个 Kotlin 文件修改后立即通过 IDEA `get_file_problems` 检查；随后运行窄范围测试和 `:desktopApp:compileKotlin`。
- 不启动 Desktop、Vite 或其他长期运行服务。
- 未获用户明确授权前不得执行 Git 提交。
- 所有新增生产类型与函数都要有简短 KDoc；所有循环动画只在工具 `Started` 时运行，并尊重减少动态效果。

---

## 文件职责

- `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`：工具意图映射、组标题选择、语义化 Canvas 图标、组标题和详情的慢速过渡、工具行尺寸。
- `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`：分支 Chip 的紧凑尺寸，以及 Compose/Swing 标题栏分支点击和指针坐标采集。
- `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`：带坐标的应用反馈状态、根层指针跟踪和 toast 定位。
- `desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownInlineExtensions.kt`：降低 `==高亮==` 的底色饱和度和明度。
- `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`：降低 Markdown 行内代码底色的饱和度和明度。
- `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`：标题栏、工具展示规则、尺寸与动画时长的单测。
- `desktopApp/src/test/kotlin/com/agent/app/chat/component/AssistantMarkdownInlineExtensionsTest.kt`：行内高亮颜色的单测。

## Task 1: 工具意图展示规则与慢速参数

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt:162-203`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt:988-1047`

**Interfaces:**
- Produces: `TimelineToolPresentation`, `timelineToolPresentation(item)`, `activeTimelineTool(items)`, `toolGroupHeadline(items)`。
- Consumes: `ToolEventItem`、`ToolEventStatus.Started` 和既有 `isTerminalToolEvent(item)`。

- [ ] **Step 1: 写入失败测试，锁定工具类别、Shell 例外和标题规则。**

```kotlin
assertEquals("Gathering context…", toolGroupHeadline(listOf(toolEvent("grep", ToolEventStatus.Started))))
assertEquals("Gathering context…", toolGroupHeadline(listOf(toolEvent("list_directory", ToolEventStatus.Started))))
assertEquals("Editing…", toolGroupHeadline(listOf(toolEvent("edit_file", ToolEventStatus.Started))))
assertEquals("已执行工具 · 1", toolGroupHeadline(listOf(toolEvent("run_powershell", ToolEventStatus.Started))))
assertEquals(TimelineToolGlyph.DIRECTORY, timelineToolPresentation(toolEvent("list_directory", ToolEventStatus.Started)).glyph)
assertEquals(TimelineToolGlyph.EDIT, timelineToolPresentation(toolEvent("apply_patch", ToolEventStatus.Started)).glyph)
```

- [ ] **Step 2: 运行测试，确认新接口尚不存在。**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: FAIL，未解析 `toolGroupHeadline`、`timelineToolPresentation` 或新图标枚举值。

- [ ] **Step 3: 实现最小、可测试的展示映射。**

```kotlin
internal data class TimelineToolPresentation(
    val glyph: TimelineToolGlyph,
    val groupHeadline: String?,
)

internal fun activeTimelineTool(items: List<ToolEventItem>): ToolEventItem? =
    items.lastOrNull { it.status == ToolEventStatus.Started }

internal fun toolGroupHeadline(items: List<ToolEventItem>): String {
    val active = activeTimelineTool(items)
    return active?.let(::timelineToolPresentation)?.groupHeadline
        ?: buildToolGroupHeadline(items.size)
}
```

将 `grep`、`find`、`search`、`list_directory`、`read_file` 映射为 `Gathering context…`；`edit`、`patch`、`write` 映射为 `Editing…`；Shell/PowerShell/terminal 的 `groupHeadline` 为 `null`，因此不会因 Shell 出现而切换标题。扩展 `TimelineToolGlyph` 为 `SEARCH`、`DIRECTORY`、`TERMINAL`、`EDIT`、`READ`、`NETWORK`、`GENERIC`，并为网络和未知工具提供简洁英文动作或数量摘要。

- [ ] **Step 4: 声明可读尺寸与慢速动效常量。**

```kotlin
internal const val TOOL_GROUP_TITLE_FONT_SIZE_SP = 16
internal const val TOOL_ROW_FONT_SIZE_SP = 15
internal const val TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS = 420
internal const val TOOL_GROUP_EXPAND_DURATION_MILLIS = 400
internal const val TOOL_GROUP_COLLAPSE_DURATION_MILLIS = 300
internal const val TOOL_ROW_EXPAND_DURATION_MILLIS = 340
internal const val TOOL_ROW_COLLAPSE_DURATION_MILLIS = 260
```

同步为组图标和工具行图标分别声明 `20.dp`、`18.dp` 的常量；保留零垂直内边距，避免增加字号后时间线过度膨胀。

- [ ] **Step 5: 运行测试，确认展示规则和常量正确。**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: PASS。

## Task 2: 工具组标题、图标和详情的语义化动画

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt:596-898`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt:988-1047`

**Interfaces:**
- Consumes: Task 1 的 `TimelineToolPresentation`、`activeTimelineTool(items)`、`toolGroupHeadline(items)` 和时间常量。
- Produces: `TimelineToolGroup` 中的动态标题和组图标，以及接收尺寸参数的 `TimelineToolGlyphIcon`。

- [ ] **Step 1: 增加失败测试，固定过渡时长、图标尺寸和运行态规则。**

```kotlin
assertEquals(420, TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS)
assertEquals(400, TOOL_GROUP_EXPAND_DURATION_MILLIS)
assertEquals(300, TOOL_GROUP_COLLAPSE_DURATION_MILLIS)
assertEquals(16, TOOL_GROUP_TITLE_FONT_SIZE_SP)
assertEquals(15, TOOL_ROW_FONT_SIZE_SP)
assertTrue(shouldAnimateTimelineToolGlyph(ToolEventStatus.Started))
assertEquals(false, shouldAnimateTimelineToolGlyph(ToolEventStatus.Finished))
```

- [ ] **Step 2: 运行测试，确认慢速参数尚未生效。**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: FAIL，常量或期望值与现有 180/120ms 动效不一致。

- [ ] **Step 3: 将工具组标题替换为双轨 `AnimatedContent`。**

```kotlin
AnimatedContent(
    targetState = toolGroupHeadline(items),
    transitionSpec = {
        (slideInVertically(tween(TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS)) { it / 2 } +
            fadeIn(tween(TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS)))
            .togetherWith(
                slideOutVertically(tween(TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS)) { -it / 2 } +
                    fadeOut(tween(TOOL_GROUP_TITLE_SWITCH_DURATION_MILLIS)),
            )
    },
    label = "tool-group-headline",
) { headline ->
    Text(
        text = headline,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = TOOL_GROUP_TITLE_FONT_SIZE_SP.sp,
        ),
    )
}
```

标题进入方向必须来自下方，离开方向必须向上。为组标题新增紧邻文本的 `AnimatedContent` 图标：图标仅使用 300ms 淡入和 `0.92f → 1f` 缩放，不参与上下位移。

- [ ] **Step 4: 替换通用摆动图标为按类别绘制的动画。**

在 `TimelineToolGlyphIcon` 中以 `Canvas` 绘制：搜索为放大镜扫过三条文字线；目录为顺次点亮的树节点；编辑为笔尖沿文字线平移并闪烁插入光标；终端为提示符光标脉冲；读取为页面扫描线；网络为方向流动。仅在 `running` 时启动无限过渡，完成、失败和取消时绘制静态图标。行图标使用 `18.dp`，组图标使用 `20.dp`。

- [ ] **Step 5: 放慢展开/收起，并放大工具行层级。**

将 `TimelineToolGroup` 和 `TimelineToolTextRow` 的 `AnimatedVisibility` 时长替换为 Task 1 常量；同步 chevron 旋转时长。标题改为 `16.sp`，工具行改为 `15.sp`，保持悬浮热区、输出展开逻辑和自动收起语义不变。

- [ ] **Step 6: 检查文件问题并运行展示测试。**

通过 IDEA 执行：`get_file_problems --filePath desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`。

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: 无新增 IDE 问题，测试 PASS。

## Task 3: 紧凑分支 Chip 与鼠标跟随复制反馈

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt:84-257, 510-805`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt:100-264`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt:839-884`

**Interfaces:**
- Produces: `AppFeedbackState(message: String, anchor: Offset?)` 和 `feedbackToastAnchor(pointerPosition: Offset?): Offset?`。
- Consumes: `AppFeedbackToast(message, modifier)`、Compose `PointerEvent` 与原生 `MouseEvent`。

- [ ] **Step 1: 写入失败测试，锁定目标 Chip 尺寸和反馈锚点规则。**

```kotlin
assertEquals(HEADER_TASK_CHIP_HEIGHT_DP, HEADER_BRANCH_CHIP_HEIGHT_DP)
assertEquals(Offset(48f, 24f), feedbackToastAnchor(Offset(48f, 24f)))
assertNull(feedbackToastAnchor(null))
```

- [ ] **Step 2: 运行测试，确认当前分支 Chip 仍高于任务 Chip，且无锚点函数。**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: FAIL，当前 `HEADER_BRANCH_CHIP_HEIGHT_DP` 为 56，且反馈状态只包含字符串。

- [ ] **Step 3: 收紧分支 Chip 并记录点击/移动位置。**

令 `HEADER_BRANCH_CHIP_HEIGHT_DP` 与 `HEADER_TASK_CHIP_HEIGHT_DP` 相等，使 Compose 和 Swing 覆盖层自动共享图二对应的紧凑高度。Compose 分支区域在指针移动和按下时保存根坐标；点击时把坐标和 `"已复制"` 一并传出。

将 `NativeTitleBarTaskHitOverlay`/`NativeTitleBarTaskHitTarget` 的主点击回调扩展为携带本地 `Offset`，并与 SwingPanel 在根布局中的位置相加后传出根坐标。任务标题菜单维持原有无坐标回调，避免改变其右键菜单行为。

- [ ] **Step 4: 用带锚点状态定位反馈 toast。**

```kotlin
internal data class AppFeedbackState(
    val message: String,
    val anchor: Offset?,
)
```

在 `ChatScreen` 根层保存 `AppFeedbackState?`；当它包含锚点时，将 `AppFeedbackToast` 对齐到 `TopStart` 并使用锚点右下的固定间距定位。根层的指针移动事件只在该状态存在时更新 `anchor`，所以 toast 会跟随鼠标；没有锚点时继续使用现有底部居中反馈，作为安全回退。

- [ ] **Step 5: 分别检查两个 Kotlin 文件并运行展示测试。**

通过 IDEA 依次执行：

```text
get_file_problems --filePath desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt
get_file_problems --filePath desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt
```

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: 无新增 IDE 问题，测试 PASS。

## Task 4: 柔化主内容高亮

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownInlineExtensions.kt:20-21`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/AssistantMarkdownInlineExtensionsTest.kt:12-29`

**Interfaces:**
- Consumes: `AssistantMarkdownHighlightBackground`。
- Produces: 低饱和灰蓝的 `Color(0xFF3A414A)` 高亮背景。

- [ ] **Step 1: 写入失败测试，固定柔和目标色。**

```kotlin
assertEquals(Color(0xFF3A414A), AssistantMarkdownHighlightBackground)
assertTrue(rendered.spanStyles.any { it.item.background == AssistantMarkdownHighlightBackground })
```

- [ ] **Step 2: 运行测试，确认旧的偏黄高亮色不满足预期。**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.AssistantMarkdownInlineExtensionsTest"`

Expected: FAIL，当前值为 `Color(0xFF5C4B12)`。

- [ ] **Step 3: 以最小改动替换高亮色。**

```kotlin
internal val AssistantMarkdownHighlightBackground = Color(0xFF3A414A)
```

不调整解析正则、选择行为或链接颜色；本任务只降低 `==高亮==` 在暗色主内容区的视觉攻击性。

- [ ] **Step 4: 检查文件问题并运行行内 Markdown 测试。**

通过 IDEA 执行：`get_file_problems --filePath desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownInlineExtensions.kt`。

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.AssistantMarkdownInlineExtensionsTest"`

Expected: 无新增 IDE 问题，测试 PASS。

## Task 5: 集成验证与交付检查

**Files:**
- Modify: 仅在前述任务产生的必要修复范围内修改。

- [ ] **Step 1: 运行受影响组件测试。**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest" --tests "com.agent.app.chat.component.AssistantMarkdownInlineExtensionsTest"`

Expected: PASS。

- [ ] **Step 2: 编译桌面端。**

Run: `.\gradlew.bat :desktopApp:compileKotlin`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 在 IDEA 检查已改 Kotlin 文件。**

通过 IDEA 对 `ChatHeader.kt`、`ChatScreen.kt`、`ConversationTimeline.kt`、`AssistantMarkdownInlineExtensions.kt` 执行 `lint_files`，确认没有新增 error 或 warning。

- [ ] **Step 4: 审查 diff 与验收点。**

确认变更仅覆盖本计划；确认分支 Chip 高度与任务 Chip 一致、Shell 不改组标题、搜索/目录/读取显示 `Gathering context…`、编辑显示 `Editing…`、所有运行工具有语义动画、组标题按旧上新下的方向慢速切换、toast 的锚点跟随指针、以及高亮目标色为 `#3A414A`。

- [ ] **Step 5: 不执行 Git 提交。**

用户尚未明确授权提交；报告改动和验证结果，等待其后续指示。
