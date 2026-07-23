package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart

internal const val TOOL_RAIL_WIDTH_DP = 60
internal const val TOOL_RAIL_TOP_PADDING_DP = 16

/**
 * 桌面布局左侧的预留工具区，与右侧工具区保持相同宽度。
 */
@Composable
internal fun ToolRailPlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(TOOL_RAIL_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(AppRailBackground),
    )
}

/**
 * 右侧 rail 操作后的轻量反馈。
 */
@Composable
internal fun RailFeedbackCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = AppChipBackground,
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
    Box(
        modifier = modifier
            .width(TOOL_RAIL_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(AppRailBackground)
            .padding(top = TOOL_RAIL_TOP_PADDING_DP.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            toolGroups.forEachIndexed { groupIndex, group ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    group.forEach { item ->
                        RingRailActionButton(
                            glyph = item.glyph,
                            active = item.glyph == activeGlyph,
                            onClick = { onToolClick(item.glyph) },
                        )
                    }
                }
                if (groupIndex != toolGroups.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .width(18.dp)
                            .height(1.dp)
                            .background(AppLine),
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
