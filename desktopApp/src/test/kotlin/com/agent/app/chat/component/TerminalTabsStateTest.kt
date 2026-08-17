package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.ui.graphics.Color
import com.agent.app.design.TerminalTabActiveBackground
import com.agent.app.design.TerminalTabHoverBackground
import com.agent.app.design.PANEL_TAB_ICON_SIZE
import com.agent.app.design.TERMINAL_ICON_KEY

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

    /** 关闭最后一个标签后应重置窗口状态，使下一次打开重新从首个终端开始。 */
    @Test
    fun `should reset terminal numbering after the terminal window closes`() {
        val closed = TerminalTabsState().addTab("C:/workspace").resetAfterTerminalWindowClosed()
        val reopened = closed.addTab("C:/workspace")

        assertEquals(emptyList(), closed.tabs)
        assertEquals("终端", reopened.tabs.single().title)
    }

    /** 标签 hover 和选中状态必须使用圆角背景，并保持静止态透明。 */
    @Test
    fun `should use rounded terminal tab hover states`() {
        assertEquals(7, TERMINAL_TAB_CORNER_RADIUS.value.toInt())
        assertEquals(32, TERMINAL_TAB_HEIGHT.value.toInt())
        assertEquals(TerminalTabActiveBackground, terminalTabBackground(selected = true, hovered = true))
        assertEquals(TerminalTabHoverBackground, terminalTabBackground(selected = false, hovered = true))
        assertEquals(Color.Transparent, terminalTabBackground(selected = false, hovered = false))
    }

    /** 终端标签应采用 16dp 的 IntelliJ 控制台图标，而不是运行/播放图标。 */
    @Test
    fun `should use console terminal icon at panel title size`() {
        assertEquals(16, PANEL_TAB_ICON_SIZE.value.toInt())
        assertEquals("debugger/console.svg", TERMINAL_ICON_KEY.path(isNewUi = true))
    }

    /** 左右方向键应在终端标签之间循环选择。 */
    @Test
    fun `should cycle terminal tabs with direction keys`() {
        val tabs = TerminalTabsState().addTab("C:/one").addTab("C:/two").tabs

        assertEquals(2L, adjacentTerminalTabId(tabs, activeTabId = 1L, direction = 1))
        assertEquals(2L, adjacentTerminalTabId(tabs, activeTabId = 1L, direction = -1))
    }

}
