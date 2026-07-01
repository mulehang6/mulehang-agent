package com.agent.app

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证桌面窗口初始尺寸换算规则。
 */
class MainTest {

    /**
     * 屏幕物理像素在高 DPI 下应先按缩放系数换算为逻辑 dp，再乘以目标占比。
     */
    @Test
    fun `should convert screen pixels to logical dp using scale factor`() {
        assertEquals(1228.8f, calculateWindowSizeDp(screenPixels = 1920, uiScale = 1.25f), 0.001f)
        assertEquals(864f, calculateWindowSizeDp(screenPixels = 1620, uiScale = 1.5f), 0.001f)
        assertEquals(1536f, calculateWindowSizeDp(screenPixels = 1920, uiScale = 1f), 0.001f)
    }
}
