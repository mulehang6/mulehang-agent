package com.agent.shared.tool.runtime

import java.nio.file.Files
import kotlin.test.Test
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
     * 外部写入需要被精确标识，供上层审批而非在路径层静默拒绝。
     */
    @Test
    fun `write should identify file outside workspace`() {
        val workspace = Files.createTempDirectory("mulehang-workspace")
        val external = Files.createTempFile("mulehang-external", ".txt")
        val support = DesktopFileToolSupport(workspace.toString())

        assertTrue(!support.resolveForWrite(external.toString()).isInsideWorkspace)
    }
}
