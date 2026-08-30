package com.agent.app.chat.component

import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.desktopPalette
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 验证 Swing/JediTerm 使用的颜色始终来自当前桌面主题色板。 */
class EmbeddedTerminalThemeTest {

    /** 浅色主题必须向终端互操作层传递浅背景与深文字。 */
    @Test
    fun `should map light palette to terminal interop colors`() {
        val colors = terminalInteropColors(
            desktopPalette(DesktopThemeMode.LIGHT).terminal,
        )

        assertEquals(0xFFFFFF, colors.background.rgb and 0xFFFFFF)
        assertEquals(0x1F2329, colors.foreground.rgb and 0xFFFFFF)
        assertEquals(0xA6ABB4, colors.scrollbarThumb.rgb and 0xFFFFFF)
    }

    /** 深色主题保持既有控制台配色，避免主题映射倒置。 */
    @Test
    fun `should map dark palette to terminal interop colors`() {
        val colors = terminalInteropColors(
            desktopPalette(DesktopThemeMode.DARK).terminal,
        )

        assertEquals(0x191A1C, colors.background.rgb and 0xFFFFFF)
        assertEquals(0xE6E8EC, colors.foreground.rgb and 0xFFFFFF)
        assertEquals(0x4B4D52, colors.scrollbarThumb.rgb and 0xFFFFFF)
    }

    /** PowerShell 的默认白色 ANSI 背景在深色终端中必须回落为终端背景。 */
    @Test
    fun `should map default ansi colors to dark terminal surfaces`() {
        val palette = desktopPalette(DesktopThemeMode.DARK).terminal

        val background = terminalAnsiPaletteColor(colorIndex = 7, foreground = false, palette = palette)
        val foreground = terminalAnsiPaletteColor(colorIndex = 7, foreground = true, palette = palette)

        assertEquals(0x191A1C, background.rgb and 0xFFFFFF)
        assertEquals(0xE6E8EC, foreground.rgb and 0xFFFFFF)
    }

    /** 已缓存的默认 TextStyle 必须在状态更新后解析为新主题颜色。 */
    @Test
    fun `should update cached default style without rebuilding terminal`() {
        val state = TerminalThemeState(desktopPalette(DesktopThemeMode.DARK).terminal)
        val provider = DynamicTerminalSettingsProvider(
            state,
            TerminalAppearanceState(TerminalAppearance()),
        )
        @Suppress("DEPRECATION")
        val cachedStyle = provider.defaultStyle

        state.update(desktopPalette(DesktopThemeMode.LIGHT).terminal)

        assertEquals(0x1F2329, requireNotNull(cachedStyle.foreground).toColor().rgb and 0xFFFFFF)
        assertEquals(0xFFFFFF, requireNotNull(cachedStyle.background).toColor().rgb and 0xFFFFFF)
    }

    /** 同一个动态 ANSI 色板应在状态更新后返回浅色主题默认色。 */
    @Test
    fun `should update ansi defaults from terminal theme state`() {
        val state = TerminalThemeState(desktopPalette(DesktopThemeMode.DARK).terminal)
        val provider = DynamicTerminalSettingsProvider(
            state,
            TerminalAppearanceState(TerminalAppearance()),
        )
        val ansiPalette = provider.terminalColorPalette

        state.update(desktopPalette(DesktopThemeMode.LIGHT).terminal)

        val foreground = ansiPalette.getForeground(com.jediterm.terminal.TerminalColor.index(7))
        val background = ansiPalette.getBackground(com.jediterm.terminal.TerminalColor.index(7))
        assertEquals(0x1F2329, foreground.rgb and 0xFFFFFF)
        assertEquals(0xFFFFFF, background.rgb and 0xFFFFFF)
    }

    /** Swing 背景与滚动条必须在 EDT 刷新为新主题色。 */
    @Test
    fun `should refresh swing background and scrollbar on theme change`() {
        val root = JPanel()
        val scrollbar = JScrollBar()
        root.add(scrollbar)
        val palette = desktopPalette(DesktopThemeMode.LIGHT).terminal

        refreshTerminalSwingTheme(root, palette)
        SwingUtilities.invokeAndWait { }

        val expected = terminalInteropColors(palette).background.rgb
        assertEquals(expected, root.background.rgb)
        assertEquals(expected, scrollbar.background.rgb)
    }

    /** JediTerm 父类构造期间创建滚动条时必须读取已经初始化的 SettingsProvider 状态。 */
    @Test
    fun `should construct themed widget without reading uninitialized subclass state`() {
        val state = TerminalThemeState(desktopPalette(DesktopThemeMode.DARK).terminal)

        SwingUtilities.invokeAndWait {
            val widget = ThemedJediTermWidget(
                state,
                TerminalAppearanceState(TerminalAppearance()),
            )
            try {
                val scrollbar = widget.componentTree().filterIsInstance<JScrollBar>().firstOrNull()
                assertNotNull(scrollbar)
                assertEquals("ThemedTerminalScrollBarUi", scrollbar.ui.javaClass.simpleName)

                state.update(desktopPalette(DesktopThemeMode.LIGHT).terminal)
                scrollbar.repaint()
            } finally {
                widget.close()
            }
        }
    }
}

/** 返回测试组件及其全部 Swing 后代。 */
private fun Component.componentTree(): Sequence<Component> = sequence {
    yield(this@componentTree)
    if (this@componentTree is Container) {
        components.forEach { child -> yieldAll(child.componentTree()) }
    }
}
