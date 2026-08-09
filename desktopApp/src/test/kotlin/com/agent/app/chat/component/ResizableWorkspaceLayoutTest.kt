package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证右侧终端分栏的宽度约束与开合布局。 */
class ResizableWorkspaceLayoutTest {

    /** 默认宽度必须被终端和主区域的最小宽度共同约束。 */
    @Test
    fun `should clamp right terminal width within workspace constraints`() {
        assertEquals(
            480f,
            clampTerminalWidth(
                requestedWidthPx = 600f,
                availableWidthPx = 900f,
                minimumTerminalWidthPx = 320f,
                minimumWorkspaceWidthPx = 420f,
            ),
        )
        assertEquals(
            320f,
            clampTerminalWidth(
                requestedWidthPx = 100f,
                availableWidthPx = 900f,
                minimumTerminalWidthPx = 320f,
                minimumWorkspaceWidthPx = 420f,
            ),
        )
    }

    /** 右侧终端展开时仅压缩主区宽度，关闭时恢复全宽。 */
    @Test
    fun `should reserve right side width only while terminal is visible`() {
        assertEquals(1_000f, workspaceWidthDuringTerminalMotion(1_000f, 500f, 0f))
        assertEquals(500f, workspaceWidthDuringTerminalMotion(1_000f, 500f, 1f))
        assertEquals(250f, terminalContainerWidthDuringMotion(500f, 0.5f))
    }
}
