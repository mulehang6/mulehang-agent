package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppText
import com.agent.app.platform.buildPowerShellCommand
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import java.awt.Dimension
import java.awt.Container
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.Color as AwtColor
import java.nio.charset.StandardCharsets
import javax.swing.BoundedRangeModel
import javax.swing.JButton
import javax.swing.JScrollBar
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import javax.swing.plaf.basic.BasicScrollBarUI

/**
 * 在主工作区底部承载当前目录的交互式 PowerShell 终端。
 */
@Composable
internal fun EmbeddedTerminalPanel(
    workspacePath: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminalResult = remember(workspacePath) {
        runCatching { createPowerShellTerminal(workspacePath) }
    }
    val terminal = terminalResult.getOrNull()

    DisposableEffect(terminal) {
        terminal?.start()
        onDispose { terminal?.close() }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppPanelBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppPanelBackground)
                .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "终端",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge.copy(color = AppText),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onClose)
                    .semantics { contentDescription = "关闭终端" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleMedium.copy(color = AppMuted),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF17181A)),
        ) {
            if (terminal != null) {
                SwingPanel(
                    factory = { terminal },
                    modifier = Modifier.fillMaxSize(),
                    background = Color(0xFF17181A),
                    update = { synchronizeTerminalInteropBackground(it) },
                )
            } else {
                Text(
                    text = terminalResult.exceptionOrNull()?.message ?: "无法启动 PowerShell",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                )
            }
        }
    }
}

/**
 * 将终端背景同步到 Swing 祖先链，避免互操作区域异步扩张时露出窗口默认亮色。
 */
internal fun synchronizeTerminalInteropBackground(component: java.awt.Component) {
    val background = AwtColor(23, 24, 26)
    generateSequence(component) { current -> current.parent }.forEach { current ->
        current.background = background
        current.repaint()
    }
}

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
 * 清除现有 Swing 边框，并拦截 Look & Feel 或迟到子组件重新注入的边框。
 */
internal fun installSwingBorderCleanup(component: java.awt.Component) {
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
    override fun getDefaultForeground(): TerminalColor = TerminalColor.rgb(230, 232, 236)

    override fun getDefaultBackground(): TerminalColor = TerminalColor.rgb(23, 24, 26)

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getDefaultStyle(): TextStyle = TextStyle(defaultForeground, defaultBackground)

    override fun getTerminalFont(): Font = terminalFont()

    override fun audibleBell(): Boolean = false
}

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
        thumbColor = AwtColor(75, 77, 82)
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
            graphics2D.color = thumbColor
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
