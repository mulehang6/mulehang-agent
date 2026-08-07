@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.chat.presentation.resolveWorkspaceForTaskCreation
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.ChatTaskStatus
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.chat.state.ConversationTitleState
import com.agent.app.design.AppAccent
import com.agent.app.design.AppDanger
import com.agent.app.design.AppHoverBackground
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.AppSuccess
import com.agent.app.design.AppText
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.MenuGrowthOrigin
import com.agent.app.design.RingHeaderActionButton
import com.agent.app.design.RingInputField
import com.agent.app.design.RingPrimaryButton
import com.agent.app.design.RingTooltip
import com.agent.app.design.menuGrowthTransformOrigin
import com.agent.app.design.rememberMenuGrowthMotion
import com.agent.app.platform.pickWorkspaceDirectory

internal const val TASK_SECTION_TITLE_FONT_SIZE_SP = 13
internal const val TASK_LIST_ITEM_VERTICAL_PADDING_DP = 0
internal const val TASK_LIST_ITEM_GAP_DP = 4
internal const val TASK_CREATE_BUTTON_HEIGHT_DP = 40
internal const val TASK_SECTION_ROW_HEIGHT_DP = 36
internal const val TASK_LIST_ITEM_HEIGHT_DP = 40
internal const val TASK_SECTION_CONTENT_GAP_DP = 4
internal const val TITLE_GENERATING_DOT_COUNT = 3
internal const val TASK_CONTEXT_MENU_HOVER_TRANSITION_DURATION_MILLIS = 80

/** 折叠箭头仅在对应的工作区或状态分组行被鼠标悬浮时显示。 */
internal fun shouldShowTaskSectionChevron(hovered: Boolean): Boolean = hovered

/**
 * 返回工作区标题使用的折叠状态键，避免与状态分组的折叠状态混用。
 */
internal fun workspaceCollapseKey(workspacePath: String): String = "workspace:$workspacePath"

/**
 * 标题生成期间只保留三点占位，避免把用户首条消息误当成最终任务名。
 */
internal fun shouldShowConversationTitleText(titleState: ConversationTitleState): Boolean =
    titleState != ConversationTitleState.GENERATING

/**
 * 返回侧栏和标题栏共用的任务上下文菜单操作顺序。
 */
internal fun taskContextMenuLabels(): List<String> = listOf("Fork", "删除", "Archive", "重命名")

internal val TaskContextMenuBackground = Color(0xFF262627)
internal val TaskContextMenuHoverBackground = Color(0xFF245286)
internal val TaskContextMenuBorder = Color(0xFF47494D)
internal val TaskContextMenuDanger = Color(0xFFFF5C78)
internal val TaskContextMenuWidth = 180.dp
internal val TaskContextMenuShape = RoundedCornerShape(12.dp)
internal val TaskContextMenuItemShape = RoundedCornerShape(8.dp)
internal val TaskContextMenuItemHeight = 36.dp
private val TaskSectionHoverBackground = Color(0xFF303744)

/**
 * 仅当可用菜单项被悬浮时显示 JetBrains Air 风格的蓝色高亮。
 */
internal fun taskContextMenuItemBackground(
    hovered: Boolean,
    enabled: Boolean,
): Color = if (hovered && enabled) TaskContextMenuHoverBackground else Color.Transparent

/**
 * 将一个屏幕物理像素转换成当前 Compose 密度下的逻辑 Dp 宽度。
 */
internal fun onePhysicalPixel(density: Float): Dp = (1f / density).dp

/**
 * 将条目内部的鼠标像素坐标换算为菜单相对锚点的 Dp 偏移，使右键菜单紧贴光标打开。
 */
