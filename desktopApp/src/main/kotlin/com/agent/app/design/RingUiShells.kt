@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * 顶部 header 与局部控制按钮使用的图标集合。
 */
internal enum class HeaderGlyph {
    MENU,
    SHARE,
    SETTINGS,
    HELP,
    ADD,
    CODE,
    SEARCH,
    SEND,
    STOP,
}

/**
 * 顶部 header 按钮模型。
 */
internal data class HeaderAction(
    val glyph: HeaderGlyph,
)

/**
 * 顶部 header 固定动作布局。
 */
internal data class HeaderActions(
    val left: HeaderAction,
    val right: List<HeaderAction>,
)

/**
 * 返回原型顶部 header 的动作定义。
 */
internal fun buildHeaderActions(): HeaderActions = HeaderActions(
    left = HeaderAction(glyph = HeaderGlyph.MENU),
    right = listOf(
        HeaderAction(glyph = HeaderGlyph.SHARE),
        HeaderAction(glyph = HeaderGlyph.SETTINGS),
        HeaderAction(glyph = HeaderGlyph.HELP),
    ),
)

/**
 * 右侧 rail 的图标类型。
 */
internal enum class RightRailGlyph {
    CODE,
    TERMINAL,
    DOWNLOAD,
    UPLOAD,
    HISTORY,
    COPY,
    FILTER,
}

internal const val RAIL_ACTION_SIZE_DP = 40
internal const val RAIL_GLYPH_SIZE_DP = 24
internal const val COMPOSER_PRIMARY_GLYPH_SIZE_DP = 24

/**
 * 右侧 rail 的按钮展示模型。
 */
internal data class RightRailButtonModel(
    val glyph: RightRailGlyph,
    val active: Boolean = false,
)

/**
 * 右侧 rail 的固定分组结构。
 */
internal fun buildRightRailGroups(): List<List<RightRailButtonModel>> = listOf(
    listOf(
        RightRailButtonModel(glyph = RightRailGlyph.TERMINAL),
    ),
)

/**
 * 顶部 header 的小图标按钮。
 */
@Composable
internal fun RingHeaderActionButton(
    glyph: HeaderGlyph,
    onClick: (() -> Unit)? = null,
    inline: Boolean = true,
    tooltip: String? = null,
) {
    RingTooltip(tooltip) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        var hovered by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120))
        Surface(
            shape = RoundedCornerShape(if (inline) 6.dp else 10.dp),
            color = when {
                hovered && onClick != null -> AppHoverBackground
                inline -> Color.Transparent
                else -> AppChipBackground
            },
            border = if (inline) null else BorderStroke(1.dp, AppLine),
            modifier = Modifier
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .then(if (tooltip != null) Modifier.semantics { contentDescription = tooltip } else Modifier),
        ) {
            Box(
                modifier = Modifier.size(if (inline) 32.dp else 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                HeaderGlyphIcon(glyph = glyph, tint = AppText)
            }
        }
    }
}

/**
 * 原型搜索输入壳。
 */
@Composable
internal fun RingInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    iconGlyph: HeaderGlyph? = null,
    borderless: Boolean = false,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(8.dp),
        leadingIcon = iconGlyph?.let { glyph ->
            {
                RingGlyphIcon(
                    glyph = glyph,
                    tint = AppMuted,
                    size = 14.dp,
                )
            }
        },
        colors = if (borderless) {
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
    )
}

/**
 * 原型主按钮壳。
 */
@Composable
internal fun RingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppAccent,
    tooltip: String? = null,
    compact: Boolean = false,
    iconGlyph: HeaderGlyph? = null,
    enabled: Boolean = true,
) {
    RingTooltip(tooltip) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120))
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .then(if (tooltip != null) Modifier.semantics { contentDescription = tooltip } else Modifier),
            interactionSource = interactionSource,
            shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
            contentPadding = if (compact) PaddingValues(0.dp) else ButtonDefaults.ContentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = Color.White,
            ),
        ) {
            if (iconGlyph != null) {
                RingGlyphIcon(
                    glyph = iconGlyph,
                    tint = Color.White,
                    size = COMPOSER_PRIMARY_GLYPH_SIZE_DP.dp,
                )
            } else {
                Text(text)
            }
        }
    }
}

/**
 * 原型 Island 风格容器壳。
 */
