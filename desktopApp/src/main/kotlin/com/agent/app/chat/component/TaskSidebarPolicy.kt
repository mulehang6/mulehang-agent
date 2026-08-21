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
internal const val TASK_SECTION_TITLE_FONT_SIZE_SP = 13
internal const val TASK_LIST_ITEM_VERTICAL_PADDING_DP = 0
internal const val TASK_LIST_ITEM_GAP_DP = 4
internal const val TASK_CREATE_BUTTON_HEIGHT_DP = 40
internal const val TASK_SECTION_ROW_HEIGHT_DP = 36
internal const val TASK_LIST_ITEM_HEIGHT_DP = 40
/** 工作区名称与其状态分组间保持紧密关联。 */
internal const val TASK_WORKSPACE_CONTENT_GAP_DP = 2
/** 状态分组标题与其具体任务间保持紧密关联。 */
internal const val TASK_SECTION_CONTENT_GAP_DP = 2
internal const val TASK_SECTION_INDENT_DP = 12
internal const val TASK_LIST_ITEM_INDENT_DP = 16
internal const val TITLE_GENERATING_DOT_COUNT = 3

/** 折叠箭头仅在对应的工作区或状态分组行被鼠标悬浮时显示。 */
internal fun shouldShowTaskSectionChevron(hovered: Boolean): Boolean = hovered

/**
 * 已完成任务默认收起，减少任务列表在历史会话较多时的视觉干扰。
 */
internal fun shouldCollapseTaskSectionByDefault(group: ChatTaskGroup): Boolean =
    group == ChatTaskGroup.DONE

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

/** 返回工作区右键菜单的紧凑操作文案。 */
internal fun workspaceContextMenuLabels(): List<String> = listOf("编辑", "删除")

internal val TaskContextMenuWidth = 180.dp

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
