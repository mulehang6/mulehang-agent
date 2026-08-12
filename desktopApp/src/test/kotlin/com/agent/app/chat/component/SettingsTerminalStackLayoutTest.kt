package com.agent.app.chat.component

import androidx.compose.ui.graphics.Color
import com.agent.app.design.AppLine
import kotlin.test.Test
import kotlin.test.assertEquals

/** 设置与终端共存布局的尺寸和失焦样式回归测试。 */
class SettingsTerminalStackLayoutTest {

    /** 纵向分割条必须同时为设置和终端保留最小可用空间。 */
    @Test
    fun `should clamp settings height between both panel minimums`() {
        assertEquals(
            280f,
            clampStackPanelHeight(
                requestedHeightPx = 40f,
                availableHeightPx = 800f,
                minimumPanelHeightPx = 280f,
                otherMinimumPanelHeightPx = 180f,
            ),
        )
        assertEquals(
            620f,
            clampStackPanelHeight(
                requestedHeightPx = 760f,
                availableHeightPx = 800f,
                minimumPanelHeightPx = 280f,
                otherMinimumPanelHeightPx = 180f,
            ),
        )
    }

    /** 非焦点终端标签应放弃蓝色边框并回退至通用灰色分割线。 */
    @Test
    fun `should use gray terminal tab border when island is unfocused`() {
        assertEquals(AppLine, terminalTabBorderColor(selected = true, focused = false))
        assertEquals(Color.Transparent, terminalTabBorderColor(selected = false, focused = false))
    }

    /** 终端关闭时必须先完成纵向收缩，不能在第一帧移除 Island。 */
    @Test
    fun `should retain terminal until stack exit motion completes`() {
        assertEquals(true, shouldKeepStackPanelRendered(targetVisible = false, motionProgress = 0.42f))
        assertEquals(false, shouldKeepStackPanelRendered(targetVisible = false, motionProgress = 0f))
        assertEquals(true, shouldKeepStackPanelRendered(targetVisible = true, motionProgress = 0f))
    }
}
