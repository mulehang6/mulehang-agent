package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** IDEA 风格 JSON 编辑器的纯状态回归测试。 */
class JsonCodeEditorTest {
    /** 撤销和重做按用户编辑顺序恢复，新的编辑会清空重做分支。 */
    @Test
    fun `should keep bounded undo and redo history`() {
        val history = JsonEditorHistory("{}")
        history.record("{\n}")
        history.record("{\n    \"a\": 1\n}")

        assertEquals("{\n}", history.undo())
        assertEquals("{}", history.undo())
        assertNull(history.undo())
        assertEquals("{\n}", history.redo())
        history.record("{\n    \"b\": 2\n}")
        assertNull(history.redo())
    }

    /** 序列化错误的行号或 offset 都能映射到 gutter 的一基行号。 */
    @Test
    fun `should locate JSON errors by line or offset`() {
        val text = "{\n  \"mcpServers\": {\n  }\n}"

        assertEquals(3, jsonErrorLine("Unexpected token at line 3, column 2", text))
        assertEquals(2, jsonErrorLine("Unexpected JSON token at offset 6", text))
        assertNull(jsonErrorLine("MCP URL 无效", text))
    }
}
