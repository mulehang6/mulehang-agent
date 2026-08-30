package com.agent.app.chat.component

import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.TerminalPalette
import com.agent.app.design.desktopPalette
import java.awt.Component
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val store = TerminalSessionStore(darkTerminalPalette()) { path, _, _ -> if (path == "C:/one") first else second }

        store.create(TerminalTab(1, "C:/one", "终端 1"))
        store.create(TerminalTab(2, "C:/two", "终端 2"))
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
        val store = TerminalSessionStore(darkTerminalPalette()) { _, _, _ -> handle }
        store.create(TerminalTab(1, "C:/workspace", "终端 1"))

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
        val store = TerminalSessionStore(darkTerminalPalette()) { path, _, _ -> if (path == "C:/one") first else second }
        store.create(TerminalTab(1, "C:/one", "终端 1"))
        store.create(TerminalTab(2, "C:/two", "终端 2"))

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
        val store = TerminalSessionStore(darkTerminalPalette()) { path, _, _ -> if (path == "C:/one") first else second }
        store.create(TerminalTab(1, "C:/one", "终端 1"))
        store.create(TerminalTab(2, "C:/two", "终端 2"))

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
        val store = TerminalSessionStore(darkTerminalPalette()) { _, palette, _ ->
            initialPalettes += palette
            FakeTerminalHandle().also(handles::add)
        }
        store.create(TerminalTab(1, "C:/one", "终端 1"))

        val lightPalette = desktopPalette(DesktopThemeMode.LIGHT).terminal
        store.updateTheme(lightPalette)
        store.create(TerminalTab(2, "C:/two", "终端 2"))

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
        val store = TerminalSessionStore(darkTerminalPalette()) { _, _, appearance ->
            initialAppearances += appearance
            FakeTerminalHandle().also(handles::add)
        }
        store.create(TerminalTab(1, "C:/one", "终端 1"))

        val updatedAppearance = TerminalAppearance(codeFontFamily = "Cascadia Mono", scalePercent = 130)
        store.updateAppearance(updatedAppearance)
        store.create(TerminalTab(2, "C:/two", "终端 2"))

        assertEquals(updatedAppearance, handles.first().appearances.single())
        assertEquals(updatedAppearance, initialAppearances.last())
        assertEquals(0, handles.first().closeCalls)
    }
}

/**
 * 仅替代进程与 Swing 边界的终端会话测试替身。
 */
private class FakeTerminalHandle : TerminalHandle {
    var closeCalls = 0
    var focusCalls = 0
    val themes = mutableListOf<TerminalPalette>()
    val appearances = mutableListOf<TerminalAppearance>()

    override val component: Component? = null
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
