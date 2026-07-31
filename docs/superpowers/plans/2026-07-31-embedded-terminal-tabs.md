# 内嵌终端聚焦与多标签页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让右侧终端图标只打开或聚焦终端，并提供可持续运行的多 PowerShell 标签页。

**Architecture:** 使用独立、纯 Kotlin 的 `TerminalTabsState` 表达标签列表与活动标签，确保状态转换可直接单元测试。Compose 层在 `ChatScreen` 持有状态和终端会话库；会话库持有每个 `JediTermWidget` 与 PowerShell 生命周期，标签页切换只改变可见内容，不终止后台进程。

**Tech Stack:** Kotlin、Compose Multiplatform Desktop、SwingPanel、JediTerm、pty4j、kotlin.test/JUnit 5。

## Global Constraints

- 只修改 `desktopApp` 内与内嵌终端相关的代码，以及对应测试和本计划。
- 每个新增或修改的生产类、对象、数据类和函数都写简短 KDoc。
- 不启动桌面应用或开发服务器；使用 IDEA 检查和 Gradle 的窄范围测试/编译验证。
- 不暂存、不提交；项目规则要求用户另行明确授权。
- PowerShell 标签页创建时绑定当前工作区路径，切换任务后不修改已创建会话的工作目录。

---

## File Structure

- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/component/TerminalTabsState.kt` — 终端标签页值对象与纯状态转换。
- Create: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalTabsStateTest.kt` — 标签新增、选择、关闭与焦点意图的单元测试。
- Create: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalSessionStoreTest.kt` — 终端进程句柄的保存、释放与聚焦委派测试。
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt` — 把单一终端的创建/关闭职责改为可复用且持久的会话库，并渲染标签栏。
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt` — 将标签页状态与会话库接入底部分割区。
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt` — 终端图标改为“首次新建，否则聚焦”，并向工作区提供标签状态。
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt` — 保留现有展示测试并补充图标状态/焦点规则的回归断言。

### Task 1: 建立可测试的终端标签页状态

**Files:**
- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/component/TerminalTabsState.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalTabsStateTest.kt`

**Interfaces:**
- Produces: `internal data class TerminalTab(val id: Long, val workspacePath: String, val title: String)`。
- Produces: `internal data class TerminalTabsState(val tabs: List<TerminalTab> = emptyList(), val activeTabId: Long? = null, val nextTabId: Long = 1)`。
- Produces: `internal fun TerminalTabsState.addTab(workspacePath: String): TerminalTabsState`、`selectTab(tabId: Long): TerminalTabsState`、`closeTab(tabId: Long): TerminalTabsState`、`hasActiveTab(): Boolean` 与 `shouldRequestTerminalFocus(isTerminalFocused: Boolean): Boolean`。
- Produces: `internal enum class TerminalIconAction { CREATE_TAB, FOCUS_ACTIVE_TAB }` 与 `internal fun terminalIconAction(hasActiveTab: Boolean): TerminalIconAction`。

- [ ] **Step 1: 写入失败测试，定义标签生命周期和焦点规则**

```kotlin
class TerminalTabsStateTest {
    @Test
    fun `should create and select sequential terminal tabs`() {
        val first = TerminalTabsState().addTab("C:/workspace")
        val second = first.addTab("D:/other")

        assertEquals(listOf("终端 1", "终端 2"), second.tabs.map(TerminalTab::title))
        assertEquals(2L, second.activeTabId)
        assertEquals("D:/other", second.tabs.last().workspacePath)
    }

    @Test
    fun `should select adjacent tab and hide panel after closing last tab`() {
        val tabs = TerminalTabsState().addTab("C:/workspace").addTab("C:/workspace")

        val remaining = tabs.closeTab(2L)
        val empty = remaining.closeTab(1L)

        assertEquals(1L, remaining.activeTabId)
        assertEquals(emptyList(), empty.tabs)
        assertEquals(null, empty.activeTabId)
    }

    @Test
    fun `should request focus only when terminal does not own it`() {
        assertEquals(true, shouldRequestTerminalFocus(isTerminalFocused = false))
        assertEquals(false, shouldRequestTerminalFocus(isTerminalFocused = true))
    }

    @Test
    fun `should focus instead of closing or creating a tab for repeated terminal icon clicks`() {
        assertEquals(TerminalIconAction.CREATE_TAB, terminalIconAction(hasActiveTab = false))
        assertEquals(TerminalIconAction.FOCUS_ACTIVE_TAB, terminalIconAction(hasActiveTab = true))
    }
}
```

