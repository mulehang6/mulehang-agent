@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppLine
import com.agent.app.design.AppText
import com.agent.app.design.DesktopInteropPalette
import com.agent.app.design.MenuGrowthOrigin
import com.agent.app.design.PopupMenuSelectedBackground
import com.agent.app.design.PopupMenuShape
import com.agent.app.design.RingDropdownMenuItem
import com.agent.app.design.PopupMenuShadowInset
import com.agent.app.design.popupMenuSurface
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.RightRailGlyphIcon
import com.agent.app.design.TerminalPalette
import com.agent.app.design.TerminalSurfaceBackground
import com.agent.app.design.TerminalTabActiveBackground
import com.agent.app.design.TerminalTabHoverBackground
import com.agent.app.design.TerminalTabSelectedBorder
import com.agent.app.design.menuGrowthTransformOrigin
import com.agent.app.design.rememberMenuGrowthMotion
import com.agent.app.platform.buildPowerShellCommand
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import java.awt.Component
import java.awt.Dimension
import java.awt.Container
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.Color as AwtColor
import com.jediterm.core.Color as TerminalRgbColor
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt
import javax.swing.BoundedRangeModel
import javax.swing.JButton
import javax.swing.JScrollBar
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import javax.swing.plaf.basic.BasicScrollBarUI

internal const val TERMINAL_CLOSE_BUTTON_SIZE_DP = 24
internal const val TERMINAL_TAB_HEIGHT_DP = 30
internal const val TERMINAL_ADD_BUTTON_SIZE_DP = 36
internal const val TERMINAL_HIDE_BUTTON_SIZE_DP = 32
/** 返回终端标签的常态边框色，选中态使用 Air 蓝描边而非悬浮反馈。 */
internal fun terminalTabBorderColor(selected: Boolean, focused: Boolean = true): Color =
    if (selected) {
        if (focused) TerminalTabSelectedBorder else AppLine
    } else {
        Color.Transparent
    }

/** 返回新建终端按钮的悬浮底色；图标本身不使用发光效果。 */
internal fun terminalAddButtonBackground(hovered: Boolean): Color =
    if (hovered) TerminalTabHoverBackground else Color.Transparent

/** 返回终端标题栏关闭图标的可访问文案，明确其不会终止终端会话。 */
internal fun terminalPanelHideActionLabel(): String = "收起终端"

/**
 * 返回终端操作图标的发光强度；新建和关闭操作保持克制的静态呈现。
 */
@Suppress("UNUSED_PARAMETER")
internal fun terminalActionGlowAlpha(hovered: Boolean): Float = 0f

/**
 * 表示 Compose 终端面板所需的 Swing 终端边界。
 */
internal interface TerminalHandle {
    /**
     * 返回可交给 SwingPanel 承载的终端组件；创建失败时为 null。
     */
    val component: Component?

    /**
     * 返回终端创建失败时应展示的消息。
     */
    val errorMessage: String

    /**
     * 启动底层终端进程。
     */
    fun start()

    /**
     * 释放底层终端进程。
     */
    fun close()

    /**
     * 仅在焦点不属于终端时恢复终端焦点。
     */
    fun focusIfNeeded()
}

/**
 * 保存各终端标签页的进程句柄，避免切换标签页时销毁后台会话。
 */
internal class TerminalSessionStore(
    private val terminalFactory: (String) -> TerminalHandle = ::createPowerShellHandle,
) {
    private val sessions = linkedMapOf<Long, TerminalHandle>()

    /**
     * 为 [tab] 创建并启动一次终端会话。
     */
    fun create(tab: TerminalTab) {
        sessions.getOrPut(tab.id) { terminalFactory(tab.workspacePath) }.start()
    }

    /**
     * 返回 [tabId] 对应的持久终端会话。
     */
    fun session(tabId: Long): TerminalHandle? = sessions[tabId]

    /**
     * 释放并移除 [tabId] 对应的终端会话。
     */
    fun close(tabId: Long) {
        sessions.remove(tabId)?.close()
    }

    /**
     * 释放窗口关闭时仍存活的所有终端会话。
     */
    fun closeAll() {
        sessions.values.toList().forEach(TerminalHandle::close)
        sessions.clear()
    }

    /**
     * 释放除 [keptTabId] 之外的所有终端会话。
     */
    fun closeAllExcept(keptTabId: Long) {
        sessions.keys.filter { it != keptTabId }.forEach(::close)
    }

    /**
     * 将焦点请求委派给当前活动的终端会话。
     */
    fun focusActiveIfNeeded(activeTabId: Long?) {
        activeTabId?.let(sessions::get)?.focusIfNeeded()
    }
}

