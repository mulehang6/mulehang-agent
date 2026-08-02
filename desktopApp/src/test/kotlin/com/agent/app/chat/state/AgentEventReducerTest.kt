package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * 验证纯 agent 事件 reducer 保持桌面时间线与结构化历史语义。
 */
class AgentEventReducerTest {
    @Test
    fun `text delta closes reasoning and appends assistant text in order`() {
        val conversation = conversation(
            items = listOf(
                ChatMessageItem(ChatMessage(ChatRole.User, "question")),
                ReasoningItem(summaryText = "thinking", rawText = "raw", isStreaming = true),
            ),
            streamingReasoningItemIndex = 1,
        )

        val result = reduceAgentEvent(
            conversation,
            AgentStreamEvent.TextDelta("delta"),
            contextWindow = 100,
        )

        assertEquals(3, result.items.size)
        assertFalse((result.items[1] as ReasoningItem).isStreaming)
        assertEquals("delta", (result.items[2] as ChatMessageItem).message.content)
        assertEquals(2, result.streamingAssistantItemIndex)
        assertNull(result.streamingReasoningItemIndex)
        assertEquals(
            listOf(AgentConversationHistoryPart.Text("delta")),
            (result.history.single() as AgentConversationHistoryMessage.Assistant).parts,
        )
    }

    @Test
    fun `reasoning delta merges into the active reasoning block and history`() {
        val conversation = conversation(
            items = listOf(
                ReasoningItem(summaryText = "first", rawText = "raw-1", isStreaming = true),
            ),
            history = listOf(
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(AgentConversationHistoryPart.Reasoning("first", "raw-1")),
                ),
            ),
            streamingReasoningItemIndex = 0,
            streamingAssistantHistoryIndex = 0,
        )

        val result = reduceAgentEvent(
            conversation,
            AgentStreamEvent.ReasoningDelta(summary = " second", rawText = " raw-2"),
            contextWindow = 100,
        )

