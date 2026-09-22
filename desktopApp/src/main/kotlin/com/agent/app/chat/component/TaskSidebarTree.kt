package com.agent.app.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.ChatTaskTreeNodeUiState
import com.agent.app.chat.state.ChatWindowState

/** 按搜索文本保留匹配节点及其祖先，空搜索直接复用原森林。 */
internal fun filterTaskForest(
    roots: List<ChatTaskTreeNodeUiState>,
    query: String,
): List<ChatTaskTreeNodeUiState> {
    if (query.isBlank()) return roots
    return roots.mapNotNull { node ->
        val children = filterTaskForest(node.children, query)
        val matches = node.task.title.contains(query, ignoreCase = true) ||
                node.task.subtitle.contains(query, ignoreCase = true)
        if (matches || children.isNotEmpty()) node.copy(children = children) else null
    }
}

/** 渲染一个工作区中不按状态拆段的完整会话森林。 */
@Composable
internal fun TaskConversationForest(
    state: ChatWindowState,
    roots: List<ChatTaskTreeNodeUiState>,
    collapsedIds: Set<String>,
    contextMenuTaskId: String?,
    onToggleCollapsed: (String) -> Unit,
    onOpenContextMenu: (String) -> Unit,
    onDismissContextMenu: () -> Unit,
    onRename: (ChatTaskListItemUiState) -> Unit,
    onFork: (ChatTaskListItemUiState) -> Unit,
    onClone: (ChatTaskListItemUiState) -> Unit,
    onArchive: (ChatTaskListItemUiState) -> Unit,
    onDelete: (ChatTaskListItemUiState) -> Unit,
) {
    Column {
        roots.forEach { node ->
            TaskConversationTreeNode(
                state = state,
                node = node,
                visualIndent = 0,
                justBranched = false,
                collapsedIds = collapsedIds,
                contextMenuTaskId = contextMenuTaskId,
                onToggleCollapsed = onToggleCollapsed,
                onOpenContextMenu = onOpenContextMenu,
                onDismissContextMenu = onDismissContextMenu,
                onRename = onRename,
                onFork = onFork,
                onClone = onClone,
                onArchive = onArchive,
                onDelete = onDelete,
            )
        }
    }
}

/** 递归渲染一个会话节点及其可见后代。 */
@Composable
private fun TaskConversationTreeNode(
    state: ChatWindowState,
    node: ChatTaskTreeNodeUiState,
    visualIndent: Int,
    justBranched: Boolean,
    collapsedIds: Set<String>,
    contextMenuTaskId: String?,
    onToggleCollapsed: (String) -> Unit,
    onOpenContextMenu: (String) -> Unit,
    onDismissContextMenu: () -> Unit,
    onRename: (ChatTaskListItemUiState) -> Unit,
    onFork: (ChatTaskListItemUiState) -> Unit,
    onClone: (ChatTaskListItemUiState) -> Unit,
    onArchive: (ChatTaskListItemUiState) -> Unit,
    onDelete: (ChatTaskListItemUiState) -> Unit,
) {
    val task = node.task
    val collapsed = task.id in collapsedIds
    Row(
        modifier = Modifier.padding(start = (visualIndent * TASK_TREE_INDENT_DP).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(TASK_TREE_CHEVRON_SLOT_DP.dp)
                .clickable(enabled = node.children.isNotEmpty()) { onToggleCollapsed(task.id) },
            contentAlignment = Alignment.Center,
        ) {
            if (node.children.isNotEmpty()) {
                TaskSectionChevronSlot(expanded = !collapsed, visible = true)
            }
        }
        TaskListItem(
            task = task,
            selected = task.id == state.ui.activeTaskId,
            onClick = { state.selectConversation(task.id) },
            contextMenuExpanded = contextMenuTaskId == task.id,
            onOpenContextMenu = { onOpenContextMenu(task.id) },
            onDismissContextMenu = onDismissContextMenu,
            onRename = { onRename(task) },
            onFork = { onFork(task) },
            onClone = { onClone(task) },
            onArchive = { onArchive(task) },
            onDelete = { onDelete(task) },
            canForkOrClone = task.treeFormatVersion > 0,
            canArchive = state.conversationTreeController.canArchive(task.id),
            modifier = Modifier.weight(1f),
        )
    }
    if (!collapsed) {
        val hasMultipleChildren = node.children.size > 1
        val childIndent = nextTaskTreeIndent(
            currentIndent = visualIndent,
            hasMultipleChildren = hasMultipleChildren,
            justBranched = justBranched,
        )
        node.children.forEach { child ->
            TaskConversationTreeNode(
                state = state,
                node = child,
                visualIndent = childIndent,
                justBranched = hasMultipleChildren,
                collapsedIds = collapsedIds,
                contextMenuTaskId = contextMenuTaskId,
                onToggleCollapsed = onToggleCollapsed,
                onOpenContextMenu = onOpenContextMenu,
                onDismissContextMenu = onDismissContextMenu,
                onRename = onRename,
                onFork = onFork,
                onClone = onClone,
                onArchive = onArchive,
                onDelete = onDelete,
            )
        }
    }
}

/**
 * 计算下一段会话树的视觉缩进。
 *
 * 连续的单子节点链保持同一层级；只有出现兄弟分支时才向右移动。分支后的第一段
 * 再缩进一级以表明归属，之后恢复为平直链。极深分支会封顶，避免标题区域被挤没。
 */
internal fun nextTaskTreeIndent(
    currentIndent: Int,
    hasMultipleChildren: Boolean,
    justBranched: Boolean,
): Int = when {
    hasMultipleChildren -> currentIndent + 1
    justBranched && currentIndent > 0 -> currentIndent + 1
    else -> currentIndent
}.coerceAtMost(TASK_TREE_MAX_INDENT_LEVEL)

private const val TASK_TREE_INDENT_DP = 14
private const val TASK_TREE_CHEVRON_SLOT_DP = 18
internal const val TASK_TREE_MAX_INDENT_LEVEL = 4
