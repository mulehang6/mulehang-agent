package com.agent.app.chat.component

import com.agent.app.design.TerminalPalette
import com.jediterm.core.Color as TerminalRgbColor
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Color as AwtColor
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.BoundedRangeModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import javax.swing.plaf.basic.BasicScrollBarUI
import kotlin.math.roundToInt

/** 保存单个 JediTerm 会话当前使用的终端色板。 */
internal class TerminalThemeState(initialPalette: TerminalPalette) {
    @Volatile
    private var currentPalette: TerminalPalette = initialPalette

    /** 返回绘制线程应读取的最新色板。 */
    fun palette(): TerminalPalette = currentPalette

    /** 原子替换会话色板，不重建终端组件或 PTY。 */
    fun update(palette: TerminalPalette) {
        currentPalette = palette
    }
}

/** 为一个会话提供 supplier-backed 默认色和动态 ANSI 色板。 */
internal class DynamicTerminalSettingsProvider(
    internal val themeState: TerminalThemeState,
) : DefaultSettingsProvider() {
    private val foreground = dynamicTerminalColor(themeState) { it.foreground }
    private val background = dynamicTerminalColor(themeState) { it.background }
    private val ansiPalette = DynamicTerminalColorPalette(themeState)

    /** 返回在每次绘制时解析的默认前景色。 */
    override fun getDefaultForeground(): TerminalColor = foreground

    /** 返回在每次绘制时解析的默认背景色。 */
    override fun getDefaultBackground(): TerminalColor = background

    /** 返回读取同一会话主题状态的 ANSI 色板。 */
    override fun getTerminalColorPalette(): ColorPalette = ansiPalette

    /** 缓存的默认样式保留动态颜色引用，以支持存量缓冲区换肤。 */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getDefaultStyle(): TextStyle = TextStyle(foreground, background)

    /** 返回终端统一字体。 */
    override fun getTerminalFont() = terminalFont()

    /** 禁用系统响铃，避免终端错误反馈干扰桌面交互。 */
    override fun audibleBell(): Boolean = false
}

/** 将动态主题状态映射到 JediTerm ANSI 基础色。 */
private class DynamicTerminalColorPalette(
    private val themeState: TerminalThemeState,
) : ColorPalette() {
    /** 返回当前色板下的 ANSI 前景色。 */
    override fun getForegroundByColorIndex(colorIndex: Int): TerminalRgbColor =
        terminalAnsiPaletteColor(colorIndex, foreground = true, palette = themeState.palette())

    /** 返回当前色板下的 ANSI 背景色。 */
    override fun getBackgroundByColorIndex(colorIndex: Int): TerminalRgbColor =
        terminalAnsiPaletteColor(colorIndex, foreground = false, palette = themeState.palette())
}

/** 使用会话主题状态创建带动态滚动条的 JediTerm 组件。 */
internal class ThemedJediTermWidget(
    themeState: TerminalThemeState,
) : JediTermWidget(DynamicTerminalSettingsProvider(themeState)) {
    /** 创建随会话色板更新、仅在存在缓冲内容时显示的滚动条。 */
    override fun createScrollBar(): JScrollBar {
        val initializedThemeState = (mySettingsProvider as DynamicTerminalSettingsProvider).themeState
        val scrollBar = ThemedTerminalScrollBar().apply {
            isOpaque = false
            unitIncrement = 3
            setUI(ThemedTerminalScrollBarUi(initializedThemeState))
        }
        val modelListener = ChangeListener { updateThemedTerminalScrollbarVisibility(scrollBar) }
        scrollBar.model.addChangeListener(modelListener)
        scrollBar.addPropertyChangeListener("model") { event ->
            (event.oldValue as? BoundedRangeModel)?.removeChangeListener(modelListener)
            (event.newValue as? BoundedRangeModel)?.addChangeListener(modelListener)
            updateThemedTerminalScrollbarVisibility(scrollBar)
        }
        updateThemedTerminalScrollbarVisibility(scrollBar)
        return scrollBar
    }
}

