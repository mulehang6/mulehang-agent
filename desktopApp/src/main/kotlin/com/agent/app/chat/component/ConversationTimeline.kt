package com.agent.app.chat.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
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
            when (displayItem) {
                is TimelineDisplayItem.ToolGroup -> TimelineToolGroup(displayItem.items)
                is TimelineDisplayItem.ReasoningGroup -> TimelineReasoningItem(mergeReasoningItems(displayItem.items))
                is TimelineDisplayItem.Content -> when (val item = displayItem.item) {
                is ChatMessageItem -> {
                    if (item.message.role == ChatRole.User) {
                        UserMessageCard(
                            content = item.message.content,
                            entryMotionId = pendingMessageEntry?.id?.takeIf { item === entryMotionTarget },
                            onEntryMotionFinished = onMessageEntryFinished,
                        )
                    } else {
                        AssistantMessageBlock(
                            content = item.message.content,
                            isStreaming = item === conversation.items.getOrNull(conversation.streamingAssistantItemIndex ?: -1),
                        )
                    }
                }

                is ReasoningItem -> TimelineReasoningItem(item)
                is AnsweredQuestionsItem -> TimelineAnswersItem(item)
                is ToolEventItem -> TimelineToolTextRow(
                    item = rememberTimelineToolDisplayItem(item),
                    isFailure = item.status == ToolEventStatus.Failed,
                )
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

/** 新消息进入动效的初始下移距离，需要足够大才能被看见。 */
private val MESSAGE_ENTRY_TRAVEL = 24.dp

/**
 * 单条用户消息卡片。
 */
@Composable
private fun UserMessageCard(
    content: String,
    entryMotionId: Long?,
    onEntryMotionFinished: (Long) -> Unit,
) {
    val travelDistancePx = with(LocalDensity.current) { MESSAGE_ENTRY_TRAVEL.toPx() }
    val progress = remember(entryMotionId) { Animatable(if (entryMotionId == null) 1f else 0f) }
    LaunchedEffect(entryMotionId) {
        val motionId = entryMotionId ?: return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.62f,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.001f,
            ),
        )
        onEntryMotionFinished(motionId)
    }
    val visuals = messageEntryVisuals(
        progress = progress.value,
        travelDistancePx = travelDistancePx,
    )
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopEnd,
    ) {
        JewelSurface(
            role = JewelSurfaceRole.PANEL,
            radius = 8.dp,
            solidColor = AppUserCardBackground,
            borderColor = Color.Transparent,
            modifier = Modifier
                .widthIn(max = maxWidth * 0.8f)
                .wrapContentWidth()
                .graphicsLayer {
                    alpha = visuals.alpha
                    scaleX = visuals.scale
                    scaleY = visuals.scale
                    translationY = visuals.translationY
                    // 从最靠近发送按钮的右下角展开，动效来源与用户操作位置一致。
                    transformOrigin = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 1f)
                },
        ) {
            Text(
                text = content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = JewelTheme.defaultTextStyle.copy(color = AppText),
            )
        }
    }
}
