package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.ChatTaskTreeNodeUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppText
import com.agent.app.design.JewelDialog
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/** 设置页中按工作区展示归档会话树，并提供单点恢复与永久删除。 */
@Composable
internal fun ArchivedSessionsSettingsContent(
    state: ChatWindowState,
    search: String,
) {
    var deletingTask by remember { mutableStateOf<ChatTaskListItemUiState?>(null) }
    val workspaces = remember(state.ui.archivedWorkspaceTaskSections, search) {
        state.ui.archivedWorkspaceTaskSections
            .map { workspace -> workspace.copy(roots = filterTaskForest(workspace.roots, search)) }
            .filter { it.roots.isNotEmpty() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "归档会话",
            style = JewelTheme.defaultTextStyle.copy(color = AppText, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = "归档内容不会出现在任务侧栏。恢复仅作用于所选会话，永久删除不会连带删除子会话。",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        if (workspaces.isEmpty()) {
            Text(
                text = if (search.isBlank()) "暂无归档会话。" else "没有匹配的归档会话。",
                style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
            )
        }
        workspaces.forEach { workspace ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = workspace.label,
                    style = JewelTheme.defaultTextStyle.copy(color = AppText, fontWeight = FontWeight.SemiBold),
                )
                workspace.roots.forEach { node ->
                    ArchivedConversationNode(
                        state = state,
                        node = node,
                        depth = 0,
                        onDelete = { task -> deletingTask = task },
                    )
                }
            }
        }
    }
    deletingTask?.let { task ->
        JewelDialog(
            title = "永久删除归档会话",
            confirmLabel = "删除",
            onDismiss = { deletingTask = null },
            onConfirm = {
                state.conversationTreeController.deleteConversation(task.id)
                deletingTask = null
            },
        ) {
            Text("将永久删除“${task.title}”。直接子会话会提升为根节点，此操作无法撤销。")
        }
    }
}

/** 渲染归档森林中的一个节点及其后代。 */
@Composable
private fun ArchivedConversationNode(
    state: ChatWindowState,
    node: ChatTaskTreeNodeUiState,
    depth: Int,
    onDelete: (ChatTaskListItemUiState) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * ARCHIVED_TREE_INDENT_DP).dp)
            .background(AppPanelBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = node.task.title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DefaultButton(
            onClick = { state.conversationTreeController.restoreConversation(node.task.id) },
        ) { Text("恢复") }
        OutlinedButton(onClick = { onDelete(node.task) }) { Text("删除") }
    }
    node.children.forEach { child ->
        ArchivedConversationNode(state, child, depth + 1, onDelete)
    }
}

private const val ARCHIVED_TREE_INDENT_DP = 16
