package com.agent.shared.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证桌面文件工具的路径边界。
 */
class DesktopFileToolSupportTest {
    /**
     * 只读工具允许读取工作区外文件。
     */
    @Test
    fun `read should allow file outside workspace`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        val external = Files.createTempFile("mulehang-external", ".txt")
        val support = DesktopFileToolSupport(workspace.toString())

        assertTrue(support.canRead(external.toString()))
    }

    /**
     * 写入工具必须拒绝工作区外路径。
     */
    @Test
    fun `write should reject file outside workspace`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        val external = Files.createTempFile("mulehang-external", ".txt")
        val support = DesktopFileToolSupport(workspace.toString())

        assertFalse(support.canWrite(external.toString()))
    }
}
