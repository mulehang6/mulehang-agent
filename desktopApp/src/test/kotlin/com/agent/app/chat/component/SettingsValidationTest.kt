package com.agent.app.chat.component

import com.agent.shared.settings.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 验证跨分类保存和模型改名期间的表单错误生命周期。 */
class SettingsValidationTest {
    /** 非法 MCP JSON 只进入通用错误映射，不替换文档里的旧合法配置。 */
    @Test
    fun `should keep valid document and block save for invalid MCP JSON`() {
        val originalServer = McpServerSettings(
            id = "local",
            transport = McpServerTransport.STDIO,
            command = listOf("npx"),
        )
        var document = SettingsDocument(agentResources = AgentResourceSettings(mcpServers = listOf(originalServer)))
        val editorState = McpJsonEditorState().apply { enterJson(document.agentResources.mcpServers) }
        val result = editorState.updateText("""{"mcpServers":{"local":{"command":7}}}""")
        if (result is McpJsonParseResult.Success) {
            document = document.copy(agentResources = document.agentResources.copy(mcpServers = result.servers))
        }
        val failure = assertIs<McpJsonParseResult.Failure>(result)

        assertEquals(listOf(originalServer), document.agentResources.mcpServers)
        assertEquals(
            failure.message,
            validateSettingsForSave(document, mapOf(MCP_JSON_VALIDATION_KEY to failure.message)),
        )
    }

    /** 保存扩展时也检查服务，保存服务时也检查扩展中的未完成配置。 */
    @Test
    fun `should validate the complete document`() {
        val provider = ProviderProfile(
            id = "test", providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://example.test", apiKey = "",
            models = listOf(ModelProfile("test")),
        )
        assertNotNull(validateSettingsForSave(SettingsDocument(providers = listOf(provider)), emptyMap()))
        val hooks = AgentHookSettings(mapOf(AgentHookEvent.STOP to listOf(AgentHookMatcher(
            hooks = listOf(AgentHookCommand(command = "")),
        ))))
        assertNotNull(validateSettingsForSave(SettingsDocument(hooks = hooks), emptyMap()))
        assertEquals("invalid draft", validateSettingsForSave(SettingsDocument(), mapOf("draft" to "invalid draft")))
        assertNull(validateSettingsForSave(SettingsDocument(), emptyMap()))
    }

    /** 改名后保留当前错误，修正新键后不存在不可清除的旧错误。 */
    @Test
    fun `should move validation errors on model rename`() {
        val errors = mutableMapOf("p:old:output" to "invalid", "p:other:output" to "other")
        renameSettingsValidationErrors(errors, "p:old", "p:new")
        assertEquals(mapOf("p:new:output" to "invalid", "p:other:output" to "other"), errors)
        errors.remove("p:new:output")
        assertEquals(mapOf("p:other:output" to "other"), errors)
    }
}
