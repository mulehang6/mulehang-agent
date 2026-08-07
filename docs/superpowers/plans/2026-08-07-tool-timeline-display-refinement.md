# 工具时间线展示收敛 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让工具时间线以清晰的英文摘要、语义图标和分层输入展示呈现调用状态，并统一正文行内代码的柔和高亮。

**Architecture:** 保持 `ToolEventItem` 与现有分组边界不变，仅在 `ConversationTimeline.kt` 的展示层决定单工具直出、组摘要和行内输入可见性。Markdown 普通渲染及包含扩展语法的渲染路径共享同一个 `SpanStyle`，确保反引号代码底色一致。

**Tech Stack:** Kotlin、Compose Multiplatform Desktop、Compose RichText、kotlin.test、JUnit 5。

## Global Constraints

- 只修改 `desktopApp` 的展示层与对应测试；不得修改 `shared` 的事件协议、会话持久化或 Markdown 解析语义。
- 保留失败工具的独立边界、输出面板和既有慢速展开/收起动画。
- 不启动 Desktop、Vite 或其他长期运行服务。
- 每个 Kotlin 文件修改后通过 IDEA `get_file_problems` 检查。
- 未获明确授权不得 Git 提交。

---

### Task 1: 工具分组、摘要和图标语义

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt:145-269, 300-347`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt:980-1050`

**Interfaces:**
- Produces: `buildToolGroupHeadline(count: Int): String` 返回 `Executed tools · N`。
- Produces: `groupTimelineItems(...)` 将单工具作为 `TimelineDisplayItem.Content`，仅两个或更多相邻可分组工具使用 `ToolGroup`。
- Consumes: `timelineToolPresentation(item)`，将 `glob` 和 `glob_files` 返回 `TimelineToolGlyph.SEARCH`。

- [ ] **Step 1: 写入失败测试，锁定英文摘要、单工具直出与 glob 映射。**

```kotlin
assertEquals("Executed tools · 2", buildToolGroupHeadline(2))
assertEquals(
    TimelineToolGlyph.SEARCH,
    timelineToolGlyph(toolEvent("glob_files", ToolEventStatus.Started)),
)
assertTrue(groupTimelineItems(listOf(toolEvent("list_dir", ToolEventStatus.Finished))).single() is TimelineDisplayItem.Content)
assertTrue(groupTimelineItems(listOf(
    toolEvent("list_dir", ToolEventStatus.Finished),
    toolEvent("glob_files", ToolEventStatus.Finished),
)).single() is TimelineDisplayItem.ToolGroup)
```

- [ ] **Step 2: 运行展示测试，确认新增断言失败。**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: FAIL，摘要仍为中文、`glob_files` 为 `GENERIC` 或单工具仍被包进 `ToolGroup`。

- [ ] **Step 3: 实现最小展示规则。**

```kotlin
internal fun buildToolGroupHeadline(count: Int): String = "Executed tools · $count"

private fun flushTools() {
    when (pendingTools.size) {
        0 -> Unit
        1 -> result += TimelineDisplayItem.Content(pendingTools.single())
        else -> result += TimelineDisplayItem.ToolGroup(pendingTools.toList())
    }
    pendingTools.clear()
}
```

在 `timelineToolPresentation` 的搜索分支中加入 `toolName.contains("glob")`，使文件匹配工具使用已有放大镜扫描图标。

- [ ] **Step 4: 检查并运行展示测试。**

通过 IDEA 执行：`get_file_problems --filePath desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`。

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: PASS。

### Task 2: 工具行的输入信息层级

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt:773-856`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt:980-1050`

**Interfaces:**
- Produces: `timelineToolRowHeadline(item: ToolEventItem): String`，终端返回命令，其余工具返回工具名。
- Produces: `timelineToolExpandedInput(item: ToolEventItem): String?`，仅非终端工具返回输入参数。
- Consumes: `buildToolEventInlineInput(item)` 与 `isTerminalToolEvent(item)`。

- [ ] **Step 1: 写入失败测试，锁定收起和展开文本。**

```kotlin
val terminal = toolEvent("run_powershell", ToolEventStatus.Started, preview = "Get-ChildItem")
val directory = toolEvent("list_dir", ToolEventStatus.Started, preview = "{\"path\":\".\"}")
assertEquals("Get-ChildItem", timelineToolRowHeadline(terminal))
assertEquals("list_dir", timelineToolRowHeadline(directory))
assertEquals("{\"path\":\".\"}", timelineToolExpandedInput(directory))
assertEquals(null, timelineToolExpandedInput(terminal))
```

