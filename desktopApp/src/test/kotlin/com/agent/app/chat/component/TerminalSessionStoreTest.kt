package com.agent.app.chat.component

import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.TerminalPalette
import com.agent.app.design.desktopPalette
import java.awt.Component
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * 验证终端会话库不会在标签切换或局部关闭时误释放其他会话。
 */
class TerminalSessionStoreTest {

    /**
     * 关闭一个终端页时，其他页的会话必须继续保留。
     */
    @Test
    fun `should close only the terminal session whose tab was closed`() {
        val first = FakeTerminalHandle()
        val second = FakeTerminalHandle()
        val store = TerminalSessionStore(darkTerminalPalette()) { path, _, _, _ -> if (path == "C:/one") first else second }

        store.create(TerminalTab(1, "C:/one"))
        store.create(TerminalTab(2, "C:/two"))
        store.close(1)

        assertEquals(1, first.closeCalls)
        assertEquals(0, second.closeCalls)
        assertSame(second, store.session(2))
    }

    /**
     * 右侧终端图标的聚焦请求只应交给当前活动会话。
     */
    @Test
    fun `should delegate focus to the active terminal session`() {
        val handle = FakeTerminalHandle()
        val store = TerminalSessionStore(darkTerminalPalette()) { _, _, _, _ -> handle }
        store.create(TerminalTab(1, "C:/workspace"))

        store.focusActiveIfNeeded(1)

        assertEquals(1, handle.focusCalls)
    }

    /**
     * Compose 宿主释放时，每个未关闭会话仅能被关闭一次。
     */
    @Test
    fun `should close each remaining terminal exactly once when store is disposed`() {
        val first = FakeTerminalHandle()
        val second = FakeTerminalHandle()
        val store = TerminalSessionStore(darkTerminalPalette()) { path, _, _, _ -> if (path == "C:/one") first else second }
        store.create(TerminalTab(1, "C:/one"))
        store.create(TerminalTab(2, "C:/two"))

        store.closeAll()
        store.closeAll()

        assertEquals(1, first.closeCalls)
        assertEquals(1, second.closeCalls)
    }

    /**
     * 关闭其他终端时必须保留右击标签对应的会话。
     */
    @Test
    fun `should close every terminal session except retained tab`() {
        val first = FakeTerminalHandle()
        val second = FakeTerminalHandle()
        val store = TerminalSessionStore(darkTerminalPalette()) { path, _, _, _ -> if (path == "C:/one") first else second }
        store.create(TerminalTab(1, "C:/one"))
        store.create(TerminalTab(2, "C:/two"))

        store.closeAllExcept(2)

        assertEquals(1, first.closeCalls)
        assertEquals(0, second.closeCalls)
        assertSame(second, store.session(2))
    }

    /** 主题更新必须覆盖存量会话，并成为后续新会话的初始色板。 */
    @Test
    fun `should update existing and future terminal sessions`() {
        val handles = mutableListOf<FakeTerminalHandle>()
        val initialPalettes = mutableListOf<TerminalPalette>()
        val store = TerminalSessionStore(darkTerminalPalette()) { _, palette, _, _ ->
            initialPalettes += palette
            FakeTerminalHandle().also(handles::add)
        }
        store.create(TerminalTab(1, "C:/one"))

        val lightPalette = desktopPalette(DesktopThemeMode.LIGHT).terminal
        store.updateTheme(lightPalette)
        store.create(TerminalTab(2, "C:/two"))

        assertEquals(lightPalette, handles.first().themes.single())
        assertEquals(lightPalette, initialPalettes.last())
    }

    /**
     * 字体或缩放更新应覆盖存量会话，并让新会话继承外观而不触发关闭流程。
     */
    @Test
    fun `should update existing and future terminal appearance without closing sessions`() {
        val handles = mutableListOf<FakeTerminalHandle>()
        val initialAppearances = mutableListOf<TerminalAppearance>()
        val store = TerminalSessionStore(darkTerminalPalette()) { _, _, appearance, _ ->
            initialAppearances += appearance
            FakeTerminalHandle().also(handles::add)
        }
        store.create(TerminalTab(1, "C:/one"))

        val updatedAppearance = TerminalAppearance(codeFontFamily = "Cascadia Mono", scalePercent = 130)
        store.updateAppearance(updatedAppearance)
        store.create(TerminalTab(2, "C:/two"))

        assertEquals(updatedAppearance, handles.first().appearances.single())
        assertEquals(updatedAppearance, initialAppearances.last())
        assertEquals(0, handles.first().closeCalls)
    }

    /**
     * 各标签必须持有不同的 Swing 边界，供活动标签通过组合键重新挂载对应组件。
     */
    @Test
    fun `should keep independent swing components for different terminal tabs`() {
        val first = FakeTerminalHandle(component = JPanel())
        val second = FakeTerminalHandle(component = JPanel())
        val store = TerminalSessionStore(darkTerminalPalette()) { path, _, _, _ ->
            if (path == "C:/one") first else second
        }

        store.create(TerminalTab(1, "C:/one"))
        store.create(TerminalTab(2, "C:/two"))

        assertSame(first, store.session(1))
        assertSame(second, store.session(2))
        assertNotSame(first.component, second.component)
    }

    /**
     * 修改默认 Shell 只能影响后续新会话，不能重建或关闭已打开的终端。
     */
    @Test
    fun `should apply updated shell command only to future terminal sessions`() {
        val launchCommands = mutableListOf<List<String>>()
        val handles = mutableListOf<FakeTerminalHandle>()
        val store = TerminalSessionStore(
            initialPalette = darkTerminalPalette(),
            initialLaunchCommand = listOf("powershell.exe", "-NoLogo"),
        ) { _, _, _, launchCommand ->
            launchCommands += launchCommand
            FakeTerminalHandle().also(handles::add)
        }

        store.create(TerminalTab(1, "C:/one"))
        store.updateLaunchCommand(listOf("cmd.exe"))
        store.create(TerminalTab(2, "C:/two"))

        assertEquals(listOf("powershell.exe", "-NoLogo"), launchCommands.first())
        assertEquals(listOf("cmd.exe"), launchCommands.last())
        assertEquals(0, handles.first().closeCalls)
    }
}

/**
 * 仅替代进程与 Swing 边界的终端会话测试替身。
 */
private class FakeTerminalHandle(
    override val component: Component? = null,
) : TerminalHandle {
    var closeCalls = 0
    var focusCalls = 0
    val themes = mutableListOf<TerminalPalette>()
    val appearances = mutableListOf<TerminalAppearance>()

    override val errorMessage: String = "error"

    override fun start() = Unit

    override fun close() {
        closeCalls += 1
    }

    override fun focusIfNeeded() {
        focusCalls += 1
    }

    /** 记录会话收到的主题更新。 */
    override fun updateTheme(palette: TerminalPalette) {
        themes += palette
    }

    /** 记录会话收到的字体和缩放更新。 */
    override fun updateAppearance(appearance: TerminalAppearance) {
        appearances += appearance
    }
}

/** 返回测试使用的默认深色终端色板。 */
private fun darkTerminalPalette(): TerminalPalette = desktopPalette(DesktopThemeMode.DARK).terminal
