package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.chat.presentation.buildReasoningHeadline
import com.agent.app.chat.presentation.buildSecondaryStatus
import com.agent.app.chat.presentation.buildToolEventHeadline
import com.agent.app.chat.presentation.buildToolEventKindLabel
import com.agent.app.chat.presentation.toolEventHasDetails
import com.agent.app.chat.presentation.shouldExpandToolEventByDefault
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.AppUserCardBackground
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus

/**
 * 完整会话时间线，按顺序渲染所有用户消息、助手回答、思考块和工具事件。
 */
@Composable
internal fun ConversationTimeline(conversation: ChatConversationUiState) {
    if (conversation.items.isEmpty() && conversation.executionState == ExecutionState.Idle) {
        Text(
            text = "可以开始新的任务",
            style = MaterialTheme.typography.bodyMedium.copy(color = AppMuted),
        )
        return
    }
    val failedState = conversation.executionState as? ExecutionState.Failed
    val hasFailedToolEvent = conversation.items.any {
        it is ToolEventItem && it.status == ToolEventStatus.Failed
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        conversation.items.forEach { item ->
            when (item) {
                is ChatMessageItem -> {
                    if (item.message.role == ChatRole.User) {
                        UserMessageCard(item.message.content)
                    } else {
                        AssistantMessageBlock(item.message.content)
                    }
                }

                is ReasoningItem -> TimelineReasoningItem(item)
                is ToolEventItem -> TimelineToolEvent(item)
            }
        }
        if (conversation.executionState == ExecutionState.Running) {
            buildSecondaryStatus(conversation)?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppMuted),
                )
            }
        }
        if (failedState != null && !hasFailedToolEvent) {
            Text(
                text = "${failedState.error.title}: ${failedState.error.message}",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A1518), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppDanger,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

/**
 * 单条用户消息卡片。
 */
@Composable
private fun UserMessageCard(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(8.dp),
            color = AppUserCardBackground,
        ) {
            Text(
                text = content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(color = AppText),
            )
        }
    }
}

/**
 * 单条助手回答块。
 */
@Composable
private fun AssistantMessageBlock(content: String) {
    val paragraphs = content
        .trim()
        .takeIf(String::isNotBlank)
        ?.split(Regex("\n\\s*\n"))
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?: listOf("No assistant output yet for this task.")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppText,
                    lineHeight = 23.sp,
                ),
            )
        }
    }
}

/**
 * 时间线中的思考块展示。
 */
@Composable
private fun TimelineReasoningItem(item: ReasoningItem) {
    var expanded by remember(item.isStreaming) { mutableStateOf(item.isStreaming) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildReasoningHeadline(item),
            modifier = Modifier.clickable { expanded = !expanded },
            style = MaterialTheme.typography.titleSmall.copy(
                color = AppMuted,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        if (expanded) {
            Text(
                text = item.displayText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppMuted,
                    lineHeight = 20.sp,
                ),
            )
        }
    }
}

/**
 * 时间线中的工具事件条目。
 */
@Composable
private fun TimelineToolEvent(item: ToolEventItem) {
    val kindLabel = buildToolEventKindLabel(item)
    val preview = item.preview?.takeIf(String::isNotBlank)
    val errorMessage = item.errorMessage?.takeIf(String::isNotBlank)
    val isFailed = item.status == ToolEventStatus.Failed
    val hasDetails = toolEventHasDetails(item)
    var expanded by remember(item.toolName, item.status, item.preview) {
        mutableStateOf(shouldExpandToolEventByDefault(item))
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = if (hasDetails) Modifier.clickable { expanded = !expanded } else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = buildToolEventHeadline(item),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isFailed) AppDanger else AppText,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (kindLabel != null) {
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.labelSmall.copy(color = AppMuted),
                )
            }
            if (hasDetails) {
                Text(
                    text = if (expanded) "⌃" else "⌄",
                    style = MaterialTheme.typography.labelSmall.copy(color = AppMuted),
                )
            }
        }
        if (preview != null && hasDetails && expanded) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppMuted,
                    lineHeight = 18.sp,
                ),
            )
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A1518), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppDanger,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}