- [ ] **Step 2: 运行测试，确认因生产 API 尚不存在而失败**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.TerminalTabsStateTest"`

Expected: FAIL，错误指向缺失的 `TerminalTabsState`、`TerminalTab` 或状态转换函数。

- [ ] **Step 3: 以最小纯状态实现使测试通过**

```kotlin
/** Represents one persistent embedded terminal tab. */
internal data class TerminalTab(
    val id: Long,
    val workspacePath: String,
    val title: String,
)

/** Holds tabs and the currently selected terminal tab. */
internal data class TerminalTabsState(
    val tabs: List<TerminalTab> = emptyList(),
    val activeTabId: Long? = null,
    val nextTabId: Long = 1,
)

/** Adds and selects a terminal bound to [workspacePath]. */
internal fun TerminalTabsState.addTab(workspacePath: String): TerminalTabsState {
    val tab = TerminalTab(nextTabId, workspacePath, "终端 $nextTabId")
    return copy(tabs = tabs + tab, activeTabId = tab.id, nextTabId = nextTabId + 1)
}
```

实现 `selectTab` 时只接受现有 ID；实现 `closeTab` 时优先选择被关闭项左侧的标签、无左侧时选择右侧，最后一个被关闭时将 `activeTabId` 置空。`shouldRequestTerminalFocus` 仅返回 `!isTerminalFocused`。`terminalIconAction` 在已有活动标签时返回 `FOCUS_ACTIVE_TAB`，否则返回 `CREATE_TAB`。

- [ ] **Step 4: 运行同一测试，确认通过**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.TerminalTabsStateTest"`

Expected: PASS。

- [ ] **Step 5: 使用 IDEA 对两个新增文件运行问题检查**

Run: `get_file_problems` for `TerminalTabsState.kt` and `TerminalTabsStateTest.kt`.

Expected: 没有新错误；修复任何由新增代码引入的问题后再继续。

### Task 2: 持久化终端会话并提供标签页 UI

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalSessionStoreTest.kt`

**Interfaces:**
- Consumes: `TerminalTabsState` 和 `TerminalTab`。
- Produces: `internal interface TerminalHandle`，提供 `component`、`errorMessage`、`start()`、`close()` 和 `focusIfNeeded()`，隔离 PowerShell/Swing 外部边界。
- Produces: `internal class TerminalSessionStore`，提供 `create(tab: TerminalTab)`、`session(tabId: Long)`、`close(tabId: Long)`、`closeAll()` 以及 `focusActiveIfNeeded(activeTabId: Long?)`。
- Produces: `EmbeddedTerminalPanel(tabs: TerminalTabsState, sessions: TerminalSessionStore, onSelectTab: (Long) -> Unit, onAddTab: () -> Unit, onCloseTab: (Long) -> Unit, modifier: Modifier = Modifier)`。

- [ ] **Step 1: 为会话保存、释放和焦点委派写失败测试**

创建 `TerminalSessionStoreTest.kt`。使用只实现 `TerminalHandle` 的测试替身，替代 PowerShell 进程创建这一外部边界：

```kotlin
private class FakeTerminalHandle : TerminalHandle {
    var startCalls = 0
    var closeCalls = 0
    var focusCalls = 0

    override val component: Component? = null
    override val errorMessage: String? = null

    override fun start() { startCalls += 1 }
    override fun close() { closeCalls += 1 }
    override fun focusIfNeeded() { focusCalls += 1 }
}

@Test
fun `should close only the terminal session whose tab was closed`() {
    val first = FakeTerminalHandle()
    val second = FakeTerminalHandle()
    val store = TerminalSessionStore { path -> if (path == "C:/one") first else second }

    store.create(TerminalTab(1, "C:/one", "终端 1"))
    store.create(TerminalTab(2, "C:/two", "终端 2"))
    store.close(1)

    assertEquals(1, first.closeCalls)
    assertEquals(0, second.closeCalls)
    assertSame(second, store.session(2))
}

