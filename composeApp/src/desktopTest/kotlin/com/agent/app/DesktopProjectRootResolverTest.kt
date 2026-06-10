package com.agent.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证桌面启动时从工作目录解析项目根目录的规则。
 */
class DesktopProjectRootResolverTest {

    /**
     * 从 Gradle 子模块工作目录启动时应回退到仓库根目录。
     */
    @Test
    fun `should resolve repository root from compose app working directory`() {
        val repositoryRoot = Files.createTempDirectory("mulehang-project-root-test")
        val composeAppRoot = Files.createDirectories(repositoryRoot.resolve("composeApp"))
        Files.writeString(repositoryRoot.resolve("settings.gradle.kts"), "rootProject.name = \"test\"")

        val resolved = DesktopProjectRootResolver.resolve(composeAppRoot)

        assertEquals(repositoryRoot, resolved)
    }
}
