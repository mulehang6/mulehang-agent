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
import javax.swing.BoundedRangeModel
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** 表示 Compose 终端面板所需的持久 Swing 终端边界。 */
internal interface TerminalHandle {
    /** 返回可交给 SwingPanel 承载的终端组件；创建失败时为 null。 */
    val component: Component?

    /**
     * 返回终端历史的实际滚动模型；不支持滚动的测试或失败句柄保持为 null。
     *
     * 可视滚动条由 Compose/Jewel 侧持有，因此这里不暴露 Swing 的 [javax.swing.JScrollBar]。
     */
    val verticalScrollModel: BoundedRangeModel?
        get() = null

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

    /** 在不重建终端进程的前提下应用新的代码字体和缩放。 */
    fun updateAppearance(appearance: TerminalAppearance)
}

/** 保存各终端标签页的进程句柄，避免切换标签页时销毁后台会话。 */
internal class TerminalSessionStore(
    initialPalette: TerminalPalette,
    initialAppearance: TerminalAppearance = TerminalAppearance(),
    initialLaunchCommand: List<String> = buildPowerShellCommand(),
    private val terminalFactory: (String, TerminalPalette, TerminalAppearance, List<String>) -> TerminalHandle =
        ::createTerminalHandle,
) {
    private val sessions = linkedMapOf<Long, TerminalHandle>()
    private var currentPalette = initialPalette
    private var currentAppearance = initialAppearance
    private var currentLaunchCommand = initialLaunchCommand.toList()

    /** 为 [tab] 创建并启动一次终端会话。 */
    fun create(tab: TerminalTab) {
        sessions.getOrPut(tab.id) {
            terminalFactory(tab.workspacePath, currentPalette, currentAppearance, currentLaunchCommand)
        }.start()
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

    /** 同步更新存量会话，并让后续新会话继承最新代码字体和缩放。 */
    fun updateAppearance(appearance: TerminalAppearance) {
        if (appearance == currentAppearance) return
        currentAppearance = appearance
        sessions.values.forEach { it.updateAppearance(appearance) }
    }

    /**
     * 更新后续新建终端的启动命令；已创建的终端保留各自正在运行的进程。
     */
    fun updateLaunchCommand(command: List<String>) {
        val normalizedCommand = command.toList()
        if (normalizedCommand == currentLaunchCommand) return
        currentLaunchCommand = normalizedCommand
    }
}

/** 用 JediTerm 组件实现一个可持久化的 Windows 终端句柄。 */
private class JediTermTerminalHandle(
    private val terminalResult: Result<ThemedJediTermWidget>,
    private val themeState: TerminalThemeState,
    private val appearanceState: TerminalAppearanceState,
) : TerminalHandle {
    private val terminal = terminalResult.getOrNull()
    private var started = false

    override val component: Component? = terminal

    /** 将 JediTerm 的历史滚动模型交给 Compose 侧的 Jewel 滚动条同步。 */
    override val verticalScrollModel: BoundedRangeModel?
        get() = terminal?.verticalScrollModel()

    override val errorMessage: String
        get() = terminalResult.exceptionOrNull()?.message ?: "无法启动终端"

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

    /** 更新动态外观状态并走 JediTerm 的非破坏性字体重建路径。 */
    override fun updateAppearance(appearance: TerminalAppearance) {
        appearanceState.update(appearance)
        terminal?.refreshFontAndResize()
    }
}

/** 创建带独立主题状态和指定启动命令的终端组件。 */
private fun createTerminal(
    workspacePath: String,
    themeState: TerminalThemeState,
    appearanceState: TerminalAppearanceState,
    launchCommand: List<String>,
): ThemedJediTermWidget {
    val process = PtyProcessBuilder(launchCommand.toTypedArray())
        .setDirectory(workspacePath)
        .setEnvironment(System.getenv())
        .setConsole(false)
        .setUseWinConPty(true)
        .start()
    return ThemedJediTermWidget(themeState, appearanceState).apply {
        installSwingBorderCleanup(this)
        setTtyConnector(WindowsPtyTtyConnector(process, launchCommand))
    }
}

/** 创建可跨标签页保留、且使用指定 Shell 命令的终端句柄。 */
private fun createTerminalHandle(
    workspacePath: String,
    palette: TerminalPalette,
    appearance: TerminalAppearance,
    launchCommand: List<String>,
): TerminalHandle {
    val themeState = TerminalThemeState(palette)
    val appearanceState = TerminalAppearanceState(appearance)
    return JediTermTerminalHandle(
        terminalResult = runCatching { createTerminal(workspacePath, themeState, appearanceState, launchCommand) },
        themeState = themeState,
        appearanceState = appearanceState,
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
private class WindowsPtyTtyConnector(
    private val process: PtyProcess,
    private val command: List<String>,
) : ProcessTtyConnector(process, StandardCharsets.UTF_8, command) {
    /** 返回终端标签使用的进程名称。 */
    override fun getName(): String = command.firstOrNull()
        ?.substringAfterLast('\\')
        ?.substringAfterLast('/')
        ?.ifBlank { null }
        ?: "Terminal"

    /** 将 JediTerm 网格尺寸同步给 Windows PTY。 */
    override fun resize(termSize: TermSize) {
        process.setWinSize(WinSize(termSize.columns, termSize.rows))
    }
}