/** 将 Compose 终端色转换为 supplier-backed JediTerm 色。 */
private fun dynamicTerminalColor(
    themeState: TerminalThemeState,
    select: (TerminalInteropColors) -> AwtColor,
): TerminalColor = TerminalColor {
    val selected = select(terminalInteropColors(themeState.palette()))
    TerminalRgbColor(selected.red, selected.green, selected.blue)
}

/** 在 Swing EDT 上刷新组件树的背景、布局与绘制缓存。 */
internal fun refreshTerminalSwingTheme(
    component: Component,
    palette: TerminalPalette,
) {
    val refresh = Runnable {
        val background = terminalInteropColors(palette).background
        generateSequence(component) { current -> current.parent }.forEach { ancestor ->
            ancestor.background = background
            ancestor.revalidateIfContainer()
            ancestor.repaint()
        }
        component.walkComponentTree().forEach { child ->
            child.background = background
            child.revalidateIfContainer()
            child.repaint()
        }
    }
    if (SwingUtilities.isEventDispatchThread()) refresh.run() else SwingUtilities.invokeLater(refresh)
}

/** 返回当前组件及其全部 Swing 后代。 */
private fun Component.walkComponentTree(): Sequence<Component> = sequence {
    yield(this@walkComponentTree)
    if (this@walkComponentTree is Container) {
        components.forEach { child -> yieldAll(child.walkComponentTree()) }
    }
}

/** 仅对容器请求重新布局。 */
private fun Component.revalidateIfContainer() {
    if (this is JComponent) revalidate()
}

/** 仅在可见时占据八像素宽度的终端滚动条。 */
private class ThemedTerminalScrollBar : JScrollBar(VERTICAL) {
    /** 根据可见状态返回紧凑宽度。 */
    override fun getPreferredSize(): Dimension = if (isVisible) Dimension(8, 0) else Dimension(0, 0)
}

/** 绘制读取会话主题状态的透明轨道和圆角滑块。 */
private class ThemedTerminalScrollBarUi(
    private val themeState: TerminalThemeState,
) : BasicScrollBarUI() {
    /** 配置透明轨道和首次使用的滑块颜色。 */
    override fun configureScrollBarColors() {
        trackColor = AwtColor(0, 0, 0, 0)
        thumbColor = terminalInteropColors(themeState.palette()).scrollbarThumb
    }

    /** 隐藏滚动条顶部按钮。 */
    override fun createDecreaseButton(orientation: Int): JButton = zeroSizeButton()

    /** 隐藏滚动条底部按钮。 */
    override fun createIncreaseButton(orientation: Int): JButton = zeroSizeButton()

    /** 透明轨道无需额外绘制。 */
    override fun paintTrack(graphics: Graphics, component: JComponent, trackBounds: Rectangle) = Unit

    /** 使用最新主题色绘制圆角滑块。 */
    override fun paintThumb(graphics: Graphics, component: JComponent, thumbBounds: Rectangle) {
        if (!scrollbar.isEnabled || thumbBounds.isEmpty) return
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics2D.color = terminalInteropColors(themeState.palette()).scrollbarThumb
            graphics2D.fillRoundRect(
                thumbBounds.x + 1,
                thumbBounds.y + 2,
                (thumbBounds.width - 2).coerceAtLeast(5),
                (thumbBounds.height - 4).coerceAtLeast(8),
                6,
                6,
            )
        } finally {
            graphics2D.dispose()
        }
    }

    /** 防止极短缓冲区产生不可操作的滑块。 */
    override fun getMinimumThumbSize(): Dimension = Dimension(5, 24)

    /** 创建不占空间的滚动按钮。 */
    private fun zeroSizeButton(): JButton = JButton().apply {
        preferredSize = Dimension(0, 0)
        minimumSize = Dimension(0, 0)
        maximumSize = Dimension(0, 0)
        isOpaque = false
        isFocusable = false
        border = null
    }
}

