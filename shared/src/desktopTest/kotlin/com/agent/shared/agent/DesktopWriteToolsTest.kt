package com.agent.shared.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 验证桌面写入工具的工作区边界和替换行为。
 */
class DesktopWriteToolsTest {
    /**
     * 工作区外写入必须被拒绝。
     */
    @Test
    fun `write file should reject workspace external path`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        val external = Files.createTempFile("mulehang-external", ".txt")
        val tools = DesktopReadWriteTools(DesktopFileToolSupport(workspace.toString()))

        val error = assertFailsWith<IllegalStateException> {
            tools.writeFile(
                path = external.toString(),
                content = "x",
            )
        }

        assertTrue(error.message.orEmpty().contains("工作区"))
    }

    /**
     * 工作区内整体写入应成功落盘。
     */
    @Test
    fun `write file should persist content inside workspace`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        val target = workspace.resolve("notes.txt")
        val tools = DesktopReadWriteTools(DesktopFileToolSupport(workspace.toString()))

        tools.writeFile(
            path = target.toString(),
            content = "hello workspace",
        )

        assertEquals("hello workspace", Files.readString(target))
    }

    /**
     * 定点编辑只应替换首个命中片段。
     */
    @Test
    fun `edit file should replace first matched text`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        val target = workspace.resolve("notes.txt")
        Files.writeString(target, "alpha beta beta")
        val tools = DesktopReadWriteTools(DesktopFileToolSupport(workspace.toString()))

        tools.editFile(
            path = target.toString(),
            oldText = "beta",
            newText = "gamma",
        )

        assertEquals("alpha gamma beta", Files.readString(target))
    }
}