internal fun contextMenuOffsetForPointer(
    pointerPosition: Offset,
    anchorHeightPixels: Int,
    density: Float,
): DpOffset = DpOffset(
    x = (pointerPosition.x / density).dp + 8.dp,
    y = ((pointerPosition.y - anchorHeightPixels) / density).dp,
)

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
    var collapsedSectionKeys by remember { mutableStateOf(emptySet<String>()) }
    var collapsedWorkspaceKeys by remember { mutableStateOf(emptySet<String>()) }
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
        state.ui.persistenceErrorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(color = AppDanger),
            )
        }
        RingPrimaryButton(
            text = "新建任务",
            onClick = startTaskInCurrentWorkspace,
            compact = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(TASK_CREATE_BUTTON_HEIGHT_DP.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            filteredWorkspaces.forEach { workspace ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val workspaceKey = workspaceCollapseKey(workspace.workspacePath)
                    val workspaceCollapsed = workspaceKey in collapsedWorkspaceKeys
                    var workspaceHovered by remember(workspaceKey) { mutableStateOf(false) }
                    RingTooltip(text = workspace.workspacePath) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TASK_SECTION_ROW_HEIGHT_DP.dp)
                                .background(
                                    color = if (workspaceHovered) TaskSectionHoverBackground else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
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
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = AppText,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            TaskSectionChevronSlot(
                                expanded = !workspaceCollapsed,
                                visible = shouldShowTaskSectionChevron(workspaceHovered),
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = !workspaceCollapsed,
                        enter = expandVertically(tween(durationMillis = 200)) + fadeIn(tween(durationMillis = 140)),
                        exit = shrinkVertically(tween(durationMillis = 150)) + fadeOut(tween(durationMillis = 110)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(TASK_SECTION_CONTENT_GAP_DP.dp)) {
                            workspace.sections.forEach { section ->
                                Column(verticalArrangement = Arrangement.spacedBy(TASK_SECTION_CONTENT_GAP_DP.dp)) {
                                    val sectionKey = "${workspace.workspacePath}:${section.title}"
                                    val collapsed = sectionKey in collapsedSectionKeys
                                    var sectionHovered by remember(sectionKey) { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(TASK_SECTION_ROW_HEIGHT_DP.dp)
                                            .background(
                                                color = if (sectionHovered) TaskSectionHoverBackground else Color.Transparent,
                                                shape = RoundedCornerShape(10.dp),
                                            )
                                            .onPointerEvent(PointerEventType.Enter) { sectionHovered = true }
                                            .onPointerEvent(PointerEventType.Exit) { sectionHovered = false }
                                            .clickable {
                                                collapsedSectionKeys = if (collapsed) {
                                                    collapsedSectionKeys - sectionKey
                                                } else {
                                                    collapsedSectionKeys + sectionKey
                                                }
                                            }
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = section.title,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodySmall.copy(
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
                                        Column(verticalArrangement = Arrangement.spacedBy(TASK_LIST_ITEM_GAP_DP.dp)) {
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
    }
}

/** 保留箭头槽位并在悬浮时淡入，避免标题文字随图标出现而发生横向跳动。 */
@Composable
private fun TaskSectionChevronSlot(expanded: Boolean, visible: Boolean) {
    Box(
        modifier = Modifier.size(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(durationMillis = 120)) + scaleIn(
                animationSpec = tween(durationMillis = 120),
                initialScale = 0.85f,
            ),
            exit = fadeOut(tween(durationMillis = 100)) + scaleOut(
                animationSpec = tween(durationMillis = 100),
                targetScale = 0.85f,
            ),
        ) {
            TaskSectionChevron(expanded = expanded)
        }
    }
}

/**
 * 使用几何线条绘制状态分组箭头，避免依赖字体 glyph 而显示为方框。
 */
@Composable
private fun TaskSectionChevron(expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "task-section-chevron",
    )
    Canvas(
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val stroke = 1.8.dp.toPx()
        drawLine(
            color = AppMuted,
            start = Offset(size.width * 0.33f, size.height * 0.24f),
            end = Offset(size.width * 0.65f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = AppMuted,
            start = Offset(size.width * 0.65f, size.height * 0.5f),
            end = Offset(size.width * 0.33f, size.height * 0.76f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
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
    var hovered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val contextMenuMotion = rememberMenuGrowthMotion(
        expanded = contextMenuExpanded,
        label = "task-context-menu",
    )
    val contextMenuOffset = contextMenuOffsetForPointer(
        pointerPosition = contextMenuClickPosition,
        anchorHeightPixels = anchorHeightPixels,
        density = density.density,
    )
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
            }
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(TASK_LIST_ITEM_HEIGHT_DP.dp),
            shape = RoundedCornerShape(12.dp),
            color = when {
                selected -> AppSelectedBackground
                hovered -> AppHoverBackground
                else -> Color.Transparent
            },
            border = null,
            onClick = onClick,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 10.dp,
                        vertical = TASK_LIST_ITEM_VERTICAL_PADDING_DP.dp,
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (shouldShowConversationTitleText(task.titleState)) {
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
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    AnimatedVisibility(
                        visible = task.titleState == ConversationTitleState.GENERATING,
                        enter = fadeIn(tween(PENDING_CARD_ENTER_DURATION_MILLIS)) +
                                scaleIn(tween(PENDING_CARD_ENTER_DURATION_MILLIS), initialScale = 0.95f),
                        exit = fadeOut(tween(PENDING_CARD_EXIT_DURATION_MILLIS)) +
                                scaleOut(tween(PENDING_CARD_EXIT_DURATION_MILLIS), targetScale = 0.95f),
                    ) {
                        TitleGeneratingIndicator()
                    }
                    TaskStatusIndicator(task.status)
                }
            }
        }
        DropdownMenu(
            expanded = contextMenuExpanded,
            onDismissRequest = onDismissContextMenu,
            offset = contextMenuOffset,
            modifier = Modifier
                .width(TaskContextMenuWidth)
                .graphicsLayer {
                    transformOrigin = menuGrowthTransformOrigin(MenuGrowthOrigin.Context)
                    scaleX = contextMenuMotion.scale
                    scaleY = contextMenuMotion.scale
                    alpha = contextMenuMotion.alpha
                    translationY = contextMenuMotion.translationYDp * density.density
                },
            shape = TaskContextMenuShape,
            containerColor = TaskContextMenuBackground,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(onePhysicalPixel(density.density), TaskContextMenuBorder),
        ) {
            TaskContextMenuActions(
                onDelete = onDelete,
                onRename = onRename,
            )
        }
    }
}

/**
 * 侧栏和标题栏共用的任务上下文菜单内容，确保操作顺序、可用状态与视觉一致。
 */
@Composable
internal fun TaskContextMenuActions(
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    TaskContextMenuItem(
        text = taskContextMenuLabels()[0],
        color = AppMuted.copy(alpha = 0.52f),
        enabled = false,
        onClick = {},
    )
    TaskContextMenuItem(
        text = taskContextMenuLabels()[1],
        color = TaskContextMenuDanger,
        onClick = onDelete,
    )
    TaskContextMenuItem(
        text = taskContextMenuLabels()[2],
        color = AppMuted.copy(alpha = 0.52f),
        enabled = false,
        onClick = {},
    )
    TaskContextMenuItem(
        text = taskContextMenuLabels()[3],
        color = AppText,
        onClick = onRename,
    )
}

/**
 * 右键菜单内高度紧凑的操作项。
 */
@Composable
internal fun TaskContextMenuItem(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    var hovered by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = taskContextMenuItemBackground(hovered = hovered, enabled = enabled),
        animationSpec = tween(durationMillis = TASK_CONTEXT_MENU_HOVER_TRANSITION_DURATION_MILLIS),
        label = "task-context-menu-hover",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TaskContextMenuItemHeight)
            .background(
                color = backgroundColor,
                shape = TaskContextMenuItemShape,
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/**
 * 为 task 名称提供可编辑的重命名弹窗。
 */
@Composable
internal fun TaskRenameDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
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
 * AI 标题生成中的三点呼吸提示；与任务运行中的旋转进度圈明确区分。
 */
@Composable
internal fun TitleGeneratingIndicator() {
    val transition = rememberInfiniteTransition(label = "title-generating-dots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(TITLE_GENERATING_DOT_COUNT) { index ->
            val intensity by transition.animateFloat(
                initialValue = 0.32f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 360,
                        delayMillis = index * 100,
                        easing = CubicBezierEasing(0.22f, 0.82f, 0.24f, 1f),
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "title-generating-dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .graphicsLayer {
                        alpha = intensity
                        scaleX = 0.82f + intensity * 0.18f
                        scaleY = 0.82f + intensity * 0.18f
                    }
                    .background(AppAccent, CircleShape),
            )
        }
    }
}

/**
 * 在条目右侧提供新建、运行和完成三种紧凑状态标识。
 */
@Composable
internal fun TaskStatusIndicator(status: ChatTaskStatus) {
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
