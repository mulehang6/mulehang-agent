package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证设置与终端四态布局的空间过渡规则。 */
class SettingsTerminalStackLayoutTest {

    /** 每组设置与终端可见性都必须映射到唯一布局状态。 */
    @Test
    fun `should resolve every settings terminal visibility combination`() {
        assertEquals(SettingsTerminalLayoutMode.HIDDEN, settingsTerminalLayoutMode(settingsVisible = false, terminalVisible = false))
        assertEquals(SettingsTerminalLayoutMode.SETTINGS, settingsTerminalLayoutMode(settingsVisible = true, terminalVisible = false))
        assertEquals(SettingsTerminalLayoutMode.TERMINAL, settingsTerminalLayoutMode(settingsVisible = false, terminalVisible = true))
        assertEquals(SettingsTerminalLayoutMode.SPLIT, settingsTerminalLayoutMode(settingsVisible = true, terminalVisible = true))
    }

    /** 单面板、双面板和隐藏状态必须给出连续布局所需的目标比例。 */
    @Test
    fun `should expose panel shares and divider only for split mode`() {
        assertEquals(0f, settingsTerminalSettingsShare(SettingsTerminalLayoutMode.HIDDEN, 0.52f))
        assertEquals(1f, settingsTerminalSettingsShare(SettingsTerminalLayoutMode.SETTINGS, 0.52f))
        assertEquals(0f, settingsTerminalSettingsShare(SettingsTerminalLayoutMode.TERMINAL, 0.52f))
        assertEquals(0.52f, settingsTerminalSettingsShare(SettingsTerminalLayoutMode.SPLIT, 0.52f))
        assertTrue(SettingsTerminalLayoutMode.SPLIT.showsDivider())
        assertFalse(SettingsTerminalLayoutMode.SETTINGS.showsDivider())
    }

    /** 分割比例必须同时满足设置和终端的最小高度。 */
    @Test
    fun `should clamp split layout within pane minimum heights`() {
        assertEquals(
            0.28f,
            clampSettingsTerminalSplitFraction(
                requestedFraction = 0.10f,
                availableHeightPx = 1_000f,
                minimumSettingsHeightPx = 280f,
                minimumTerminalHeightPx = 180f,
            ),
        )
        assertEquals(
            0.82f,
            clampSettingsTerminalSplitFraction(
                requestedFraction = 0.95f,
                availableHeightPx = 1_000f,
                minimumSettingsHeightPx = 280f,
                minimumTerminalHeightPx = 180f,
            ),
        )
        assertEquals(
            DEFAULT_SETTINGS_TERMINAL_SPLIT_FRACTION,
            clampSettingsTerminalSplitFraction(
                requestedFraction = 0.1f,
                availableHeightPx = 300f,
                minimumSettingsHeightPx = 280f,
                minimumTerminalHeightPx = 180f,
            ),
        )
    }

    /** 相邻 Island 切换保持短促的进入和退出节奏。 */
    @Test
    fun `should use concise settings terminal transition timings`() {
        assertEquals(220, SETTINGS_TERMINAL_PANEL_ENTER_DURATION_MILLIS)
        assertEquals(180, SETTINGS_TERMINAL_PANEL_EXIT_DURATION_MILLIS)
        assertEquals(
            SETTINGS_TERMINAL_PANEL_EXIT_DURATION_MILLIS,
            settingsTerminalPanelTransitionDuration(SettingsTerminalLayoutMode.HIDDEN),
        )
        assertEquals(
            SETTINGS_TERMINAL_PANEL_ENTER_DURATION_MILLIS,
            settingsTerminalPanelTransitionDuration(SettingsTerminalLayoutMode.SPLIT),
        )
        assertEquals(8, SETTINGS_TERMINAL_DIVIDER_HEIGHT.value.toInt())
    }

    /** 拖拽期间必须绕过布局动画，直接让面板比例追随指针。 */
    @Test
    fun `should use live split share while divider is dragging`() {
        assertEquals(
            0.72f,
            settingsTerminalRenderedShare(
                desiredShare = 0.72f,
                animatedShare = 0.52f,
                dividerDragging = true,
            ),
        )
        assertEquals(
            0.52f,
            settingsTerminalRenderedShare(
                desiredShare = 0.72f,
                animatedShare = 0.52f,
                dividerDragging = false,
            ),
        )
    }
}
