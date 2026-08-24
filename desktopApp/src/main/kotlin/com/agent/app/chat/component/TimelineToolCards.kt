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
 * 暂存工具事件的展示状态，使快速完成的非终端工具仍可展示完整的运行图标动效。
 */
@Composable
internal fun rememberTimelineToolDisplayItem(item: ToolEventItem): ToolEventItem {
    val identity = toolEventExpansionIdentity(item)
    var displayItem by remember(identity) { mutableStateOf(initialTimelineToolDisplayItem(item)) }
    var startedAtMillis by remember(identity) {
        mutableStateOf(if (item.status == ToolEventStatus.Started) System.currentTimeMillis() else 0L)
    }
    var hasSeenStartedState by remember(identity) { mutableStateOf(item.status == ToolEventStatus.Started) }
    LaunchedEffect(item) {
        if (item.status == ToolEventStatus.Started) {
            hasSeenStartedState = true
            startedAtMillis = System.currentTimeMillis()
            displayItem = item
        } else if (!hasSeenStartedState) {
            displayItem = item
        } else {
            displayItem = item
        }
    }
    return displayItem
}

/**
 * 快速完成的非终端工具首次进入组合时，先构造运行态以保证图标动效可见。
 */
internal fun initialTimelineToolDisplayItem(item: ToolEventItem): ToolEventItem = item

/** 只有真实完成或失败的非终端工具需要补足此前未观测到的运行态。 */
@Suppress("UNUSED_PARAMETER")
internal fun shouldSynthesizeRunningToolDisplay(item: ToolEventItem): Boolean =
    false

/** 渲染工具组中前景当前卡和一张带纵深反馈的后置预览卡。 */
@Composable
private fun TimelineToolCardStack(items: List<ToolEventItem>) {
    val visibleItems = visibleToolCardStack(items)
    val currentItem = visibleItems.firstOrNull() ?: return
    val previewItem = visibleItems.getOrNull(1)
    val density = LocalDensity.current
    val previewOffsetX = with(density) { 8.dp.toPx() }
    val previewOffsetY = with(density) { 6.dp.toPx() }
    Box(modifier = Modifier.fillMaxWidth()) {
        previewItem?.let { item ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 0.52f
                        scaleX = 0.97f
                        scaleY = 0.97f
                        translationX = previewOffsetX
                        translationY = previewOffsetY
                    },
            ) {
                TimelineToolStackCard(item = item, preview = true)
            }
        }
        AnimatedContent(
            targetState = currentItem,
            transitionSpec = {
                (slideInHorizontally(tween(durationMillis = 220)) { width -> width / 8 } +
                        fadeIn(tween(durationMillis = 180)) +
                        scaleIn(initialScale = 0.97f, animationSpec = tween(durationMillis = 220)))
                    .togetherWith(
                        slideOutHorizontally(tween(durationMillis = 160)) { width -> -width / 5 } +
                                fadeOut(tween(durationMillis = 130)),
                    )
            },
            label = "tool-card-stack",
        ) { item ->
            TimelineToolStackCard(item = item, preview = false)
        }
    }
}

/** 渲染树状工具组中的紧凑工具行，不再包裹整行卡片。 */
@Composable
internal fun TimelineToolStackCard(
    item: ToolEventItem,
    preview: Boolean,
) {
    TimelineToolTextRow(
        item = item,
        isFailure = item.status == ToolEventStatus.Failed,
        preview = preview,
    )
}

/**
 * 无边框的单个工具文本行，可按需展开完整输出或错误内容。
 */