@Test
fun `should delegate focus to the active terminal session`() {
    val handle = FakeTerminalHandle()
    val store = TerminalSessionStore { handle }
    store.create(TerminalTab(1, "C:/workspace", "终端 1"))

    store.focusActiveIfNeeded(1)

    assertEquals(1, handle.focusCalls)
}

@Test
fun `should close each remaining terminal exactly once when store is disposed`() {
    val first = FakeTerminalHandle()
    val second = FakeTerminalHandle()
    val store = TerminalSessionStore { path -> if (path == "C:/one") first else second }
    store.create(TerminalTab(1, "C:/one", "终端 1"))
    store.create(TerminalTab(2, "C:/two", "终端 2"))

    store.closeAll()
    store.closeAll()

    assertEquals(1, first.closeCalls)
    assertEquals(1, second.closeCalls)
}
```

测试文件导入 `java.awt.Component`、`kotlin.test.Test`、`assertEquals` 与 `assertSame`。`FakeTerminalHandle` 只位于测试文件，生产代码不能添加仅供测试读取的状态。

- [ ] **Step 2: 运行受影响测试，确认新增会话库和标签面板 API 尚未实现**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.TerminalSessionStoreTest"`

Expected: FAIL，错误指向未定义的 `TerminalHandle` 或 `TerminalSessionStore`；不得因测试语法或依赖配置失败。

- [ ] **Step 3: 实现持久会话库和标签栏，不改变现有终端样式**

在 `EmbeddedTerminalPanel.kt` 中：

```kotlin
/** Represents the terminal operations required by the Compose panel. */
internal interface TerminalHandle {
    /** Exposes the component displayed by SwingPanel, when creation succeeded. */
    val component: Component?

    /** Exposes the creation error to render in the matching terminal tab. */
    val errorMessage: String?

    /** Starts the underlying terminal process. */
    fun start()

    /** Releases the underlying terminal process. */
    fun close()

    /** Restores keyboard focus only when this terminal does not already own it. */
    fun focusIfNeeded()
}

/** Owns terminal handles so inactive tabs keep their processes alive. */
internal class TerminalSessionStore(
    private val terminalFactory: (String) -> TerminalHandle = ::createPowerShellHandle,
) {
    private val sessions = linkedMapOf<Long, TerminalHandle>()

    /** Creates and starts one session for [tab]. */
    fun create(tab: TerminalTab) = sessions.getOrPut(tab.id) {
        terminalFactory(tab.workspacePath)
    }.start()

    /** Returns the retained handle for [tabId], if one exists. */
    fun session(tabId: Long): TerminalHandle? = sessions[tabId]

    /** Closes and removes one terminal session. */
    fun close(tabId: Long) { sessions.remove(tabId)?.close() }

    /** Closes every remaining session exactly once. */
    fun closeAll() { sessions.values.toList().forEach(TerminalHandle::close); sessions.clear() }

    /** Delegates focus restoration to the selected session. */
    fun focusActiveIfNeeded(activeTabId: Long?) { activeTabId?.let(sessions::get)?.focusIfNeeded() }
}
```

实现私有 `JediTermTerminalHandle`：构造时以 `runCatching { createPowerShellTerminal(workspacePath) }` 保存结果；`start()` 仅启动成功组件；`close()` 仅关闭成功组件；`component` 和 `errorMessage` 从该结果导出。将旧的 `remember(workspacePath)` 和组件内 `DisposableEffect(terminal)` 移出 `EmbeddedTerminalPanel`。面板只显示活动会话的 `SwingPanel`，从会话库取得同一个组件；标签切换不会调用 `close`。标题栏应渲染标签、可访问名称为“新建终端”的“+”按钮，以及每个标签的关闭按钮；保留原关闭按钮的 36dp 点击区规则用于标签关闭。创建失败时仍显示对应异常消息。

使用 `KeyboardFocusManager.currentKeyboardFocusManager.focusOwner` 与 `SwingUtilities.isDescendingFrom` 判断焦点归属，并且仅在 `shouldRequestTerminalFocus` 返回 true 时调用 `requestFocusInWindow()`。

- [ ] **Step 4: 运行受影响测试，确认通过**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.TerminalSessionStoreTest"`

Expected: PASS，且会话关闭不会影响未关闭的标签页句柄。

- [ ] **Step 5: 用 IDEA 检查修改后的源文件**

Run: `get_file_problems` for `EmbeddedTerminalPanel.kt`.

