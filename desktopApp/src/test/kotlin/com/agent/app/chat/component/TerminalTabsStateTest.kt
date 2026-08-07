package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证内嵌终端标签页的状态转换与终端图标意图。
 */
class TerminalTabsStateTest {

    /**
     * 新建标签页时应分配连续标题、记录创建路径并选中最新标签。
     */
    @Test
    fun `should create and select sequential terminal tabs`() {
        val first = TerminalTabsState().addTab("C:/workspace")
        val second = first.addTab("D:/other")

        assertEquals(listOf("终端", "终端 2"), second.tabs.map(TerminalTab::title))
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

    /** 关闭最后一个标签后应重置窗口状态，使下一次打开重新从首个终端开始。 */
    @Test
    fun `should reset terminal numbering after the terminal window closes`() {
        val closed = TerminalTabsState().addTab("C:/workspace").resetAfterTerminalWindowClosed()
        val reopened = closed.addTab("C:/workspace")

        assertEquals(emptyList(), closed.tabs)
        assertEquals("终端", reopened.tabs.single().title)
    }

}