/** 根据缓冲区范围更新终端滚动条可见性。 */
private fun updateThemedTerminalScrollbarVisibility(scrollBar: JScrollBar) {
    val update = Runnable {
        val model = scrollBar.model
        val visible = shouldShowTerminalScrollbar(model.minimum, model.maximum, model.extent)
        if (scrollBar.isVisible != visible) {
            scrollBar.isVisible = visible
            scrollBar.parent?.revalidate()
            scrollBar.parent?.repaint()
        }
    }
    if (SwingUtilities.isEventDispatchThread()) update.run() else SwingUtilities.invokeLater(update)
}

/** 将 Compose 颜色转换为 AWT RGBA 色。 */
internal fun androidx.compose.ui.graphics.Color.toTerminalAwtColor(): AwtColor = AwtColor(
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
    (alpha * 255).roundToInt(),
)

/** JVM 互操作层使用的终端 AWT 色值。 */
internal data class TerminalInteropColors(
    val background: AwtColor,
    val foreground: AwtColor,
    val scrollbarThumb: AwtColor,
)

/** 将明确的终端色板映射为 JediTerm 与 Swing 可消费的颜色。 */
internal fun terminalInteropColors(palette: TerminalPalette): TerminalInteropColors = TerminalInteropColors(
    background = palette.background.toTerminalAwtColor(),
    foreground = palette.foreground.toTerminalAwtColor(),
    scrollbarThumb = palette.scrollbarThumb.toTerminalAwtColor(),
)

/** 同步 Swing 祖先链背景，避免互操作区域扩张时露出窗口默认色。 */
internal fun synchronizeTerminalInteropBackground(component: Component, palette: TerminalPalette) {
    val background = terminalInteropColors(palette).background
    generateSequence(component) { current -> current.parent }.forEach { ancestor ->
        ancestor.background = background
        ancestor.repaint()
    }
}

/** 返回终端默认字体。 */
internal fun terminalFont(): Font = Font("Maple Mono NF CN SemiBold", Font.PLAIN, 14)

/** 判断终端缓冲内容是否超过当前可见范围。 */
internal fun shouldShowTerminalScrollbar(minimum: Int, maximum: Int, extent: Int): Boolean =
    maximum - minimum > extent

/** 返回 ANSI 索引的最终颜色，其中 PowerShell 默认黑白色遵循会话主题。 */
internal fun terminalAnsiPaletteColor(
    colorIndex: Int,
    foreground: Boolean,
    palette: TerminalPalette,
): TerminalRgbColor {
    val colors = terminalInteropColors(palette)
    val themeColor = if (foreground) colors.foreground else colors.background
    if (colorIndex in DEFAULT_TERMINAL_COLOR_INDICES) {
        return TerminalRgbColor(themeColor.red, themeColor.green, themeColor.blue)
    }
    val color = WINDOWS_TERMINAL_ANSI_COLORS.getOrElse(colorIndex) { WINDOWS_TERMINAL_ANSI_COLORS.first() }
    return TerminalRgbColor(color.red, color.green, color.blue)
}

/** PowerShell 默认使用的 ANSI 黑、白与亮白索引。 */
private val DEFAULT_TERMINAL_COLOR_INDICES = setOf(0, 7, 15)

/** Windows 控制台基础 ANSI 色，非默认色仍保持错误、警告等语义。 */
private val WINDOWS_TERMINAL_ANSI_COLORS = listOf(
    AwtColor(0x00, 0x00, 0x00),
    AwtColor(0x80, 0x00, 0x00),
    AwtColor(0x00, 0x80, 0x00),
    AwtColor(0x80, 0x80, 0x00),
    AwtColor(0x00, 0x00, 0x80),
    AwtColor(0x80, 0x00, 0x80),
    AwtColor(0x00, 0x80, 0x80),
    AwtColor(0xC0, 0xC0, 0xC0),
    AwtColor(0x80, 0x80, 0x80),
    AwtColor(0xFF, 0x00, 0x00),
    AwtColor(0x00, 0xFF, 0x00),
    AwtColor(0xFF, 0xFF, 0x00),
    AwtColor(0x46, 0x82, 0xB4),
    AwtColor(0xFF, 0x00, 0xFF),
    AwtColor(0x00, 0xFF, 0xFF),
    AwtColor(0xFF, 0xFF, 0xFF),
)