Expected: 没有新错误；若出现未使用导入或可见性错误，立即在该文件内修复并重复检查。

### Task 3: 将终端图标、状态和会话库接入工作区

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Consumes: `TerminalTabsState.addTab`、`closeTab`、`terminalIconAction`、`TerminalSessionStore` 和新的 `EmbeddedTerminalPanel` 参数。
- Produces: 图标点击事件：无标签时创建第一个标签；有标签时只调用 `focusActiveIfNeeded`。

- [ ] **Step 1: 运行任务 1 与任务 2 的测试，建立接线前基线**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.TerminalTabsStateTest" --tests "com.agent.app.chat.component.TerminalSessionStoreTest"`

Expected: PASS。Task 1 已通过失败测试固定“重复点击只聚焦”的纯状态契约；本任务只将已验证的状态和外部会话边界接入 Compose。

- [ ] **Step 2: 以最小接线替换布尔状态**

在 `ChatScreen` 中以 `remember { TerminalTabsState() }` 和 `remember { TerminalSessionStore() }` 替换 `terminalVisible`；用 `DisposableEffect` 在窗口组合离开时调用 `closeAll()`。右侧图标的核心分支必须是：

```kotlin
if (activeConversation == null) {
    railFeedback = "请先选择工作区"
} else {
    when (terminalIconAction(terminalTabs.hasActiveTab())) {
        TerminalIconAction.FOCUS_ACTIVE_TAB -> {
            terminalSessions.focusActiveIfNeeded(terminalTabs.activeTabId)
        }
        TerminalIconAction.CREATE_TAB -> {
            terminalTabs = terminalTabs.addTab(activeConversation.workspacePath)
            terminalSessions.create(terminalTabs.tabs.last())
        }
    }
}
```

将 `WorkspacePanel` 的 `terminalVisible`、`onCloseTerminal` 替换为 `terminalTabs`、`terminalSessions` 和选择/新增/关闭回调。`ResizableWorkspaceLayout.terminalVisible` 使用 `terminalTabs.hasActiveTab()`；“+”从当前活动会话创建新标签。关闭回调必须先让 `TerminalSessionStore.close(tabId)` 释放进程，再用 `closeTab(tabId)` 更新界面状态。关闭后如仍有活动标签，调用 `focusActiveIfNeeded`。

`resolveActiveRailGlyph` 保持现有布尔入参，但传入 `terminalTabs.hasActiveTab()`，确保有任一页时终端图标维持高亮。

- [ ] **Step 3: 运行展示测试和状态/会话测试，确认通过**

Run: `.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest" --tests "com.agent.app.chat.component.TerminalTabsStateTest" --tests "com.agent.app.chat.component.TerminalSessionStoreTest"`

Expected: PASS。

- [ ] **Step 4: 逐文件 IDEA 问题检查**

Run: `get_file_problems` for `ChatScreen.kt`, then `WorkspacePanel.kt`, then `ChatScreenPresentationTest.kt`.

Expected: 每个文件均无新错误后再编辑下一个文件；任何现存的无关问题仅记录，不修改。

### Task 4: 完成模块级验证

**Files:**
- Verify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/TerminalTabsState.kt`
- Verify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/EmbeddedTerminalPanel.kt`
- Verify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt`
- Verify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatScreen.kt`
- Verify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalTabsStateTest.kt`
- Verify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/TerminalSessionStoreTest.kt`
- Verify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

- [ ] **Step 1: 运行完整 desktopApp 测试集**

Run: `.\gradlew.bat :desktopApp:test`

Expected: PASS，且没有新增测试失败。

- [ ] **Step 2: 编译 desktopApp Kotlin 源码**

Run: `.\gradlew.bat :desktopApp:compileKotlin`

Expected: BUILD SUCCESSFUL；仅可保留项目已有的已知警告，不得引入新的编译错误。

- [ ] **Step 3: 对全部受影响文件运行 IDEA 检查并审阅 diff**

Run: `lint_files` for the four生产文件 and two测试文件, then `git diff --check` and `git diff -- <affected paths>` through the available project tooling.

Expected: 没有新增高严重度问题、空白错误或与终端功能无关的改动。

- [ ] **Step 4: 记录验证结果，不提交**

在交付说明中列出每条已执行命令、通过状态和任何不可消除的既有警告。保持改动未暂存、未提交，除非用户明确授权提交。
