package com.agent.app.chat.component

import com.agent.shared.settings.model.AgentHookCommand
import com.agent.shared.settings.model.AgentHookEvent
import com.agent.shared.settings.model.AgentHookMatcher
import com.agent.shared.settings.model.AgentHookSettings
import com.agent.shared.settings.model.AgentResourceSettings
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport
import com.agent.shared.settings.model.ProviderProfile
import com.agent.shared.settings.model.ProviderType
import com.agent.shared.settings.model.SettingsDocument
import com.agent.shared.settings.persistence.DesktopEnvironmentOverrides
import com.agent.shared.settings.persistence.DesktopPathResolver
import com.agent.shared.settings.persistence.DesktopSettingsRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 分区保存的字段级合并与差异判断测试。 */
class SettingsSectionSaveTest {
    /** MCP 保存只替换服务列表，并保留磁盘上更新过的 Hooks 与扩展字段。 */
    @Test
    fun `should merge only MCP fields into latest disk document`() {
        val latest = SettingsDocument(
            hooks = hooks("latest"),
            agentResources = AgentResourceSettings(skillDirectories = listOf("latest-skills")),
        )
        val draft = SettingsDocument(
            hooks = hooks("draft"),
            agentResources = AgentResourceSettings(
                skillDirectories = listOf("draft-skills"),
                mcpServers = listOf(mcp("draft-mcp")),
            ),
        )

        val merged = mergeSettingsArea(latest, draft, SettingsSaveArea.MCP)

        assertEquals(latest.hooks, merged.hooks)
        assertEquals(listOf("latest-skills"), merged.agentResources.skillDirectories)
        assertEquals(listOf(mcp("draft-mcp")), merged.agentResources.mcpServers)
    }

    /** Hooks、扩展与 Provider 保存均不得覆盖其他区域的最新磁盘内容。 */
    @Test
    fun `should isolate hooks extensions and provider saves`() {
        val latest = SettingsDocument(
            providers = listOf(provider("latest-provider")),
            hooks = hooks("latest-hook"),
            agentResources = AgentResourceSettings(mcpServers = listOf(mcp("latest-mcp"))),
        )
        val draft = SettingsDocument(
            providers = listOf(provider("draft-provider")),
            hooks = hooks("draft-hook"),
            agentResources = AgentResourceSettings(
                mcpServers = listOf(mcp("draft-mcp")),
                promptDirectories = listOf("draft-prompts"),
            ),
        )

        assertEquals(hooks("draft-hook"), mergeSettingsArea(latest, draft, SettingsSaveArea.HOOKS).hooks)
        val extensions = mergeSettingsArea(latest, draft, SettingsSaveArea.EXTENSIONS)
        assertEquals(listOf(mcp("latest-mcp")), extensions.agentResources.mcpServers)
        assertEquals(listOf("draft-prompts"), extensions.agentResources.promptDirectories)
        val providers = mergeSettingsArea(latest, draft, SettingsSaveArea.PROVIDERS)
        assertEquals(listOf(provider("draft-provider")), providers.providers)
        assertEquals(latest.hooks, providers.hooks)
    }

    /** 撤销到基线和未修改区域都不应触发保存。 */
    @Test
    fun `should detect changes only in selected area`() {
        val baseline = SettingsDocument(agentResources = AgentResourceSettings(mcpServers = listOf(mcp("one"))))
        val hooksOnly = baseline.copy(hooks = hooks("changed"))

        assertFalse(hasSettingsAreaChanges(baseline, hooksOnly, SettingsSaveArea.MCP))
        assertTrue(hasSettingsAreaChanges(baseline, hooksOnly, SettingsSaveArea.HOOKS))
        assertFalse(hasSettingsAreaChanges(baseline, baseline.copy(), SettingsSaveArea.HOOKS))
    }

    /** 分区保存必须真正写入 settings.json，重新创建仓库后仍能读取且不覆盖其他区域。 */
    @Test
    fun `should persist scoped settings across repository reopen`() {
        val home = Files.createTempDirectory("mulehang-settings-home")
        val repository = DesktopSettingsRepository(
            DesktopPathResolver(home, home),
            DesktopEnvironmentOverrides(emptyMap()),
        )
        val state = SettingsPanelUiState().apply {
            layer = ConfigLayer.USER
            document = SettingsDocument(agentResources = AgentResourceSettings(mcpServers = listOf(mcp("saved"))))
            lastSavedDocument = SettingsDocument()
        }

        assertTrue(persistSettingsArea(state, repository, SettingsSaveArea.MCP) {})
        assertTrue(state.resourceReloadPending)
        assertTrue(state.mcpReloadPending)
        state.document = state.document.copy(hooks = hooks("saved-hook"))
        assertTrue(persistSettingsArea(state, repository, SettingsSaveArea.HOOKS) {})

        val reopened = DesktopSettingsRepository(
            DesktopPathResolver(home, home),
            DesktopEnvironmentOverrides(emptyMap()),
        ).loadDocument(ConfigLayer.USER)
        assertEquals(listOf(mcp("saved")), reopened.agentResources.mcpServers)
        assertEquals(hooks("saved-hook"), reopened.hooks)
        val notificationCount = state.changeNotifications.entries.size
        assertFalse(persistSettingsArea(state, repository, SettingsSaveArea.HOOKS) {})
        assertEquals(notificationCount, state.changeNotifications.entries.size)
    }

    /** 构造最小有效 MCP。 */
    private fun mcp(id: String) = McpServerSettings(id, McpServerTransport.STDIO, command = listOf("npx"))

    /** 构造单条有效 Hook。 */
    private fun hooks(command: String) = AgentHookSettings(
        mapOf(AgentHookEvent.USER_PROMPT_SUBMIT to listOf(AgentHookMatcher(hooks = listOf(AgentHookCommand(command = command))))),
    )

    /** 构造最小 Provider；这里只比较字段隔离，不参与保存校验。 */
    private fun provider(id: String) = ProviderProfile(
        id = id,
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://example.test",
        apiKey = "",
    )
}
