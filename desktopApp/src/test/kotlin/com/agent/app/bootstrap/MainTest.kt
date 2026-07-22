@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.bootstrap

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowDecoration
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

    /**
     * 最大化按钮应在浮动与最大化状态之间切换。
     */
    @Test
    fun `should toggle between floating and maximized placement`() {
        assertEquals(WindowPlacement.Maximized, toggleWindowPlacement(WindowPlacement.Floating))
        assertEquals(WindowPlacement.Floating, toggleWindowPlacement(WindowPlacement.Maximized))
        assertEquals(WindowPlacement.Maximized, toggleWindowPlacement(WindowPlacement.Fullscreen))
    }

    /**
     * 支持 JBR 原生装饰时应使用系统标题栏，否则保留 Compose 回退路径。
     */
    @Test
    fun `should prefer native title bar and retain compose fallback`() {
        assertEquals(WindowChromeMode.JBR_NATIVE, resolveWindowChromeMode(true))
        assertEquals(WindowChromeMode.COMPOSE_FALLBACK, resolveWindowChromeMode(false))
        assertEquals(WindowDecoration.SystemDefault, windowDecorationFor(WindowChromeMode.JBR_NATIVE))
        assertEquals(48, APP_TITLE_BAR_HEIGHT_DP)
    }

    /**
     * JBR/AWT 标题栏高度不应再次乘以 Compose density。
     */
    @Test
    fun `should keep native title bar height in awt coordinates`() {
        assertEquals(48f, nativeTitleBarHeightPx())
    }

    /**
     * 菜单指针事件应明确声明为 JBR 标题栏客户区交互。
     */
    @Test
    fun `should mark menu pointer events as native title bar client area`() {
        var requestedClientArea: Boolean? = null
        val handle = NativeTitleBarHandle { client -> requestedClientArea = client }

        handle.forceClientArea()

        assertEquals(true, requestedClientArea)
    }
}
