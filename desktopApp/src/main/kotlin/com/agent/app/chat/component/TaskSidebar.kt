@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.chat.presentation.resolveWorkspaceForTaskCreation
import com.agent.app.chat.state.ChatTaskGroup
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.ChatTaskStatus
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.ConversationTitleState
import com.agent.app.chat.state.WorkspaceTaskSectionUiState
import com.agent.app.design.AppAccent
import com.agent.app.design.AppDanger
import com.agent.app.design.AppHoverBackground
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppSuccess
import com.agent.app.design.AppText
import com.agent.app.design.PopupMenuBackground
import com.agent.app.design.PopupMenuBorder
import com.agent.app.design.PopupMenuSelectedBackground
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.JewelDialog
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.OffsetPopupPositionProvider
import com.agent.app.design.iconKey
import com.agent.app.platform.pickWorkspaceDirectory
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip

/**
 * 原型左侧 task 侧栏。
 */
@Composable
internal fun TaskSidebar(
    state: ChatWindowState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf(TextFieldValue()) }
    var contextMenuTaskId by remember { mutableStateOf<String?>(null) }
    var renamingTask by remember { mutableStateOf<ChatTaskListItemUiState?>(null) }
    var workspaceContextMenuPath by remember { mutableStateOf<String?>(null) }
    var workspaceContextMenuClickPosition by remember { mutableStateOf(Offset.Zero) }
    var workspaceContextMenuAnchorHeightPixels by remember { mutableStateOf(0) }
    var editingWorkspace by remember { mutableStateOf<WorkspaceTaskSectionUiState?>(null) }
    var disconnectingWorkspace by remember { mutableStateOf<WorkspaceTaskSectionUiState?>(null) }
    var legacyRestoreWorkspacePath by remember { mutableStateOf<String?>(null) }
    var sectionCollapsedOverrides by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    var collapsedWorkspaceKeys by remember { mutableStateOf(emptySet<String>()) }
    val density = LocalDensity.current
    val palette = LocalDesktopPalette.current
    val startTaskInCurrentWorkspace: () -> Unit = {
        val workspacePath = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = state.ui.activeConversationOrNull?.workspacePath,
            forceDirectoryPicker = false,
            pickWorkspaceDirectory = ::pickWorkspaceDirectory,
        )
        if (workspacePath != null) {
            state.createConversationForWorkspace(workspacePath)
        }
    }
    val startTaskInSelectedWorkspace: () -> Unit = {
        val workspacePath = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = state.ui.activeConversationOrNull?.workspacePath,
            forceDirectoryPicker = true,
            pickWorkspaceDirectory = ::pickWorkspaceDirectory,
        )
        if (workspacePath != null) {
            if (state.legacyUnlinkedHistoryCount > 0) {
                legacyRestoreWorkspacePath = workspacePath
            } else {
                state.createConversationForWorkspace(workspacePath)
            }
        }
    }
    val filteredWorkspaces = remember(state.ui.workspaceTaskSections, searchQuery.text) {
        state.ui.workspaceTaskSections
            .map { workspace ->
                workspace.copy(
                    sections = workspace.sections.map { section ->
                        section.copy(
                            tasks = section.tasks.filter { task ->
                                searchQuery.text.isBlank() ||
                                        task.title.contains(searchQuery.text, ignoreCase = true) ||
                                        task.subtitle.contains(searchQuery.text, ignoreCase = true)
                            },
                        )
                    },
                )
            }
            .filter { workspace -> workspace.sections.any { section -> section.tasks.isNotEmpty() } }
    }
    Column(
        modifier = modifier
            .padding(if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                modifier = Modifier.weight(1f),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索任务") },
                leadingIcon = { Icon(HeaderGlyph.SEARCH.iconKey, "搜索任务") },
            )
            ActionButton(
                onClick = startTaskInSelectedWorkspace,
                tooltip = { Text("在其他工作区新建任务") },
            ) { Icon(HeaderGlyph.ADD.iconKey, "在其他工作区新建任务") }
        }
        state.ui.persistenceErrorMessage?.let { message ->
            Text(
                text = message,
                style = JewelTheme.defaultTextStyle.copy(color = AppDanger),
            )
        }
        DefaultButton(
            onClick = startTaskInCurrentWorkspace,
            modifier = Modifier
                .fillMaxWidth()
                .height(TASK_CREATE_BUTTON_HEIGHT_DP.dp),
        ) { Text("新建任务") }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            filteredWorkspaces.forEach { workspace ->
                Column(verticalArrangement = Arrangement.spacedBy(TASK_WORKSPACE_CONTENT_GAP_DP.dp)) {
                    val workspaceKey = workspaceCollapseKey(workspace.workspacePath)
                    val workspaceCollapsed = workspaceKey in collapsedWorkspaceKeys
                    var workspaceHovered by remember(workspaceKey) { mutableStateOf(false) }
                    var workspaceHeaderHeightPixels by remember(workspaceKey) { mutableStateOf(0) }
                    val workspaceIssue = state.workspaceIssueForPath(workspace.workspacePath)
                    Box {
                    Tooltip(tooltip = { Text(workspace.workspacePath) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TASK_SECTION_ROW_HEIGHT_DP.dp)
                                 .onSizeChanged { size -> workspaceHeaderHeightPixels = size.height }
                                 .background(
                                     color = if (workspaceHovered) palette.hoverBackground else Color.Transparent,
                                     shape = RoundedCornerShape(10.dp),
                                 )
                                 .onPointerEvent(PointerEventType.Press) { event ->
                                      if (event.buttons.isSecondaryPressed && workspace.workspacePath.isNotBlank()) {
                                          workspaceContextMenuClickPosition = event.changes.firstOrNull()?.position ?: Offset.Zero
                                          workspaceContextMenuAnchorHeightPixels = workspaceHeaderHeightPixels
                                          workspaceContextMenuPath = workspace.workspacePath
                                     }
                                 }
                                 .onPointerEvent(PointerEventType.Enter) { workspaceHovered = true }
                                .onPointerEvent(PointerEventType.Exit) { workspaceHovered = false }
                                .clickable {
                                    collapsedWorkspaceKeys = if (workspaceCollapsed) {
                                        collapsedWorkspaceKeys - workspaceKey
                                    } else {
                                        collapsedWorkspaceKeys + workspaceKey
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = workspace.label,
                                modifier = Modifier.weight(1f),
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = AppText,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            workspaceIssue?.let {
                                Text(
                                    text = if (workspace.workspacePath.isBlank()) "未关联" else "路径不可用",
                                    modifier = Modifier.padding(end = 6.dp),
                                    style = JewelTheme.defaultTextStyle.copy(color = AppDanger),
                                )
                            }
                            TaskSectionChevronSlot(
                                expanded = !workspaceCollapsed,
                                visible = shouldShowTaskSectionChevron(workspaceHovered),
                            )
                        }
                    }
                    if (workspace.workspacePath.isNotBlank()) {
                        val workspaceContextMenuOffset = contextMenuOffsetForPointer(
                            pointerPosition = workspaceContextMenuClickPosition,
                            anchorHeightPixels = workspaceContextMenuAnchorHeightPixels,
                            density = density.density,
                        )
                        if (workspaceContextMenuPath == workspace.workspacePath) {
                            PopupMenu(
                                onDismissRequest = {
                                    workspaceContextMenuPath = null
                                    true
                                },
                                popupPositionProvider = remember(workspaceContextMenuOffset, density.density) {
                                    OffsetPopupPositionProvider(workspaceContextMenuOffset, density.density)
                                },
                                modifier = Modifier.width(TaskContextMenuWidth),
                            ) {
                                selectableItem(selected = false, onClick = {
                                    workspaceContextMenuPath = null
                                    editingWorkspace = workspace
                                }) { Text(workspaceContextMenuLabels().first()) }
                                selectableItem(selected = false, onClick = {
                                    workspaceContextMenuPath = null
                                    disconnectingWorkspace = workspace
                                }) { Text(workspaceContextMenuLabels().last()) }
                            }
                        }
                    }
                    }
                    AnimatedVisibility(
                        visible = !workspaceCollapsed,
                        enter = expandVertically(tween(durationMillis = 200)) + fadeIn(tween(durationMillis = 140)),
                        exit = shrinkVertically(tween(durationMillis = 150)) + fadeOut(tween(durationMillis = 110)),
                    ) {
                        Column(
                            modifier = Modifier.padding(start = TASK_SECTION_INDENT_DP.dp),
                            verticalArrangement = Arrangement.spacedBy(TASK_SECTION_CONTENT_GAP_DP.dp),
                        ) {
                            workspace.sections.forEach { section ->
                                Column(verticalArrangement = Arrangement.spacedBy(TASK_SECTION_CONTENT_GAP_DP.dp)) {
                                    val sectionKey = "${workspace.workspacePath}:${section.title}"
                                    val collapsed = sectionCollapsedOverrides[sectionKey]
                                        ?: shouldCollapseTaskSectionByDefault(section.group)
                                    var sectionHovered by remember(sectionKey) { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(TASK_SECTION_ROW_HEIGHT_DP.dp)
                                            .background(
                                                color = if (sectionHovered) palette.hoverBackground else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp),
                                            )
                                            .onPointerEvent(PointerEventType.Enter) { sectionHovered = true }
                                            .onPointerEvent(PointerEventType.Exit) { sectionHovered = false }
                                            .clickable {
                                                sectionCollapsedOverrides = sectionCollapsedOverrides + (sectionKey to !collapsed)
                                            }
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = section.title,
                                            modifier = Modifier.weight(1f),
                                            style = JewelTheme.defaultTextStyle.copy(
                                                color = AppMuted,
                                                fontSize = TASK_SECTION_TITLE_FONT_SIZE_SP.sp,
                                                lineHeight = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.2.sp,
                                            ),
                                        )
                                        TaskSectionChevronSlot(
                                            expanded = !collapsed,
                                            visible = shouldShowTaskSectionChevron(sectionHovered),
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = !collapsed,
                                        enter = expandVertically(tween(durationMillis = 200)) + fadeIn(tween(durationMillis = 140)),
                                        exit = shrinkVertically(tween(durationMillis = 150)) + fadeOut(tween(durationMillis = 110)),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(start = TASK_LIST_ITEM_INDENT_DP.dp),
                                            verticalArrangement = Arrangement.spacedBy(TASK_LIST_ITEM_GAP_DP.dp),
                                        ) {
                                            section.tasks.forEach { task ->
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
                                                    onDelete = {
                                                        contextMenuTaskId = null
                                                        state.deleteConversation(task.id)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
        editingWorkspace?.let { workspace ->
            WorkspaceEditDialog(
                workspace = workspace,
                onDismiss = { editingWorkspace = null },
                onConfirm = { name, path -> state.editWorkspace(workspace.workspacePath, name, path) },
            )
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
        disconnectingWorkspace?.let { workspace ->
            val isDisconnectingActiveWorkspace =
                state.ui.activeConversationOrNull?.workspacePath == workspace.workspacePath
            JewelDialog(
                title = "解除工作区关联",
                confirmLabel = "解除关联",
                onDismiss = { disconnectingWorkspace = null },
                onConfirm = {
                    state.disconnectWorkspace(workspace.workspacePath)
                    disconnectingWorkspace = null
                },
            ) {
                Text(
                    if (isDisconnectingActiveWorkspace) {
                        "“${workspace.label}”下的任务将保留为未关联历史，并切换到最近可用工作区的新任务；若无可用工作区则返回欢迎页。"
                    } else {
                        "“${workspace.label}”下的任务将保留为未关联历史，之后可重新关联目录。"
                    },
                )
            }
        }
    }
}
