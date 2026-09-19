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
}