/**
 * 用 JediTerm 组件实现一个可持久化的 PowerShell 终端句柄。
 */
private class JediTermTerminalHandle(
    private val terminalResult: Result<JediTermWidget>,
) : TerminalHandle {
    private val terminal = terminalResult.getOrNull()
    private var started = false

    override val component: Component? = terminal

    override val errorMessage: String
        get() = terminalResult.exceptionOrNull()?.message ?: "无法启动 PowerShell"

    override fun start() {
        if (!started) {
            terminal?.start()
            started = true
        }
    }

    override fun close() {
        terminal?.close()
    }

    override fun focusIfNeeded() {
        val terminal = component ?: return
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val terminalOwnsFocus = focusOwner != null &&
                (focusOwner == terminal || SwingUtilities.isDescendingFrom(focusOwner, terminal))
        if (shouldRequestTerminalFocus(terminalOwnsFocus)) {
            terminal.requestFocusInWindow()
        }
    }
}

/**
 * 在主工作区右侧承载可切换的交互式 PowerShell 终端标签页。
 */
@Composable
internal fun EmbeddedTerminalPanel(
    tabs: TerminalTabsState,
    sessions: TerminalSessionStore,
    onSelectTab: (Long) -> Unit,
    onAddTab: () -> Unit,
    onCloseTab: (Long) -> Unit,
    onCloseOtherTabs: (Long) -> Unit,
    onHidePanel: () -> Unit,
    focused: Boolean = true,
    onFocus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activeTab = tabs.tabs.firstOrNull { it.id == tabs.activeTabId }
    val activeSession = activeTab?.let { sessions.session(it.id) }
    val activeComponent = activeSession?.component
    val density = LocalDensity.current
    val contextMenuLabels = terminalTabContextMenuLabels()
    var terminalOrigin by remember { mutableStateOf(Offset.Zero) }
    var contextMenuTabId by remember { mutableStateOf<Long?>(null) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var addHovered by remember { mutableStateOf(false) }
    var hideHovered by remember { mutableStateOf(false) }
    val contextMenuMotion = rememberMenuGrowthMotion(
        expanded = contextMenuTabId != null,
        label = "terminal-context-menu",
    )

    LaunchedEffect(tabs.activeTabId) {
        sessions.focusActiveIfNeeded(tabs.activeTabId)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TerminalSurfaceBackground)
            .border(1.dp, AppLine, RoundedCornerShape(10.dp))
            .onPointerEvent(PointerEventType.Press) { onFocus() }
            .onGloballyPositioned { terminalOrigin = it.positionInRoot() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalSurfaceBackground)
                .padding(start = 4.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.tabs.forEach { tab ->
                    TerminalTabChip(
                        tab = tab,
                        selected = tab.id == tabs.activeTabId,
                        focused = focused,
                        onSelect = { onSelectTab(tab.id) },
                        onClose = { onCloseTab(tab.id) },
                        onOpenContextMenu = { position ->
                            contextMenuTabId = tab.id
                            contextMenuOffset = with(density) {
                                DpOffset(
                                    x = (position.x - terminalOrigin.x).toDp(),
                                    y = (position.y - terminalOrigin.y).toDp(),
                                )
                            }
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(TERMINAL_ADD_BUTTON_SIZE_DP.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(terminalAddButtonBackground(addHovered))
                        .onPointerEvent(PointerEventType.Enter) { addHovered = true }
                        .onPointerEvent(PointerEventType.Exit) { addHovered = false }
                        .clickable(onClick = onAddTab)
                        .semantics { contentDescription = "新建终端" },
                    contentAlignment = Alignment.Center,
                ) {
                    TerminalActionGlyph(
                        cross = false,
                        color = AppText,
                        glowAlpha = 0f,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(TERMINAL_HIDE_BUTTON_SIZE_DP.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(terminalAddButtonBackground(hideHovered))
                    .onPointerEvent(PointerEventType.Enter) { hideHovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hideHovered = false }
                    .clickable(onClick = onHidePanel)
                    .semantics { contentDescription = terminalPanelHideActionLabel() },
                contentAlignment = Alignment.Center,
            ) {
                TerminalActionGlyph(
                    cross = true,
                    color = AppMuted,
                    glowAlpha = 0f,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalSurfaceBackground),
        ) {
            if (activeComponent != null) {
                SwingPanel(
                    factory = { activeComponent },
                    modifier = Modifier.fillMaxSize(),
                    background = TerminalSurfaceBackground,
                    update = { synchronizeTerminalInteropBackground(it) },
                )
            } else {
                Text(
                    text = activeSession?.errorMessage ?: "无法启动 PowerShell",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                )
            }
        }
        DropdownMenu(
            expanded = contextMenuTabId != null,
            onDismissRequest = { contextMenuTabId = null },
            offset = contextMenuOffset,
            modifier = Modifier
                .padding(PopupMenuShadowInset)
                .width(152.dp)
                .graphicsLayer {
                    transformOrigin = menuGrowthTransformOrigin(MenuGrowthOrigin.Context)
                    scaleX = contextMenuMotion.scale
                    scaleY = contextMenuMotion.scale
                    alpha = contextMenuMotion.alpha
                    translationY = contextMenuMotion.translationYDp * density.density
                }
                .popupMenuSurface(),
            shape = PopupMenuShape,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = null,
        ) {
            TerminalContextMenuItem(
                text = contextMenuLabels[0],
                itemIndex = 0,
                onClick = {
                    contextMenuTabId = null
                    onAddTab()
                },
            )
            TerminalContextMenuItem(
                text = contextMenuLabels[1],
                itemIndex = 1,
                onClick = {
                    contextMenuTabId?.let(onCloseTab)
                    contextMenuTabId = null
                },
            )
            TerminalContextMenuItem(
                text = contextMenuLabels[2],
                itemIndex = 2,
                onClick = {
                    contextMenuTabId?.let(onCloseOtherTabs)
                    contextMenuTabId = null
                },
            )
        }
    }
}

/**
 * 返回终端标签右键菜单的固定操作文案。
 */
internal fun terminalTabContextMenuLabels(): List<String> = listOf("新建终端", "关闭当前终端", "关闭其他终端")

/**
 * 渲染一个可切换、可关闭并支持右键菜单的终端标签。
 */
@Composable
private fun TerminalTabChip(
    tab: TerminalTab,
    selected: Boolean,
    focused: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onOpenContextMenu: (Offset) -> Unit,
) {
    var tabOrigin by remember { mutableStateOf(Offset.Zero) }
    var tabHovered by remember { mutableStateOf(false) }
    var closeHovered by remember { mutableStateOf(false) }
    var closePressed by remember { mutableStateOf(false) }
    val closeVisible = selected || tabHovered
    Row(
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> TerminalTabActiveBackground
                    tabHovered -> TerminalTabHoverBackground
                    else -> Color.Transparent
                },
            )
            .border(
                width = 1.dp,
                color = terminalTabBorderColor(selected, focused),
                shape = RoundedCornerShape(6.dp),
            )
            .onGloballyPositioned { tabOrigin = it.positionInRoot() }
            .onPointerEvent(PointerEventType.Enter) { tabHovered = true }
            .onPointerEvent(PointerEventType.Exit) { tabHovered = false }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    onOpenContextMenu(tabOrigin + (event.changes.firstOrNull()?.position ?: Offset.Zero))
                }
            }
            .clickable(onClick = onSelect)
            .height(TERMINAL_TAB_HEIGHT_DP.dp)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RightRailGlyphIcon(
            glyph = RightRailGlyph.TERMINAL,
            tint = if (selected) AppText else AppMuted,
            glyphSize = 22.dp,
        )
        Text(
            text = tab.title,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(color = AppText),
        )
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(TERMINAL_CLOSE_BUTTON_SIZE_DP.dp)
                .graphicsLayer {
                    alpha = if (closeVisible) 1f else 0f
                    scaleX = if (closePressed) 0.94f else 1f
                    scaleY = if (closePressed) 0.94f else 1f
                }
                .clip(RoundedCornerShape(5.dp))
                .background(if (closeHovered) TerminalTabHoverBackground else Color.Transparent)
                .onPointerEvent(PointerEventType.Enter) { closeHovered = true }
                .onPointerEvent(PointerEventType.Exit) {
                    closeHovered = false
                    closePressed = false
                }
                .onPointerEvent(PointerEventType.Press) { closePressed = true }
                .onPointerEvent(PointerEventType.Release) { closePressed = false }
                .clickable(enabled = closeVisible, onClick = onClose)
                .semantics { contentDescription = "关闭${tab.title}" },
            contentAlignment = Alignment.Center,
        ) {
            TerminalActionGlyph(
                cross = true,
                color = if (closeHovered) AppText else AppMuted,
                glowAlpha = 0f,
            )
        }
    }
}

/**
 * 在操作按钮的几何中心绘制加号或关闭图标，避免文字基线造成视觉偏移。
 */
@Composable
private fun TerminalActionGlyph(
    cross: Boolean,
    color: Color,
    glowAlpha: Float,
) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val inset = size.minDimension * 0.1f
        val center = size.minDimension / 2f
        val strokeWidth = 2.dp.toPx()
        fun drawGlyph(lineColor: Color, lineWidth: Float) {
            if (cross) {
                drawLine(lineColor, Offset(inset, inset), Offset(size.width - inset, size.height - inset), lineWidth, StrokeCap.Round)
                drawLine(lineColor, Offset(size.width - inset, inset), Offset(inset, size.height - inset), lineWidth, StrokeCap.Round)
            } else {
                drawLine(lineColor, Offset(inset, center), Offset(size.width - inset, center), lineWidth, StrokeCap.Round)
                drawLine(lineColor, Offset(center, inset), Offset(center, size.height - inset), lineWidth, StrokeCap.Round)
            }
        }
        if (glowAlpha > 0f) drawGlyph(color.copy(alpha = glowAlpha), strokeWidth + 3.dp.toPx())
        drawGlyph(color, strokeWidth)
    }
}

