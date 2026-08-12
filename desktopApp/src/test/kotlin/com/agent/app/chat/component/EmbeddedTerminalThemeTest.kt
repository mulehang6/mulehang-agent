package com.agent.app.chat.component

import com.agent.app.design.DesktopAccentColor
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.desktopPalette
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证 Swing/JediTerm 使用的颜色始终来自当前桌面主题色板。 */
class EmbeddedTerminalThemeTest {

    /** 浅色主题必须向终端互操作层传递浅背景与深文字。 */
    @Test
    fun `should map light palette to terminal interop colors`() {
        val colors = terminalInteropColors(
            desktopPalette(DesktopThemeMode.LIGHT, DesktopAccentColor.BLUE).terminal,
        )

        assertEquals(0xF7F8FA, colors.background.rgb and 0xFFFFFF)
        assertEquals(0x1F2329, colors.foreground.rgb and 0xFFFFFF)
        assertEquals(0xA6ABB4, colors.scrollbarThumb.rgb and 0xFFFFFF)
    }

    /** 深色主题保持既有控制台配色，避免主题映射倒置。 */
    @Test
    fun `should map dark palette to terminal interop colors`() {
        val colors = terminalInteropColors(
            desktopPalette(DesktopThemeMode.DARK, DesktopAccentColor.BLUE).terminal,
        )

        assertEquals(0x17181A, colors.background.rgb and 0xFFFFFF)
        assertEquals(0xE6E8EC, colors.foreground.rgb and 0xFFFFFF)
        assertEquals(0x4B4D52, colors.scrollbarThumb.rgb and 0xFFFFFF)
    }

    /** PowerShell 的默认白色 ANSI 背景在深色终端中必须回落为终端背景。 */
    @Test
    fun `should map default ansi colors to dark terminal surfaces`() {
        val palette = desktopPalette(DesktopThemeMode.DARK, DesktopAccentColor.BLUE).terminal

        val background = terminalAnsiPaletteColor(colorIndex = 7, foreground = false, palette = palette)
        val foreground = terminalAnsiPaletteColor(colorIndex = 7, foreground = true, palette = palette)

        assertEquals(0x17181A, background.rgb and 0xFFFFFF)
        assertEquals(0xE6E8EC, foreground.rgb and 0xFFFFFF)
    }
}
