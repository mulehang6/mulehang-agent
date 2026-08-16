package com.agent.app.bootstrap

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证桌面窗口初始尺寸换算规则。
 */
class MainTest {

    /**
     * SwingPanel 与 Compose 同层合成应在创建窗口前启用，避免动态裁剪边缘闪烁。
     */
    @Test
    fun `should enable compose interop blending before application starts`() {
        val previous = System.getProperty(COMPOSE_INTEROP_BLENDING_PROPERTY)
        try {
            System.clearProperty(COMPOSE_INTEROP_BLENDING_PROPERTY)

            configureDesktopRendering()

            assertEquals("true", System.getProperty(COMPOSE_INTEROP_BLENDING_PROPERTY))
        } finally {
            if (previous == null) {
                System.clearProperty(COMPOSE_INTEROP_BLENDING_PROPERTY)
            } else {
                System.setProperty(COMPOSE_INTEROP_BLENDING_PROPERTY, previous)
            }
        }
    }

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