- [ ] **Step 2: 运行展示测试，确认新纯展示接口尚不存在。**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: FAIL，未解析 `timelineToolRowHeadline` 与 `timelineToolExpandedInput`。

- [ ] **Step 3: 实现行内与展开内容的拆分。**

```kotlin
internal fun timelineToolRowHeadline(item: ToolEventItem): String =
    if (isTerminalToolEvent(item)) buildToolEventInlineInput(item).orEmpty() else item.toolName

internal fun timelineToolExpandedInput(item: ToolEventItem): String? =
    buildToolEventInlineInput(item)?.takeUnless { isTerminalToolEvent(item) }
```

让 `TimelineToolTextRow` 用 `timelineToolRowHeadline` 渲染收起行，并在其现有 `AnimatedVisibility` 的展开 Column 顶部显示 `timelineToolExpandedInput`。参数使用等宽字体与 `AppMuted`，输出与错误面板维持原有布局。

- [ ] **Step 4: 检查并运行展示测试。**

通过 IDEA 执行：`get_file_problems --filePath desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`。

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: PASS。

### Task 3: 统一行内代码底色

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt:470-510`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownInlineExtensions.kt:20-90`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/AssistantMarkdownInlineExtensionsTest.kt:12-35`

**Interfaces:**
- Produces: 单一 `AssistantMarkdownInlineCodeStyle`，供 RichText 和扩展 Markdown 文本使用。
- Consumes: 现有 `AssistantMarkdownInlineCodeBackground = Color(0xFF343A42)`。

- [ ] **Step 1: 写入失败测试，锁定统一的行内代码背景。**

```kotlin
assertEquals(
    AssistantMarkdownInlineCodeBackground,
    assistantMarkdownStringStyle().codeStyle?.background,
)
assertEquals(
    AssistantMarkdownInlineCodeBackground,
    assistantMarkdownInlineCodeStyle().background,
)
```

- [ ] **Step 2: 运行 Markdown 测试，确认扩展路径尚未使用共用样式。**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.AssistantMarkdownInlineExtensionsTest"`

Expected: FAIL，未解析 `assistantMarkdownInlineCodeStyle` 或扩展路径仍未提供代码样式。

- [ ] **Step 3: 抽取并复用代码 SpanStyle。**

```kotlin
internal fun assistantMarkdownInlineCodeStyle(): SpanStyle = SpanStyle(
    fontFamily = FontFamily.Monospace,
    background = AssistantMarkdownInlineCodeBackground,
)
```

将 `assistantMarkdownStringStyle()` 的 `codeStyle` 改为调用该函数；在扩展 Markdown 渲染器遇到反引号代码时应用同一 `SpanStyle`，不改变现有下划线、上下标和 `==高亮==` 的处理。

- [ ] **Step 4: 检查并运行 Markdown 测试。**

通过 IDEA 分别执行：

```text
get_file_problems --filePath desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt
get_file_problems --filePath desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownInlineExtensions.kt
```

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.AssistantMarkdownInlineExtensionsTest"`

Expected: PASS。

### Task 4: 集成验证

**Files:**
- Modify: 仅限前三项所需的修复。

- [ ] **Step 1: 运行受影响测试。**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest" --tests "com.agent.app.chat.component.AssistantMarkdownInlineExtensionsTest"`

Expected: PASS。

- [ ] **Step 2: 编译桌面端。**

Run: `.\gradlew.bat :desktopApp:compileKotlin`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 检查变更范围和格式。**

通过 IDEA 对前述 Kotlin 文件执行 `lint_files`；再运行：

```powershell
git diff --check -- desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt desktopApp/src/main/kotlin/com/agent/app/chat/component/AssistantMarkdownInlineExtensions.kt desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt desktopApp/src/test/kotlin/com/agent/app/chat/component/AssistantMarkdownInlineExtensionsTest.kt
```

Expected: 无新增 error；`git diff --check` 退出码为 0。

- [ ] **Step 4: 不执行 Git 提交。**

报告改动与验证结果，保持当前 `codex/ui-improve` 分支的未提交状态。
