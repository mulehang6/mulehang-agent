@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.agent.app.chat.presentation.buildReasoningHeadline
import com.agent.app.chat.presentation.buildSecondaryStatus
import com.agent.app.chat.presentation.buildToolEventInlineInput
import com.agent.app.chat.presentation.buildToolEventHeadline
import com.agent.app.chat.presentation.buildToolEventOperationIntent
import com.agent.app.chat.presentation.isTerminalToolEvent
import com.agent.app.chat.presentation.shouldShowToolEventHeadline
import com.agent.app.chat.presentation.toolEventHasDetails
import com.agent.app.chat.presentation.toolEventOutputChunks
import com.agent.app.chat.presentation.toolEventOutputText
import com.agent.app.chat.presentation.shouldExpandToolEventByDefault
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.AppDanger
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
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
internal fun ConversationTimeline(
    conversation: ChatConversationUiState,
) {
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
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopEnd,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = maxWidth * 0.8f)
                .wrapContentWidth(),
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
            AssistantMarkdownBlock(paragraph)
        }
    }
}

/**
 * 渲染助手正文中常用的 Markdown 标题、列表、代码块与粗体，保持普通文本的阅读节奏。
 */
@Composable
private fun AssistantMarkdownBlock(content: String) {
    val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(content)
    when {
        headingMatch != null -> Text(
            text = headingMatch.groupValues[2],
            style = MaterialTheme.typography.titleMedium.copy(
                color = AppText,
                fontWeight = FontWeight.SemiBold,
            ),
        )

        content.startsWith("```") && content.endsWith("```") -> Text(
            text = content.removePrefix("```").removeSuffix("```").trim(),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF17181A), RoundedCornerShape(8.dp))
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AppText,
                fontFamily = FontFamily.Monospace,
                lineHeight = 21.sp,
            ),
        )

        content.lineSequence().all { it.trimStart().startsWith("- ") || it.trimStart().startsWith("* ") } ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                content.lineSequence().forEach { line ->
                    Text(
                        text = "• ${line.trimStart().drop(2)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AppText,
                            lineHeight = 23.sp,
                        ),
                    )
                }
            }

        else -> Text(
            text = markdownInlineText(content),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AppText,
                lineHeight = 23.sp,
            ),
        )
    }
}

/**
 * 解析普通段落中的 `**粗体**`，其余 Markdown 文本保持原样以避免丢失内容。
 */
private fun markdownInlineText(content: String) = buildAnnotatedString {
    val pattern = Regex("\\*\\*(.+?)\\*\\*")
    var cursor = 0
    pattern.findAll(content).forEach { match ->
        append(content.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(match.groupValues[1])
        }
        cursor = match.range.last + 1
    }
    append(content.substring(cursor))
}

/**
 * 时间线中的思考块展示。
 */
@Composable
private fun TimelineReasoningItem(item: ReasoningItem) {
    var expanded by remember(item.isStreaming) { mutableStateOf(item.isStreaming) }
    val shimmerTransition = rememberInfiniteTransition(label = "thinking-shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -120f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
        ),
        label = "thinking-shimmer-offset",
    )
    val headlineBrush = Brush.linearGradient(
        colors = listOf(AppMuted, Color(0xFFEAF2FF), AppMuted),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 120f, 0f),
    )
    val headlineStyle = if (item.isStreaming) {
        MaterialTheme.typography.titleSmall.copy(
            brush = headlineBrush,
            fontWeight = FontWeight.SemiBold,
        )
    } else {
        MaterialTheme.typography.titleSmall.copy(
            color = AppMuted,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = buildReasoningHeadline(item),
            modifier = Modifier.clickable { expanded = !expanded },
            style = headlineStyle,
        )
        if (expanded) {
            Text(
                text = item.displayText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppMuted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                ),
            )
        }
    }
}

/**
 * 时间线中的工具事件条目。
 */
