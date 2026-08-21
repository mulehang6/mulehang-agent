@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.LocalDesktopPalette
import org.jetbrains.skia.Data
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** SVG 根节点声明的原始尺寸，供适配和裁剪计算使用。 */
internal data class DiagramSvgIntrinsicSize(
    val width: Float,
    val height: Float,
)

/** 从 SVG 的 viewBox 或宽高属性读取原始尺寸。 */
internal fun diagramSvgIntrinsicSize(svg: String): DiagramSvgIntrinsicSize {
    val viewBox = SVG_VIEW_BOX.find(svg)
    if (viewBox != null) {
        val viewBoxWidth = viewBox.groupValues[1].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
        val viewBoxHeight = viewBox.groupValues[2].toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
        if (viewBoxWidth != null && viewBoxHeight != null) {
            return DiagramSvgIntrinsicSize(width = viewBoxWidth, height = viewBoxHeight)
        }
    }
    val width = SVG_WIDTH.find(svg)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    val height = SVG_HEIGHT.find(svg)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    return DiagramSvgIntrinsicSize(
        width = width?.takeIf { it > 0f } ?: 1f,
        height = height?.takeIf { it > 0f } ?: 1f,
    )
}

/** 返回 SVG 的正宽高比，无法读取时交给默认视口高度。 */
internal fun diagramSvgAspectRatio(svg: String): Float? = diagramSvgIntrinsicSize(svg).let { size ->
    (size.width / size.height).takeIf { it.isFinite() && it > 0f }
}

/** 计算 SVG 在当前视口内不放大的初始适配倍率。 */
internal fun diagramSvgFitScale(
    intrinsicSize: DiagramSvgIntrinsicSize,
    viewportWidth: Float,
    viewportHeight: Float,
): Float {
    if (!viewportWidth.isFinite() || !viewportHeight.isFinite() || viewportWidth <= 0f || viewportHeight <= 0f) {
        return 1f
    }
    return minOf(
        viewportWidth / intrinsicSize.width,
        viewportHeight / intrinsicSize.height,
    ).coerceAtMost(1f)
}

/** 计算矢量图在当前缩放值下允许的平移边界。 */
internal fun diagramSvgPanBounds(
    intrinsicSize: DiagramSvgIntrinsicSize,
    fitScale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    zoomPercent: Int,
): DiagramPanBounds {
    val zoomScale = normalizeDiagramZoomPercent(zoomPercent) / DIAGRAM_DEFAULT_ZOOM_PERCENT.toFloat()
    val renderedWidth = intrinsicSize.width * fitScale * zoomScale
    val renderedHeight = intrinsicSize.height * fitScale * zoomScale
    return DiagramPanBounds(
        horizontal = ((renderedWidth - viewportWidth) / 2f).coerceAtLeast(0f),
        vertical = ((renderedHeight - viewportHeight) / 2f).coerceAtLeast(0f),
    )
}

/** 在缩放锚点处保持图形内容稳定，并返回新的平移偏移。 */
internal fun diagramSvgPanAfterZoom(
    currentPan: Offset,
    currentZoomPercent: Int,
    nextZoomPercent: Int,
    anchor: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
): Offset {
    val currentScale = normalizeDiagramZoomPercent(currentZoomPercent) / DIAGRAM_DEFAULT_ZOOM_PERCENT.toFloat()
    val nextScale = normalizeDiagramZoomPercent(nextZoomPercent) / DIAGRAM_DEFAULT_ZOOM_PERCENT.toFloat()
    if (currentScale == 0f || !currentScale.isFinite() || !nextScale.isFinite()) return currentPan
    val relativeX = anchor.x - viewportWidth / 2f - currentPan.x
    val relativeY = anchor.y - viewportHeight / 2f - currentPan.y
    return Offset(
        x = currentPan.x + relativeX * (1f - nextScale / currentScale),
        y = currentPan.y + relativeY * (1f - nextScale / currentScale),
    )
}