/**
 * 渲染终端标签右键菜单中的一个操作。
 */
@Composable
private fun TerminalContextMenuItem(
    text: String,
    itemIndex: Int,
    onClick: () -> Unit,
) {
    RingDropdownMenuItem(
        text = text,
        selected = false,
        onClick = onClick,
        itemIndex = itemIndex,
        itemCount = 3,
        hoverBackground = PopupMenuSelectedBackground,
    )
}

/**
 * 将终端背景同步到 Swing 祖先链，避免互操作区域异步扩张时露出窗口默认亮色。
 */
internal fun synchronizeTerminalInteropBackground(component: Component) {
    val background = terminalInteropColors().background
    generateSequence(component) { current -> current.parent }.forEach { current ->
        current.background = background
        current.repaint()
    }
}

/** JVM 互操作层使用的终端 AWT 色值，确保 Swing 不读取 Compose state。 */
internal data class TerminalInteropColors(
    val background: AwtColor,
    val foreground: AwtColor,
    val scrollbarThumb: AwtColor,
)

/** 将动态终端 palette 映射为 JediTerm 与 Swing 可直接消费的颜色。 */
internal fun terminalInteropColors(palette: TerminalPalette = DesktopInteropPalette.terminal): TerminalInteropColors =
    TerminalInteropColors(
        background = palette.background.toAwtColor(),
        foreground = palette.foreground.toAwtColor(),
        scrollbarThumb = palette.scrollbarThumb.toAwtColor(),
    )

