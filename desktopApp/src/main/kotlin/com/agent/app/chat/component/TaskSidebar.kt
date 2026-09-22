@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)
@file:Suppress("UnstableApiUsage")

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.agent.app.chat.presentation.resolveWorkspaceForTaskCreation
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.JewelDialog
import com.agent.app.design.iconKey
import com.agent.app.platform.pickWorkspaceDirectory
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/** 工作区选择器与当前工作区平铺任务列表组成的侧栏。 */
@Composable
internal fun TaskSidebar(
    state: ChatWindowState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf(TextFieldValue()) }
    var selectedWorkspacePath by remember {
        mutableStateOf(state.ui.activeConversationOrNull?.workspacePath?.takeIf(String::isNotBlank))
    }
    var contextMenuTaskId by remember { mutableStateOf<String?>(null) }
    var renamingTask by remember { mutableStateOf<ChatTaskListItemUiState?>(null) }
    var newSessionTask by remember { mutableStateOf<ChatTaskListItemUiState?>(null) }
    var deletingTask by remember { mutableStateOf<ChatTaskListItemUiState?>(null) }
    var blockedDeleteMessage by remember { mutableStateOf<String?>(null) }
    var legacyRestoreWorkspacePath by remember { mutableStateOf<String?>(null) }
    val workspaces = remember(state.ui.activeTasks) { buildTaskSidebarWorkspaces(state.ui.activeTasks) }
    val activeWorkspacePath = state.ui.activeConversationOrNull?.workspacePath?.takeIf(String::isNotBlank)

    LaunchedEffect(activeWorkspacePath, workspaces) {
        selectedWorkspacePath = activeWorkspacePath
            ?: selectedWorkspacePath?.takeIf { path -> workspaces.any { it.path == path } }
            ?: workspaces.firstOrNull()?.path
    }

    val tasks = remember(state.ui.activeTasks, selectedWorkspacePath, searchQuery.text) {
        buildFlatTaskSidebarItems(state.ui.activeTasks, selectedWorkspacePath, searchQuery.text)
    }
    val selectedWorkspace = workspaces.firstOrNull { it.path == selectedWorkspacePath }
    val listState = rememberLazyListState()

    /** 打开目录选择器，并按旧历史恢复规则创建新任务。 */
    fun chooseAnotherWorkspace() {
        val workspacePath = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = selectedWorkspacePath,
            forceDirectoryPicker = true,
            pickWorkspaceDirectory = ::pickWorkspaceDirectory,
        ) ?: return
        if (state.legacyUnlinkedHistoryCount > 0) {
            legacyRestoreWorkspacePath = workspacePath
        } else {
            state.createConversationForWorkspace(workspacePath)
        }
    }

    Column(
        modifier = modifier.padding(if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dropdown(
            modifier = Modifier.fillMaxWidth(),
            menuContent = {
                workspaces.forEach { workspace ->
                    selectableItem(
                        selected = workspace.path == selectedWorkspacePath,
                        onClick = { selectedWorkspacePath = workspace.path },
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(workspace.label, modifier = Modifier.weight(1f))
                            Text(workspace.taskCount.toString(), color = AppMuted)
                        }
                    }
                }
                selectableItem(selected = false, onClick = ::chooseAnotherWorkspace) {
                    Text("选择其他工作区…")
                }
            },
        ) {
            Text(selectedWorkspace?.label ?: "选择工作区", color = AppText)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                modifier = Modifier.weight(1f),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索当前工作区") },
                leadingIcon = { Icon(HeaderGlyph.SEARCH.iconKey, "搜索任务") },
            )
            ActionButton(onClick = ::chooseAnotherWorkspace, tooltip = { Text("选择其他工作区") }) {
                Icon(HeaderGlyph.ADD.iconKey, "选择其他工作区")
            }
        }

        state.ui.persistenceErrorMessage?.let { message ->
            Text(message, style = JewelTheme.defaultTextStyle.copy(color = AppDanger))
        }

        DefaultButton(
            onClick = {
                selectedWorkspacePath?.let(state::createConversationForWorkspace) ?: chooseAnotherWorkspace()
            },
            modifier = Modifier.fillMaxWidth().height(TASK_CREATE_BUTTON_HEIGHT_DP.dp),
        ) { Text("新建任务") }

        Box(Modifier.fillMaxSize()) {
            when {
                selectedWorkspacePath == null -> Text(
                    "选择工作区后即可查看任务",
                    color = AppMuted,
                    modifier = Modifier.align(Alignment.Center),
                )

                tasks.isEmpty() -> Text(
                    if (searchQuery.text.isBlank()) "当前工作区还没有任务" else "没有匹配的任务",
                    color = AppMuted,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(end = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(TASK_LIST_ITEM_GAP_DP.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(tasks, key = ChatTaskListItemUiState::id) { task ->
                            TaskListItem(
                                task = task,
                                selected = task.id == state.ui.activeTaskId,
                                onClick = { state.selectConversation(task.id) },
                                contextMenuExpanded = contextMenuTaskId == task.id,
                                onOpenContextMenu = { contextMenuTaskId = task.id },
                                onDismissContextMenu = { contextMenuTaskId = null },
                                onRename = {
                                    contextMenuTaskId = null
                                    renamingTask = task
                                },
                                onRegenerateTitle = {
                                    contextMenuTaskId = null
                                    state.regenerateConversationTitle(task.id)
                                },
                                onFork = {
                                    contextMenuTaskId = null
                                    newSessionTask = task
                                },
                                onClone = {
                                    contextMenuTaskId = null
                                    state.conversationTreeController.cloneConversation(task.id)
                                },
                                onArchive = {
                                    contextMenuTaskId = null
                                    state.conversationTreeController.archiveConversation(task.id)
                                },
                                onDelete = {
                                    contextMenuTaskId = null
                                    if (state.conversationTreeController.canDelete(task.id)) {
                                        deletingTask = task
                                    } else {
                                        blockedDeleteMessage = state.conversationTreeController.deleteBlockReason(task.id)
                                    }
                                },
                                canRegenerateTitle = state.canRegenerateConversationTitle(task.id),
                                canForkOrClone = task.treeFormatVersion > 0,
                                canArchive = state.conversationTreeController.canArchive(task.id),
                                canDelete = state.conversationTreeController.canDelete(task.id),
                            )
                        }
                    }
                    VerticalScrollbar(
                        scrollState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }

    renamingTask?.let { task ->
        TaskRenameDialog(
            initialTitle = task.title,
            onDismiss = { renamingTask = null },
            onConfirm = { title ->
                state.renameConversation(task.id, title)
                renamingTask = null
            },
        )
    }
    newSessionTask?.let { task ->
        ConversationNewSessionDialog(
            taskTitle = task.title,
            candidates = state.conversationTreeController.newSessionCandidates(task.id),
            onDismiss = { newSessionTask = null },
            onConfirm = { entryId ->
                state.conversationTreeController.createConversationFromUserEntry(task.id, entryId)
                newSessionTask = null
            },
        )
    }
    deletingTask?.let { task ->
        JewelDialog(
            title = "永久删除会话",
            confirmLabel = "删除",
            onDismiss = { deletingTask = null },
            onConfirm = {
                state.deleteConversation(task.id)
                deletingTask = null
            },
        ) {
            Text("将永久删除“${task.title}”。它的直接子会话会提升为根节点，此操作无法撤销。")
        }
    }
    blockedDeleteMessage?.let { message ->
        JewelDialog(
            title = "无法删除会话",
            confirmLabel = "知道了",
            dismissLabel = null,
            onDismiss = { blockedDeleteMessage = null },
            onConfirm = { blockedDeleteMessage = null },
        ) { Text(message) }
    }
    legacyRestoreWorkspacePath?.let { workspacePath ->
        JewelDialog(
            title = "恢复隐藏历史",
            confirmLabel = "恢复历史",
            onDismiss = {
                state.createConversationForWorkspace(workspacePath)
                legacyRestoreWorkspacePath = null
            },
            onConfirm = {
                state.restoreLegacyUnlinkedHistory(workspacePath)
                state.createConversationForWorkspace(workspacePath)
                legacyRestoreWorkspacePath = null
            },
        ) {
            Text("发现 ${state.legacyUnlinkedHistoryCount} 条无来源隐藏历史，是否恢复到“$workspacePath”？")
        }
    }
}
