package com.agent.app.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证桌面动态主题的模式解析和核心视觉 token。 */
class DesktopThemeSettingsTest {

    /** 跟随系统必须根据系统色彩偏好解析为可渲染的明确模式。 */
    @Test
    fun `should resolve system theme from platform preference`() {
        assertEquals(DesktopThemeMode.DARK, resolveDesktopThemeMode(DesktopThemeMode.SYSTEM, systemIsDark = true))
        assertEquals(DesktopThemeMode.LIGHT, resolveDesktopThemeMode(DesktopThemeMode.SYSTEM, systemIsDark = false))
        assertEquals(DesktopThemeMode.LIGHT, resolveDesktopThemeMode(DesktopThemeMode.LIGHT, systemIsDark = true))
    }

    /** 默认深色蓝色主题应保留既有菜单选中颜色，避免交互色板回归。 */
    @Test
    fun `should preserve popup selection color for default dark palette`() {
        val palette = desktopPalette(DesktopThemeMode.DARK, DesktopAccentColor.BLUE)

        assertEquals(Color(0xFF194474), palette.popupSelectedBackground)
        assertEquals(Color(0xFF2E436E), palette.selectedBackground)
        assertEquals(DesktopAccentColor.BLUE.color, palette.accent)
    }

    /** 浅色 palette 必须同时切换表面、文字和菜单，以避免只改变 Material 控件。 */
    @Test
    fun `should provide readable light palette`() {
        val palette = desktopPalette(DesktopThemeMode.LIGHT, DesktopAccentColor.TEAL)

        assertEquals(false, palette.isDark)
        assertEquals(Color(0xFFF6F7F9), palette.background)
        assertEquals(Color(0xFF202124), palette.text)
        assertEquals(DesktopAccentColor.TEAL.color, palette.accent)
        assertEquals(Color(0xFFFFFFFF), palette.composerInputBackground)
        assertEquals(Color(0xFFF1F3F6), palette.providerCardBackground)
        assertEquals(Color(0xFFE3E7EC), palette.providerCardHoverBackground)
        assertEquals(Color(0xFFF7F8FA), palette.terminal.background)
        assertEquals(Color(0xFF1F2329), palette.terminal.foreground)
    }

    /** 深色 token 需保留既有 Provider 卡片与终端颜色，避免本次浅色修复回归深色界面。 */
    @Test
    fun `should preserve dark composer provider and terminal tokens`() {
        val palette = desktopPalette(DesktopThemeMode.DARK, DesktopAccentColor.BLUE)

        assertEquals(true, palette.isDark)
        assertEquals(Color(0xFF0A0B0D), palette.composerInputBackground)
        assertEquals(Color(0xFF252629), palette.providerCardBackground)
        assertEquals(Color(0xFF38393B), palette.providerCardHoverBackground)
        assertEquals(Color(0xFF17181A), palette.terminal.background)
        assertEquals(Color(0xFFE6E8EC), palette.terminal.foreground)
    }
}
