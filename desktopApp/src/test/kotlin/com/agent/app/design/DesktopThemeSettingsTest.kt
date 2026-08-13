package com.agent.app.design

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证桌面动态主题的模式解析和核心视觉 token。 */
class DesktopThemeSettingsTest {

    /** 三种主题持久化值必须稳定解析，未知值保持既有深色默认。 */
    @Test
    fun `should parse all persisted theme modes`() {
        assertEquals(DesktopThemeMode.SYSTEM, DesktopThemeMode.fromStorage("system"))
        assertEquals(DesktopThemeMode.DARK, DesktopThemeMode.fromStorage("dark"))
        assertEquals(DesktopThemeMode.LIGHT, DesktopThemeMode.fromStorage("light"))
        assertEquals(DesktopThemeMode.DARK, DesktopThemeMode.fromStorage("unknown"))
    }

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
        val palette = desktopPalette(DesktopThemeMode.DARK)

        assertEquals(Color(0xFF194474), palette.popupSelectedBackground)
        assertEquals(Color(0xFF2E436E), palette.selectedBackground)
        assertEquals(DesktopAccentBlue, palette.accent)
    }

    /** 浅色 palette 必须同时切换表面、文字和菜单，以避免只改变 Material 控件。 */
    @Test
    fun `should provide readable light palette`() {
        val palette = desktopPalette(DesktopThemeMode.LIGHT)

        assertEquals(false, palette.isDark)
        assertEquals(Color(0xFFF6F7F9), palette.background)
        assertEquals(Color(0xFF202124), palette.text)
        assertEquals(DesktopAccentBlue, palette.accent)
        assertEquals(Color(0xFFFFFFFF), palette.composerInputBackground)
        assertEquals(Color(0xFFF1F3F6), palette.providerCardBackground)
        assertEquals(Color(0xFFE3E7EC), palette.providerCardHoverBackground)
        assertEquals(Color(0xFFF7F8FA), palette.terminal.background)
        assertEquals(Color(0xFF1F2329), palette.terminal.foreground)
    }

    /** 深色 token 需保留既有 Provider 卡片与终端颜色，避免本次浅色修复回归深色界面。 */
    @Test
    fun `should preserve dark composer provider and terminal tokens`() {
        val palette = desktopPalette(DesktopThemeMode.DARK)

        assertEquals(true, palette.isDark)
        assertEquals(Color(0xFF0A0B0D), palette.composerInputBackground)
        assertEquals(Color(0xFF252629), palette.providerCardBackground)
        assertEquals(Color(0xFF38393B), palette.providerCardHoverBackground)
        assertEquals(Color(0xFF17181A), palette.terminal.background)
        assertEquals(Color(0xFFE6E8EC), palette.terminal.foreground)
    }

    /** Liquid Glass 只改变材质维度，深浅色与终端颜色仍由主题模式决定。 */
    @Test
    fun `should resolve liquid glass independently from light and dark palettes`() {
        val light = desktopPalette(DesktopThemeMode.LIGHT, materialMode = DesktopMaterialMode.LIQUID_GLASS)
        val dark = desktopPalette(DesktopThemeMode.DARK, materialMode = DesktopMaterialMode.LIQUID_GLASS)

        assertEquals(DesktopMaterialMode.LIQUID_GLASS, light.materialMode)
        assertEquals(false, light.isDark)
        assertEquals(Color(0xFFF7F8FA), light.terminal.background)
        assertEquals(DesktopMaterialMode.LIQUID_GLASS, dark.materialMode)
        assertEquals(true, dark.isDark)
        assertEquals(Color(0xFF17181A), dark.terminal.background)
    }
}
