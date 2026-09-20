package com.agent.app.chat.component

import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** MCP JSON 配置的严格解析、规范化与往返测试。 */
class McpJsonConfigurationTest {
    /** stdio 配置支持参数、环境变量和两种启停写法。 */
    @Test
    fun `should parse stdio arguments environment and enabled aliases`() {
        val result = parseSuccess(
            """
            {
              "mcpServers": {
                "files": {
                  "command": "npx",
                  "args": ["-y", "server"],
                  "env": {"ROOT": "C:/workspace"},
                  "enabled": false
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            McpServerSettings(
                id = "files",
                transport = McpServerTransport.STDIO,
                command = listOf("npx", "-y", "server"),
                environment = mapOf("ROOT" to "C:/workspace"),
                enabled = false,
            ),
            result.single(),
        )
    }

    /** SSE 与 streamable HTTP 均解析为现有远程传输模型。 */
    @Test
    fun `should parse supported remote transports and transport alias`() {
        val result = parseSuccess(
            """
            {
              "mcpServers": {
                "events": {"transport": "sse", "url": "https://example.test/events", "headers": {"Authorization": "Bearer token"}},
                "http": {"type": "streamable-http", "url": "https://example.test/mcp", "headers": {"X-Tenant": "demo"}, "disabled": true}
              }
            }
            """.trimIndent(),
        )

        assertEquals(McpServerTransport.SSE, result[0].transport)
        assertEquals(mapOf("Authorization" to "Bearer token"), result[0].headers)
        assertEquals(McpServerTransport.STREAMABLE_HTTP, result[1].transport)
        assertEquals(mapOf("X-Tenant" to "demo"), result[1].headers)
        assertFalse(result[1].enabled)
    }

    /** 规范化输出省略 stdio 类型、空环境，并只在停用时写 disabled。 */
    @Test
    fun `should format canonical MCP JSON`() {
        val text = formatMcpJsonConfiguration(
            listOf(
                McpServerSettings(
                    id = "local",
                    transport = McpServerTransport.STDIO,
                    command = listOf("uvx"),
                ),
                McpServerSettings(
                    id = "remote",
                    transport = McpServerTransport.SSE,
                    url = "https://example.test/sse",
                    enabled = false,
                ),
            ),
        )

        assertContains(text, "\"command\": \"uvx\"")
        assertContains(text, "\"args\": []")
        assertFalse(text.contains("\"env\""))
        assertContains(text, "\"type\": \"sse\"")
        assertContains(text, "\"disabled\": true")
    }

    /** 支持的模型经过格式化与解析后保持等价。 */
    @Test
    fun `should round trip supported MCP settings`() {
        val servers = listOf(
            McpServerSettings(
                id = "local",
                transport = McpServerTransport.STDIO,
                command = listOf("npx", "-y", "pkg"),
                environment = mapOf("TOKEN_FILE" to "C:/token"),
            ),
            McpServerSettings(
                id = "remote",
                    transport = McpServerTransport.STREAMABLE_HTTP,
                    url = "http://localhost:8080/mcp",
                    headers = mapOf("Authorization" to "Bearer placeholder"),
            ),
        )

        assertEquals(servers, parseSuccess(formatMcpJsonConfiguration(servers)))
    }

    /** 会丢失或歧义的输入必须返回明确错误而不是静默删字段。 */
    @Test
    fun `should reject invalid roots conflicts field types and unsupported fields`() {
        val cases = listOf(
            "[]" to "顶层必须是对象",
            "{}" to "必须包含对象字段 'mcpServers'",
            """{"mcpServers":{"":{"command":"npx"}}}""" to "ID 不能为空",
            """{"mcpServers":{"x":{"command":"npx","args":"-y"}}}""" to "args 必须是字符串数组",
            """{"mcpServers":{"x":{"command":"npx","enabled":"true"}}}""" to "enabled 必须是布尔值",
            """{"mcpServers":{"x":{"command":"npx","enabled":true,"disabled":false}}}""" to "不能同时设置 enabled 和 disabled",
            """{"mcpServers":{"x":{"type":"sse","transport":"stdio","url":"https://example.test"}}}""" to "相互冲突",
            """{"mcpServers":{"x":{"type":"sse","url":"not-a-url"}}}""" to "有效的 http(s) 服务地址",
            """{"mcpServers":{"x":{"command":"npx","headers":{"X-Test":"1"}}}}""" to "不支持 headers 字段",
            """{"mcpServers":{"x":{"type":"sse","url":"https://example.test","headers":{"":"1"}}}}""" to "无效 Header",
            """{"mcpServers":{"x":{"type":"sse","url":"https://example.test","headers":{"X-Test":"1","x-test":"2"}}}}""" to "不能仅以大小写区分",
            """{"mcpServers":{"x":{"type":"sse","url":"https://example.test","headers":{"X-Test":7}}}}""" to "必须是字符串",
            """{"mcpServers":{"x":{"type":"sse","url":"https://example.test","headers":{"X-Test":"bad\nvalue"}}}}""" to "无效 Header",
            """{"mcpServers":{"x":{"type":"sse","url":"https://user:secret@example.test"}}}""" to "有效的 http(s) 服务地址",
            """{"mcpServers":{"x":{"command":"npx","alwaysAllow":[]}}}""" to "不支持字段 'alwaysAllow'",
        )

        cases.forEach { (text, expected) ->
            val failure = assertIs<McpJsonParseResult.Failure>(parseMcpJsonConfiguration(text), "input=$text")
            assertContains(failure.message, expected)
        }
    }

    /** 解析帮助方法把成功分支收窄为服务列表。 */
    private fun parseSuccess(text: String): List<McpServerSettings> {
        val result = assertIs<McpJsonParseResult.Success>(parseMcpJsonConfiguration(text))
        assertTrue(result.servers.isNotEmpty())
        return result.servers
    }
}
