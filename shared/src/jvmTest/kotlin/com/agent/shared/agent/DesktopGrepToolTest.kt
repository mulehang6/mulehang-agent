package com.agent.shared.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证 grep 代码搜索工具。
 */
class DesktopGrepToolTest {
    /**
     * 命中过多时应标记 partial，提示 agent 缩小范围。
     */
    @Test
    fun `grep should mark partial when output exceeds max results`() {
        val root = Files.createTempDirectory("mulehang-grep")
        Files.writeString(root.resolve("a.txt"), "targetSymbol\n")
        Files.writeString(root.resolve("b.txt"), "targetSymbol\n")

        val result = DesktopGrepTool().execute(
            DesktopGrepTool.Args(
                pattern = "targetSymbol",
                path = root.toString(),
                maxResults = 1,
            ),
        )

        assertTrue(result.contains("partial=true"))
    }

    /**
     * 请求上下文行时，输出中应包含前后文标记。
     */
    @Test
    fun `grep should include context lines when requested`() {
        val root = Files.createTempDirectory("mulehang-grep")
        Files.writeString(
            root.resolve("sample.txt"),
            listOf("before", "targetSymbol", "after").joinToString(separator = System.lineSeparator()),
        )

        val result = DesktopGrepTool().execute(
            DesktopGrepTool.Args(
                pattern = "targetSymbol",
                path = root.toString(),
                contextLines = 1,
            ),
        )

        assertTrue(result.contains("before"))
        assertTrue(result.contains("after"))
    }
}
