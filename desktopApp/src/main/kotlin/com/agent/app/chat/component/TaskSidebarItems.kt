@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatTaskListItemUiState
import com.agent.app.chat.state.ConversationTitleState
import com.agent.app.design.AppHoverBackground
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppText
import com.agent.app.design.OffsetPopupPositionProvider
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
/** 保留箭头槽位并在悬浮时淡入，避免标题文字随图标出现而发生横向跳动。 */
@Composable
internal fun TaskSectionChevronSlot(expanded: Boolean, visible: Boolean) {
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
internal fun TaskListItem(
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TASK_LIST_ITEM_HEIGHT_DP.dp)
                .background(
                    color = when {
                        selected -> AppSelectedBackground
                        hovered -> AppHoverBackground
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onClick),
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
                    AnimatedVisibility(
                        visible = task.titleState == ConversationTitleState.GENERATING,
                        enter = fadeIn(tween(PENDING_CARD_ENTER_DURATION_MILLIS)) +
                                scaleIn(tween(PENDING_CARD_ENTER_DURATION_MILLIS), initialScale = 0.95f),
                        exit = fadeOut(tween(PENDING_CARD_EXIT_DURATION_MILLIS)) +
                                scaleOut(tween(PENDING_CARD_EXIT_DURATION_MILLIS), targetScale = 0.95f),
                    ) {
                        TitleGeneratingIndicator()
                    }
                    if (shouldShowConversationTitleText(task.titleState)) {
                        Text(
                            text = task.title,
                            modifier = Modifier.weight(1f),
                            style = JewelTheme.defaultTextStyle.copy(
                                color = AppText,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TaskStatusIndicator(task.status)
                }
            }
        }
        if (contextMenuExpanded) {
            PopupMenu(
                onDismissRequest = {
                    onDismissContextMenu()
                    true
                },
                popupPositionProvider = remember(contextMenuOffset, density.density) {
                    OffsetPopupPositionProvider(contextMenuOffset, density.density)
                },
                modifier = Modifier.width(TaskContextMenuWidth),
            ) {
                taskContextMenuActions(onDelete = onDelete, onRename = onRename)
            }
        }
    }
}

/**
 * 侧栏和标题栏共用的任务上下文菜单内容，确保操作顺序、可用状态与视觉一致。
 */
internal fun MenuScope.taskContextMenuActions(
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    selectableItem(
        selected = false,
        onClick = onRename,
    ) { Text(taskContextMenuLabels()[0], color = taskContextMenuTextColor(taskContextMenuLabels()[0])) }
    selectableItem(
        selected = false,
        enabled = false,
        onClick = {},
    ) { Text(taskContextMenuLabels()[1], color = taskContextMenuTextColor(taskContextMenuLabels()[1])) }
    selectableItem(
        selected = false,
        enabled = false,
        onClick = {},
    ) { Text(taskContextMenuLabels()[2], color = taskContextMenuTextColor(taskContextMenuLabels()[2])) }
    selectableItem(
        selected = false,
        onClick = onDelete,
    ) { Text(taskContextMenuLabels()[3], color = taskContextMenuTextColor(taskContextMenuLabels()[3])) }
}
