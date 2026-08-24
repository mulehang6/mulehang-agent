package com.agent.shared.session

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 验证按项目记忆的 UI 状态存储。
 */
class DesktopUiStateStoreTest {

    /**
     * UI 状态应按项目路径保存和读取最近选择的 profile。
     */
    @Test
    fun `should remember last selected profile for each project`() {
        val root = Files.createTempDirectory("mulehang-ui-state-test")
        val store = DesktopUiStateStore(root.resolve(".mulehang/ui-state.json"))

        store.saveSelectedProfile(
            projectPath = "D:/workspace/demo",
            profileId = "openai-main",
        )

        val remembered = store.loadSelectedProfile("D:/workspace/demo")

        assertEquals("openai-main", remembered)
    }

    /**
     * UI 状态应保存和读取最近使用的工作区。
     */
    @Test
    fun `should remember last selected workspace`() {
        val root = Files.createTempDirectory("mulehang-ui-workspace-state-test")
        val store = DesktopUiStateStore(root.resolve(".mulehang/ui-state.json"))

        store.saveRecentWorkspace("D:/workspace/demo")

        assertEquals("D:/workspace/demo", store.loadRecentWorkspace())
    }

    /** 旧强调色字段应可读取，并在下一次状态写入时从文档中自然移除。 */
    @Test
    fun `should discard legacy accent color after next write`() {
        val root = Files.createTempDirectory("mulehang-ui-legacy-accent-test")
        val statePath = root.resolve(".mulehang/ui-state.json")
        Files.createDirectories(statePath.parent)
        Files.writeString(
            statePath,
            """{"themeMode":"dark","accentColor":"teal"}""",
        )
        val store = DesktopUiStateStore(statePath)

        assertEquals("dark", store.loadThemeMode())
        store.saveThemeMode("light")

        assertFalse(Files.readString(statePath).contains("accentColor"))
        assertEquals("light", store.loadThemeMode())
    }

    /** 旧 Liquid Glass 字段应由 ignoreUnknownKeys 忽略，并在下次写入时自然移除。 */
    @Test
    fun `should ignore legacy liquid glass state`() {
        val root = Files.createTempDirectory("mulehang-ui-liquid-glass-test")
        val statePath = root.resolve(".mulehang/ui-state.json")
        Files.createDirectories(statePath.parent)
        Files.writeString(
            statePath,
            """{"themeMode":"dark","liquidGlassEnabled":true}""",
        )
        val store = DesktopUiStateStore(statePath)

        assertEquals("dark", store.loadThemeMode())
        store.saveThemeMode("light")

        assertEquals("light", store.loadThemeMode())
        assertFalse(Files.readString(statePath).contains("liquidGlassEnabled"))
    }
}
