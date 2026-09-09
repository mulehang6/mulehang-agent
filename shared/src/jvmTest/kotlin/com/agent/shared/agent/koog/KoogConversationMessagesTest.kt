package com.agent.shared.agent.koog

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 验证结构化历史及未完成工具调用的协议回放。 */
class KoogConversationMessagesTest {

    /**
     * 历史里工具调用参数只是 UI 预览，可能被截断成非法 JSON；回放时必须降级为合法
     * 占位，否则 Koog 序列化 assistant 消息时懒解析 argsJson 会直接崩溃。
     */
    @Test
    fun `should fallback truncated tool call arguments to valid json when replaying history`() {
        val messages = buildConversationMessages(
            history = listOf(
                AgentConversationHistoryMessage.User("first"),
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-1",
                            name = "grep_code",
                            argumentsPreview = """{"pattern":"Koog", "path":".", "glob":"null", "regex":false, "case_sensitive":false, "context_lines":2, "max_results":50""",
                        ),
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-2",
                            name = "read_file",
                            argumentsPreview = """{"path":"README.md"}""",
                        ),
                    ),
                ),
            ),
            prompt = "second",
        )

        val parts = assertIs<Message.Assistant>(messages[1]).parts
        val truncated = assertIs<MessagePart.Tool.Call>(parts[0])
        assertTrue(runCatching { truncated.argsJson }.isSuccess)
        assertEquals("{}", truncated.args)
        val intact = assertIs<MessagePart.Tool.Call>(parts[1])
        assertEquals("""{"path":"README.md"}""", intact.args)
    }

    /**
     * 历史恢复时空的 reasoning 片段也必须保留，避免跨会话回放触发同样的 reasoning_text 回传校验。
     */
    @Test
    fun `should keep empty reasoning part from structured history`() {
        val messages = buildConversationMessages(
            history = listOf(
                AgentConversationHistoryMessage.User("first"),
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(
                        AgentConversationHistoryPart.Reasoning(summary = "", rawText = ""),
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-1",
                            name = "read_file",
                            argumentsPreview = """{"path":"README.md"}""",
                        ),
                    ),
                ),
            ),
            prompt = "second",
        )

        assertEquals(
            listOf(
                MessagePart.Reasoning(content = listOf(""), encrypted = ""),
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = """{"path":"README.md"}""",
                ),
            ),
            assertIs<Message.Assistant>(messages[1]).parts,
        )
    }

    /**
     * 首轮 Koog 请求也必须把已有结构化历史映射回 prompt，而不是只发送当前 prompt。
     */
    @Test
    fun `should build koog prompt messages from structured conversation history`() {
        val messages = buildConversationMessages(
            history = listOf(
                AgentConversationHistoryMessage.User("first"),
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(
                        AgentConversationHistoryPart.Reasoning(
                            summary = "先分析",
                            rawText = "先分析原始思考",
                        ),
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-1",
                            name = "read_file",
                            argumentsPreview = """{"path":"README.md"}""",
                        ),
                        AgentConversationHistoryPart.ToolResult(
                            id = "call-1",
                            name = "read_file",
                            resultPreview = "file-content",
                        ),
                        AgentConversationHistoryPart.Text("done"),
                    ),
                ),
            ),
            prompt = "second",
        )

        assertEquals(5, messages.size)
        assertEquals(listOf(MessagePart.Text("first")), assertIs<Message.User>(messages[0]).parts)
        assertEquals(
            listOf(
                MessagePart.Reasoning(
                    content = listOf("先分析原始思考"),
                    summary = listOf("先分析"),
                    encrypted = "",
                ),
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = """{"path":"README.md"}""",
                ),
            ),
            assertIs<Message.Assistant>(messages[1]).parts,
        )
        assertEquals(
            listOf(
                MessagePart.Tool.Result(
                    id = "call-1",
                    tool = "read_file",
                    output = "file-content",
                ),
            ),
            assertIs<Message.User>(messages[2]).parts,
        )
        assertEquals(listOf(MessagePart.Text("done")), assertIs<Message.Assistant>(messages[3]).parts)
        assertEquals(listOf(MessagePart.Text("second")), assertIs<Message.User>(messages[4]).parts)
    }

    /**
     * 中断或失败的上一轮可能只留下 tool call；恢复历史时必须补齐 tool result，避免兼容 API 拒绝请求。
     */
    @Test
    fun `should synthesize tool result for orphaned historical tool call`() {
        val messages = buildConversationMessages(
            history = listOf(
                AgentConversationHistoryMessage.User("first"),
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-1",
                            name = "read_file",
                            argumentsPreview = """{"path":"README.md"}""",
                        ),
                    ),
                ),
            ),
            prompt = "second",
        )

        assertEquals(4, messages.size)
        assertEquals(
            listOf(
                MessagePart.Tool.Call(
                    id = "call-1",
                    tool = "read_file",
                    args = """{"path":"README.md"}""",
                ),
            ),
            assertIs<Message.Assistant>(messages[1]).parts,
        )
        assertEquals(
            listOf(
                MessagePart.Tool.Result(
                    id = "call-1",
                    tool = "read_file",
                    output = "工具调用未完成，未产生可用结果。",
                ),
            ),
            assertIs<Message.User>(messages[2]).parts,
        )
        assertEquals(listOf(MessagePart.Text("second")), assertIs<Message.User>(messages[3]).parts)
    }

    /**
     * Anthropic 要求同一 assistant 消息中的每个 tool_use 都在紧随其后的同一条 user
     * 消息中获得 tool_result；拆成多条消息会使后续调用被服务端拒绝。
     */
    @Test
    fun `should synthesize orphaned tool results in one user message`() {
        val messages = buildConversationMessages(
            history = listOf(
                AgentConversationHistoryMessage.User("first"),
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-1",
                            name = "read_file",
                            argumentsPreview = "{\"path\":\"README.md\"}",
                        ),
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-2",
                            name = "list_dir",
                            argumentsPreview = "{\"path\":\".\"}",
                        ),
                    ),
                ),
            ),
            prompt = "second",
        )

        assertEquals(4, messages.size)
        assertEquals(
            listOf(
                MessagePart.Tool.Result(
                    id = "call-1",
                    tool = "read_file",
                    output = "工具调用未完成，未产生可用结果。",
                ),
                MessagePart.Tool.Result(
                    id = "call-2",
                    tool = "list_dir",
                    output = "工具调用未完成，未产生可用结果。",
                ),
            ),
            assertIs<Message.User>(messages[2]).parts,
        )
        assertEquals(listOf(MessagePart.Text("second")), assertIs<Message.User>(messages[3]).parts)
    }

    /**
     * 多个已完成工具调用也必须合并回放，保证 Anthropic 将每个 tool_result 视为同一轮
     * tool_use 的直接响应。
     */
    @Test
    fun `should replay completed tool results in one user message`() {
        val messages = buildConversationMessages(
            history = listOf(
                AgentConversationHistoryMessage.User("first"),
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-1",
                            name = "read_file",
                            argumentsPreview = "{\"path\":\"README.md\"}",
                        ),
                        AgentConversationHistoryPart.ToolCall(
                            id = "call-2",
                            name = "list_dir",
                            argumentsPreview = "{\"path\":\".\"}",
                        ),
                        AgentConversationHistoryPart.ToolResult(
                            id = "call-1",
                            name = "read_file",
                            resultPreview = "README",
                        ),
                        AgentConversationHistoryPart.ToolResult(
                            id = "call-2",
                            name = "list_dir",
                            resultPreview = "src",
                        ),
                    ),
                ),
            ),
            prompt = "second",
        )

        assertEquals(4, messages.size)
        assertEquals(
            listOf(
                MessagePart.Tool.Result(id = "call-1", tool = "read_file", output = "README"),
                MessagePart.Tool.Result(id = "call-2", tool = "list_dir", output = "src"),
            ),
            assertIs<Message.User>(messages[2]).parts,
        )
        assertEquals(listOf(MessagePart.Text("second")), assertIs<Message.User>(messages[3]).parts)
    }

}