@Composable
internal fun RingIsland(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    color: Color = AppSidebarBackground,
    borderColor: Color = AppLine,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, borderColor),
    ) {
        content()
    }
}

/**
 * 原型 Select button 壳。
 */
internal const val SELECT_POPUP_FOCUSABLE = false

/** Composer 下拉框说明在持续悬停多久后显示。 */
internal const val SELECT_TOOLTIP_DELAY_MILLIS = 1500L

/**
 * 以按下 trigger 时的展开状态为准，避免 popup 的外部关闭先改写受控状态。
 */
internal fun desiredSelectExpandedState(
    expandedAtPointerPress: Boolean?,
    expandedAtClick: Boolean,
): Boolean = !(expandedAtPointerPress ?: expandedAtClick)

@Composable
internal fun RingSelectChip(
    label: String,
    expanded: Boolean,
    tone: Color = AppChipBackground,
    onExpandedChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    tooltip: String? = null,
    content: @Composable () -> Unit,
) {
    RingTooltip(
        text = tooltip,
        belowAnchor = true,
        hoverDelayMillis = SELECT_TOOLTIP_DELAY_MILLIS,
    ) {
        var hovered by remember { mutableStateOf(false) }
        val density = LocalDensity.current
        var anchorWidth by remember { mutableStateOf(112.dp) }
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        var expandedAtPointerPress by remember { mutableStateOf<Boolean?>(null) }
        val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120))
        val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(120))
        Box {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    expanded -> AppSidebarBackground
                    hovered -> AppHoverBackground
                    else -> tone
                },
                border = BorderStroke(1.dp, AppLine),
                modifier = modifier
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .onGloballyPositioned { coordinates ->
                        anchorWidth = with(density) { coordinates.size.width.toDp() }
                    }
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .onPointerEvent(
                        eventType = PointerEventType.Press,
                        pass = PointerEventPass.Initial,
                    ) {
                        expandedAtPointerPress = expanded
                    }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) {
                        val shouldExpand = desiredSelectExpandedState(
                            expandedAtPointerPress = expandedAtPointerPress,
                            expandedAtClick = expanded,
                        )
                        expandedAtPointerPress = null
                        onExpandedChange(shouldExpand)
                    }
                    .then(if (tooltip != null) Modifier.semantics { contentDescription = tooltip } else Modifier),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium.copy(color = AppText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "▾",
                        modifier = Modifier.graphicsLayer(rotationZ = arrowRotation),
                        style = MaterialTheme.typography.labelSmall.copy(color = AppMuted),
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismissRequest,
                modifier = Modifier.widthIn(min = anchorWidth, max = 280.dp),
                offset = DpOffset(0.dp, (-4).dp),
                properties = PopupProperties(focusable = SELECT_POPUP_FOCUSABLE),
                shape = RoundedCornerShape(10.dp),
                containerColor = AppSidebarBackground,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, AppLine),
            ) {
                content()
            }
        }
    }
}

/**
 * 以克制的圆环展示上下文占用，并把精确值放入 tooltip。
 */