@Composable
internal fun TimelineToolTextRow(
    item: ToolEventItem,
    isFailure: Boolean,
    preview: Boolean = false,
) {
    val hasDetails = toolEventHasDetails(item) || item.errorMessage?.isNotBlank() == true
    val glyph = timelineToolGlyph(item)
    val running = shouldAnimateTimelineToolGlyph(item.status)
    var expanded by remember(toolEventExpansionIdentity(item)) { mutableStateOf(false) }
    var userSetExpansion by remember(toolEventExpansionIdentity(item)) { mutableStateOf(false) }
    var hovered by remember(toolEventExpansionIdentity(item)) { mutableStateOf(false) }
    LaunchedEffect(item.status, item.resultDisplay, item.resultPreview, userSetExpansion) {
        if (!userSetExpansion) {
            when {
                shouldAutoExpandRunningTerminalOutput(item) -> expanded = true
                shouldAutoCollapseStandaloneTerminalTool(item) -> {
                    delay(TOOL_GROUP_AUTO_COLLAPSE_DELAY_MILLIS.milliseconds)
                    expanded = false
                }
            }
        }
    }
    val chevronRotation by animateFloatAsState(
        targetValue = toolEventChevronRotation(expanded),
        animationSpec = tween(
            durationMillis = if (expanded) TOOL_ROW_EXPAND_DURATION_MILLIS else TOOL_ROW_COLLAPSE_DURATION_MILLIS,
        ),
        label = "tool-text-chevron",
    )
    val titleTint = timelineToolTitleTint(
        hovered = hovered,
        restingTint = if (isFailure) AppDanger else AppText,
    )
    val input = timelineToolExpandedInput(item)
    val output = toolEventOutputText(item)
    val error = item.errorMessage?.takeIf(String::isNotBlank)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) {
                    hovered = false
                }
            .clickable(
                enabled = hasDetails && !preview,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                userSetExpansion = true
                expanded = !expanded
            }
                .padding(horizontal = 4.dp, vertical = TOOL_EVENT_ROW_VERTICAL_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimelineToolGlyphIcon(
                glyph = glyph,
                tint = when {
                    isFailure -> AppDanger
                    running -> AppAccent
                    else -> AppMuted
                },
                running = running,
                iconSize = 18.dp,
            )
            Text(
                text = timelineToolRowHeadline(item),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = JewelTheme.defaultTextStyle.copy(
                    color = titleTint,
                    fontSize = TOOL_ROW_FONT_SIZE_SP.sp,
                    fontFamily = if (isTerminalToolEvent(item)) FontFamily.Monospace else FontFamily.Default,
                ),
            )
            if (hasDetails && !preview) {
                Spacer(modifier = Modifier.width(TOOL_ROW_CHEVRON_GAP_DP.dp))
                TimelineToolChevronSlot(
                    visible = shouldShowTimelineToolChevron(hovered),
                    rotation = chevronRotation,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && hasDetails && !preview,
            enter = expandVertically(tween(durationMillis = TOOL_ROW_EXPAND_DURATION_MILLIS)) +
                    fadeIn(tween(durationMillis = TOOL_ROW_EXPAND_DURATION_MILLIS)),
            exit = shrinkVertically(tween(durationMillis = TOOL_ROW_COLLAPSE_DURATION_MILLIS)) +
                    fadeOut(tween(durationMillis = TOOL_ROW_COLLAPSE_DURATION_MILLIS)),
        ) {
            Column(
                modifier = Modifier
                    .padding(
                        start = TOOL_EVENT_DETAILS_START_PADDING_DP.dp,
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
                item.fileDiffs.forEach { diff ->
                    EditorDiffPreview(diff)
                }
                input?.let { inputText ->
                    ToolEventOutputPane(
                        text = inputText,
                        backgroundColor = ToolEventInputPaneBackground,
                        textColor = AppMuted,
                    )
                }
                output?.let { outputText ->
                    ToolEventOutputPane(
                        text = outputText,
                        backgroundColor = ToolEventOutputPaneBackground,
                        textColor = AppMuted,
                    )
                }
                error?.let { errorText ->
                    ToolEventOutputPane(
                        text = errorText,
                        backgroundColor = Color(0xFF2A1518),
                        textColor = AppDanger,
                    )
                }
            }
        }
    }
}
