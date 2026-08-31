package com.agent.app.chat.component

import javax.swing.JPanel
import javax.swing.SwingUtilities
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 验证稳定的 Swing 宿主会按活动终端标签替换可见组件。 */
class TerminalSwingHostTest {

    /** 切换标签时，宿主必须卸载旧组件并只保留新标签对应的组件。 */
    @Test
    fun `should replace the mounted terminal component when the active tab changes`() = onSwingEventThread {
        val firstTerminal = JPanel()
        val secondTerminal = JPanel()
        val host = TerminalSwingHost(initialTabId = 1, initialComponent = firstTerminal)

        host.mount(tabId = 2, component = secondTerminal)

        assertEquals(1, host.componentCount)
        assertSame(secondTerminal, host.components.single())
        assertNull(firstTerminal.parent)
    }

    /** 同一标签再次组合时不得重复添加它已经挂载的终端组件。 */
    @Test
    fun `should keep one mounted component when the active tab is recomposed`() = onSwingEventThread {
        val terminal = JPanel()
        val host = TerminalSwingHost(initialTabId = 1, initialComponent = terminal)

        host.mount(tabId = 1, component = terminal)

        assertEquals(1, host.componentCount)
        assertSame(terminal, host.components.single())
    }

    /** 首次工厂调用前就必须存在终端子组件，供 SwingPanel 用其首选尺寸完成测量。 */
    @Test
    fun `should expose the initial terminal preferred size before SwingPanel measures the host`() = onSwingEventThread {
        val terminal = JPanel().apply { preferredSize = Dimension(640, 360) }
        val host = TerminalSwingHost(initialTabId = 1, initialComponent = terminal)

        assertSame(terminal, host.components.single())
        assertEquals(Dimension(640, 360), host.preferredSize)
    }
}

/** 在 Swing EDT 上执行 [action]，使宿主行为与 Compose SwingPanel 的更新线程一致。 */
private fun onSwingEventThread(action: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) {
        action()
    } else {
        SwingUtilities.invokeAndWait(action)
    }
}
