package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.agent.app.design.PANEL_TAB_ICON_SIZE
import com.agent.app.design.TERMINAL_ICON_KEY

/**
 * 验证内嵌终端标签页的状态转换与终端图标意图。
 */
class TerminalTabsStateTest {

    /**
     * 新建标签页时应分配单调递增会话 ID，并按当前可见顺序生成连续标题。
     */
    @Test
    fun `should create and select sequential terminal tabs`() {
        val first = TerminalTabsState().addTab("C:/workspace")
        val second = first.addTab("D:/other")

        assertEquals(listOf("终端", "终端 2"), List(second.tabs.size, ::terminalTabLabel))
        assertEquals(listOf(1L, 2L), second.tabs.map(TerminalTab::id))
        assertEquals(2L, second.activeTabId)
        assertEquals("D:/other", second.tabs.last().workspacePath)
    }

    /**
     * 关闭当前页应选择相邻页；关闭最后一页后不保留活动标签。
     */
    @Test
    fun `should select adjacent tab and hide panel after closing last tab`() {
        val tabs = TerminalTabsState().addTab("C:/workspace").addTab("C:/workspace")

        val remaining = tabs.closeTab(2L)
        val empty = remaining.closeTab(1L)

        assertEquals(1L, remaining.activeTabId)
        assertEquals(emptyList(), empty.tabs)
        assertEquals(null, empty.activeTabId)
    }

    /**
     * 关闭其他终端时，右击的标签页应成为唯一保留且选中的终端页。
     */
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

    /**
     * 关闭中间标签后，剩余标签的展示编号应连续，但内部会话 ID 不能复用。
     */
    @Test
    fun `should keep visible labels contiguous after closing a middle terminal tab`() {
        val closedMiddle = TerminalTabsState()
            .addTab("C:/one")
            .addTab("C:/two")
            .addTab("C:/three")
            .closeTab(2L)

        assertEquals(listOf(1L, 3L), closedMiddle.tabs.map(TerminalTab::id))
        assertEquals(listOf("终端", "终端 2"), List(closedMiddle.tabs.size, ::terminalTabLabel))
        assertEquals(4L, closedMiddle.nextTabId)
    }

    /**
     * 只剩首个终端后再新建时，显示名必须为“终端 2”，即使内部 ID 已单调增长到第六个。
     */
    @Test
    fun `should renumber visible labels after closing several terminal tabs`() {
        val firstFive = generateSequence(TerminalTabsState()) { tabs -> tabs.addTab("C:/workspace") }
            .drop(1)
            .take(5)
            .last()
        val onlyFirst = listOf(2L, 3L, 4L, 5L).fold(firstFive) { tabs, tabId -> tabs.closeTab(tabId) }
        val reopened = onlyFirst.addTab("C:/workspace")

        assertEquals(listOf(1L, 6L), reopened.tabs.map(TerminalTab::id))
        assertEquals(listOf("终端", "终端 2"), List(reopened.tabs.size, ::terminalTabLabel))
        assertEquals(7L, reopened.nextTabId)
    }

    /**
     * 焦点已经位于终端时不应重复发送 Swing 聚焦请求。
     */
    @Test
    fun `should request focus only when terminal does not own it`() {
        assertTrue(shouldRequestTerminalFocus(isTerminalFocused = false))
        assertFalse(shouldRequestTerminalFocus(isTerminalFocused = true))
    }

    /** 右侧终端按钮应纯粹切换面板可见性，不能关闭已有会话。 */
    @Test
    fun `should toggle terminal panel without closing its active tab`() {
        assertEquals(
            TerminalRailAction.CREATE_AND_SHOW,
            terminalRailAction(panelVisible = false, hasActiveTab = false),
        )
        assertEquals(
            TerminalRailAction.SHOW,
            terminalRailAction(panelVisible = false, hasActiveTab = true),
        )
        assertEquals(
            TerminalRailAction.HIDE,
            terminalRailAction(panelVisible = true, hasActiveTab = true),
        )
    }

    /** 终端标题栏关闭图标必须明确表达仅收起面板的语义。 */
    @Test
    fun `should label terminal header close action as hide`() {
        assertEquals("收起终端", terminalPanelHideActionLabel())
    }

    /** 关闭最后一个标签前应先播放面板退出动画；多标签关闭无需等待。 */
    @Test
    fun `should defer closing only the final terminal tab`() {
        assertTrue(shouldDeferTerminalTabClose(TerminalTabsState().addTab("C:/workspace")))
        assertFalse(
            shouldDeferTerminalTabClose(
                TerminalTabsState().addTab("C:/workspace").addTab("C:/workspace"),
            ),
        )
    }

    /** 关闭最后一个标签后应重置可见状态，但不能复用已关闭会话的内部 ID。 */
    @Test
    fun `should reset visible label without reusing terminal session id`() {
        val closed = TerminalTabsState().addTab("C:/workspace").resetAfterTerminalWindowClosed()
        val reopened = closed.addTab("C:/workspace")

        assertEquals(emptyList(), closed.tabs)
        assertEquals("终端", terminalTabLabel(0))
        assertEquals(2L, reopened.tabs.single().id)
        assertEquals(3L, reopened.nextTabId)
    }

    /** 终端标签应采用随应用打包的 IntelliJ 终端工具窗口图标。 */
    @Test
    fun `should use bundled IntelliJ terminal icon at panel title size`() {
        assertEquals(16, PANEL_TAB_ICON_SIZE.value.toInt())
        assertEquals("icons/terminal.svg", TERMINAL_ICON_KEY.path(isNewUi = true))
    }

}
