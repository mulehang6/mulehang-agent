package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.chat.presentation.*
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.*
import com.agent.shared.chat.model.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 完整会话时间线，按顺序渲染所有用户消息、助手回答、思考块和工具事件。
 */
@Composable
internal fun ConversationTimeline(
    conversation: ChatConversationUiState,
    pendingMessageEntry: PendingMessageEntry? = null,
    onMessageEntryFinished: (Long) -> Unit = {},
    operationEntryId: String? = null,
    onTurnPositioned: (anchorId: String, topInWindow: Float, bottomInWindow: Float) -> Unit = { _, _, _ -> },
    onEntryPositioned: (entryId: String, topInWindow: Float, bottomInWindow: Float) -> Unit = { _, _, _ -> },
    onEditFromHere: (String) -> Unit = {},
    onNewSession: (String) -> Unit = {},
) {
    if (conversation.items.isEmpty() && conversation.executionState == ExecutionState.Idle) {
        Text(
            text = "可以开始新的任务",
            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        )
        return
    }
    val failedState = conversation.executionState as? ExecutionState.Failed
    val hasFailedToolEvent = conversation.items.any {
        it is ToolEventItem && it.status == ToolEventStatus.Failed
    }
    val displayItems = groupTimelineItems(conversation.items)
    val displayEntryIds = remember(conversation.entries, conversation.activeEntryId, conversation.items) {
        buildTimelineDisplayEntryIds(conversation)
    }
    val timelineTurns = remember(
        conversation.treeFormatVersion,
        conversation.entries,
        conversation.activeEntryId,
        conversation.items,
    ) { buildTimelineTurnPresentations(conversation) }
    var nextUserTurnIndex = 0
    val entryMotionTarget = latestMatchingUserMessage(conversation.items, pendingMessageEntry?.content)
    Column(modifier = Modifier.fillMaxWidth()) {
        displayItems.forEachIndexed { index, displayItem ->
            if (index > 0) {
                Spacer(
                    modifier = Modifier.height(
                        timelineDisplayItemSpacing(displayItems[index - 1], displayItem).dp,
                    ),
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow(clipBounds = false)
                    displayEntryIds.getOrNull(index).orEmpty().forEach { entryId ->
                        onEntryPositioned(entryId, bounds.top, bounds.bottom)
                    }
                },
            ) {
                when (displayItem) {
                    is TimelineDisplayItem.ToolGroup -> TimelineToolGroup(displayItem.items)
                    is TimelineDisplayItem.ReasoningGroup -> TimelineReasoningItem(mergeReasoningItems(displayItem.items))
                    is TimelineDisplayItem.Content -> when (val item = displayItem.item) {
                        is ChatMessageItem -> {
                            if (item.message.role == ChatRole.User) {
                                val turn = timelineTurns.getOrNull(nextUserTurnIndex++)
                                    ?: TimelineTurnPresentation(
                                        anchorId = "rendered-user-${nextUserTurnIndex - 1}",
                                        sourceUserEntryId = null,
                                        userText = item.message.content,
                                        assistantText = null,
                                    )
                                UserMessageCard(
                                    turn = turn,
                                    entryMotionId = pendingMessageEntry?.id?.takeIf { item === entryMotionTarget },
                                    operationInProgress = isTimelineOperationInProgress(
                                        operationEntryId = operationEntryId,
                                        sourceUserEntryId = turn.sourceUserEntryId,
                                    ),
                                    onEntryMotionFinished = onMessageEntryFinished,
                                    onPositioned = onTurnPositioned,
                                    onEditFromHere = onEditFromHere,
                                    onNewSession = onNewSession,
                                )
                            } else {
                                AssistantMessageBlock(
                                    content = item.message.content,
                                    isStreaming = item === conversation.items.getOrNull(
                                        conversation.streamingAssistantItemIndex ?: -1,
                                    ),
                                )
                            }
                        }

                        is ReasoningItem -> TimelineReasoningItem(item)
                        is AnsweredQuestionsItem -> TimelineAnswersItem(item)
                        // 兼容旧版本保存的阶段记录，避免再显示不可点击的工具外观。
                        is ToolEventItem -> if (item.status == ToolEventStatus.Status) {
                            Text(
                                text = item.preview.orEmpty(),
                                style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                            )
                        } else TimelineToolTextRow(
                            item = rememberTimelineToolDisplayItem(item),
                            isFailure = item.status == ToolEventStatus.Failed,
                        )
                    }
                }
            }
        }
        if (conversation.executionState == ExecutionState.Running) {
            buildSecondaryStatus(conversation)?.let { status ->
                if (displayItems.isNotEmpty()) Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = status,
                    style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                )
            }
        }
        if (failedState != null && !hasFailedToolEvent) {
            if (displayItems.isNotEmpty()) Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${failedState.error.title}: ${failedState.error.message}",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A1518), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = JewelTheme.defaultTextStyle.copy(
                    color = AppDanger,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}