/** 在 Compose 中绘制已生成的 SVG，并把 JCEF 只留在后台渲染阶段。 */
@Composable
internal fun DiagramSvgSurface(
    kind: AssistantDiagramKind,
    source: String,
    svg: String,
    zoomPercent: Int,
    zoomInput: androidx.compose.ui.text.input.TextFieldValue,
    onZoomInputChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onZoomChange: (Int) -> Unit,
    onDiagramWheel: (Float) -> Unit,
    onDisplayModeChange: (DiagramPreviewDisplayMode) -> Unit,
) {
    val palette = LocalDesktopPalette.current
    val intrinsicSize = remember(svg) { diagramSvgIntrinsicSize(svg) }
    val documentResult = remember(svg) {
        runCatching { SVGDOM(Data.makeFromBytes(svg.encodeToByteArray())) }
    }
    val document = documentResult.getOrNull()
    if (document != null) {
        DisposableEffect(document) {
            onDispose(document::close)
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val viewportHeight = diagramViewportHeightDp(maxWidth.value, diagramSvgAspectRatio(svg))
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { viewportHeight.dp.toPx() }
        JewelSurface(
            role = JewelSurfaceRole.PANEL,
            radius = 12.dp,
            solidColor = palette.panelBackground,
            borderColor = palette.line,
            modifier = Modifier
                .fillMaxWidth()
                .height((viewportHeight + DIAGRAM_TOOLBAR_HEIGHT_DP).dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DiagramPreviewToolbar(
                    displayMode = DiagramPreviewDisplayMode.RENDERED,
                    enabled = document != null,
                    zoomPercent = zoomPercent,
                    zoomInput = zoomInput,
                    onZoomInputChange = onZoomInputChange,
                    onZoomChange = onZoomChange,
                    onDisplayModeChange = onDisplayModeChange,
                )
                if (document == null) {
                    DiagramSvgFallback(
                        kind = kind,
                        source = source,
                    )
                } else {
                    DiagramSvgCanvas(
                        document = document,
                        intrinsicSize = intrinsicSize,
                        zoomPercent = zoomPercent,
                        viewportWidthPx = viewportWidthPx,
                        viewportHeightPx = viewportHeightPx,
                        onDiagramWheel = onDiagramWheel,
                        onDiagramZoom = onZoomChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(viewportHeight.dp),
                    )
                }
            }
        }
    }
}

/** 负责矢量图的裁剪、拖拽、滚轮缩放和锚点保持。 */
@Composable
private fun DiagramSvgCanvas(
    document: SVGDOM,
    intrinsicSize: DiagramSvgIntrinsicSize,
    zoomPercent: Int,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    onDiagramWheel: (Float) -> Unit,
    onDiagramZoom: (Int) -> Unit,
    modifier: Modifier,
) {
    var panOffset by remember(document) { mutableStateOf(Offset.Zero) }
    val normalizedZoom = normalizeDiagramZoomPercent(zoomPercent)
    val fitScale = diagramSvgFitScale(intrinsicSize, viewportWidthPx, viewportHeightPx)
    LaunchedEffect(normalizedZoom, viewportWidthPx, viewportHeightPx, fitScale) {
        val bounds = diagramSvgPanBounds(
            intrinsicSize = intrinsicSize,
            fitScale = fitScale,
            viewportWidth = viewportWidthPx,
            viewportHeight = viewportHeightPx,
            zoomPercent = normalizedZoom,
        )
        panOffset = Offset(
            x = clampDiagramPanOffset(panOffset.x, bounds.horizontal),
            y = clampDiagramPanOffset(panOffset.y, bounds.vertical),
        )
    }
    Canvas(
        modifier = modifier
            .clipToBounds()
            .onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Initial) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val scrollDelta = change.scrollDelta.y
                if (scrollDelta == 0f) return@onPointerEvent
                change.consume()
                if (event.keyboardModifiers.isCtrlPressed) {
                    val nextZoom = diagramZoomPercentAfterWheel(normalizedZoom, scrollDelta)
                    if (nextZoom != normalizedZoom) {
                        val anchor = change.position
                        panOffset = diagramSvgPanAfterZoom(
                            currentPan = panOffset,
                            currentZoomPercent = normalizedZoom,
                            nextZoomPercent = nextZoom,
                            anchor = anchor,
                            viewportWidth = viewportWidthPx,
                            viewportHeight = viewportHeightPx,
                        )
                        onDiagramZoom(nextZoom)
                    }
                } else {
                    onDiagramWheel(scrollDelta)
                }
            }
            .pointerInput(document, normalizedZoom, viewportWidthPx, viewportHeightPx) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) {
                        onDiagramZoom(
                            normalizeDiagramZoomPercent((normalizedZoom * zoom).toInt()),
                        )
                    }
                    if (pan != Offset.Zero) {
                        val bounds = diagramSvgPanBounds(
                            intrinsicSize = intrinsicSize,
                            fitScale = fitScale,
                            viewportWidth = viewportWidthPx,
                            viewportHeight = viewportHeightPx,
                            zoomPercent = normalizedZoom,
                        )
                        panOffset = Offset(
                            x = clampDiagramPanOffset(panOffset.x + pan.x, bounds.horizontal),
                            y = clampDiagramPanOffset(panOffset.y + pan.y, bounds.vertical),
                        )
                    }
                }
            },
    ) {
        val zoomScale = normalizedZoom / DIAGRAM_DEFAULT_ZOOM_PERCENT.toFloat()
        val renderScale = fitScale * zoomScale
        val renderedWidth = intrinsicSize.width * renderScale
        val renderedHeight = intrinsicSize.height * renderScale
        val centeredOffset = Offset(
            x = (size.width - renderedWidth) / 2f + panOffset.x,
            y = (size.height - renderedHeight) / 2f + panOffset.y,
        )
        document.setContainerSize(intrinsicSize.width, intrinsicSize.height)
        withTransform({
            translate(left = centeredOffset.x, top = centeredOffset.y)
            scale(scaleX = renderScale, scaleY = renderScale, pivot = Offset.Zero)
        }) {
            drawIntoCanvas { canvas -> document.render(canvas.skiaCanvas) }
        }
    }
}

private val SVG_VIEW_BOX = Regex(
    """\bviewBox\s*=\s*["']\s*[-+]?\d+(?:\.\d+)?[\s,]+[-+]?\d+(?:\.\d+)?[\s,]+([-+]?\d+(?:\.\d+)?)[\s,]+([-+]?\d+(?:\.\d+)?)\s*["']""",
    RegexOption.IGNORE_CASE,
)

private val SVG_WIDTH = Regex(
    """\bwidth\s*=\s*["']\s*([-+]?\d+(?:\.\d+)?)(?:px)?\s*["']""",
    RegexOption.IGNORE_CASE,
)

private val SVG_HEIGHT = Regex(
    """\bheight\s*=\s*["']\s*([-+]?\d+(?:\.\d+)?)(?:px)?\s*["']""",
    RegexOption.IGNORE_CASE,
)

/** 当矢量文档无法解析时保留源码，避免图表错误阻塞整条回答。 */
@Composable
private fun DiagramSvgFallback(
    kind: AssistantDiagramKind,
    source: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "图表 SVG 无法绘制，已显示为代码。",
            style = JewelTheme.defaultTextStyle.copy(color = LocalDesktopPalette.current.muted),
        )
        AssistantCodeBlock(language = kind.fenceLanguage, source = source)
    }
}
