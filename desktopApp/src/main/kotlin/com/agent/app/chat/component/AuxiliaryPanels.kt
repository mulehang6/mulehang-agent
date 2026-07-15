package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.agent.app.chat.export.buildConversationMarkdown
import com.agent.app.chat.export.sanitizeFileName
import com.agent.app.chat.export.writeConversationMarkdown
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
import com.agent.app.platform.pickTranscriptSaveFile
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import java.io.File

/**
 * 右侧 rail 操作后的轻量反馈。
 */
@Composable
internal fun RailFeedbackCard(message: String) {
    Surface(
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
 * 终端视图。
 */
@Composable
internal fun TerminalPanel(
    conversation: ChatConversationUiState,
    filterToolActivityOnly: Boolean,
) {
    val entries = buildTerminalEntries(conversation, filterToolActivityOnly)
    RingIsland(
        modifier = Modifier.fillMaxWidth(),
        color = AppSidebarBackground,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Terminal",
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
                text = "History",
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
    Column(
        modifier = modifier
            .background(AppRailBackground)
            .padding(top = 10.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
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

/**
 * 构造终端视图条目。
 */
private fun buildTerminalEntries(
    conversation: ChatConversationUiState,
    filterToolActivityOnly: Boolean,
): List<String> {
    val entries = conversation.items.mapNotNull { item ->
        when (item) {
            is ChatMessageItem -> if (filterToolActivityOnly) {
                null
            } else {
                val prefix = if (item.message.role == ChatRole.User) "$" else "assistant>"
                "$prefix ${item.message.content.trim()}"
            }

            is ReasoningItem -> if (filterToolActivityOnly) null else "thinking> ${item.displayText.trim()}"
            is ToolEventItem -> "tool> ${item.toolName}${
                item.preview?.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""
            }"
        }
    }
    return entries.ifEmpty { listOf(if (filterToolActivityOnly) "No tool activity yet." else "No timeline events yet.") }
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
    return entries.ifEmpty { listOf(if (filterToolActivityOnly) "No tool history yet." else "No structured history yet.") }
}

/**
 * 提取最后一条助手正文，用于复制动作。
 */
internal fun latestAssistantAnswerText(conversation: ChatConversationUiState): String? =
    conversation.items
        .asReversed()
        .filterIsInstance<ChatMessageItem>()
        .firstOrNull { it.message.role == ChatRole.Assistant }
        ?.message
        ?.content
        ?.trim()
        ?.takeIf(String::isNotBlank)

/**
 * 导出当前会话为 markdown。
 */
internal fun exportConversationMarkdown(conversation: ChatConversationUiState): String? = runCatching {
    val selectedFile = pickTranscriptSaveFile("${sanitizeFileName(conversation.title)}.md") ?: return null
    val target = selectedFile.let { file ->
        if (file.extension.equals("md", ignoreCase = true)) file else File(file.parentFile, "${file.name}.md")
    }
    writeConversationMarkdown(target, buildConversationMarkdown(conversation))
    target.absolutePath
}.getOrNull()
