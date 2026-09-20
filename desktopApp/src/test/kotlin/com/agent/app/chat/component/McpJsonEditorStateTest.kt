package com.agent.app.chat.component

import com.agent.shared.settings.model.McpServerSettings
import com.agent.shared.settings.model.McpServerTransport
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** MCP JSON 与可视化双视图的临时状态回归测试。 */
class McpJsonEditorStateTest {
    /** 合法文本立即返回可同步的服务，并允许回到可视化配置。 */
    @Test
    fun `should synchronize valid JSON and allow visual mode`() {
        val state = McpJsonEditorState().apply { enterJson(emptyList()) }
        val text = """{"mcpServers":{"local":{"command":"npx","args":["-y"]}}}"""

        val result = assertIs<McpJsonParseResult.Success>(state.updateText(text))

        assertEquals(listOf("npx", "-y"), result.servers.single().command)
        assertNull(state.error)
        assertTrue(state.enterVisual())
        assertEquals(McpEditorMode.VISUAL, state.mode)
    }

    /** 非法文本保留用户原文，并阻止切回可视化配置。 */
    @Test
    fun `should retain invalid JSON and block visual mode`() {
        val state = McpJsonEditorState().apply { enterJson(emptyList()) }
        val invalidText = """{"mcpServers":{"broken":{"command":7}}}"""

        assertIs<McpJsonParseResult.Failure>(state.updateText(invalidText))

        assertEquals(invalidText, state.text)
        assertFalse(state.enterVisual())
        assertEquals(McpEditorMode.JSON, state.mode)
    }

    /** 再次进入 JSON 时必须以最新可视化草稿重建规范文本。 */
    @Test
    fun `should regenerate canonical JSON from visual changes`() {
        val state = McpJsonEditorState()
        state.enterJson(
            listOf(
                McpServerSettings(
                    id = "first",
                    transport = McpServerTransport.STDIO,
                    command = listOf("npx"),
                ),
            ),
        )
        assertTrue(state.enterVisual())

        state.enterJson(
            listOf(
                McpServerSettings(
                    id = "second",
                    transport = McpServerTransport.SSE,
                    url = "https://example.test/sse",
                ),
            ),
        )

        assertContains(state.text, "\"second\"")
        assertFalse(state.text.contains("\"first\""))
        assertEquals(McpEditorMode.JSON, state.mode)
    }

    /** 配置层级切换会恢复默认可视化状态并清理旧文本。 */
    @Test
    fun `should reset JSON draft for another settings layer`() {
        val state = McpJsonEditorState().apply {
            enterJson(emptyList())
            updateText("broken")
        }

        state.reset()

        assertEquals(McpEditorMode.VISUAL, state.mode)
        assertEquals("", state.text)
        assertNull(state.error)
    }

    /** JSON 视图默认隐藏 Header 值，但解析占位符时仍保留原始凭据。 */
    @Test
    fun `should mask and restore sensitive headers in JSON editor`() {
        val server = McpServerSettings(
            id = "remote",
            transport = McpServerTransport.SSE,
            url = "https://example.test/mcp",
            headers = mapOf("Authorization" to "Bearer secret"),
        )
        val state = McpJsonEditorState()

        state.enterJson(listOf(server))

        assertFalse(state.sensitiveValuesVisible)
        assertContains(state.text, MCP_REDACTED_HEADER_VALUE)
        assertFalse(state.text.contains("Bearer secret"))
        val hiddenResult = assertIs<McpJsonParseResult.Success>(state.updateText(state.text))
        assertEquals("Bearer secret", hiddenResult.servers.single().headers["Authorization"])

        state.toggleSensitiveValues()

        assertTrue(state.sensitiveValuesVisible)
        assertContains(state.text, "Bearer secret")
    }

    /** 服务 ID 或 Header 改名时，遮罩占位符仍必须恢复原始凭据。 */
    @Test
    fun `should preserve masked headers when server and header names change`() {
        val server = McpServerSettings(
            id = "remote",
            transport = McpServerTransport.SSE,
            url = "https://example.test/mcp",
            headers = mapOf("Authorization" to "Bearer secret"),
        )
        val state = McpJsonEditorState()
        state.enterJson(listOf(server))

        val renamed = state.text
            .replace("\"remote\"", "\"github\"")
            .replace("\"Authorization\"", "\"X-Auth\"")
        val result = assertIs<McpJsonParseResult.Success>(state.updateText(renamed))

        assertEquals("Bearer secret", result.servers.single().headers["X-Auth"])
    }

    /** 格式化后再次编辑其他字段时，用户刚输入的新凭据不能被旧快照覆盖。 */
    @Test
    fun `should retain newly entered secret after formatting`() {
        val server = McpServerSettings(
            id = "remote",
            transport = McpServerTransport.SSE,
            url = "https://example.test/mcp",
            headers = mapOf("Authorization" to "Bearer old"),
        )
        val state = McpJsonEditorState()
        state.enterJson(listOf(server))

        val replaced = state.text.replace(MCP_REDACTED_HEADER_VALUE, "Bearer new")
        assertEquals("Bearer new", assertIs<McpJsonParseResult.Success>(state.updateText(replaced)).servers.single().headers["Authorization"])
        assertIs<McpJsonParseResult.Success>(state.format())
        assertFalse(state.text.contains("Bearer new"))

        val edited = state.text.replace("example.test/mcp", "example.test/other")
        val result = assertIs<McpJsonParseResult.Success>(state.updateText(edited))
        assertEquals("Bearer new", result.servers.single().headers["Authorization"])
    }

    /** 切换敏感值时若 JSON 尚未合法，必须保留原始草稿和错误状态。 */
    @Test
    fun `should retain invalid JSON while toggling sensitive values`() {
        val state = McpJsonEditorState()
        state.enterJson(
            listOf(
                McpServerSettings(
                    id = "remote",
                    transport = McpServerTransport.SSE,
                    url = "https://example.test/mcp",
                    headers = mapOf("Authorization" to "Bearer secret"),
                ),
            ),
        )
        val invalid = state.text.dropLast(1)
        assertIs<McpJsonParseResult.Failure>(state.updateText(invalid))

        state.toggleSensitiveValues()

        assertEquals(invalid, state.text)
        assertFalse(state.sensitiveValuesVisible)
        assertTrue(state.error != null)
    }
}