@Composable
internal fun RingContextIndicator(
    sweepAngle: Float,
    tooltip: String,
    modifier: Modifier = Modifier,
) {
    RingTooltip(tooltip) {
        Box(
            modifier = modifier
                .size(34.dp)
                .semantics { contentDescription = tooltip },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val strokeWidth = 2.dp.toPx()
                drawCircle(
                    color = AppLine,
                    style = Stroke(width = strokeWidth),
                )
                drawArc(
                    color = AppAccent,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * 右侧 rail 的按钮壳。
 */
@Composable
internal fun RingRailActionButton(
    glyph: RightRailGlyph,
    active: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val tooltip = glyph.tooltip
    RingTooltip(tooltip) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        var hovered by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120))
        Box(
            modifier = Modifier
                .size(RAIL_ACTION_SIZE_DP.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .background(
                    color = when {
                        active -> AppSelectedBackground
                        hovered -> AppHoverBackground
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(8.dp),
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .semantics { contentDescription = tooltip },
            contentAlignment = Alignment.Center,
        ) {
            RightRailGlyphIcon(
                glyph = glyph,
                tint = if (active) Color.White else AppText.copy(alpha = 0.72f),
            )
        }
    }
}

/**
 * 为控件提供桌面 tooltip；可选延迟只用于需要降低悬停噪声的控件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RingTooltip(
    text: String?,
    belowAnchor: Boolean = false,
    hoverDelayMillis: Long = 0L,
    content: @Composable () -> Unit,
) {
    if (text == null) {
        content()
        return
    }
    val positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
        if (belowAnchor) TooltipAnchorPosition.Below else TooltipAnchorPosition.Above,
    )
    if (hoverDelayMillis <= 0L) {
        TooltipBox(
            positionProvider = positionProvider,
            tooltip = { PlainTooltip { Text(text) } },
            state = rememberTooltipState(),
            content = content,
        )
        return
    }

    // Material 默认在进入时立即显示并自动超时；下拉框需要反转为延迟且持续的悬停行为。
    val state = rememberTooltipState(isPersistent = true)
    var hovered by remember { mutableStateOf(false) }
    LaunchedEffect(hovered, hoverDelayMillis) {
        if (hovered) {
            delay(hoverDelayMillis.milliseconds)
            state.show()
        } else {
            state.dismiss()
        }
    }
    TooltipBox(
        positionProvider = positionProvider,
        tooltip = { PlainTooltip { Text(text) } },
        state = state,
        enableUserInput = false,
    ) {
        Box(
            modifier = Modifier
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false },
        ) {
            content()
        }
    }
}

private val RightRailGlyph.tooltip: String
    get() = when (this) {
        RightRailGlyph.CODE -> "对话"
        RightRailGlyph.TERMINAL -> "终端"
        RightRailGlyph.DOWNLOAD -> "导出会话"
        RightRailGlyph.UPLOAD -> "添加附件"
        RightRailGlyph.HISTORY -> "历史记录"
        RightRailGlyph.COPY -> "复制最新回答"
        RightRailGlyph.FILTER -> "筛选工具活动"
    }

/**
 * 纯图标渲染，匹配原型中非按钮式 glyph 的位置。
 */
@Composable
internal fun RingGlyphIcon(
    glyph: HeaderGlyph,
    tint: Color = AppText,
    size: Dp = 16.dp,
) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        HeaderGlyphIcon(glyph = glyph, tint = tint, canvasSize = size)
    }
}

/**
 * 绘制顶部 header 的图标。
 */
@Composable
private fun HeaderGlyphIcon(
    glyph: HeaderGlyph,
    tint: Color,
    canvasSize: Dp = 16.dp,
) {
    Canvas(modifier = Modifier.size(canvasSize)) {
        val strokeWidth = 1.8.dp.toPx()
        when (glyph) {
            HeaderGlyph.MENU -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.18f, size.height * 0.28f),
                    Offset(size.width * 0.82f, size.height * 0.28f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.18f, size.height * 0.5f),
                    Offset(size.width * 0.82f, size.height * 0.5f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.18f, size.height * 0.72f),
                    Offset(size.width * 0.82f, size.height * 0.72f),
                    strokeWidth,
                    StrokeCap.Round
                )
            }

            HeaderGlyph.SHARE -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.5f, size.height * 0.72f),
                    Offset(size.width * 0.5f, size.height * 0.24f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.34f, size.height * 0.38f),
                    Offset(size.width * 0.5f, size.height * 0.22f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.66f, size.height * 0.38f),
                    Offset(size.width * 0.5f, size.height * 0.22f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.26f, size.height * 0.78f),
                    Offset(size.width * 0.74f, size.height * 0.78f),
                    strokeWidth,
                    StrokeCap.Round
                )
            }

            HeaderGlyph.SETTINGS -> {
                drawCircle(
                    tint,
                    radius = size.minDimension * 0.16f,
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.5f, size.height * 0.1f),
                    Offset(size.width * 0.5f, size.height * 0.24f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.5f, size.height * 0.76f),
                    Offset(size.width * 0.5f, size.height * 0.9f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.1f, size.height * 0.5f),
                    Offset(size.width * 0.24f, size.height * 0.5f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.76f, size.height * 0.5f),
                    Offset(size.width * 0.9f, size.height * 0.5f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.2f, size.height * 0.2f),
                    Offset(size.width * 0.3f, size.height * 0.3f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.7f, size.height * 0.7f),
                    Offset(size.width * 0.8f, size.height * 0.8f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.2f, size.height * 0.8f),
                    Offset(size.width * 0.3f, size.height * 0.7f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.7f, size.height * 0.3f),
                    Offset(size.width * 0.8f, size.height * 0.2f),
                    strokeWidth,
                    StrokeCap.Round
                )
            }

            HeaderGlyph.HELP -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.34f, size.height * 0.34f),
                    Offset(size.width * 0.5f, size.height * 0.22f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.5f, size.height * 0.22f),
                    Offset(size.width * 0.66f, size.height * 0.34f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.66f, size.height * 0.34f),
                    Offset(size.width * 0.58f, size.height * 0.5f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.58f, size.height * 0.5f),
                    Offset(size.width * 0.5f, size.height * 0.58f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.5f, size.height * 0.68f),
                    Offset(size.width * 0.5f, size.height * 0.7f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawCircle(tint, radius = strokeWidth / 2f, center = Offset(size.width * 0.5f, size.height * 0.82f))
            }

            HeaderGlyph.ADD -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.5f, size.height * 0.2f),
                    Offset(size.width * 0.5f, size.height * 0.8f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.2f, size.height * 0.5f),
                    Offset(size.width * 0.8f, size.height * 0.5f),
                    strokeWidth,
                    StrokeCap.Round
                )
            }

            HeaderGlyph.CODE -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.42f, size.height * 0.22f),
                    Offset(size.width * 0.24f, size.height * 0.5f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.24f, size.height * 0.5f),
                    Offset(size.width * 0.42f, size.height * 0.78f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.58f, size.height * 0.22f),
                    Offset(size.width * 0.76f, size.height * 0.5f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.76f, size.height * 0.5f),
                    Offset(size.width * 0.58f, size.height * 0.78f),
                    strokeWidth,
                    StrokeCap.Round
                )
            }

            HeaderGlyph.SEARCH -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.22f,
                    center = Offset(size.width * 0.45f, size.height * 0.45f),
                    style = Stroke(width = strokeWidth),
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.6f, size.height * 0.6f),
                    Offset(size.width * 0.8f, size.height * 0.8f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }

            HeaderGlyph.SEND -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.5f, size.height * 0.78f),
                    Offset(size.width * 0.5f, size.height * 0.24f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.28f, size.height * 0.46f),
                    Offset(size.width * 0.5f, size.height * 0.24f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.72f, size.height * 0.46f),
                    Offset(size.width * 0.5f, size.height * 0.24f),
                    strokeWidth,
                    StrokeCap.Round
                )
            }

            HeaderGlyph.STOP -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.29f, size.height * 0.29f),
                    size = Size(size.width * 0.42f, size.height * 0.42f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }
    }
}