@Composable
private fun TimelineToolEvent(
    item: ToolEventItem,
) {
    val errorMessage = item.errorMessage?.takeIf(String::isNotBlank)
    val isFailed = item.status == ToolEventStatus.Failed
    val isTerminalTool = isTerminalToolEvent(item)
    val hasDetails = toolEventHasDetails(item)
    val inlineInput = buildToolEventInlineInput(item)
    val outputChunks = remember(item.resultPreview, item.resultDisplay) { toolEventOutputChunks(item) }
    val outputListState = rememberLazyListState()
    var expanded by remember(toolEventExpansionIdentity(item)) {
        mutableStateOf(shouldExpandToolEventByDefault(item))
    }
    var chevronHovered by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = toolEventChevronRotation(expanded),
        animationSpec = tween(durationMillis = 160),
        label = "tool-event-chevron",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = AppPanelBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppLine.copy(alpha = 0.65f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (shouldShowToolEventHeadline(item)) {
                        Text(
                            text = buildToolEventHeadline(item),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isFailed) AppDanger else AppText,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                    inlineInput?.let { input ->
                        Text(
                            text = input,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isTerminalTool) AppText else AppMuted,
                                fontFamily = if (isTerminalTool) FontFamily.Monospace else FontFamily.Default,
                            ),
                        )
                    }
                }
                if (hasDetails) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .onPointerEvent(PointerEventType.Enter) { chevronHovered = true }
                            .onPointerEvent(PointerEventType.Exit) { chevronHovered = false }
                            .background(
                                color = if (chevronHovered) AppLine.copy(alpha = 0.72f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clickable { expanded = !expanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                        ) {
                            val stroke = 1.8.dp.toPx()
                            drawLine(AppMuted, Offset(size.width * 0.22f, size.height * 0.34f), Offset(size.width * 0.5f, size.height * 0.64f), stroke, StrokeCap.Round)
                            drawLine(AppMuted, Offset(size.width * 0.5f, size.height * 0.64f), Offset(size.width * 0.78f, size.height * 0.34f), stroke, StrokeCap.Round)
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(durationMillis = 180)) + fadeIn(tween(durationMillis = 150)),
                exit = shrinkVertically(tween(durationMillis = 120)) + fadeOut(tween(durationMillis = 100)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    toolEventOutputText(item)?.let {
                        if (outputChunks.isEmpty()) {
                            Text(
                                text = "无输出内容",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF17181A), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = AppMuted,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                ),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .background(Color(0xFF17181A), RoundedCornerShape(6.dp)),
                            ) {
                                LazyColumn(
                                    state = outputListState,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    items(outputChunks) { chunk ->
                                        Text(
                                            text = chunk,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = AppMuted,
                                                fontSize = 14.sp,
                                                lineHeight = 21.sp,
                                            ),
                                        )
                                    }
                                }
                                if (
                                    shouldShowToolOutputScrollbar(
                                        canScrollForward = outputListState.canScrollForward ||
                                            outputListState.canScrollBackward,
                                    )
                                ) {
                                    CompositionLocalProvider(
                                        LocalScrollbarStyle provides LocalScrollbarStyle.current.copy(
                                            unhoverColor = Color(0xFF747983),
                                            hoverColor = Color(0xFFB8BEC8),
                                        ),
                                    ) {
                                        VerticalScrollbar(
                                            adapter = rememberScrollbarAdapter(outputListState),
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxHeight()
                                                .padding(vertical = 4.dp, horizontal = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2A1518), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AppDanger,
                                lineHeight = 20.sp,
                            ),
                        )
                    }
                }
            }
            }
        }
        if (isTerminalTool) {
            buildToolEventOperationIntent(item)?.let { intent ->
                Text(
                    text = intent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppText,
                        lineHeight = 23.sp,
                    ),
                )
            }
        }
    }
}

/**
 * 返回工具详情箭头的旋转角度，展开时朝上，收起时朝下。
 */
internal fun toolEventChevronRotation(expanded: Boolean): Float = if (expanded) 180f else 0f

/**
 * 返回决定工具卡片展开状态归属的稳定字段，结果文本更新不应重置用户的展开选择。
 */
internal fun toolEventExpansionIdentity(item: ToolEventItem): List<Any?> = listOf(
    item.toolCallId,
    item.toolName,
    item.preview,
)

/**
 * 工具输出列表存在可滚动内容时显示滚动条，避免短输出产生无意义的视觉噪声。
 */
internal fun shouldShowToolOutputScrollbar(canScrollForward: Boolean): Boolean = canScrollForward
