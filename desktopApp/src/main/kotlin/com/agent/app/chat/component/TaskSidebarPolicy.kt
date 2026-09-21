package com.agent.app.chat.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatTaskGroup
import com.agent.app.chat.state.ConversationTitleState
import com.agent.app.design.AppDanger
import com.agent.app.design.AppText

internal const val TASK_SECTION_TITLE_FONT_SIZE_SP = 13
internal const val TASK_LIST_ITEM_VERTICAL_PADDING_DP = 0
internal const val TASK_LIST_ITEM_GAP_DP = 4
internal const val TASK_CREATE_BUTTON_HEIGHT_DP = 40
internal const val TASK_SECTION_ROW_HEIGHT_DP = 36
internal const val TASK_LIST_ITEM_HEIGHT_DP = 56
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
internal fun taskContextMenuLabels(): List<String> =
    listOf("重命名", "重新生成标题", "创建分支", "克隆", "归档", "删除")

/** 删除是任务菜单中唯一的破坏性操作，因此使用危险色区分。 */
internal fun taskContextMenuTextColor(label: String): Color = if (label == "删除") AppDanger else AppText

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