/** 转换 Compose 色值，避免各个 Swing 组件各自维护一份主题色。 */
private fun Color.toAwtColor(): AwtColor = AwtColor(
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
    (alpha * 255).roundToInt(),
)

private fun createPowerShellTerminal(workspacePath: String): JediTermWidget {
    val command = buildPowerShellCommand()
    val process = PtyProcessBuilder(command.toTypedArray())
        .setDirectory(workspacePath)
        .setEnvironment(System.getenv())
        .setConsole(false)
        .setUseWinConPty(true)
        .start()
    return AppJediTermWidget().apply {
        installSwingBorderCleanup(this)
        setTtyConnector(PowerShellTtyConnector(process, command))
    }
}

/**
 * 创建可跨标签页保留的 PowerShell 终端句柄。
 */
private fun createPowerShellHandle(workspacePath: String): TerminalHandle =
    JediTermTerminalHandle(runCatching { createPowerShellTerminal(workspacePath) })

/**
 * 清除现有 Swing 边框，并拦截 Look & Feel 或迟到子组件重新注入的边框。
 */
internal fun installSwingBorderCleanup(component: Component) {
    if (component is JComponent) {
        component.border = null
        component.addPropertyChangeListener("border") {
            if (component.border != null) component.border = null
        }
    }
    if (component is Container) {
        component.components.forEach(::installSwingBorderCleanup)
        component.addContainerListener(
            object : ContainerAdapter() {
                override fun componentAdded(event: ContainerEvent) {
                    installSwingBorderCleanup(event.child)
                }
            },
        )
    }
}

