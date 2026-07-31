# 内嵌终端一体式顶部栏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将内嵌终端标签栏、关闭操作、创建入口和右键菜单整合为一个与终端内容同背景的顶部栏。

**Architecture:** 在 `TerminalTabsState` 中增加“保留指定标签页”的纯转换，在 `TerminalSessionStore` 中增加对应的批量关闭入口。`ChatScreen` 协调状态与进程释放；`EmbeddedTerminalPanel` 使用现有 `TaskSidebar` 的右键菜单定位方式渲染标签菜单，且仅负责调用回调。

**Tech Stack:** Kotlin、Compose Multiplatform Desktop、Material 3、JediTerm、kotlin.test/JUnit 5。

## Global Constraints

- 顶部栏和终端内容均使用 `Color(0xFF17181A)`，只以低对比一像素分隔线划分区域。
- 标签内必须提供“×”；“+”位于标签列表之后；删除独立的右侧关闭按钮。
- 标签右键菜单固定为：新建终端、关闭当前终端、关闭其他终端。
- 关闭其他终端只保留右击标签的 PowerShell 会话，并使其成为活动标签。
- 每个新增或修改的生产类、对象、数据类和函数都写简短 KDoc。
- 不启动桌面应用或开发服务器；不暂存、不提交。

---

## File Structure

- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/TerminalTabsState.kt` — 增加保留单标签的状态转换。
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt` — 一体式顶部栏、标签关闭按钮和右键菜单。
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt` — 处理关闭其他终端的状态和会话释放。
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalTabsStateTest.kt` — 覆盖保留标签后的活动状态。
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalSessionStoreTest.kt` — 覆盖关闭其他会话时保留目标句柄。

### Task 1: 定义“关闭其他终端”的状态与会话契约

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/TerminalTabsState.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalTabsStateTest.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalSessionStoreTest.kt`

**Interfaces:**
- Produces: `internal fun TerminalTabsState.retainOnly(tabId: Long): TerminalTabsState`。
- Produces: `fun TerminalSessionStore.closeAllExcept(keptTabId: Long)`。

- [ ] **Step 1: 先写失败测试**

```kotlin
@Test
fun `should retain only requested terminal tab and select it`() {
    val tabs = TerminalTabsState()
        .addTab("C:/one")
        .addTab("C:/two")
        .addTab("C:/three")

    val retained = tabs.retainOnly(2)

    assertEquals(listOf(2L), retained.tabs.map(TerminalTab::id))
    assertEquals(2L, retained.activeTabId)
}

@Test
fun `should close every terminal session except retained tab`() {
    val first = FakeTerminalHandle()
    val second = FakeTerminalHandle()
    val store = TerminalSessionStore { path -> if (path == "C:/one") first else second }
    store.create(TerminalTab(1, "C:/one", "终端 1"))
    store.create(TerminalTab(2, "C:/two", "终端 2"))

    store.closeAllExcept(2)

    assertEquals(1, first.closeCalls)
    assertEquals(0, second.closeCalls)
    assertSame(second, store.session(2))
}
```

- [ ] **Step 2: 运行失败测试**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.TerminalTabsStateTest" --tests "com.agent.app.chat.component.TerminalSessionStoreTest"`

Expected: FAIL，且仅报缺失的 `retainOnly` 或 `closeAllExcept`。

- [ ] **Step 3: 写入最小实现**

```kotlin
/** Retains [tabId] as the only active terminal tab when it exists. */
internal fun TerminalTabsState.retainOnly(tabId: Long): TerminalTabsState {
    val retainedTab = tabs.firstOrNull { it.id == tabId } ?: return this
    return copy(tabs = listOf(retainedTab), activeTabId = retainedTab.id)
}

/** Closes every session except [keptTabId]. */
fun TerminalSessionStore.closeAllExcept(keptTabId: Long) {
    sessions.keys.filter { it != keptTabId }.forEach(::close)
}
```

使 `closeAllExcept` 成为 `TerminalSessionStore` 的成员函数，以便访问其私有会话映射；不得向生产类添加仅供测试读取的属性。

- [ ] **Step 4: 运行同一测试确认通过**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.TerminalTabsStateTest" --tests "com.agent.app.chat.component.TerminalSessionStoreTest"`

Expected: PASS。

