package com.agent.app.chat.state

import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证侧栏以工作区为一级、任务状态为二级的分组模型。
 */
class WorkspaceTaskGroupingTest {

    /**
     * 同一工作区内应分别呈现新建、运行和完成任务，工作区之间不得混合。
     */
    @Test
    fun `should group sidebar tasks by workspace then execution state`() {
        val ui = ChatWindowUiState(
            tasks = listOf(
                ChatConversationUiState(
                    id = "new",
                    title = DEFAULT_CONVERSATION_TITLE,
                    workspacePath = "D:\\work\\alpha",
                ),
                ChatConversationUiState(
                    id = "running",
                    title = "正在分析",
                    workspacePath = "D:\\work\\alpha",
                    executionState = ExecutionState.Running,
                ),
                ChatConversationUiState(
                    id = "done-alpha",
                    title = "已完成任务",
                    workspacePath = "D:\\work\\alpha",
                    items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "完成"))),
                ),
                ChatConversationUiState(
                    id = "done-beta",
                    title = "另一个工作区任务",
                    workspacePath = "D:\\work\\beta",
                    items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "完成"))),
                ),
            ),
            activeTaskId = "new",
        )

        val workspaces = ui.workspaceTaskSections

        assertEquals(listOf("alpha", "beta"), workspaces.map { it.label })
        assertEquals(
            listOf("new", "running"),
            workspaces.first().sections.first { it.group == ChatTaskGroup.RUNNING }.tasks.map { it.id },
        )
        assertEquals(
            listOf("done-alpha"),
            workspaces.first().sections.first { it.group == ChatTaskGroup.DONE }.tasks.map { it.id },
        )
        assertEquals(ChatTaskStatus.NEW, workspaces.first().sections.first().tasks.first().status)
        assertEquals(ChatTaskStatus.RUNNING, workspaces.first().sections.first().tasks.last().status)
        assertEquals(ChatTaskStatus.DONE, workspaces.last().sections.last().tasks.single().status)
    }
}
