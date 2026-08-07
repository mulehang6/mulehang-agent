# 工具交互动效与终端收起 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Desktop 工具交互增加终端收起、非终端工具最短运行展示、双层卡片堆叠和更精致的提问/审批交互卡。

**Architecture:** 终端收起沿用 `ChatScreen` 已有的 `terminalPanelVisible` 状态，只新增从面板标题栏回传的隐藏回调。工具时间线在 `ConversationTimeline` 中维护仅供渲染的状态快照：非终端工具完成过快时继续显示 `Started` 至两秒，随后切换为实际结果；工具组一次只渲染当前项与下一项预览。提问与审批保持现有状态模型，只重构 Compose 外观与进入/离开转换。

**Tech Stack:** Kotlin Multiplatform、Compose Multiplatform Desktop、Material 3、kotlin.test、JUnit 5。

## Global Constraints

- 只改 `desktopApp`，不修改 `shared` 工具协议或执行时序。
- 非终端工具立即执行；仅结果展示确保从开始起至少持续 2,000ms。
- `run_powershell` 等终端工具不施加展示延迟。
- 终端收起绝不能释放 `TerminalSessionStore` 中的会话或移除标签。
- 工具组最多显示前景当前卡与一张后置预览，不渲染更多预览卡。
- 不提交 Git；仓库规则要求显式授权后才能提交。

---

### Task 1: 为终端面板增加仅收起的标题栏操作

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt:231-374`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt:58-280`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt:194-228`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalTabsStateTest.kt`

**Interfaces:**
- Consumes: `terminalPanelVisible: Boolean` and `TerminalRailAction.HIDE`.
- Produces: `EmbeddedTerminalPanel(..., onHidePanel: () -> Unit)` and `terminalPanelHideActionLabel()`; the callback is handled as `terminalPanelVisible = false` without calling `TerminalSessionStore.close` or `TerminalTabsState.closeTab`.

- [ ] **Step 1: Write the failing test**

Add a test for the new header action’s explicit, non-destructive semantic label, alongside the existing rail-hide coverage:

```kotlin
@Test
fun `terminal header close action is labeled as hide`() {
    assertEquals("收起终端", terminalPanelHideActionLabel())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.TerminalTabsStateTest"`

Expected: FAIL because `terminalPanelHideActionLabel` does not yet exist.

- [ ] **Step 3: Write minimal implementation**

Extend the panel and parent plumbing; render a 24dp `TerminalActionGlyph(cross = true, ...)` in the terminal header’s right edge with semantic text `收起终端`:

```kotlin
internal fun EmbeddedTerminalPanel(
    tabs: TerminalTabsState,
    sessions: TerminalSessionStore,
    onSelectTab: (Long) -> Unit,
    onAddTab: () -> Unit,
    onCloseTab: (Long) -> Unit,
    onCloseOtherTabs: (Long) -> Unit,
    onHidePanel: () -> Unit,
    modifier: Modifier = Modifier,
)

internal fun terminalPanelHideActionLabel(): String = "收起终端"

```

`WorkspacePanel` adds and forwards an `onHideTerminalPanel: () -> Unit` parameter; `ChatScreen` passes `{ terminalPanelVisible = false }`.

Do not route this new callback through `onCloseTab`, `pendingTerminalTabCloseId`, `TerminalSessionStore.close`, or `TerminalTabsState.closeTab`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.TerminalTabsStateTest"`

Expected: PASS; existing tab close behavior remains covered by its current tests.

### Task 2: 建立非终端工具的最短运行展示与双层堆叠模型

**Files:**
- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ToolTimelineCardStack.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt:702-895`
- Create: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ToolTimelineCardStackTest.kt`

**Interfaces:**
- Consumes: `ToolEventItem`, `ToolEventStatus`, `isTerminalToolEvent(item)` and the current clock in milliseconds.
- Produces: `TOOL_MINIMUM_RUNNING_DISPLAY_MILLIS = 2_000L`, `toolCompletionDelayMillis(item, startedAtMillis, nowMillis): Long`, and `visibleToolCardStack(items): List<ToolEventItem>` returning at most two items.
- Produces: `ToolTimelineCardStack(items)` which retains a `Started` display snapshot until `toolCompletionDelayMillis` returns zero, then renders the actual completed/failed event.

- [ ] **Step 1: Write the failing tests**

Define the exact rules as pure tests:

```kotlin
@Test
fun `non terminal completion waits for the two second running display`() {
    val item = toolEvent("read_file", ToolEventStatus.Finished)

    assertEquals(1_250L, toolCompletionDelayMillis(item, startedAtMillis = 100L, nowMillis = 850L))
    assertEquals(0L, toolCompletionDelayMillis(item, startedAtMillis = 100L, nowMillis = 2_100L))
}

@Test
fun `terminal completion is never delayed`() {
    assertEquals(0L, toolCompletionDelayMillis(toolEvent("run_powershell", ToolEventStatus.Finished), 100L, 100L))
}

@Test
fun `tool card stack exposes current card and one preview only`() {
    val items = listOf("one", "two", "three").map { toolEvent(it, ToolEventStatus.Started) }

    assertEquals(listOf("one", "two"), visibleToolCardStack(items).map(ToolEventItem::toolName))
}

private fun toolEvent(name: String, status: ToolEventStatus): ToolEventItem = ToolEventItem(
    toolName = name,
    status = status,
)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ToolTimelineCardStackTest"`

