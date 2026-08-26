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
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.icons.AllIconsKeys
/**
 * 以固定最大高度显示工具详情的原始代码文本，并仅在内容溢出时展示滚动条。
 */
@Composable
internal fun ToolEventOutputPane(
    text: String,
    backgroundColor: Color,
    textColor: Color,
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = TOOL_EVENT_OUTPUT_MAX_HEIGHT)
            .clip(RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp))
            .background(backgroundColor)
            .border(
                1.dp,
                AppLine.copy(alpha = 0.5f),
                RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp),
            ),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(10.dp),
            style = JewelTheme.defaultTextStyle.copy(
                color = textColor,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp,
            ),
        )
        if (shouldShowToolOutputScrollbar(maxScrollValue = scrollState.maxValue)) {
            VerticalScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp, horizontal = 3.dp),
            )
        }
    }
}

/** 渲染 Answers 详情中的单个内层岛屿，保持问答内容在同一视觉语法内。 */
@Composable
internal fun DetailIsland(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp))
            .background(DetailIslandBackground)
            .border(
                1.dp,
                AppLine.copy(alpha = 0.5f),
                RoundedCornerShape(DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP.dp),
            )
            .padding(10.dp),
        content = { content() },
    )
}

/** 工具行仅在鼠标悬浮时展示展开方向，避免静态时间线产生视觉噪声。 */
internal fun shouldShowTimelineToolChevron(hovered: Boolean): Boolean = hovered

/** 保留固定箭头槽位，避免鼠标进出时工具名称和摘要发生位移。 */
@Composable
internal fun TimelineToolChevronSlot(
    visible: Boolean,
    rotation: Float,
) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .graphicsLayer { alpha = if (visible) 1f else 0f },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            key = AllIconsKeys.General.ArrowDown,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * 旧工具卡片保留至本次渲染替换完成，避免影响其现有辅助函数的复用。
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
        JewelSurface(
            role = JewelSurfaceRole.PANEL,
            radius = 10.dp,
            solidColor = AppPanelBackground,
            borderColor = AppLine.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth(),
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
                            style = JewelTheme.defaultTextStyle.copy(
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
                            style = JewelTheme.defaultTextStyle.copy(
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
                    item.fileDiffs.forEach { diff ->
                        EditorDiffPreview(diff)
                    }
                    toolEventOutputText(item)?.let { output ->
                        ToolEventOutputPane(
                            text = output,
                            backgroundColor = Color(0xFF17181A),
                            textColor = AppMuted,
                        )
                    }
                    if (errorMessage != null) {
                        ToolEventOutputPane(
                            text = errorMessage,
                            backgroundColor = Color(0xFF2A1518),
                            textColor = AppDanger,
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
                    style = JewelTheme.defaultTextStyle.copy(
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
internal fun toolEventChevronRotation(expanded: Boolean): Float = if (expanded) 90f else 0f

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
internal fun shouldShowToolOutputScrollbar(maxScrollValue: Int): Boolean = maxScrollValue > 0
