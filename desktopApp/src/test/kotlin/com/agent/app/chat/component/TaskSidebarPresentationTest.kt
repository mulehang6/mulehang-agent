package com.agent.app.chat.component

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.DEFAULT_CONVERSATION_TITLE
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState
import kotlin.test.Test
import kotlin.test.assertEquals

/** 覆盖侧栏工作区选择器与平铺任务投影。 */
class TaskSidebarPresentationTest {
    /** 工作区按最近任务活动排序，归档任务不参与计数。 */
    @Test
    fun `workspaces are sorted by latest active task`() {
        val conversations = listOf(
            conversation("old", "D:/a", 10L),
            conversation("new", "D:/b", 30L),
            conversation("archived", "D:/a", 50L, archivedAt = 60L),
        )

        val workspaces = buildTaskSidebarWorkspaces(conversations)

        assertEquals(listOf("D:/b", "D:/a"), workspaces.map { it.path })
        assertEquals(listOf(1, 1), workspaces.map { it.taskCount })
    }

    /** 选中工作区中的父子会话按活动时间平铺，关系不会产生视觉缩进。 */
    @Test
    fun `tasks stay flat and searchable within selected workspace`() {
        val parent = conversation("parent", "D:/a", 10L, title = "修复登录")
        val child = conversation("child", "D:/a", 20L, title = "修复登录 - fork")
            .copy(parentConversationId = parent.id)
        val other = conversation("other", "D:/b", 40L, title = "其他")

        val all = buildFlatTaskSidebarItems(listOf(parent, child, other), "D:/a", "")
        val filtered = buildFlatTaskSidebarItems(listOf(parent, child, other), "D:/a", "fork")

        assertEquals(listOf("child", "parent"), all.map { it.id })
        assertEquals(listOf("child"), filtered.map { it.id })
    }

    /** 空白会话即使使用树格式，也必须保持新建状态而不是运行圈。 */
    @Test
    fun `blank task remains new and idle`() {
        val task = conversation("blank", "D:/a", 1L, title = DEFAULT_CONVERSATION_TITLE)
            .copy(executionState = ExecutionState.Idle)

        assertEquals(com.agent.app.chat.state.ChatTaskStatus.NEW, buildFlatTaskSidebarItems(listOf(task), "D:/a", "").single().status)
    }

    /** 创建可用于平铺投影的最小会话。 */
    private fun conversation(
        id: String,
        workspacePath: String,
        updatedAt: Long,
        title: String = id,
        archivedAt: Long? = null,
    ): ChatConversationUiState = ChatConversationUiState(
        id = id,
        title = title,
        workspacePath = workspacePath,
        archivedAt = archivedAt,
        updatedAt = updatedAt,
        items = if (title == DEFAULT_CONVERSATION_TITLE) {
            emptyList()
        } else {
            listOf(ChatMessageItem(ChatMessage(ChatRole.User, title)))
        },
    )
}
