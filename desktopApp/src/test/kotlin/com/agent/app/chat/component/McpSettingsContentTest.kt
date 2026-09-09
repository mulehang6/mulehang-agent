package com.agent.app.chat.component

import com.agent.shared.settings.model.AgentResourceSettings
import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport
import com.agent.shared.settings.model.SettingsDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** MCP 设置页的输入规范化与保存前校验回归测试。 */
class McpSettingsContentTest {

    /** 参数编辑按逗号分隔，同时剔除用户输入的空白段。 */
    @Test
    fun `should normalize comma separated MCP command arguments`() {
        assertEquals(listOf("-y", "@scope/server", "--verbose"), splitMcpArguments(" -y, , @scope/server, --verbose "))
    }

    /** 新服务 ID 必须避开已经存在的稳定配置 ID。 */
    @Test
    fun `should select next available MCP service id`() {
        val servers = listOf(
            McpServerSettings(id = "mcp-1", transport = McpServerTransport.STDIO),
            McpServerSettings(id = "mcp-3", transport = McpServerTransport.STDIO),
        )

        assertEquals("mcp-2", nextMcpServerId(servers))
    }

    /** 保存空闲的有效服务不应产生阻塞性提示。 */
    @Test
    fun `should accept valid direct MCP settings`() {
        val document = SettingsDocument(
            agentResources = AgentResourceSettings(
                mcpServers = listOf(
                    McpServerSettings(
                        id = "filesystem",
                        transport = McpServerTransport.STDIO,
                        command = listOf("npx", "-y", "@modelcontextprotocol/server-filesystem"),
                        environment = mapOf("ROOT" to "C:/workspace"),
                    ),
                    McpServerSettings(
                        id = "remote",
                        transport = McpServerTransport.STREAMABLE_HTTP,
                        url = "https://example.com/mcp",
                    ),
                ),
            ),
        )

        assertNull(validateMcpServerSettings(document))
    }

    /** 启用的 stdio 服务若没有可执行命令，必须在保存前被拦截。 */
    @Test
    fun `should reject enabled stdio MCP without command`() {
        val document = SettingsDocument(
            agentResources = AgentResourceSettings(
                mcpServers = listOf(McpServerSettings(id = "broken", transport = McpServerTransport.STDIO)),
            ),
        )

        assertEquals("stdio MCP 'broken' 必须填写命令。", validateMcpServerSettings(document))
    }
}
