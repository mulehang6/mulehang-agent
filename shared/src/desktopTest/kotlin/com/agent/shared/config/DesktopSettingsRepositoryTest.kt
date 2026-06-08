package com.agent.shared.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证双层 settings 加载与环境变量覆盖。
 */
class DesktopSettingsRepositoryTest {

    /**
     * 桌面仓库应读取用户级与项目级配置，并应用项目级优先级。
     */
    @Test
    fun `should merge user and project settings with project precedence`() {
        val root = Files.createTempDirectory("mulehang-settings-test")
        val userHome = root.resolve("user-home")
        val projectRoot = root.resolve("workspace")
        Files.createDirectories(userHome.resolve(".mulehang"))
        Files.createDirectories(projectRoot.resolve("mulehang"))

        Files.writeString(
            userHome.resolve(".mulehang/settings.json"),
            """{"profiles":[{"id":"main","providerType":"openai-responses","baseUrl":"https://api.openai.com/v1","apiKey":"user","model":"gpt-4.1"}]}""",
        )
        Files.writeString(
            projectRoot.resolve("mulehang/settings.json"),
            """{"profiles":[{"id":"main","providerType":"openai-responses","baseUrl":"https://project.example/v1","apiKey":"project","model":"gpt-4.1-mini"}]}""",
        )

        val repository = DesktopSettingsRepository(
            pathResolver = DesktopPathResolver(userHome, projectRoot),
            environmentOverrides = DesktopEnvironmentOverrides(emptyMap()),
        )

        val profiles = repository.loadResolvedProfiles()

        assertEquals("https://project.example/v1", profiles.single().baseUrl)
        assertEquals("project", profiles.single().apiKey)
    }

    /**
     * 示例配置应写入项目级 mulehang 目录。
     */
    @Test
    fun `should write example settings under project mulehang directory`() {
        val root = Files.createTempDirectory("mulehang-settings-example-test")
        val repository = DesktopSettingsRepository(
            pathResolver = DesktopPathResolver(root.resolve("home"), root.resolve("workspace")),
            environmentOverrides = DesktopEnvironmentOverrides(emptyMap()),
        )

        repository.writeExampleSettings("""{"profiles":[]}""")

        assertEquals(
            """{"profiles":[]}""",
            Files.readString(root.resolve("workspace/mulehang/settings.json.example")),
        )
    }
}
