package com.agent.app.chat.component

import kotlin.math.max

/** 图表预览在一张卡片内可使用的缩放范围，其中 100% 表示适应画布。 */
internal const val DIAGRAM_MIN_ZOOM_PERCENT = 50
internal const val DIAGRAM_DEFAULT_ZOOM_PERCENT = 100
internal const val DIAGRAM_MAX_ZOOM_PERCENT = 500
internal const val DIAGRAM_ZOOM_STEP_PERCENT = 10
internal const val DIAGRAM_ZOOM_SLIDER_STEPS =
    (DIAGRAM_MAX_ZOOM_PERCENT - DIAGRAM_MIN_ZOOM_PERCENT) / DIAGRAM_ZOOM_STEP_PERCENT - 1

/** 图表卡片的整体高度上限，包含工具条。 */
internal const val DIAGRAM_MAX_CARD_HEIGHT_DP = 960f

/** 图表工具条占用的稳定高度，避免加载完成时控件发生跳动。 */
internal const val DIAGRAM_TOOLBAR_HEIGHT_DP = 40f

/** 未取得 SVG 宽高比时使用的浏览器视口高度。 */
internal const val DIAGRAM_LOADING_VIEWPORT_HEIGHT_DP = 360f

/** 过宽图表仍保留的最小可读视口高度。 */
internal const val DIAGRAM_MIN_VIEWPORT_HEIGHT_DP = 200f

/** 自动适配时为图形与卡片边框保留的稳定内边距。 */
internal const val DIAGRAM_VIEWPORT_CONTENT_INSET_DP = 16f

/** 图表卡片在渲染视图与原始源码视图之间的用户选择。 */
internal enum class DiagramPreviewDisplayMode {
    RENDERED,
    SOURCE,
}

/** 返回当前图表卡片的另一种显示模式。 */
internal fun DiagramPreviewDisplayMode.toggled(): DiagramPreviewDisplayMode = when (this) {
    DiagramPreviewDisplayMode.RENDERED -> DiagramPreviewDisplayMode.SOURCE
    DiagramPreviewDisplayMode.SOURCE -> DiagramPreviewDisplayMode.RENDERED
}

/** 图表在缩放后可沿两个方向移动的最大半程。 */
internal data class DiagramPanBounds(
    val horizontal: Float,
    val vertical: Float,
)

/**
 * 根据 SVG 宽高比计算浏览器视口的高度。
 *
 * 宽高比未知时保留稳定的加载高度；已知时优先完整展示图表，再限制在卡片的可读范围内。
 */
internal fun diagramViewportHeightDp(
    availableWidthDp: Float,
    aspectRatio: Float?,
): Float {
    val maxViewportHeight = DIAGRAM_MAX_CARD_HEIGHT_DP - DIAGRAM_TOOLBAR_HEIGHT_DP
    val safeWidth = availableWidthDp.takeIf { it.isFinite() && it > 0f } ?: 0f
    val safeAspectRatio = aspectRatio?.takeIf { it.isFinite() && it > 0f }
    val targetHeight = safeAspectRatio?.let { safeWidth / it } ?: DIAGRAM_LOADING_VIEWPORT_HEIGHT_DP
    return targetHeight.coerceIn(DIAGRAM_MIN_VIEWPORT_HEIGHT_DP, maxViewportHeight)
}

/** 将缩放值限制在图表工具条允许的范围内。 */
internal fun normalizeDiagramZoomPercent(percent: Int): Int =
    percent.coerceIn(DIAGRAM_MIN_ZOOM_PERCENT, DIAGRAM_MAX_ZOOM_PERCENT)

/** 根据带方向的指针滚轮增量计算下一个 10% 对齐的缩放值。 */
internal fun diagramZoomPercentAfterWheel(
    currentPercent: Int,
    scrollDelta: Float,
): Int {
    val normalizedDelta = scrollDelta.takeIf(Float::isFinite) ?: 0f
    if (normalizedDelta == 0f) return normalizeDiagramZoomPercent(currentPercent)
    val adjustment = if (normalizedDelta < 0f) {
        DIAGRAM_ZOOM_STEP_PERCENT
    } else {
        -DIAGRAM_ZOOM_STEP_PERCENT
    }
    return normalizeDiagramZoomPercent(currentPercent + adjustment)
}

/**
 * 在输入框提交时规范化缩放百分比。
 *
 * 空值、非数字和溢出值恢复为当前有效缩放；数值超出范围时钳制到边界。
 */
internal fun normalizeDiagramZoomInput(
    input: String,
    currentPercent: Int,
): Int {
    val normalizedCurrent = normalizeDiagramZoomPercent(currentPercent)
    val candidate = input.trim()
    if (candidate.isEmpty() || candidate.any { !it.isDigit() }) return normalizedCurrent
    return candidate.toIntOrNull()?.let(::normalizeDiagramZoomPercent) ?: normalizedCurrent
}

/** 判断输入框中的临时文本是否仍可构成一个整数百分比。 */
internal fun isDiagramZoomInputCandidate(input: String): Boolean = input.all(Char::isDigit)

/**
 * 计算缩放后的图表允许平移范围。
 *
 * 100% 及以下不允许平移；超过 100% 时边界恰好遮住视口，不会露出空白背景。
 */
internal fun diagramPanBounds(
    viewportWidth: Float,
    viewportHeight: Float,
    zoomPercent: Int,
): DiagramPanBounds {
    val scale = normalizeDiagramZoomPercent(zoomPercent) / DIAGRAM_DEFAULT_ZOOM_PERCENT.toFloat()
    val safeWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: 0f
    val safeHeight = viewportHeight.takeIf { it.isFinite() && it > 0f } ?: 0f
    return DiagramPanBounds(
        horizontal = max(0f, (safeWidth * scale - safeWidth) / 2f),
        vertical = max(0f, (safeHeight * scale - safeHeight) / 2f),
    )
}

/** 将拖拽后的偏移限制在 [DiagramPanBounds] 对应的单轴边界内。 */
internal fun clampDiagramPanOffset(
    offset: Float,
    bound: Float,
): Float {
    val safeBound = bound.takeIf { it.isFinite() && it > 0f } ?: 0f
    if (safeBound == 0f) return 0f
    val safeOffset = offset.takeIf { it.isFinite() } ?: 0f
    return safeOffset.coerceIn(-safeBound, safeBound)
}
