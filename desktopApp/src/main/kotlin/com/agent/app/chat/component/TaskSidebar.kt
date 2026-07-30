@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.chat.presentation.resolveWorkspaceForTaskCreation
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.ChatTaskStatus
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppAccent
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.AppSuccess
import com.agent.app.design.AppText
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.RingHeaderActionButton
import com.agent.app.design.RingInputField
import com.agent.app.design.RingPrimaryButton
import com.agent.app.design.RingTooltip
import com.agent.app.platform.pickWorkspaceDirectory

internal const val TASK_SECTION_TITLE_FONT_SIZE_SP = 13

private val TaskContextMenuBackground = Color(0xFF303744)
private val TaskContextMenuHoverBackground = Color(0xFF3A4658)
private val TaskContextMenuBorder = Color(0xFF49515E)

/**
 * 原型左侧 task 侧栏。
 */
@Composable
internal fun TaskSidebar(
    state: ChatWindowState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var contextMenuTaskId by remember { mutableStateOf<String?>(null) }
    var renamingTask by remember { mutableStateOf<ChatTaskListItemUiState?>(null) }
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
            state.createConversationForWorkspace(workspacePath)
        }
    }
    val filteredWorkspaces = remember(state.ui.workspaceTaskSections, searchQuery) {
        state.ui.workspaceTaskSections
            .map { workspace ->
                workspace.copy(
                    sections = workspace.sections.map { section ->
                        section.copy(
                            tasks = section.tasks.filter { task ->
                                searchQuery.isBlank() ||
                                        task.title.contains(searchQuery, ignoreCase = true) ||
                                        task.subtitle.contains(searchQuery, ignoreCase = true)
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
            RingInputField(
                modifier = Modifier.weight(1f),
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = "搜索任务",
                iconGlyph = HeaderGlyph.SEARCH,
                borderless = true,
            )
            RingHeaderActionButton(
                glyph = HeaderGlyph.ADD,
                onClick = startTaskInSelectedWorkspace,
                inline = true,
                tooltip = "在其他工作区新建任务",
            )
        }
        RingPrimaryButton(
            text = "新建任务",
            onClick = startTaskInCurrentWorkspace,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            filteredWorkspaces.forEach { workspace ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RingTooltip(text = workspace.workspacePath) {
                        Text(
                            text = workspace.label,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = AppText,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                    workspace.sections.forEach { section ->
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AppMuted,
                                fontSize = TASK_SECTION_TITLE_FONT_SIZE_SP.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.2.sp,
                            ),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        renamingTask?.let { task ->
            TaskRenameDialog(
                task = task,
                onDismiss = { renamingTask = null },
                onConfirm = { title ->
                    state.renameConversation(task.id, title)
                    renamingTask = null
                },
            )
        }
    }
}

/**
 * 左侧 task 条目。
 */
@Composable
private fun TaskListItem(
    task: ChatTaskListItemUiState,
    selected: Boolean,
    onClick: () -> Unit,
    contextMenuExpanded: Boolean,
    onOpenContextMenu: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var anchorHeightPixels by remember { mutableStateOf(0) }
    var contextMenuClickPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val contextMenuOffset = with(density) {
        DpOffset(
            x = contextMenuClickPosition.x.toDp() + 8.dp,
            y = contextMenuClickPosition.y.toDp() - anchorHeightPixels.toDp(),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                anchorHeightPixels = size.height
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    contextMenuClickPosition = event.changes.firstOrNull()?.position ?: Offset.Zero
                    onOpenContextMenu()
                }
            },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (selected) AppSelectedBackground else Color.Transparent,
            border = null,
            onClick = onClick,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = task.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AppText,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TaskStatusIndicator(task.status)
                }
            }
        }
        DropdownMenu(
            expanded = contextMenuExpanded,
            onDismissRequest = onDismissContextMenu,
            offset = contextMenuOffset,
            modifier = Modifier.width(144.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = TaskContextMenuBackground,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, TaskContextMenuBorder),
        ) {
            TaskContextMenuItem(
                text = "Fork",
                color = AppMuted.copy(alpha = 0.52f),
                enabled = false,
                onClick = {},
            )
            TaskContextMenuItem(
                text = "删除",
                color = AppDanger,
                onClick = onDelete,
            )
            TaskContextMenuItem(
                text = "Archive",
                color = AppMuted.copy(alpha = 0.52f),
                enabled = false,
                onClick = {},
            )
            TaskContextMenuItem(
                text = "重命名",
                color = AppText,
                onClick = onRename,
            )
        }
    }
}

/**
 * 右键菜单内高度紧凑的操作项。
 */
@Composable
private fun TaskContextMenuItem(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    var hovered by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(
                color = if (hovered && enabled) TaskContextMenuHoverBackground else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = color,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/**
 * 为 task 名称提供可编辑的重命名弹窗。
 */
@Composable
private fun TaskRenameDialog(
    task: ChatTaskListItemUiState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppSidebarBackground,
        titleContentColor = AppText,
        textContentColor = AppText,
        title = { Text("重命名任务") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title) },
            ) {
                Text("重命名", color = AppAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppMuted)
            }
        },
    )
}

/**
 * 在条目右侧提供新建、运行和完成三种紧凑状态标识。
 */
@Composable
private fun TaskStatusIndicator(status: ChatTaskStatus) {
    val rotationTransition = rememberInfiniteTransition(label = "running-task-indicator")
    val rotation by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_050, easing = LinearEasing),
        ),
        label = "running-task-rotation",
    )
    Canvas(
        modifier = Modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = if (status == ChatTaskStatus.RUNNING) rotation else 0f },
    ) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val inset = 2.5.dp.toPx()
        when (status) {
            ChatTaskStatus.NEW -> drawCircle(
                color = AppMuted,
                radius = (size.minDimension - inset * 2f) / 2f,
                style = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4.dp.toPx(), 2.4.dp.toPx())),
                ),
            )

            ChatTaskStatus.RUNNING -> drawArc(
                color = AppAccent,
                startAngle = -72f,
                sweepAngle = 246f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
                style = stroke,
            )

            ChatTaskStatus.DONE -> {
                drawLine(
                    color = AppSuccess,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.53f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.73f),
                    strokeWidth = 1.9.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = AppSuccess,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.73f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.3f),
                    strokeWidth = 1.9.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
