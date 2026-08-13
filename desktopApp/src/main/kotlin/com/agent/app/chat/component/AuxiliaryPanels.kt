package com.agent.app.chat.component

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppRailBackground
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.AppText
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.RingIsland
import com.agent.app.design.RingRailActionButton
import com.agent.app.design.buildRightRailGroups
import com.agent.app.design.liquidglass.AdaptiveLiquidGlassSurface
import com.agent.app.design.liquidglass.LiquidGlassSurfaceRole
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart

internal const val TOOL_RAIL_WIDTH_DP = 48
internal const val TOOL_RAIL_TOP_PADDING_DP = 16

/**
 * 桌面布局左侧的预留工具区，与右侧工具区保持相同宽度。
 */
@Composable
internal fun ToolRailPlaceholder(
    modifier: Modifier = Modifier,
) {
    AdaptiveLiquidGlassSurface(
        role = LiquidGlassSurfaceRole.CHROME,
        radius = 0.dp,
        solidColor = AppRailBackground,
        modifier = modifier.width(TOOL_RAIL_WIDTH_DP.dp).fillMaxHeight(),
    ) { }
}

/**
 * 应用级操作后的轻量 toast 反馈。
 */
@Composable
internal fun AppFeedbackToast(
    message: String,
    modifier: Modifier = Modifier,
) {
    AdaptiveLiquidGlassSurface(
        role = LiquidGlassSurfaceRole.FLOATING,
        modifier = modifier,
        radius = 8.dp,
        solidColor = AppChipBackground,
        borderColor = AppLine,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
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
    RingIsland(
        modifier = Modifier.fillMaxWidth(),
        color = AppSidebarBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "历史记录",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = AppText,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            entries.forEach { entry ->
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AppText,
                        lineHeight = 20.sp,
                    ),
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
    AdaptiveLiquidGlassSurface(
        role = LiquidGlassSurfaceRole.CHROME,
        radius = 0.dp,
        solidColor = AppRailBackground,
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
                            RingRailActionButton(
                                glyph = item.glyph,
                                active = item.glyph == activeGlyph,
                                onClick = { onToolClick(item.glyph) },
                            )
                        }
                    }
                }
                toolGroups.drop(1).flatten().forEach { item ->
                    RingRailActionButton(
                        glyph = item.glyph,
                        active = item.glyph == activeGlyph,
                        onClick = { onToolClick(item.glyph) },
                    )
                }
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
