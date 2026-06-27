package com.agent.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 验证桌面项目根目录解析规则。
 */
class DesktopProjectRootResolverTest {

    /**
     * 用户实际选择的目录应作为项目根目录，不应再被提升到 Gradle 仓库根。
     */
    @Test
    fun `should keep the selected directory as project root`() {
        val repositoryRoot = Files.createTempDirectory("mulehang-project-root-test")
        val selectedRoot = Files.createDirectories(repositoryRoot.resolve("desktopApp"))
        Files.writeString(repositoryRoot.resolve("settings.gradle.kts"), "rootProject.name = \"test\"")

        val resolved = DesktopProjectRootResolver.resolve(selectedRoot)

        assertEquals(selectedRoot, resolved)
    }

    /**
     * 如果调用方传入文件路径，应使用文件所在目录作为项目根目录。
     */
    @Test
    fun `should use parent directory when selected path is a file`() {
        val selectedRoot = Files.createTempDirectory("mulehang-project-root-file-test")
        val selectedFile = Files.writeString(selectedRoot.resolve("README.md"), "test")

        val resolved = DesktopProjectRootResolver.resolve(selectedFile)

        assertEquals(selectedRoot, resolved)
    }

    /**
     * 启动参数传入目录时，应使用该目录作为初始项目根目录。
     */
    @Test
    fun `should use first launch argument as initial project root`() {
        val selectedRoot = Files.createTempDirectory("mulehang-launch-root-test")

        val resolved = resolveInitialProjectRoot(arrayOf(selectedRoot.toString()))

        assertEquals(selectedRoot, resolved)
    }

    /**
     * 没有启动参数时不应在启动阶段检测工作区。
     */
    @Test
    fun `should not resolve workspace when launch argument is missing`() {
        assertNull(resolveInitialProjectRoot(emptyArray()))
    }
}

