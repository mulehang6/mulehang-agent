package com.agent.app.design

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    /** 浅色 palette 的非 Island 基底必须统一为 #E9EAEE，并保持文字可读。 */
    @Test
    fun `should provide readable light palette`() {
        val palette = desktopPalette(DesktopThemeMode.LIGHT)

        assertEquals(false, palette.isDark)
        assertEquals(Color(0xFFE9EAEE), palette.frameBackground)
        assertEquals(Color(0xFFE9EAEE), palette.background)
        assertEquals(Color(0xFFE9EAEE), palette.headerBackground)
        assertEquals(Color(0xFFE9EAEE), palette.sidebarBackground)
        assertEquals(Color(0xFFFFFFFF), palette.workspaceBackground)
        assertEquals(Color(0xFF202124), palette.text)
        assertEquals(DesktopAccentBlue, palette.accent)
        assertEquals(Color(0xFFFFFFFF), palette.composerInputBackground)
        assertEquals(Color(0xFFF1F3F6), palette.providerCardBackground)
        assertEquals(Color(0xFFE3E7EC), palette.providerCardHoverBackground)
        assertEquals(Color(0xFFFFFFFF), palette.terminal.background)
        assertEquals(Color(0xFF1F2329), palette.terminal.foreground)
    }

    /** 深色 token 需保留既有 Provider 卡片与终端颜色，避免本次浅色修复回归深色界面。 */
    @Test
    fun `should preserve dark composer provider and terminal tokens`() {
        val palette = desktopPalette(DesktopThemeMode.DARK)

        assertEquals(true, palette.isDark)
        assertEquals(Color(0xFF202226), palette.frameBackground)
        assertEquals(Color(0xFF202226), palette.background)
        assertEquals(Color(0xFF202226), palette.headerBackground)
        assertEquals(Color(0xFF26282C), palette.sidebarBackground)
        assertEquals(Color(0xFF191A1C), palette.workspaceBackground)
        assertEquals(Color(0xFF0A0B0D), palette.composerInputBackground)
        assertEquals(Color(0xFF252629), palette.providerCardBackground)
        assertEquals(Color(0xFF38393B), palette.providerCardHoverBackground)
        assertEquals(Color(0xFF191A1C), palette.terminal.background)
        assertEquals(Color(0xFFE6E8EC), palette.terminal.foreground)
    }

    /** 深色主文字必须避免纯白，并在常用主界面表面保持 AA 可读性。 */
    @Test
    fun `should use soft accessible primary text for dark palette`() {
        val palette = desktopPalette(DesktopThemeMode.DARK)
        val systemDarkPalette = desktopPalette(DesktopThemeMode.SYSTEM, systemIsDark = true)
        val primaryText = Color(0xFFD7DAE0)

        assertEquals(primaryText, palette.text)
        assertEquals(primaryText, systemDarkPalette.text)
        listOf(
            palette.workspaceBackground,
            palette.frameBackground,
            palette.sidebarBackground,
            palette.composerBackground,
            palette.composerInputBackground,
        ).forEach { surface ->
            assertTrue(
                contrastRatio(primaryText, surface) >= 4.5,
                "深色主文字在 $surface 上必须达到 AA 对比度",
            )
        }
    }

    /** 标题栏维持 IDEA 的 54dp 原生窗口命中高度；项目环境光由根画布负责。 */
    @Test
    fun `should retain the IDEA title bar height for the root ambient canvas`() {
        val metrics = ideaTitleBarMetrics()

        assertEquals(Color(0xFF28434A), desktopPalette(DesktopThemeMode.DARK).titleBarGradientStart)
        assertEquals(54f, metrics.height.value)
    }

    /** 浅色标题栏的常规悬浮必须使用中性灰，不能误用蓝色选中态。 */
    @Test
    fun `should use neutral title bar hover colors for both themes`() {
        assertEquals(Color(0xFFD5D9E0), titleBarHoverBackground(isDark = false))
        assertEquals(Color(0xFFC7CCD4), titleBarPressedBackground(isDark = false))
        assertEquals(Color.White.copy(alpha = 0.12f), titleBarHoverBackground(isDark = true))
    }

    /** 计算两个 sRGB 颜色的 WCAG 对比度。 */
    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        return (maxOf(firstLuminance, secondLuminance) + 0.05) /
            (minOf(firstLuminance, secondLuminance) + 0.05)
    }

    /** 将一个 sRGB 颜色转换为相对亮度。 */
    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearized(color.red) +
            0.7152 * linearized(color.green) +
            0.0722 * linearized(color.blue)

    /** 将一个 sRGB 分量转换为线性亮度。 */
    private fun linearized(component: Float): Double {
        val value = component.toDouble()
        return if (value <= 0.04045) {
            value / 12.92
        } else {
            ((value + 0.055) / 1.055).pow(2.4)
        }
    }
}
