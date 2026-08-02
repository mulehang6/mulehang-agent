package com.agent.app.chat.persistence

import com.agent.app.chat.state.ChatAttachmentUiState
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证桌面聊天状态与共享持久化快照之间的完整映射。
 */
class ChatTaskSnapshotMapperTest {
    /**
     * 映射必须保留用户可见和 Agent 可续接的所有原始文本。
     */
    @Test
    fun `should preserve full timeline and history payloads`() {
        val source = ChatConversationUiState(
            id = "task-1",
            title = "完整持久化",
            workspacePath = "D:\\workspace",
            items = listOf(
                ChatMessageItem(ChatMessage(ChatRole.User, "读取 secret.txt")),
                ReasoningItem(summaryText = "摘要", rawText = "原始 reasoning", isStreaming = false),
                ToolEventItem(
                    toolName = "run_powershell",
                    status = ToolEventStatus.Finished,
                    preview = "Get-Content secret.txt",
                    resultPreview = "简短结果",
                    resultDisplay = "完整工具结果",
                ),
            ),
            attachments = listOf(ChatAttachmentUiState("D:\\workspace\\input.txt", "input.txt")),
            history = listOf(
                AgentConversationHistoryMessage.User("读取 secret.txt"),
                AgentConversationHistoryMessage.Assistant(
                    listOf(
                        AgentConversationHistoryPart.Reasoning("摘要", "原始 reasoning"),
                        AgentConversationHistoryPart.ToolCall(name = "run_powershell", argumentsPreview = "Get-Content secret.txt"),
                        AgentConversationHistoryPart.ToolResult(name = "run_powershell", resultPreview = "简短结果"),
                    ),
                ),
            ),
            reasoningEffort = ReasoningEffort.HIGH,
            executionState = ExecutionState.Idle,
            contextUsageFraction = 0.25f,
        )

        assertEquals(source, ChatTaskSnapshotMapper.toConversation(ChatTaskSnapshotMapper.toPersistedTask(source)))
    }

    /**
     * 重启后不能继续运行已消失的协程或工具进程。
     */
    @Test
    fun `should mark running task as interrupted during restore`() {
        val source = ChatConversationUiState(
            id = "task-running",
            title = "运行中",
            workspacePath = "D:\\workspace",
            reasoningEffort = ReasoningEffort.MEDIUM,
            executionState = ExecutionState.Running,
        )

        val restored = ChatTaskSnapshotMapper.toConversation(ChatTaskSnapshotMapper.toPersistedTask(source))

        assertTrue(restored.executionState is ExecutionState.Failed)
        assertEquals("执行已中断", (restored.executionState as ExecutionState.Failed).error.title)
    }
}
