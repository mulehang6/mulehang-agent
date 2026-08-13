package com.agent.app.chat.component

import com.agent.app.design.TerminalPalette
import com.agent.app.platform.buildPowerShellCommand
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import java.awt.Component
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.nio.charset.StandardCharsets
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** 表示 Compose 终端面板所需的持久 Swing 终端边界。 */
internal interface TerminalHandle {
    /** 返回可交给 SwingPanel 承载的终端组件；创建失败时为 null。 */
    val component: Component?

    /** 返回终端创建失败时应展示的消息。 */
    val errorMessage: String

    /** 启动底层终端进程。 */
    fun start()

    /** 释放底层终端进程。 */
    fun close()

    /** 仅在焦点不属于终端时恢复终端焦点。 */
    fun focusIfNeeded()

    /** 在不重建终端进程的前提下应用新的会话色板。 */
    fun updateTheme(palette: TerminalPalette)
}

/** 保存各终端标签页的进程句柄，避免切换标签页时销毁后台会话。 */
internal class TerminalSessionStore(
    initialPalette: TerminalPalette,
    private val terminalFactory: (String, TerminalPalette) -> TerminalHandle = ::createPowerShellHandle,
) {
    private val sessions = linkedMapOf<Long, TerminalHandle>()
    private var currentPalette = initialPalette

    /** 为 [tab] 创建并启动一次终端会话。 */
    fun create(tab: TerminalTab) {
        sessions.getOrPut(tab.id) { terminalFactory(tab.workspacePath, currentPalette) }.start()
    }

    /** 返回 [tabId] 对应的持久终端会话。 */
    fun session(tabId: Long): TerminalHandle? = sessions[tabId]

    /** 释放并移除 [tabId] 对应的终端会话。 */
    fun close(tabId: Long) {
        sessions.remove(tabId)?.close()
    }

    /** 释放窗口关闭时仍存活的所有终端会话。 */
    fun closeAll() {
        sessions.values.toList().forEach(TerminalHandle::close)
        sessions.clear()
    }

    /** 释放除 [keptTabId] 之外的所有终端会话。 */
    fun closeAllExcept(keptTabId: Long) {
        sessions.keys.filter { it != keptTabId }.forEach(::close)
    }

    /** 将焦点请求委派给当前活动的终端会话。 */
    fun focusActiveIfNeeded(activeTabId: Long?) {
        activeTabId?.let(sessions::get)?.focusIfNeeded()
    }

    /** 同步更新存量会话，并让后续新会话继承最新色板。 */
    fun updateTheme(palette: TerminalPalette) {
        if (palette == currentPalette) return
        currentPalette = palette
        sessions.values.forEach { it.updateTheme(palette) }
    }
}

/** 用 JediTerm 组件实现一个可持久化的 PowerShell 终端句柄。 */
private class JediTermTerminalHandle(
    private val terminalResult: Result<ThemedJediTermWidget>,
    private val themeState: TerminalThemeState,
) : TerminalHandle {
    private val terminal = terminalResult.getOrNull()
    private var started = false

    override val component: Component? = terminal

    override val errorMessage: String
        get() = terminalResult.exceptionOrNull()?.message ?: "无法启动 PowerShell"

    /** 仅首次调用时启动 PTY。 */
    override fun start() {
        if (!started) {
            terminal?.start()
            started = true
        }
    }

    /** 关闭 JediTerm 与其底层 PTY。 */
    override fun close() {
        terminal?.close()
    }

    /** 在终端未持有焦点时恢复输入焦点。 */
    override fun focusIfNeeded() {
        val terminal = component ?: return
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val terminalOwnsFocus = focusOwner != null &&
                (focusOwner == terminal || SwingUtilities.isDescendingFrom(focusOwner, terminal))
        if (shouldRequestTerminalFocus(terminalOwnsFocus)) terminal.requestFocusInWindow()
    }

    /** 更新动态颜色状态，并在 Swing EDT 上刷新现有组件树。 */
    override fun updateTheme(palette: TerminalPalette) {
        themeState.update(palette)
        terminal?.let { refreshTerminalSwingTheme(it, palette) }
    }
}

/** 创建带独立主题状态的 PowerShell 终端组件。 */
private fun createPowerShellTerminal(
    workspacePath: String,
    themeState: TerminalThemeState,
): ThemedJediTermWidget {
    val command = buildPowerShellCommand()
    val process = PtyProcessBuilder(command.toTypedArray())
        .setDirectory(workspacePath)
        .setEnvironment(System.getenv())
        .setConsole(false)
        .setUseWinConPty(true)
        .start()
    return ThemedJediTermWidget(themeState).apply {
        installSwingBorderCleanup(this)
        setTtyConnector(PowerShellTtyConnector(process, command))
    }
}

/** 创建可跨标签页保留的 PowerShell 终端句柄。 */
private fun createPowerShellHandle(workspacePath: String, palette: TerminalPalette): TerminalHandle {
    val themeState = TerminalThemeState(palette)
    return JediTermTerminalHandle(
        terminalResult = runCatching { createPowerShellTerminal(workspacePath, themeState) },
        themeState = themeState,
    )
}

/** 清除现有 Swing 边框，并拦截迟到子组件重新注入的边框。 */
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
                /** 清理运行中新增的 Swing 子组件边框。 */
                override fun componentAdded(event: ContainerEvent) {
                    installSwingBorderCleanup(event.child)
                }
            },
        )
    }
}

/** 将 PTY4J 的 Windows 进程适配为 JediTerm 连接器。 */
private class PowerShellTtyConnector(
    private val process: PtyProcess,
    command: List<String>,
) : ProcessTtyConnector(process, StandardCharsets.UTF_8, command) {
    /** 返回终端标签使用的进程名称。 */
    override fun getName(): String = "PowerShell"

    /** 将 JediTerm 网格尺寸同步给 Windows PTY。 */
    override fun resize(termSize: TermSize) {
        process.setWinSize(WinSize(termSize.columns, termSize.rows))
    }
}