- [ ] **Step 5: IDEA 问题检查**

Run: `get_file_problems` for the two modified production files and both modified tests.

Expected: 没有新增错误。

### Task 2: 实现一体式标签栏与右键菜单

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`

**Interfaces:**
- Consumes: `onSelectTab`、`onAddTab`、`onCloseActiveTab`。
- Produces: `EmbeddedTerminalPanel` 新增 `onCloseTab: (Long) -> Unit` 与 `onCloseOtherTabs: (Long) -> Unit` 参数。

- [ ] **Step 1: 以现有右键菜单模式写失败的展示规则测试**

在 `ChatScreenPresentationTest.kt` 添加以下纯规则测试，并在 `EmbeddedTerminalPanel.kt` 定义对应函数：

```kotlin
@Test
fun `should keep terminal tab actions attached to selected tab`() {
    assertEquals(
        listOf("新建终端", "关闭终端", "关闭其他终端"),
        terminalTabContextMenuLabels(),
    )
}
```

- [ ] **Step 2: 运行展示测试确认失败**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: FAIL，错误指向未定义的 `terminalTabContextMenuLabels`。

- [ ] **Step 3: 替换独立标题栏**

在 `EmbeddedTerminalPanel` 中完成以下布局：

```kotlin
Column(modifier = modifier.background(Color(0xFF17181A))) {
    TerminalTabStrip(
        tabs = tabs,
        onSelectTab = onSelectTab,
        onAddTab = onAddTab,
        onCloseTab = onCloseTab,
        onCloseOtherTabs = onCloseOtherTabs,
    )
    Box(Modifier.fillMaxWidth().height(1.dp).background(AppLine.copy(alpha = 0.28f)))
    TerminalContent(...)
}
```

`TerminalTabStrip` 的活动标签使用 6dp 圆角、低对比背景和细边界；每个标签内渲染标题与 24dp“×”点击区。`+`使用 32dp点击区并紧贴可横向滚动的标签组。右键定位采用 `TaskListItem` 的 `PointerEventType.Press`、`isSecondaryPressed`、`DpOffset` 与 `Popup` 模式，菜单项由 `terminalTabContextMenuLabels()` 提供。菜单的三个回调依次调用新建、关闭右击页和关闭其他页；右键不应改变活动标签。

删除当前顶部栏外层圆角、浅色 `AppPanelBackground` 和最右独立关闭按钮。终端内容区继续保留 `SwingPanel`、错误文本和当前背景同步逻辑。

- [ ] **Step 4: 在 ChatScreen 接线关闭其他操作**

```kotlin
onCloseOtherTerminalTabs = { keptTabId ->
    terminalSessions.closeAllExcept(keptTabId)
    terminalTabs = terminalTabs.retainOnly(keptTabId)
}
```

新增单页关闭回调时按传入 `tabId` 关闭会话和更新 `terminalTabs.closeTab(tabId)`，使标签内“×”可关闭非活动页。

- [ ] **Step 5: 运行展示与状态测试**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest" --tests "com.agent.app.chat.component.TerminalTabsStateTest" --tests "com.agent.app.chat.component.TerminalSessionStoreTest"`

Expected: PASS。

- [ ] **Step 6: 对每个改动源文件运行 IDEA 问题检查**

Run: `get_file_problems` after each patch for `EmbeddedTerminalPanel.kt` and `ChatScreen.kt`.

Expected: 没有新增错误或未使用导入。

### Task 3: 模块验证

**Files:**
- Verify: `TerminalTabsState.kt`, `EmbeddedTerminalPanel.kt`, `ChatScreen.kt` and the three affected test files.

- [ ] **Step 1: 运行桌面模块测试与编译**

Run: `.\gradlew.bat :desktopApp:test` and `.\gradlew.bat :desktopApp:compileKotlin`

Expected: 两条命令均为 `BUILD SUCCESSFUL`。

- [ ] **Step 2: 静态检查和 diff 检查**

Run: `lint_files` for the affected files, then `git diff --check`.

Expected: 无新增静态问题和空白错误；改动仅限终端状态、会话、顶部栏、测试和本文档。

- [ ] **Step 3: 记录验证结果，不提交**

在交付说明中报告测试、编译和静态检查结果，保持改动未暂存、未提交。
