package com.agent.app.chat.presentation

import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证聊天时间线展示文案的最小规则。
 */
class ConversationPresentationTest {

    /**
     * 聊天正文应直接显示内容，不再拼接角色前缀。
     */
    @Test
    fun `should render chat message without role prefix`() {
        val userItem = ChatMessageItem(ChatMessage(role = ChatRole.User, content = "你好"))
        val assistantItem = ChatMessageItem(ChatMessage(role = ChatRole.Assistant, content = "世界"))

        assertEquals("你好", buildChatMessageText(userItem))
        assertEquals("世界", buildChatMessageText(assistantItem))
    }

    /**
     * 思考块标题应保留 Thinking 文案，并区分流式中与完成态。
     */
    @Test
    fun `should keep thinking headline for reasoning block`() {
        assertEquals("正在思考…", buildReasoningHeadline(ReasoningItem(isStreaming = true)))
        assertEquals("思考过程", buildReasoningHeadline(ReasoningItem(isStreaming = false)))
    }

    /**
     * 工具事件应显示纯文本标题，并把输入输出放到轻量标签里。
     */
    @Test
    fun `should expose plain text tool event headline and kind label`() {
        assertEquals(
            "read_file",
            buildToolEventHeadline(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Started,
                    preview = """{"path":"README.md"}""",
                ),
            ),
        )
        assertEquals(
            "输入",
            buildToolEventKindLabel(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Started,
                    preview = """{"path":"README.md"}""",
                ),
            ),
        )
        assertEquals(
            "输出",
            buildToolEventKindLabel(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Finished,
                    preview = "ok",
                ),
            ),
        )
        assertEquals(
            "正在整理结果",
            buildToolEventHeadline(
                ToolEventItem(
                    toolName = "status",
                    status = ToolEventStatus.Status,
                    preview = "正在整理结果",
                ),
            ),
        )
    }

    /**
     * 只有带输入输出预览的工具事件才需要展开详情。
     */
    @Test
    fun `should only expand tool events that have preview details`() {
        assertEquals(
            true,
            toolEventHasDetails(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Started,
                    preview = """{"path":"README.md"}""",
                ),
            ),
        )
        assertEquals(
            false,
            toolEventHasDetails(
                ToolEventItem(
                    toolName = "status",
                    status = ToolEventStatus.Status,
                    preview = "working",
                ),
            ),
        )
        assertEquals(
            false,
            toolEventHasDetails(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Finished,
                    preview = "",
                ),
            ),
        )
    }

    /**
     * Failed 状态的工具事件应展示失败标题和错误标签，且仍然保留 preview 详情。
     */
    @Test
    fun `should expose failed headline and error label for failed tool event`() {
        val failedItem = ToolEventItem(
            toolName = "read_file",
            status = ToolEventStatus.Failed,
            preview = """{"path":"README.md"}""",
            errorMessage = "file not found",
        )
        assertEquals("失败: read_file", buildToolEventHeadline(failedItem))
        assertEquals("错误", buildToolEventKindLabel(failedItem))
        assertEquals(true, toolEventHasDetails(failedItem))
    }

    /**
     * Failed 状态的工具事件在缺少 preview 时不应展开详情。
     */
    @Test
    fun `should not expand details for failed tool event without preview`() {
        val failedItem = ToolEventItem(
            toolName = "error",
            status = ToolEventStatus.Failed,
            preview = null,
            errorMessage = "network timeout",
        )
        assertEquals(false, toolEventHasDetails(failedItem))
    }

    /**
     * 运行中和失败的工具事件需要主动暴露上下文，完成态默认保持紧凑。
     */
    @Test
    fun `should expand running and failed tool events by default`() {
        assertEquals(
            true,
            shouldExpandToolEventByDefault(
                ToolEventItem("read_file", ToolEventStatus.Started, preview = "input"),
            ),
        )
        assertEquals(
            true,
            shouldExpandToolEventByDefault(
                ToolEventItem("read_file", ToolEventStatus.Failed, preview = "input", errorMessage = "failed"),
            ),
        )
        assertEquals(
            false,
            shouldExpandToolEventByDefault(
                ToolEventItem("read_file", ToolEventStatus.Finished, preview = "output"),
            ),
        )
    }
}
