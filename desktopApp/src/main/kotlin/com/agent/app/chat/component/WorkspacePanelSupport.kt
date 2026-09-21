package com.agent.app.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.agent.app.chat.presentation.TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.LocalDesktopPalette
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.theme.iconButtonStyle

/** 仅在用户未跟随最新输出时显示回到底部动作。 */
internal fun shouldShowScrollToBottomButton(
    isFollowingLatest: Boolean,
    hasTimelineContent: Boolean = true,
): Boolean = hasTimelineContent && !isFollowingLatest

/** 底部交互卡扩高时，决定是否保持时间线贴住最新输出。 */
internal fun shouldKeepTimelineAtBottomAfterViewportChange(isFollowingLatest: Boolean): Boolean =
    isFollowingLatest

/** 判断当前时间线位置是否仍跟随最新输出。 */
internal fun isTimelineFollowingLatest(
    scrollValue: Int,
    maxScrollValue: Int,
): Boolean = scrollValue >= maxScrollValue - TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX

/** 提问或审批挂起时都应在 composer 上方展示独立交互卡片。 */
internal fun shouldShowPendingInteractionCard(
    hasPendingQuestion: Boolean,
    hasPendingApproval: Boolean,
): Boolean = hasPendingQuestion || hasPendingApproval

/** 主内容实际溢出时才显示垂直滚动条。 */
internal fun shouldShowTimelineScrollbar(maxScrollValue: Int): Boolean = maxScrollValue > 0

/** 为回到底部浮动按钮提供 36dp 圆角表面和中性 hover 反馈。 */
@Composable
internal fun timelineScrollToBottomButtonStyle(): IconButtonStyle {
    val palette = LocalDesktopPalette.current
    val base = JewelTheme.iconButtonStyle
    return remember(base, palette) {
        IconButtonStyle(
            colors = IconButtonColors(
                foregroundSelectedActivated = base.colors.foregroundSelectedActivated,
                background = palette.panelBackground,
                backgroundDisabled = base.colors.backgroundDisabled,
                backgroundSelected = base.colors.backgroundSelected,
                backgroundSelectedActivated = base.colors.backgroundSelectedActivated,
                backgroundFocused = palette.hoverBackground,
                backgroundPressed = palette.hoverBackground.copy(alpha = 0.8f),
                backgroundHovered = palette.hoverBackground,
                border = palette.line,
                borderDisabled = base.colors.borderDisabled,
                borderSelected = base.colors.borderSelected,
                borderSelectedActivated = base.colors.borderSelectedActivated,
                borderFocused = palette.line,
                borderPressed = palette.line,
                borderHovered = palette.line,
            ),
            metrics = IconButtonMetrics(
                cornerSize = CornerSize(8.dp),
                borderWidth = base.metrics.borderWidth,
                padding = PaddingValues(0.dp),
                minSize = DpSize(36.dp, 36.dp),
            ),
        )
    }
}

/** 绘制空任务态的说明和高价值起步动作。 */
@Composable
internal fun EmptyWorkspaceState(state: ChatWindowState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "从一个任务开始",
            style = JewelTheme.defaultTextStyle.copy(color = AppText, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = "选择工作区后，告诉 MH Agent 你想推进什么。",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyStateAction("审查改动", "审查当前工作区的改动，优先指出高风险问题。", state)
            EmptyStateAction("解释项目", "解释这个项目的结构、入口和关键数据流。", state)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EmptyStateAction("规划任务", "为这个需求制定一份可执行的实施计划。", state)
            EmptyStateAction("修复问题", "定位并修复当前项目中的问题。", state)
        }
    }
}

/** 空态按钮只填充草稿，不自动发送。 */
@Composable
private fun EmptyStateAction(
    label: String,
    prompt: String,
    state: ChatWindowState,
) {
    OutlinedButton(onClick = { state.updateDraft(prompt) }) { Text(label) }
}