/**
 * 返回终端默认使用的字体。
 */
internal fun terminalFont(): Font = Font("Maple Mono NF CN SemiBold", Font.PLAIN, 14)

/**
 * 判断终端缓冲内容是否超过当前可见范围。
 */
internal fun shouldShowTerminalScrollbar(
    minimum: Int,
    maximum: Int,
    extent: Int,
): Boolean = maximum - minimum > extent

private object AppTerminalSettingsProvider : DefaultSettingsProvider() {
    override fun getDefaultForeground(): TerminalColor = terminalInteropColors().foreground.toTerminalColor()

    override fun getDefaultBackground(): TerminalColor = terminalInteropColors().background.toTerminalColor()

    /**
     * PowerShell 会显式输出 Windows 控制台的黑白 ANSI 色。将其默认前景和背景
     * 映射回当前主题，避免深色终端被 ANSI 白色背景覆盖。
     */
    override fun getTerminalColorPalette(): ColorPalette = AppTerminalColorPalette

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getDefaultStyle(): TextStyle = TextStyle(defaultForeground, defaultBackground)

    override fun getTerminalFont(): Font = terminalFont()

    override fun audibleBell(): Boolean = false
}

/**
 * 提供随桌面主题变化的 ANSI 基础色板；常规彩色输出保留 Windows 终端语义。
 */
private object AppTerminalColorPalette : ColorPalette() {
    override fun getForegroundByColorIndex(colorIndex: Int): TerminalRgbColor =
        terminalAnsiPaletteColor(colorIndex = colorIndex, foreground = true)

    override fun getBackgroundByColorIndex(colorIndex: Int): TerminalRgbColor =
        terminalAnsiPaletteColor(colorIndex = colorIndex, foreground = false)
}

/**
 * 返回 JediTerm ANSI 色索引的最终颜色，其中 PowerShell 的默认黑白色遵循应用主题。
 */
internal fun terminalAnsiPaletteColor(
    colorIndex: Int,
    foreground: Boolean,
    palette: TerminalPalette = DesktopInteropPalette.terminal,
): TerminalRgbColor {
    val terminalColors = terminalInteropColors(palette)
    val themeColor = if (foreground) terminalColors.foreground else terminalColors.background
    if (colorIndex in DEFAULT_TERMINAL_COLOR_INDICES) return themeColor.toTerminalRgbColor()
    val color = WINDOWS_TERMINAL_ANSI_COLORS.getOrElse(colorIndex) { WINDOWS_TERMINAL_ANSI_COLORS.first() }
    return TerminalRgbColor(color.red, color.green, color.blue)
}

