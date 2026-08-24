@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.agent.app.chat.presentation.*
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.design.*
import com.agent.app.tool.component.EditorDiffPreview
import com.agent.shared.chat.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
/**
 * 时间线中的思考块展示。
 */
@Composable
internal fun TimelineReasoningItem(item: ReasoningItem) {
    var expanded by remember(item.isStreaming) { mutableStateOf(item.isStreaming) }
    val reasoningTint = timelineReasoningTint(item.isStreaming)
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
        JewelTheme.defaultTextStyle.copy(
            brush = headlineBrush,
            fontWeight = FontWeight.SemiBold,
            fontSize = REASONING_HEADLINE_FONT_SIZE_SP.sp,
        )
    } else {
        JewelTheme.defaultTextStyle.copy(
            color = reasoningTint,
            fontWeight = FontWeight.SemiBold,
            fontSize = REASONING_HEADLINE_FONT_SIZE_SP.sp,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimelineReasoningGlyph(streaming = item.isStreaming, tint = reasoningTint)
            Text(
                text = buildReasoningHeadline(item),
                modifier = Modifier.padding(start = 7.dp),
                style = headlineStyle,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(durationMillis = REASONING_BODY_EXPAND_DURATION_MILLIS)) +
                    fadeIn(tween(durationMillis = REASONING_BODY_EXPAND_DURATION_MILLIS)),
            exit = shrinkVertically(tween(durationMillis = REASONING_BODY_COLLAPSE_DURATION_MILLIS)) +
                    fadeOut(tween(durationMillis = REASONING_BODY_COLLAPSE_DURATION_MILLIS)),
        ) {
            Text(
                text = item.displayText,
                style = JewelTheme.defaultTextStyle.copy(
                    color = AppMuted,
                    fontSize = REASONING_BODY_FONT_SIZE_SP.sp,
                    lineHeight = 23.sp,
                ),
            )
        }
    }
}

/**
 * 时间线中的工具事件条目。
 */
