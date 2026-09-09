package com.agent.shared.settings.persistence

import java.nio.file.Files
import com.agent.shared.settings.model.AgentResourceSettings
import com.agent.shared.settings.model.AgentHookCommand
import com.agent.shared.settings.model.AgentHookEvent
import com.agent.shared.settings.model.AgentHookMatcher
import com.agent.shared.settings.model.AgentHookSettings
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ModelProfile
import com.agent.shared.settings.model.ProviderProfile
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.settings.model.SettingsDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 验证双层 settings 加载与环境变量覆盖。
 */
class DesktopSettingsRepositoryTest {

    /**
     * 桌面仓库只能读取用户级 AI 配置，项目级同名配置不得覆盖它。
     */
    @Test
    fun `should ignore project AI settings when resolving profiles`() {
        val root = Files.createTempDirectory("mulehang-settings-test")
        val userHome = root.resolve("user-home")
        val projectRoot = root.resolve("workspace")
        Files.createDirectories(userHome.resolve(".mulehang"))
        Files.createDirectories(projectRoot.resolve(".mulehang"))

        Files.writeString(
            userHome.resolve(".mulehang/settings.json"),
            """{"providers":[{"id":"openai","providerType":"openai-responses","baseUrl":"https://api.openai.com/v1","apiKey":"user","models":[{"id":"gpt-4.1"}]}]}""",
        )
        Files.writeString(
            projectRoot.resolve(".mulehang/settings.json"),
            """{"providers":[{"id":"openai","providerType":"openai-responses","baseUrl":"https://project.example/v1","apiKey":"project","models":[{"id":"gpt-4.1-mini"}]}]}""",
        )

        val repository = DesktopSettingsRepository(
            pathResolver = DesktopPathResolver(userHome, projectRoot),
            environmentOverrides = DesktopEnvironmentOverrides(emptyMap()),
        )

        val profiles = repository.loadResolvedProfiles()

        assertEquals("https://api.openai.com/v1", profiles.single().baseUrl)
        assertEquals("user", profiles.single().apiKey)
    }

    /**
     * 保存项目资源设置时需要清理旧文档中的 AI 字段，避免用户之后迁移设置时重新生效。
     */
    @Test
    fun `should remove AI settings when saving a project document`() {
        val root = Files.createTempDirectory("mulehang-project-settings-test")
        val projectRoot = root.resolve("workspace")
        val repository = DesktopSettingsRepository(
            pathResolver = DesktopPathResolver(root.resolve("home"), projectRoot),
            environmentOverrides = DesktopEnvironmentOverrides(emptyMap()),
        )

        repository.saveDocument(
            ConfigLayer.PROJECT,
            SettingsDocument(
                providers = listOf(
                    ProviderProfile(
                        id = "custom",
                        providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                        baseUrl = "https://gateway.example/v1",
                        apiKey = "secret",
                        models = listOf(ModelProfile(id = "custom-model")),
                    ),
                ),
                maxIterations = 75,
                hooks = AgentHookSettings(
                    hooks = mapOf(
                        AgentHookEvent.USER_PROMPT_SUBMIT to listOf(
                            AgentHookMatcher(hooks = listOf(AgentHookCommand(command = "echo project-hook"))),
                        ),
                    ),
                ),
                agentResources = AgentResourceSettings(skillDirectories = listOf("C:/skills")),
            ),
        )

        val saved = repository.loadDocument(ConfigLayer.PROJECT)

        assertEquals(emptyList(), saved.providers)
        assertEquals(null, saved.maxIterations)
        assertEquals(emptyMap(), saved.hooks.hooks)
        assertEquals(listOf("C:/skills"), saved.agentResources.skillDirectories)
        assertFalse(Files.readString(projectRoot.resolve(".mulehang/settings.json")).contains("custom-model"))
        assertFalse(Files.readString(projectRoot.resolve(".mulehang/settings.json")).contains("project-hook"))
    }

    /**
     * 示例配置应写入项目级 .mulehang 目录。
     */
    @Test
    fun `should write example settings under project dot mulehang directory`() {
        val root = Files.createTempDirectory("mulehang-settings-example-test")
        val repository = DesktopSettingsRepository(
            pathResolver = DesktopPathResolver(root.resolve("home"), root.resolve("workspace")),
            environmentOverrides = DesktopEnvironmentOverrides(emptyMap()),
        )

        repository.writeExampleSettings("""{"providers":[]}""")

        assertEquals(
            """{"providers":[]}""",
            Files.readString(root.resolve("workspace/.mulehang/settings.json.example")),
        )
    }
}
