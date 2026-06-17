package com.agent.shared.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 glob 文件搜索工具。
 */
class DesktopGlobToolTest {
    /**
     * glob 结果应遵守请求预算上限。
     */
    @Test
    fun `glob should cap results at requested budget`() {
        val root = Files.createTempDirectory("mulehang-glob")
        Files.createDirectories(root.resolve("nested"))
        Files.writeString(root.resolve("a.kt"), "a")
        Files.writeString(root.resolve("b.kt"), "b")
        Files.writeString(root.resolve("nested").resolve("c.kt"), "c")

        val result = DesktopGlobTool().execute(
            DesktopGlobTool.Args(
                pattern = "**/*.kt",
                path = root.toString(),
                maxResults = 2,
            ),
        )

        assertEquals(2, result.lines().filter { it.isNotBlank() }.size)
    }

    /**
     * 没有路径分隔符的 glob 应按文件名匹配整个树。
     */
    @Test
    fun `glob should treat bare pattern as recursive file match`() {
        val root = Files.createTempDirectory("mulehang-glob")
        Files.createDirectories(root.resolve("nested"))
        val target = root.resolve("nested").resolve("match.md")
        Files.writeString(target, "x")

        val result = DesktopGlobTool().execute(
            DesktopGlobTool.Args(
                pattern = "*.md",
                path = root.toString(),
                maxResults = 10,
            ),
        )

        assertTrue(result.lines().any { it.endsWith("match.md") })
    }
}