@Composable
internal fun TimelineToolGroup(items: List<ToolEventItem>) {
    var expanded by remember(items.map(ToolEventItem::toolCallId)) {
        mutableStateOf(initialTimelineToolGroupExpanded())
    }
    var hovered by remember(items.map(ToolEventItem::toolCallId)) { mutableStateOf(false) }
    val displayItems = items.map { item -> rememberTimelineToolDisplayItem(item) }
    val chevronRotation by animateFloatAsState(
        targetValue = toolEventChevronRotation(expanded),
        animationSpec = tween(
            durationMillis = if (expanded) TOOL_GROUP_EXPAND_DURATION_MILLIS else TOOL_GROUP_COLLAPSE_DURATION_MILLIS,
        ),
        label = "tool-group-chevron",
    )
    val activeTool = activeTimelineTool(displayItems)
    val groupGlyph = timelineToolGroupGlyph(displayItems)
    val groupTint = timelineToolGroupTint(displayItems)
    val groupTitleTint = timelineToolTitleTint(hovered = hovered, restingTint = AppMuted)
    val summaries = toolGroupSummaries(displayItems)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) {
                    hovered = false
                }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                expanded = !expanded
            }
                .padding(horizontal = 4.dp, vertical = TOOL_EVENT_ROW_VERTICAL_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = groupGlyph,
                transitionSpec = {
                    (fadeIn(tween(durationMillis = 300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300)))
                        .togetherWith(fadeOut(tween(durationMillis = 220)))
                },
                label = "tool-group-glyph",
            ) { glyph ->
                TimelineToolGlyphIcon(
                    glyph = glyph,
                    tint = groupTint,
                    running = activeTool != null,
                    iconSize = 20.dp,
                )
            }
            Row(
                modifier = Modifier
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                summaries.forEachIndexed { index, summary ->
                    key(summary) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(durationMillis = 160)),
                            exit = ExitTransition.None,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (index > 0) {
                                    Text(
                                        text = "·",
                                        style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                                    )
                                }
                                Text(
                                    text = summary,
                                    style = JewelTheme.defaultTextStyle.copy(
                                        color = groupTitleTint,
                                        fontSize = TOOL_GROUP_TITLE_FONT_SIZE_SP.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(TOOL_GROUP_CHEVRON_GAP_DP.dp))
            TimelineToolChevronSlot(
                visible = shouldShowTimelineToolChevron(hovered),
                rotation = chevronRotation,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(durationMillis = TOOL_GROUP_EXPAND_DURATION_MILLIS)) +
                    fadeIn(tween(durationMillis = TOOL_GROUP_EXPAND_DURATION_MILLIS)),
            exit = shrinkVertically(tween(durationMillis = TOOL_GROUP_COLLAPSE_DURATION_MILLIS)) +
                    fadeOut(tween(durationMillis = TOOL_GROUP_COLLAPSE_DURATION_MILLIS)),
        ) {
            Column(
                modifier = Modifier
                    .drawBehind {
                        val guideX = 8.dp.toPx()
                        drawLine(
                            color = AppLine.copy(alpha = 0.72f),
                            start = Offset(guideX, 0f),
                            end = Offset(guideX, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .padding(start = 20.dp, top = 3.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                displayItems.forEach { item ->
                    TimelineToolStackCard(item = item, preview = false)
                }
            }
        }
    }
}

/**
 * 以可展开的紧凑摘要展示已经提交给 Agent 的批量问答。
 */
@Composable
internal fun TimelineAnswersItem(item: AnsweredQuestionsItem) {
    var expanded by remember(item) { mutableStateOf(false) }
    var hovered by remember(item) { mutableStateOf(false) }
    val interactionSource = remember(item) { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = toolEventChevronRotation(expanded),
        animationSpec = tween(durationMillis = TOOL_ROW_EXPAND_DURATION_MILLIS),
        label = "answers-chevron",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            ) {
            AnswersGlyphIcon(tint = AppText)
            Text(
                text = "Answers",
                modifier = Modifier
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) { expanded = !expanded },
                style = JewelTheme.defaultTextStyle.copy(
                    color = timelineAnswersTitleTint(hovered),
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(modifier = Modifier.width(TOOL_ROW_CHEVRON_GAP_DP.dp))
            TimelineToolChevronSlot(
                visible = shouldShowTimelineToolChevron(hovered),
                rotation = chevronRotation,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(durationMillis = 180)) + fadeIn(tween(durationMillis = 150)),
            exit = shrinkVertically(tween(durationMillis = 120)) + fadeOut(tween(durationMillis = 100)),
        ) {
            Column(
                modifier = Modifier
                    .padding(
                        start = ANSWERS_DETAILS_START_PADDING_DP.dp,
                        end = 10.dp,
                        bottom = 6.dp,
                    )
                    .clip(RoundedCornerShape(DETAIL_ISLANDS_OUTER_CORNER_RADIUS_DP.dp))
                    .background(DetailIslandsOuterBackground)
                    .border(
                        1.dp,
                        AppLine.copy(alpha = 0.65f),
                        RoundedCornerShape(DETAIL_ISLANDS_OUTER_CORNER_RADIUS_DP.dp),
                    )
                    .padding(DETAIL_ISLANDS_OUTER_PADDING_DP.dp),
                verticalArrangement = Arrangement.spacedBy(DETAIL_ISLANDS_GAP_DP.dp),
            ) {
                item.answers.forEach { answer ->
                    DetailIsland {
                        Text(
                            text = answer.question,
                            style = JewelTheme.defaultTextStyle.copy(
                                color = AppText,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                    DetailIsland {
                        Text(
                            text = answer.answer,
                            style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                        )
                    }
                }
            }
        }
    }
}

/** 绘制代表已提交问答的对话气泡图标，避免使用辨识度低的文字占位符。 */
@Composable
private fun AnswersGlyphIcon(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.12f, size.height * 0.12f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.34f, size.height * 0.74f),
            end = Offset(size.width * 0.25f, size.height * 0.9f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.25f, size.height * 0.9f),
            end = Offset(size.width * 0.49f, size.height * 0.76f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        listOf(0.32f, 0.5f, 0.68f).forEach { xRatio ->
            drawCircle(
                color = tint,
                radius = stroke * 0.42f,
                center = Offset(size.width * xRatio, size.height * 0.43f),
            )
        }
    }
}
