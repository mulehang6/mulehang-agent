@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.AppText
import com.agent.app.design.DesktopPalette
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.buildRightRailGroups
import com.agent.app.design.iconKey
import com.agent.app.design.titleBarHoverBackground
import com.agent.app.design.tooltip
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

internal const val TOOL_RAIL_WIDTH_DP = 48
internal const val TOOL_RAIL_TOP_PADDING_DP = 16
internal const val TOOL_RAIL_ACTION_SIZE_DP = 40
internal const val TOOL_RAIL_ICON_SIZE_DP = 22
/** Rail 必须透明，以承接窗口根画布的 Islands 项目环境光。 */
internal val TOOL_RAIL_BACKGROUND = Color.Transparent

/** 返回工具栏动作的主题背景，普通悬浮使用与标题栏一致的中性高对比色。 */
internal fun toolRailActionBackground(
    selected: Boolean,
    hovered: Boolean,
    palette: DesktopPalette,
): Color = when {
    selected -> palette.selectedBackground
    hovered -> titleBarHoverBackground(palette.isDark)
    else -> Color.Transparent
}

/** 返回终端和设置等工具栏图标共享的主题语义 tint。 */
internal fun toolRailIconTint(
    selected: Boolean,
    hovered: Boolean,
    palette: DesktopPalette,
): Color = if (selected || hovered) palette.text else palette.muted

/**
 * 应用级操作后的轻量 toast 反馈。
 */
@Composable
internal fun AppFeedbackToast(
    message: String,
    modifier: Modifier = Modifier,
) {
    JewelSurface(
        role = JewelSurfaceRole.FLOATING,
        modifier = modifier,
        radius = 8.dp,
        solidColor = AppChipBackground,
        borderColor = AppLine,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = AppMuted,
        )
    }
}

/**
 * 历史视图。
 */
@Composable
internal fun HistoryPanel(
    conversation: ChatConversationUiState,
    filterToolActivityOnly: Boolean,
) {
    val entries = buildHistoryEntries(conversation, filterToolActivityOnly)
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 12.dp,
        modifier = Modifier.fillMaxWidth(),
        solidColor = AppSidebarBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "历史记录",
                color = AppText,
                fontWeight = FontWeight.SemiBold,
            )
            entries.forEach { entry ->
                Text(
                    text = entry,
                    color = AppText,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

/**
 * 右侧固定工具栏。
 */
@Composable
internal fun ToolRail(
    activeGlyph: RightRailGlyph,
    onToolClick: (RightRailGlyph) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toolGroups = buildRightRailGroups()
    JewelSurface(
        role = JewelSurfaceRole.CHROME,
        radius = 0.dp,
        borderWidth = 0.dp,
        solidColor = TOOL_RAIL_BACKGROUND,
        modifier = modifier.width(TOOL_RAIL_WIDTH_DP.dp).fillMaxHeight(),
    ) {
        Box(
            modifier = Modifier.fillMaxHeight()
                .padding(top = TOOL_RAIL_TOP_PADDING_DP.dp, bottom = 16.dp, start = 4.dp, end = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                toolGroups.firstOrNull()?.let { topGroup ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        topGroup.forEach { item ->
                            ToolRailAction(
                                glyph = item.glyph,
                                selected = item.glyph == activeGlyph,
                                onClick = { onToolClick(item.glyph) },
                            )
                        }
                    }
                }
                toolGroups.drop(1).flatten().forEach { item ->
                    ToolRailAction(
                        glyph = item.glyph,
                        selected = item.glyph == activeGlyph,
                        onClick = { onToolClick(item.glyph) },
                    )
                }
            }
        }
    }
}

/**
 * 使用与 IDEA 工具窗口栏一致的 40dp 命中目标渲染一个 Rail 动作。
 *
 * Rail 保持在窗口底板上，仅在 hover 或选中时出现低对比背景，避免形成独立的边框面板。
 */
@Composable
private fun ToolRailAction(
    glyph: RightRailGlyph,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = toolRailActionBackground(
        selected = selected,
        hovered = hovered,
        palette = palette,
    )

    Tooltip(tooltip = { Text(glyph.tooltip) }) {
        JewelSurface(
            role = JewelSurfaceRole.CHROME,
            radius = 8.dp,
            borderWidth = 0.dp,
            solidColor = background,
            borderColor = Color.Transparent,
            modifier = Modifier
                .size(TOOL_RAIL_ACTION_SIZE_DP.dp)
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    key = glyph.iconKey,
                    contentDescription = glyph.tooltip,
                    modifier = Modifier.size(TOOL_RAIL_ICON_SIZE_DP.dp),
                    tint = toolRailIconTint(
                        selected = selected,
                        hovered = hovered,
                        palette = palette,
                    ),
                )
            }
        }
    }
}

/**
 * 构造历史视图条目。
 */
private fun buildHistoryEntries(
    conversation: ChatConversationUiState,
    filterToolActivityOnly: Boolean,
): List<String> {
    val entries = conversation.history.flatMap { message ->
        when (message) {
            is AgentConversationHistoryMessage.User -> if (filterToolActivityOnly) {
                emptyList()
            } else {
                listOf("user> ${message.content}")
            }

            is AgentConversationHistoryMessage.Assistant -> {
                message.parts.mapNotNull { part ->
                    when (part) {
                        is AgentConversationHistoryPart.Text -> if (filterToolActivityOnly) null else "assistant> ${part.text}"
                        is AgentConversationHistoryPart.Reasoning -> if (filterToolActivityOnly) null else "reasoning> ${part.summary ?: part.rawText.orEmpty()}"
                        is AgentConversationHistoryPart.ToolCall -> "tool-call> ${part.name}${part.argumentsPreview?.let { ": $it" } ?: ""}"
                        is AgentConversationHistoryPart.ToolResult -> "tool-result> ${part.name}${part.resultPreview?.let { ": $it" } ?: ""}"
                    }
                }
            }
        }
    }
    return entries.ifEmpty { listOf(if (filterToolActivityOnly) "暂无工具历史。" else "暂无结构化历史。") }
}
