package com.agent.app.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** IDEA `IslandsGradientPainter` 使用的项目色混合强度。 */
internal const val IDEA_FRAME_PROJECT_COLOR_SATURATION = 0.85f

/** IDEA 在项目徽章尚未完成布局时使用的根画布渐变锚点。 */
internal val IDEA_FRAME_GRADIENT_FALLBACK_ANCHOR = 150.dp

/** IDEA 根画布中项目色从锚点向右回落至底色的范围。 */
internal val IDEA_FRAME_GRADIENT_RIGHT_FADE_WIDTH = 700.dp

/** IDEA 根画布中项目环境光向底板渐隐的总高度。 */
internal val IDEA_FRAME_GRADIENT_HEIGHT = 200.dp

/** Jewel 标题栏在内容区前保留的固定分隔高度。 */
internal val IDEA_TITLE_BAR_SEPARATOR_HEIGHT = 1.dp

/**
 * 根画布渐变的像素坐标规格。
 *
 * 这对应 IDEA `IslandsGradientPainter.doGradientPaintLegacy` 的水平项目锚点、右侧范围与垂直衰减范围。
 */
internal data class FrameAmbientSpec(
    val anchorXPx: Float,
    val rightFadeWidthPx: Float,
    val heightPx: Float,
)

/** 由实际标题栏布局坐标创建 IDEA 根画布渐变规格。 */
internal fun ideaFrameAmbientSpec(
    anchorXPx: Float?,
    densityScale: Float,
): FrameAmbientSpec {
    val fallbackAnchorPx = IDEA_FRAME_GRADIENT_FALLBACK_ANCHOR.value * densityScale
    return FrameAmbientSpec(
        anchorXPx = anchorXPx?.takeIf { it.isFinite() && it >= 0f } ?: fallbackAnchorPx,
        rightFadeWidthPx = IDEA_FRAME_GRADIENT_RIGHT_FADE_WIDTH.value * densityScale,
        heightPx = IDEA_FRAME_GRADIENT_HEIGHT.value * densityScale,
    )
}

/**
 * 计算 [value] 在 [from] 与 [to] 之间的 sRGB 直线混色。
 *
 * IDEA 的 Java2D 实现直接以 RGB 通道混色，因此这里不使用可能跨色彩空间的通用插值。
 */
internal fun blendFrameAmbientColors(
    from: Color,
    to: Color,
    value: Float,
): Color {
    val fraction = value.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * fraction,
        green = from.green + (to.green - from.green) * fraction,
        blue = from.blue + (to.blue - from.blue) * fraction,
        alpha = from.alpha + (to.alpha - from.alpha) * fraction,
    )
}

/** 返回 IDEA 根画布在全局 [xPx]、[yPx] 坐标处应显示的颜色。 */
internal fun FrameAmbientSpec.colorAt(
    frameColor: Color,
    projectColor: Color,
    xPx: Float,
    yPx: Float,
): Color {
    val mixedProjectColor = blendFrameAmbientColors(
        from = frameColor,
        to = projectColor,
        value = IDEA_FRAME_PROJECT_COLOR_SATURATION * projectColor.alpha,
    )
    val horizontalColor = when {
        xPx <= anchorXPx -> blendFrameAmbientColors(
            from = frameColor,
            to = mixedProjectColor,
            value = if (anchorXPx <= 0f) 1f else xPx / anchorXPx,
        )

        else -> blendFrameAmbientColors(
            from = mixedProjectColor,
            to = frameColor,
            value = (xPx - anchorXPx) / rightFadeWidthPx,
        )
    }
    return blendFrameAmbientColors(
        from = horizontalColor,
        to = frameColor,
        value = yPx / heightPx,
    )
}

/**
 * 在窗口根画布上绘制连续的 IDEA 项目环境光。
 *
 * [verticalOffset] 是此组件在窗口根坐标中的纵向偏移，使标题栏与内容区分别绘制同一张 200dp 画布的相邻区域。
 */
internal fun Modifier.ideaFrameAmbientBackground(
    frameColor: Color,
    projectColor: Color,
    anchorXPx: Float?,
    verticalOffset: Dp = 0.dp,
): Modifier = drawWithCache {
    val spec = ideaFrameAmbientSpec(anchorXPx = anchorXPx, densityScale = density)
    val verticalOffsetPx = verticalOffset.toPx()
    val visibleGradientHeight = (spec.heightPx - verticalOffsetPx).coerceAtLeast(0f)
    val horizontalExtent = (spec.anchorXPx + spec.rightFadeWidthPx).coerceAtLeast(1f)
    val projectMix = blendFrameAmbientColors(
        from = frameColor,
        to = projectColor,
        value = IDEA_FRAME_PROJECT_COLOR_SATURATION * projectColor.alpha,
    )
    val horizontalBrush = Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to frameColor,
            (spec.anchorXPx / horizontalExtent).coerceIn(0f, 1f) to projectMix,
            1f to frameColor,
        ),
        startX = 0f,
        endX = horizontalExtent,
    )
    val topFrameAlpha = (verticalOffsetPx / spec.heightPx).coerceIn(0f, 1f)
    val verticalFadeBrush = Brush.verticalGradient(
        colors = listOf(frameColor.copy(alpha = topFrameAlpha), frameColor),
        startY = 0f,
        endY = visibleGradientHeight.coerceAtLeast(1f),
    )

    onDrawBehind {
        drawRect(color = frameColor)
        if (visibleGradientHeight <= 0f) return@onDrawBehind

        val gradientSize = Size(width = size.width, height = visibleGradientHeight.coerceAtMost(size.height))
        drawRect(
            brush = horizontalBrush,
            topLeft = Offset.Zero,
            size = gradientSize,
        )
        drawRect(
            brush = verticalFadeBrush,
            topLeft = Offset.Zero,
            size = gradientSize,
        )
    }
}
