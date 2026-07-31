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

        assertEquals(listOf("终端 1", "终端 2"), second.tabs.map(TerminalTab::title))
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

    /**
     * 重复点击终端图标必须聚焦当前页，而不是关闭面板或创建新页。
     */
    @Test
    fun `should focus instead of closing or creating a tab for repeated terminal icon clicks`() {
        assertEquals(TerminalIconAction.CREATE_TAB, terminalIconAction(hasActiveTab = false))
        assertEquals(TerminalIconAction.FOCUS_ACTIVE_TAB, terminalIconAction(hasActiveTab = true))
    }
}
