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

    /**
     * "已完成"分组应按最后操作时间倒序展示，最近更新的排在最前。
     */
    @Test
    fun `should order done tasks by last updated time descending`() {
        val ui = ChatWindowUiState(
            tasks = listOf(
                ChatConversationUiState(
                    id = "older",
                    title = "较早",
                    workspacePath = "D:\\work\\alpha",
                    updatedAt = 100L,
                    items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "完成"))),
                ),
                ChatConversationUiState(
                    id = "newer",
                    title = "较新",
                    workspacePath = "D:\\work\\alpha",
                    updatedAt = 300L,
                    items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "完成"))),
                ),
                ChatConversationUiState(
                    id = "middle",
                    title = "中间",
                    workspacePath = "D:\\work\\alpha",
                    updatedAt = 200L,
                    items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "完成"))),
                ),
            ),
            activeTaskId = "older",
        )

        val doneIds = ui.taskSections
            .first { it.group == ChatTaskGroup.DONE }
            .tasks
            .map { it.id }

        assertEquals(listOf("newer", "middle", "older"), doneIds)
    }

    /**
     * 同一工作区内的"已完成"分组也应遵循最近更新倒序。
     */
    @Test
    fun `should order done tasks by last updated time within workspace`() {
        val ui = ChatWindowUiState(
            tasks = listOf(
                ChatConversationUiState(
                    id = "stale",
                    title = "旧任务",
                    workspacePath = "D:\\work\\alpha",
                    updatedAt = 100L,
                    items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "完成"))),
                ),
                ChatConversationUiState(
                    id = "fresh",
                    title = "新任务",
                    workspacePath = "D:\\work\\alpha",
                    updatedAt = 400L,
                    items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "完成"))),
                ),
            ),
            activeTaskId = "stale",
        )

        val doneIds = ui.workspaceTaskSections
            .single()
            .sections.first { it.group == ChatTaskGroup.DONE }
            .tasks
            .map { it.id }

        assertEquals(listOf("fresh", "stale"), doneIds)
    }
}
