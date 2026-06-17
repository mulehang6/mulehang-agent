package com.agent.shared.agent

import com.agent.shared.state.PermissionPreset
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证桌面只读工具的基础行为。
 */
class DesktopReadOnlyToolsTest {
    /**
     * `read_file` 应允许读取工作区外文件。
     */
    @Test
    fun `read file should return content outside workspace`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        val external = Files.createTempFile("mulehang-external", ".txt")
        Files.writeString(external, "hello from outside")
        val toolSet = DesktopToolSet(
            workspacePath = workspace.toString(),
            permissionPreset = PermissionPreset.DEFAULT,
            interactionBridge = fakeBridge(),
        )

        val result = toolSet.read_file(external.toString())

        assertTrue(result.contains("hello from outside"))
    }

    /**
     * `list_dir` 应返回排序后的目录项。
     */
    @Test
    fun `list dir should return sorted entries`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        Files.createFile(workspace.resolve("b.txt"))
        Files.createFile(workspace.resolve("a.txt"))
        val toolSet = DesktopToolSet(
            workspacePath = workspace.toString(),
            permissionPreset = PermissionPreset.DEFAULT,
            interactionBridge = fakeBridge(),
        )

        val result = toolSet.list_dir(workspace.toString())

        assertEquals(listOf("a.txt", "b.txt"), result.lines().filter { it.isNotBlank() })
    }
}