        val reasoning = result.items.single() as ReasoningItem
        assertEquals("first second", reasoning.summaryText)
        assertEquals("raw-1 raw-2", reasoning.rawText)
        assertEquals(0, result.streamingReasoningItemIndex)
        assertEquals(
            listOf(AgentConversationHistoryPart.Reasoning("first second", "raw-1 raw-2")),
            (result.history.single() as AgentConversationHistoryMessage.Assistant).parts,
        )
    }

    @Test
    fun `tool start closes reasoning and appends standalone tool event`() {
        val conversation = conversation(
            items = listOf(
                ReasoningItem(summaryText = "thinking", rawText = "raw", isStreaming = true),
            ),
            streamingReasoningItemIndex = 0,
        )

        val result = reduceAgentEvent(
            conversation,
            AgentStreamEvent.ToolCallStarted(
                toolCallId = "call-1",
                name = "read_file",
                argumentsPreview = "README.md",
            ),
            contextWindow = 100,
        )

        assertFalse((result.items[0] as ReasoningItem).isStreaming)
        assertNull(result.streamingReasoningItemIndex)
        assertEquals(
            ToolEventItem(
                toolName = "read_file",
                status = ToolEventStatus.Started,
                preview = "README.md",
                toolCallId = "call-1",
            ),
            result.items[1],
        )
        assertEquals(
            AgentConversationHistoryPart.ToolCall("call-1", "read_file", "README.md"),
            (result.history.single() as AgentConversationHistoryMessage.Assistant).parts.single(),
        )
    }

    /**
     * PowerShell 工具事件必须保留模型声明的操作意图，供时间线卡片展示。
     */
    @Test
    fun `terminal tool start keeps operation intent in the timeline`() {
        val result = reduceAgentEvent(
            conversation(),
            AgentStreamEvent.ToolCallStarted(
                toolCallId = "call-terminal",
                name = "run_powershell",
                argumentsPreview = "Get-ChildItem",
                operationIntent = "列出当前目录内容",
            ),
            contextWindow = 100,
        )

        assertEquals(
            "列出当前目录内容",
            (result.items.single() as ToolEventItem).operationIntent,
        )
    }

    /**
     * 同一次工具调用的完成事件必须回填到原输入卡片，避免时间线被拆散。
     */
    @Test
    fun `tool finish merges output into its matching input card`() {
        val started = reduceAgentEvent(
            conversation(),
            AgentStreamEvent.ToolCallStarted(
                toolCallId = "call-1",
                name = "grep_code",
                argumentsPreview = "{pattern=TODO}",
            ),
            contextWindow = 100,
        )

        val result = reduceAgentEvent(
            started,
            AgentStreamEvent.ToolCallFinished(
                toolCallId = "call-1",
                name = "grep_code",
                resultPreview = "src/App.kt:12",
                resultDisplay = "src/App.kt:12\nfun main() = Unit",
            ),
            contextWindow = 100,
        )

        val items = result.items.filterIsInstance<ToolEventItem>()

        assertEquals(1, items.size)
        assertEquals(ToolEventStatus.Finished, items.single().status)
        assertEquals("{pattern=TODO}", items.single().preview)
        assertEquals("call-1", items.single().toolCallId)
        assertEquals("src/App.kt:12", items.single().resultPreview)
        assertEquals("src/App.kt:12\nfun main() = Unit", items.single().resultDisplay)
    }

    /**
     * 工具调用应切分助手正文，确保工具前后的文本按真实事件顺序显示。
     */
    @Test
    fun `tool call keeps assistant text before and after the tool in timeline order`() {
        val afterTextBeforeTool = reduceAgentEvent(
            conversation(),
            AgentStreamEvent.TextDelta("before"),
            contextWindow = 100,
        )
        val afterToolStart = reduceAgentEvent(
            afterTextBeforeTool,
            AgentStreamEvent.ToolCallStarted(
                toolCallId = "call-1",
                name = "read_file",
                argumentsPreview = "README.md",
            ),
            contextWindow = 100,
        )
        val afterToolFinish = reduceAgentEvent(
            afterToolStart,
            AgentStreamEvent.ToolCallFinished(
                toolCallId = "call-1",
                name = "read_file",
                resultPreview = "contents",
                resultDisplay = "contents",
            ),
            contextWindow = 100,
        )
        val result = reduceAgentEvent(
            afterToolFinish,
            AgentStreamEvent.TextDelta("after"),
            contextWindow = 100,
        )

        assertEquals("before", (result.items[0] as ChatMessageItem).message.content)
        assertEquals(ToolEventStatus.Finished, (result.items[1] as ToolEventItem).status)
        assertEquals("after", (result.items[2] as ChatMessageItem).message.content)
        assertEquals(2, result.streamingAssistantItemIndex)
    }

    @Test
    fun `completed moves the streaming answer to the end without duplicate text`() {
        val conversation = conversation(
            items = listOf(
                ChatMessageItem(ChatMessage(ChatRole.Assistant, "draft")),
                ReasoningItem(summaryText = "done thinking", rawText = "raw", isStreaming = true),
            ),
            history = listOf(
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(AgentConversationHistoryPart.Text("draft")),
                ),
            ),
            executionState = ExecutionState.Running,
            streamingAssistantItemIndex = 0,
            streamingReasoningItemIndex = 1,
            streamingAssistantHistoryIndex = 0,
            pendingQuestion = PendingQuestionUiState(
                requestId = "question-1",
                question = "Continue?",
                options = listOf("Yes", "No"),
                allowFreeText = false,
            ),
        )

        val result = reduceAgentEvent(
            conversation,
            AgentStreamEvent.Completed("final"),
            contextWindow = 100,
        )

        assertEquals(2, result.items.size)
        assertFalse((result.items[0] as ReasoningItem).isStreaming)
        assertEquals("final", (result.items[1] as ChatMessageItem).message.content)
        assertEquals(1, result.items.filterIsInstance<ChatMessageItem>().size)
        assertEquals(ExecutionState.Idle, result.executionState)
        assertNull(result.streamingAssistantItemIndex)
        assertNull(result.streamingReasoningItemIndex)
        assertNull(result.streamingAssistantHistoryIndex)
        assertNull(result.pendingQuestion)
        assertNull(result.pendingApproval)
        assertEquals(
            listOf(AgentConversationHistoryPart.Text("final")),
            (result.history.single() as AgentConversationHistoryMessage.Assistant).parts,
        )
    }

    @Test
    fun `failed closes reasoning and marks the active tool failure in place`() {
        val conversation = conversation(
            items = listOf(
                ReasoningItem(summaryText = "thinking", rawText = "raw", isStreaming = true),
                ToolEventItem("read_file", ToolEventStatus.Started, "README.md"),
            ),
            executionState = ExecutionState.Running,
            streamingReasoningItemIndex = 0,
            pendingApproval = PendingApprovalUiState(
                requestId = "approval-1",
                toolName = "read_file",
                summary = "Read README",
                targetPath = "README.md",
                payloadPreview = null,
            ),
        )

        val result = reduceAgentEvent(
            conversation,
            AgentStreamEvent.Failed("file not found"),
            contextWindow = 100,
        )

        assertFalse((result.items[0] as ReasoningItem).isStreaming)
        assertEquals(
            ToolEventItem(
                toolName = "read_file",
                status = ToolEventStatus.Failed,
                preview = "README.md",
                errorMessage = "file not found",
            ),
            result.items[1],
        )
        assertEquals(
            ExecutionState.Failed(AppError("Agent 执行失败", "file not found")),
            result.executionState,
        )
        assertNull(result.streamingAssistantItemIndex)
        assertNull(result.streamingReasoningItemIndex)
        assertNull(result.streamingAssistantHistoryIndex)
        assertNull(result.pendingQuestion)
        assertNull(result.pendingApproval)
    }

    private fun conversation(
        items: List<com.agent.shared.chat.model.ConversationItem> = emptyList(),
        history: List<AgentConversationHistoryMessage> = emptyList(),
        executionState: ExecutionState = ExecutionState.Idle,
        streamingAssistantItemIndex: Int? = null,
        streamingReasoningItemIndex: Int? = null,
        streamingAssistantHistoryIndex: Int? = null,
        pendingQuestion: PendingQuestionUiState? = null,
        pendingApproval: PendingApprovalUiState? = null,
    ): ChatConversationUiState = ChatConversationUiState(
        id = "conversation",
        title = "Title",
        workspacePath = "E:\\workspace",
        items = items,
        history = history,
        executionState = executionState,
        streamingAssistantItemIndex = streamingAssistantItemIndex,
        streamingReasoningItemIndex = streamingReasoningItemIndex,
        streamingAssistantHistoryIndex = streamingAssistantHistoryIndex,
        pendingQuestion = pendingQuestion,
        pendingApproval = pendingApproval,
    )
}
