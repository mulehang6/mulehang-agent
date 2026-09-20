package com.agent.app.chat.component

import com.agent.shared.settings.model.AgentResourceSettings
import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport
import com.agent.shared.settings.model.SettingsDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /** 停用的 MCP 即使尚未填完，也不应阻塞保存其他设置。 */
    @Test
    fun `should ignore disabled MCP validation`() {
        val document = SettingsDocument(
            agentResources = AgentResourceSettings(
                mcpServers = listOf(
                    McpServerSettings(
                        id = "disabled",
                        transport = McpServerTransport.STDIO,
                        enabled = false,
                    ),
                ),
            ),
        )

        assertNull(validateMcpServerSettings(document))
    }

    /** 修改启用状态只能替换原位置的记录，不能改变设置列表顺序。 */
    @Test
    fun `should preserve MCP order when updating a server`() {
        val first = McpServerSettings(id = "first", transport = McpServerTransport.STDIO, command = listOf("first"))
        val second = McpServerSettings(id = "second", transport = McpServerTransport.STDIO, command = listOf("second"))
        val document = SettingsDocument(agentResources = AgentResourceSettings(mcpServers = listOf(first, second)))

        val updated = document.withUpdatedMcpServer("first", first.copy(enabled = false))

        assertEquals(listOf("first", "second"), updated.agentResources.mcpServers.map(McpServerSettings::id))
        assertTrue(!updated.agentResources.mcpServers.first().enabled)
    }

    /** Header 重命名为已有名称时必须保留两个原值，避免凭据被静默覆盖。 */
    @Test
    fun `should reject case insensitive MCP header collision`() {
        val headers = mapOf("Authorization" to "old", "X-Trace" to "trace")

        assertNull(updateMcpHeader(headers, "X-Trace", "authorization", "new"))
        assertEquals(
            mapOf("Authorization" to "old", "X-Trace" to "new"),
            updateMcpHeader(headers, "X-Trace", "X-Trace", "new"),
        )
    }

    /** 设置卡片展示远程地址时必须移除 URI user-info。 */
    @Test
    fun `should redact credentials from MCP url display`() {
        val displayed = sanitizeMcpUrlForDisplay("https://user:secret@example.test/mcp")

        assertEquals("https://example.test/mcp", displayed)
        assertTrue("secret" !in displayed)
    }
}
