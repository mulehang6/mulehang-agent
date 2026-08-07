package com.agent.app.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** 分隔条高亮的视觉厚度，不改变实际拖拽热区。 */
internal const val DIVIDER_HIGHLIGHT_THICKNESS_DP = 3

/** JetBrains Air 分隔条的蓝色峰值。 */
internal val DividerAirBlue = Color(0xFF0A6CD9)

/** 分隔条高亮的主轴方向。 */
internal enum class DividerHighlightAxis {
    Horizontal,
    Vertical,
}

/**
 * 计算高亮在轨道中的起点，使其围绕指针并始终保持在可见范围内。
 */
internal fun dividerHighlightStartPx(
    trackLengthPx: Float,
    pointerPositionPx: Float,
    highlightLengthPx: Float,
): Float = (pointerPositionPx - highlightLengthPx / 2f)
    .coerceIn(0f, (trackLengthPx - highlightLengthPx).coerceAtLeast(0f))

/**
 * 将指针沿分隔轴的位置换算为 0 到 1 的渐变峰值位置。
 *
 * 分隔条使用完整轴长绘制，因此峰值只能落在当前轨道内部。
 */
internal fun dividerHighlightPeakFraction(
    pointerPositionPx: Float,
    trackLengthPx: Float,
): Float = if (trackLengthPx <= 0f) {
    0f
} else {
    (pointerPositionPx / trackLengthPx).coerceIn(0f, 1f)
}

/**
 * 返回距指针峰值 [distancePx] 处的光带强度；超过 [radiusPx] 后完全透明。
 */
internal fun dividerHighlightIntensity(
    distancePx: Float,
    radiusPx: Float,
): Float = if (radiusPx <= 0f) {
    0f
} else {
    (1f - abs(distancePx) / radiusPx).coerceIn(0f, 1f)
}

/**
 * 绘制覆盖完整分隔轴的高亮；鼠标位置是蓝色峰值，向两端连续淡出。
 */
@Composable
internal fun PointerFollowingDividerHighlight(
    axis: DividerHighlightAxis,
    pointerPositionPx: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val density = LocalDensity.current
    val highlightThicknessPx = with(density) { DIVIDER_HIGHLIGHT_THICKNESS_DP.dp.toPx() }
    Canvas(modifier = modifier) {
        drawPointerFollowingDividerHighlight(
            axis = axis,
            pointerPositionPx = pointerPositionPx,
            highlightThicknessPx = highlightThicknessPx,
        )
    }
}

/** 在当前 Canvas 范围内绘制贯穿整个分隔轴的 Air 风格渐变线。 */
private fun DrawScope.drawPointerFollowingDividerHighlight(
    axis: DividerHighlightAxis,
    pointerPositionPx: Float,
    highlightThicknessPx: Float,
) {
    val trackLengthPx = if (axis == DividerHighlightAxis.Horizontal) size.width else size.height
    if (trackLengthPx <= 0f) return

    val peakFraction = dividerHighlightPeakFraction(pointerPositionPx, trackLengthPx)
    val cornerRadius = CornerRadius(highlightThicknessPx / 2f, highlightThicknessPx / 2f)
    val topLeft = when (axis) {
        DividerHighlightAxis.Horizontal -> Offset(0f, (size.height - highlightThicknessPx) / 2f)
        DividerHighlightAxis.Vertical -> Offset((size.width - highlightThicknessPx) / 2f, 0f)
    }
    val highlightSize = when (axis) {
        DividerHighlightAxis.Horizontal -> Size(size.width, highlightThicknessPx)
        DividerHighlightAxis.Vertical -> Size(highlightThicknessPx, size.height)
    }
    val gradientStart = when (axis) {
        DividerHighlightAxis.Horizontal -> Offset(0f, size.height / 2f)
        DividerHighlightAxis.Vertical -> Offset(size.width / 2f, 0f)
    }
    val gradientEnd = when (axis) {
        DividerHighlightAxis.Horizontal -> Offset(size.width, size.height / 2f)
        DividerHighlightAxis.Vertical -> Offset(size.width / 2f, size.height)
    }
    drawRoundRect(
        brush = Brush.linearGradient(
            colorStops = dividerHighlightGradientStops(peakFraction),
            start = gradientStart,
            end = gradientEnd,
        ),
        topLeft = topLeft,
        size = highlightSize,
        cornerRadius = cornerRadius,
    )
}

/** 为完整分隔轴生成以鼠标为峰值、向两端淡出的颜色停靠点。 */
private fun dividerHighlightGradientStops(peakFraction: Float): Array<Pair<Float, Color>> = when {
    peakFraction <= 0f -> arrayOf(
        0f to DividerAirBlue.copy(alpha = 0.84f),
        1f to Color.Transparent,
    )

    peakFraction >= 1f -> arrayOf(
        0f to Color.Transparent,
        1f to DividerAirBlue.copy(alpha = 0.84f),
    )

    else -> arrayOf(
        0f to Color.Transparent,
        peakFraction to DividerAirBlue.copy(alpha = 0.84f),
        1f to Color.Transparent,
    )
}