Expected: FAIL because the stack functions and constant do not yet exist.

- [ ] **Step 3: Write minimal presentation-state implementation**

Add a focused `ToolTimelineCardStack.kt` with pure delay/visibility helpers and a composable. In `ConversationTimeline`, replace `items.forEach { TimelineToolTextRow(...) }` inside `TimelineToolGroup` with the stack component. Use an `AnimatedContent` keyed by `toolCallId` for front-card transitions: outgoing card slides left/fades; incoming card starts slightly right, scaled down, then reaches full scale. Draw only the next item behind it with approximately `translationX = 8.dp`, `translationY = 6.dp`, `scale = 0.97f`, and reduced alpha.

For state timing, capture a `Started` snapshot and start timestamp when a tool first appears. When the corresponding non-terminal event changes to `Finished` or `Failed`, retain the snapshot and launch `delay(remainingMillis)` before replacing it. Use the actual item immediately for terminal tools and for results that arrive after two seconds.

- [ ] **Step 4: Run focused tests to verify they pass**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ToolTimelineCardStackTest" --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: PASS; existing glyph, grouping and terminal presentation tests remain green.

### Task 3: 重新设计提问输入卡并统一两类交互卡入场动画

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/tool/component/ToolInteractionCards.kt:83-207`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ComposerPanel.kt:223-249`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/tool/component/ToolInteractionCardsTest.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Consumes: `QuestionCardModel`, `ApprovalCardModel` and the current `PendingInteractionCards` visibility condition.
- Produces: shared motion constants `PENDING_CARD_ENTER_DURATION_MILLIS` and `PENDING_CARD_EXIT_DURATION_MILLIS`, with enter applying fade + upward motion + scale, and exit applying fade + upward motion.
- Produces: `QuestionFreeTextInput` as the custom desktop-style multi-line input surface; it calls `onSubmitText(draft.trim())` only for nonblank input.

- [ ] **Step 1: Write the failing tests**

Add focused rules rather than pixel tests:

```kotlin
@Test
fun `question free text submit is enabled only for non blank input`() {
    assertEquals(false, canSubmitQuestionFreeText("   "))
    assertEquals(true, canSubmitQuestionFreeText("使用第一种方式"))
}

@Test
fun `pending interaction enter motion includes a scale step`() {
    assertEquals(0.96f, PENDING_CARD_ENTER_INITIAL_SCALE)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.tool.component.ToolInteractionCardsTest" --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: FAIL because the input predicate and initial-scale constant do not exist.

- [ ] **Step 3: Write minimal implementation**

Replace `OutlinedTextField` in `QuestionCard` with a custom `BasicTextField` surface: dark `AppPanelBackground`, 14dp rounded corners, 1dp `AppLine.copy(alpha = 0.72f)` border, 12dp content padding and muted placeholder `补充你的回答…`. Keep the existing options and callbacks. Place the submit action on the input surface’s trailing/bottom row using `RingPrimaryButton`, and use `canSubmitQuestionFreeText(draft)` for both enabled state and click guard.

Update `PendingInteractionCards` enter transition:

```kotlin
enter = fadeIn(tween(PENDING_CARD_ENTER_DURATION_MILLIS)) +
    slideInVertically(tween(PENDING_CARD_ENTER_DURATION_MILLIS)) { height -> height / 8 } +
    scaleIn(
        initialScale = PENDING_CARD_ENTER_INITIAL_SCALE,
        animationSpec = tween(PENDING_CARD_ENTER_DURATION_MILLIS),
    )
```

Keep a single shared transition for approval and question cards. Do not alter `PendingQuestionUiState` or `PendingApprovalUiState`.

- [ ] **Step 4: Run focused tests to verify they pass**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.tool.component.ToolInteractionCardsTest" --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: PASS.

### Task 4: IDE inspection and module-level verification

**Files:**
- Inspect: all source and test files changed by Tasks 1-3.

**Interfaces:**
- Consumes: completed source edits and focused test results.
- Produces: an IDE-clean change set that compiles within `desktopApp`.

- [ ] **Step 1: Inspect each edited source file immediately after its patch**

Use IDEA `get_file_problems` for each changed Kotlin file. Resolve every new error and any warning caused by these changes before moving to another file.

- [ ] **Step 2: Run the complete module test suite**

Run: `./gradlew.bat :desktopApp:test`

Expected: PASS.

- [ ] **Step 3: Compile production sources**

Run: `./gradlew.bat :desktopApp:compileKotlin`

Expected: BUILD SUCCESSFUL with no errors from the changed files.

- [ ] **Step 4: Review the diff without committing**

Inspect the final diff and verify every changed line maps to terminal collapse, tool timing/stacking, or interaction card redesign. Do not stage or commit without separate user authorization.
