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
            ToolEventItem("read_file", ToolEventStatus.Started, "README.md"),
            result.items[1],
        )
        assertEquals(
            AgentConversationHistoryPart.ToolCall("call-1", "read_file", "README.md"),
            (result.history.single() as AgentConversationHistoryMessage.Assistant).parts.single(),
        )
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
    }

    private fun conversation(
        items: List<com.agent.shared.chat.model.ConversationItem> = emptyList(),
        history: List<AgentConversationHistoryMessage> = emptyList(),
        executionState: ExecutionState = ExecutionState.Idle,
        streamingAssistantItemIndex: Int? = null,
        streamingReasoningItemIndex: Int? = null,
        streamingAssistantHistoryIndex: Int? = null,
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
    )
}
