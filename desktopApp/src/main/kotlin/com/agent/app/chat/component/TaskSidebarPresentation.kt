package com.agent.app.chat.component

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.buildWorkspaceLabel
import com.agent.app.chat.state.toTaskListItem

/** 平铺任务侧栏中的一个工作区选项。 */
internal data class TaskSidebarWorkspaceUiState(
    val path: String,
    val label: String,
    val latestActivityAt: Long,
    val taskCount: Int,
)

/** 按整个工作区最近活动时间生成选择器选项。 */
internal fun buildTaskSidebarWorkspaces(
    conversations: List<ChatConversationUiState>,
): List<TaskSidebarWorkspaceUiState> = conversations
    .filter { conversation -> conversation.archivedAt == null && conversation.workspacePath.isNotBlank() }
    .groupBy(ChatConversationUiState::workspacePath)
    .map { (path, tasks) ->
        TaskSidebarWorkspaceUiState(
            path = path,
            label = buildWorkspaceLabel(path, tasks.firstOrNull()?.workspaceName),
            latestActivityAt = tasks.maxOfOrNull(ChatConversationUiState::updatedAt) ?: 0L,
            taskCount = tasks.size,
        )
    }
    .sortedByDescending(TaskSidebarWorkspaceUiState::latestActivityAt)

/** 仅投影选中工作区的未归档任务，并按最近活动时间倒序平铺。 */
internal fun buildFlatTaskSidebarItems(
    conversations: List<ChatConversationUiState>,
    workspacePath: String?,
    query: String,
): List<ChatTaskListItemUiState> {
    val normalizedQuery = query.trim()
    return conversations.asSequence()
        .filter { conversation -> conversation.archivedAt == null && conversation.workspacePath == workspacePath }
        .filter { conversation ->
            normalizedQuery.isEmpty() ||
                    conversation.title.contains(normalizedQuery, ignoreCase = true) ||
                    toTaskListItem(conversation).subtitle.contains(normalizedQuery, ignoreCase = true)
        }
        .sortedByDescending(ChatConversationUiState::updatedAt)
        .map(::toTaskListItem)
        .toList()
}
