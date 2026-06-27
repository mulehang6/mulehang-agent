package com.agent.shared.state

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
