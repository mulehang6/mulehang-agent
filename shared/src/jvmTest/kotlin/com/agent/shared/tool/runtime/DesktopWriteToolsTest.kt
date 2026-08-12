package com.agent.shared.tool.runtime

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 验证多文件补丁的路径归属、原子写入和审批等待期保护。 */
class DesktopWriteToolsTest {
    /** 单次补丁可创建、更新和删除多个工作区文件。 */
    @Test
    fun `batch patch persists all file operations`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        Files.writeString(workspace.resolve("change.txt"), "before")
        Files.writeString(workspace.resolve("delete.txt"), "old")
        val tools = DesktopReadWriteTools(DesktopFileToolSupport(workspace.toString()))

        val pending = tools.previewPatch(
            """
            *** Begin Patch
            *** Add File: created.txt
            +hello workspace
            *** Update File: change.txt
            @@
            -before
            +after
            *** Delete File: delete.txt
            *** End Patch
            """.trimIndent(),
        )
        val result = tools.applyPatch(pending)

        assertEquals("hello workspace", Files.readString(workspace.resolve("created.txt")))
        assertEquals("after", Files.readString(workspace.resolve("change.txt")))
        assertTrue(!Files.exists(workspace.resolve("delete.txt")))
        assertTrue(result.startsWith("PATCH_APPLIED"))
    }

    /** 外部绝对路径应保留在同一批次中并由上层升级为外部写审批。 */
    @Test
    fun `preview patch identifies external target`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        val external = Files.createTempFile("mulehang-external", ".txt")
        Files.writeString(external, "before")
        val tools = DesktopReadWriteTools(DesktopFileToolSupport(workspace.toString()))

        val pending = tools.previewPatch(
            """
            *** Begin Patch
            *** Update File: $external
            @@
            -before
            +after
            *** End Patch
            """.trimIndent(),
        )

        assertTrue(pending.containsExternalWrite)
        assertEquals("after", pending.files.single().nextContent)
    }

    /** 审批后任一文件变化都必须拒绝整个批次落盘。 */
    @Test
    fun `batch patch rejects changes while approval is pending`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        val target = workspace.resolve("notes.txt")
        Files.writeString(target, "before")
        val tools = DesktopReadWriteTools(DesktopFileToolSupport(workspace.toString()))
        val pending = tools.previewPatch(
            """
            *** Begin Patch
            *** Update File: notes.txt
            @@
            -before
            +after
            *** End Patch
            """.trimIndent(),
        )
        Files.writeString(target, "user update")

        val error = assertFailsWith<IllegalStateException> { tools.applyPatch(pending) }

        assertTrue(error.message.orEmpty().startsWith("PATCH_STALE"))
    }
}
