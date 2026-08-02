# 工具调用分组展示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将连续成功工具调用合并为默认收起的文本式可展开组，并使失败调用独立显示。

**Architecture:** 仅在 `ConversationTimeline` 的渲染前创建展示段，不改变 `ConversationItem`、Agent reducer 或 SQLite 数据。新的分组纯函数负责边界规则，Compose 组件负责标题、工具行和两级展开动画。

**Tech Stack:** Kotlin、Compose Multiplatform、kotlin.test。

## Global Constraints

- 只改渲染层，不改事件产生、history 或数据库格式。
- 成功完成的相邻工具事件可分组；消息、思考、状态事件和失败事件均打断分组。
- 失败工具调用始终独立显示。
- 展开高度 180ms、透明度 150ms、箭头旋转 160ms；不添加循环或位移动画。
- 不提交 Git 提交，除非用户明确授权。

---

### Task 1: 建立展示段分组规则与测试

**Files:**

- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt:105-143`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ConversationTimelineTest.kt`

**Interfaces:**

- Produces `TimelineDisplayItem` with `Content(item)`, `SuccessfulToolGroup(items)` and `FailedTool(item)` variants.
- Produces `groupTimelineItems(items: List<ConversationItem>): List<TimelineDisplayItem>`.

- [ ] **Step 1: 写失败测试。**

```kotlin
@Test
fun `should group adjacent finished tools and keep failure separate`() {
    val displayItems = groupTimelineItems(listOf(finishedTool("a"), finishedTool("b"), failedTool("c"), finishedTool("d")))

    assertEquals(listOf(2, 1, 1), displayItems.map(TimelineDisplayItem::size))
    assertTrue(displayItems[1] is TimelineDisplayItem.FailedTool)
}
```

另加消息、思考块与 `ToolEventStatus.Status` 打断分组的测试。

- [ ] **Step 2: 运行测试，确认缺少分组 API 导致失败。**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ConversationTimelineTest"`

Expected: FAIL，提示 `groupTimelineItems` 或 `TimelineDisplayItem` 未解析。

- [ ] **Step 3: 实现纯展示段函数。**

```kotlin
internal fun groupTimelineItems(items: List<ConversationItem>): List<TimelineDisplayItem> {
    val result = mutableListOf<TimelineDisplayItem>()
    val pendingTools = mutableListOf<ToolEventItem>()
    fun flushTools() { if (pendingTools.isNotEmpty()) result += TimelineDisplayItem.SuccessfulToolGroup(pendingTools.toList()).also { pendingTools.clear() } }
    items.forEach { item ->
        if (item is ToolEventItem && item.status == ToolEventStatus.Finished) pendingTools += item
        else { flushTools(); result += if (item is ToolEventItem && item.status == ToolEventStatus.Failed) TimelineDisplayItem.FailedTool(item) else TimelineDisplayItem.Content(item) }
    }
    flushTools()
    return result
}
```

- [ ] **Step 4: 运行测试，确认通过。**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ConversationTimelineTest"`

Expected: PASS。

### Task 2: 替换工具卡片为文本式分组组件

**Files:**

- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt:123-627`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ConversationTimelineTest.kt`

**Interfaces:**

- Consumes `TimelineDisplayItem.SuccessfulToolGroup` and `TimelineDisplayItem.FailedTool`.
- Produces `TimelineToolGroup` and `TimelineToolTextRow` composables.

- [ ] **Step 1: 写标题与默认状态测试。**

```kotlin
@Test
fun `should use a collapsed executed tools label`() {
    assertEquals("已执行工具 · 3", buildToolGroupHeadline(3))
    assertFalse(isToolGroupExpandedByDefault())
}
```

- [ ] **Step 2: 实现轻量可互动文本组件。**

组标题和工具行使用全宽点击区域、`AppHoverBackground` 悬浮反馈和 `toolEventChevronRotation`。组标题默认收起；`AnimatedVisibility` 使用 `expandVertically(tween(180)) + fadeIn(tween(150))` 与对应收起动画。工具行展开时复用现有输出分块、滚动条和错误内容，但移除 `Surface`、边框和卡片填充。

- [ ] **Step 3: 用展示段替换逐项 `TimelineToolEvent` 调用。**

`ConversationTimeline` 遍历 `groupTimelineItems(conversation.items)`：普通项走已有消息/思考渲染，成功组走 `TimelineToolGroup`，失败项走独立文本工具行。

- [ ] **Step 4: 运行组件测试与 Kotlin 编译。**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ConversationTimelineTest" :desktopApp:compileKotlin`

Expected: PASS，且不影响 Agent reducer、持久化或历史恢复测试。

### Task 3: 回归验证

**Files:**

- No additional files.

- [ ] **Step 1: 运行受影响模块的完整测试。**

Run: `./gradlew.bat :desktopApp:test :desktopApp:compileKotlin`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 检查工作树。**

Run: `git diff --check; git status --short`

Expected: 没有空白错误，且改动仅限需求相关文件与规格/计划文档。