/** PowerShell 默认使用的 ANSI 黑、白与亮白色需映射为当前终端的主色。 */
private val DEFAULT_TERMINAL_COLOR_INDICES = setOf(0, 7, 15)

/** Windows 控制台 ANSI 色，供非默认颜色输出（例如错误和警告）保持可辨识。 */
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

/** 将 AWT 颜色映射为 JediTerm core 颜色。 */
private fun AwtColor.toTerminalRgbColor(): TerminalRgbColor = TerminalRgbColor(red, green, blue)

/** 将 AWT 颜色映射为 JediTerm 使用的 RGB 颜色。 */
private fun AwtColor.toTerminalColor(): TerminalColor = TerminalColor.rgb(red, green, blue)

private class AppJediTermWidget : JediTermWidget(AppTerminalSettingsProvider) {

    override fun createScrollBar(): JScrollBar {
        val scrollBar = AppTerminalScrollBar().apply {
            isOpaque = false
            unitIncrement = 3
            setUI(AppTerminalScrollBarUi())
        }
        val modelListener = ChangeListener { updateTerminalScrollbarVisibility(scrollBar) }
        scrollBar.model.addChangeListener(modelListener)
        scrollBar.addPropertyChangeListener("model") { event ->
            (event.oldValue as? BoundedRangeModel)?.removeChangeListener(modelListener)
            (event.newValue as? BoundedRangeModel)?.addChangeListener(modelListener)
            updateTerminalScrollbarVisibility(scrollBar)
        }
        updateTerminalScrollbarVisibility(scrollBar)
        return scrollBar
    }
}

private class AppTerminalScrollBar : JScrollBar(VERTICAL) {
    override fun getPreferredSize(): Dimension =
        if (isVisible) Dimension(8, 0) else Dimension(0, 0)
}

private class AppTerminalScrollBarUi : BasicScrollBarUI() {

    override fun configureScrollBarColors() {
        trackColor = AwtColor(0, 0, 0, 0)
        thumbColor = terminalInteropColors().scrollbarThumb
    }

    override fun createDecreaseButton(orientation: Int): JButton = zeroSizeButton()

    override fun createIncreaseButton(orientation: Int): JButton = zeroSizeButton()

    override fun paintTrack(
        graphics: Graphics,
        component: JComponent,
        trackBounds: Rectangle,
    ) = Unit

    override fun paintThumb(
        graphics: Graphics,
        component: JComponent,
        thumbBounds: Rectangle,
    ) {
        if (!scrollbar.isEnabled || thumbBounds.isEmpty) return
        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics2D.color = terminalInteropColors().scrollbarThumb
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

    override fun getMinimumThumbSize(): Dimension = Dimension(5, 24)

    private fun zeroSizeButton(): JButton = JButton().apply {
        preferredSize = Dimension(0, 0)
        minimumSize = Dimension(0, 0)
        maximumSize = Dimension(0, 0)
        isOpaque = false
        isFocusable = false
        border = null
    }
}

private fun updateTerminalScrollbarVisibility(scrollBar: JScrollBar) {
    val update = Runnable {
        val model = scrollBar.model
        val visible = shouldShowTerminalScrollbar(
            minimum = model.minimum,
            maximum = model.maximum,
            extent = model.extent,
        )
        if (scrollBar.isVisible != visible) {
            scrollBar.isVisible = visible
            scrollBar.parent?.revalidate()
            scrollBar.parent?.repaint()
        }
    }
    if (SwingUtilities.isEventDispatchThread()) update.run() else SwingUtilities.invokeLater(update)
}

private class PowerShellTtyConnector(
    private val process: PtyProcess,
    command: List<String>,
) : ProcessTtyConnector(process, StandardCharsets.UTF_8, command) {

    override fun getName(): String = "PowerShell"

    override fun resize(termSize: TermSize) {
        process.setWinSize(WinSize(termSize.columns, termSize.rows))
    }
}