/**
 * 与 Composer trigger 同一视觉语言的下拉菜单项。
 */
@Composable
internal fun RingDropdownMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var hovered by remember { mutableStateOf(false) }
    val backgroundColor = when {
        selected -> AppSelectedBackground
        hovered && enabled -> AppHoverBackground
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height(40.dp)
            .background(backgroundColor, RoundedCornerShape(7.dp))
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge.copy(
                color = if (enabled) AppText else AppMuted,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelMedium.copy(color = AppText),
            )
        }
    }
}

/**
 * 用 Compose Canvas 绘制右侧 rail 图标。
 */
@Composable
private fun RightRailGlyphIcon(
    glyph: RightRailGlyph,
    tint: Color,
) {
    Box(
        modifier = Modifier.size(RAIL_GLYPH_SIZE_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(RAIL_GLYPH_SIZE_DP.dp)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val width = size.width
            val height = size.height
            when (glyph) {
                RightRailGlyph.CODE -> {
                    drawLine(
                        tint,
                        Offset(width * 0.42f, height * 0.24f),
                        Offset(width * 0.24f, height * 0.5f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.24f, height * 0.5f),
                        Offset(width * 0.42f, height * 0.76f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.58f, height * 0.24f),
                        Offset(width * 0.76f, height * 0.5f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.76f, height * 0.5f),
                        Offset(width * 0.58f, height * 0.76f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.53f, height * 0.18f),
                        Offset(width * 0.47f, height * 0.82f),
                        stroke.width,
                        StrokeCap.Round
                    )
                }

                RightRailGlyph.TERMINAL -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(width * 0.15f, height * 0.2f),
                        size = Size(width * 0.7f, height * 0.55f),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = stroke,
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.3f, height * 0.36f),
                        Offset(width * 0.42f, height * 0.48f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.42f, height * 0.48f),
                        Offset(width * 0.3f, height * 0.6f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.5f, height * 0.61f),
                        Offset(width * 0.66f, height * 0.61f),
                        stroke.width,
                        StrokeCap.Round
                    )
                }

                RightRailGlyph.DOWNLOAD -> {
                    drawLine(
                        tint,
                        Offset(width * 0.5f, height * 0.2f),
                        Offset(width * 0.5f, height * 0.58f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.34f, height * 0.45f),
                        Offset(width * 0.5f, height * 0.62f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.66f, height * 0.45f),
                        Offset(width * 0.5f, height * 0.62f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.28f, height * 0.8f),
                        Offset(width * 0.72f, height * 0.8f),
                        stroke.width,
                        StrokeCap.Round
                    )
                }

                RightRailGlyph.UPLOAD -> {
                    drawLine(
                        tint,
                        Offset(width * 0.26f, height * 0.75f),
                        Offset(width * 0.72f, height * 0.29f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.5f, height * 0.18f),
                        Offset(width * 0.5f, height * 0.62f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.34f, height * 0.34f),
                        Offset(width * 0.5f, height * 0.18f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.66f, height * 0.34f),
                        Offset(width * 0.5f, height * 0.18f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.28f, height * 0.78f),
                        Offset(width * 0.72f, height * 0.78f),
                        stroke.width,
                        StrokeCap.Round
                    )
                }

                RightRailGlyph.HISTORY -> {
                    drawArc(
                        color = tint,
                        startAngle = -30f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(width * 0.2f, height * 0.2f),
                        size = Size(width * 0.6f, height * 0.6f),
                        style = stroke,
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.64f, height * 0.2f),
                        Offset(width * 0.78f, height * 0.18f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.64f, height * 0.2f),
                        Offset(width * 0.69f, height * 0.33f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.5f, height * 0.35f),
                        Offset(width * 0.5f, height * 0.5f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.5f, height * 0.5f),
                        Offset(width * 0.62f, height * 0.56f),
                        stroke.width,
                        StrokeCap.Round
                    )
                }

                RightRailGlyph.COPY -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(width * 0.34f, height * 0.24f),
                        size = Size(width * 0.42f, height * 0.5f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = stroke,
                    )
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(width * 0.2f, height * 0.36f),
                        size = Size(width * 0.42f, height * 0.5f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = stroke,
                    )
                }

                RightRailGlyph.FILTER -> {
                    drawLine(
                        tint,
                        Offset(width * 0.2f, height * 0.26f),
                        Offset(width * 0.8f, height * 0.26f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.2f, height * 0.26f),
                        Offset(width * 0.56f, height * 0.56f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.8f, height * 0.26f),
                        Offset(width * 0.56f, height * 0.56f),
                        stroke.width,
                        StrokeCap.Round
                    )
                    drawLine(
                        tint,
                        Offset(width * 0.56f, height * 0.56f),
                        Offset(width * 0.56f, height * 0.8f),
                        stroke.width,
                        StrokeCap.Round
                    )
                }
            }
        }
    }
}

internal val AppBackground = Color(0xFF1E1F22)
internal val AppHeaderBackground = Color(0xFF1E1F22)
internal val AppWorkspaceBackground = Color(0xFF151719)
internal val AppSidebarBackground = Color(0xFF2B2D30)
internal val AppPanelBackground = Color(0xFF1E1F22)
internal val AppSelectedBackground = Color(0xFF2E436E)
internal val AppHoverBackground = Color(0xFF35383E)
internal val AppUserCardBackground = Color(0xFF43454A)
internal val AppChipBackground = Color(0xFF43454A)
internal val ComposerBackground = Color(0xFF2B2D30)
internal val AppRailBackground = AppHeaderBackground
internal val AppLine = Color(0xFF393B40)
internal val AppText = Color(0xFFFFFFFF)
internal val AppMuted = Color(0xFF9DA0A8)
internal val AppAccent = Color(0xFF548AF7)
internal val AppSuccess = Color(0xFF5FAD65)
internal val AppDanger = Color(0xFFE37774)
