package com.agent.app.chat.component

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.ByteArrayOutputStream
import java.io.StringReader
import kotlin.math.min
import kotlin.math.sqrt
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import org.jetbrains.skia.Image

/**
 * Skia 的 SVGDOM 不绘制 `<text>` 元素（实测 PlantUML 与 Mermaid 的 SVG 文字全部丢失），
 * 因此图表在 JVM 内用 Batik 栅格化为位图后再交给 Compose 画布缩放绘制，文字与 CSS 样式得以保留。
 */

/** 返回按目标宽度栅格化后的位图及其原始像素尺寸，位图与 SVG 保持相同宽高比。 */
internal data class DiagramSvgRasterImage(
    val bitmap: ImageBitmap,
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * 把目标宽度限制在面积与单边像素预算内。
 *
 * 细长的纵向图（时序图常见）按宽度缩放会让栅格高度爆炸式增长；横向图同理。先按
 * [RASTER_MAX_TOTAL_PIXELS] 面积预算等比收缩，再叠加 [RASTER_MAX_SIDE_PX] 单边上限，
 * 避免高缩放值下产生数百 MB 的位图。
 */
internal fun clampDiagramSvgRasterWidth(
    targetWidthPx: Float,
    aspectRatio: Float?,
): Float {
    val safeAspect = aspectRatio?.takeIf { it.isFinite() && it > 0f } ?: return targetWidthPx
    var width = targetWidthPx.coerceAtLeast(1f)
    var height = width / safeAspect
    val withinBudget =
        width <= RASTER_MAX_SIDE_PX && height <= RASTER_MAX_SIDE_PX && width * height <= RASTER_MAX_TOTAL_PIXELS
    if (!withinBudget) {
        width = sqrt(RASTER_MAX_TOTAL_PIXELS * safeAspect)
        height = width / safeAspect
        if (width > RASTER_MAX_SIDE_PX || height > RASTER_MAX_SIDE_PX) {
            width = min(width, RASTER_MAX_SIDE_PX)
            height = width / safeAspect
            if (height > RASTER_MAX_SIDE_PX) {
                width = RASTER_MAX_SIDE_PX * safeAspect
            }
        }
    }
    return width
}

/**
 * 用 Batik 把 SVG 栅格化为 PNG 字节（隔离测试入口）。
 *
 * [targetWidthPx] 为输出位图的宽度，高度按 SVG 宽高比等比计算；PNGTranscoder 自动
 * 应用到 SVG 根节点的百分比宽度（Mermaid 的 `width="100%"`）。调用方应在线程池中执行。
 */
internal fun renderDiagramSvgToPngBytes(
    svg: String,
    targetWidthPx: Float,
): ByteArray {
    require(targetWidthPx > 0.0f && targetWidthPx.isFinite()) { "无效的栅格化宽度：$targetWidthPx" }
    val intrinsicSize = diagramSvgIntrinsicSize(svg)
    val safeWidth = clampDiagramSvgRasterWidth(
        targetWidthPx = targetWidthPx,
        aspectRatio = if (intrinsicSize.width > 0f && intrinsicSize.height > 0f) {
            intrinsicSize.width / intrinsicSize.height
        } else {
            null
        },
    )
    val transcoder = PNGTranscoder().apply {
        addTranscodingHint(PNGTranscoder.KEY_WIDTH, safeWidth)
    }
    val output = ByteArrayOutputStream()
    try {
        transcoder.transcode(
            TranscoderInput(StringReader(svg)),
            TranscoderOutput(output),
        )
    } catch (error: Exception) {
        throw IllegalStateException("SVG 栅格化失败：${error.message}", error)
    }
    return output.toByteArray()
}

/** 将 SVG 渲染为 Compose 可绘制的位图。 */
internal fun rasterizeDiagramSvg(
    svg: String,
    targetWidthPx: Float,
): DiagramSvgRasterImage {
    val image = Image.makeFromEncoded(renderDiagramSvgToPngBytes(svg, targetWidthPx))
    return DiagramSvgRasterImage(
        bitmap = image.toComposeImageBitmap(),
        widthPx = image.width,
        heightPx = image.height,
    )
}

/**
 * 计算当前视口与缩放值对应的栅格目标宽度。
 *
 * 以 [viewportWidthPx] 为 100% 基准，保留 [RASTER_OVERSCAN_SCALE] 余量，避免拖拽平移
 * 时露出未覆盖区域；缩放先按 [RASTER_ZOOM_BUCKET_PERCENT] 取整，同一缩放档位内复用
 * 同一位图，避免缩放滑块每个步进都触发一次重栅格化。
 */
internal fun diagramSvgRasterTargetWidthPx(
    viewportWidthPx: Float,
    zoomPercent: Int,
): Float {
    val normalizedZoom = normalizeDiagramZoomPercent(zoomPercent)
    val bucketedZoom = normalizedZoom / RASTER_ZOOM_BUCKET_PERCENT * RASTER_ZOOM_BUCKET_PERCENT
    val scale = bucketedZoom / DIAGRAM_DEFAULT_ZOOM_PERCENT.toFloat()
    return (viewportWidthPx * scale * RASTER_OVERSCAN_SCALE).coerceAtLeast(1f)
}

/** 栅格化输出的宽度余量，保证平移边界内不出现空白。 */
internal const val RASTER_OVERSCAN_SCALE = 1.25f

/** 缩放每变化多少百分比算同一档栅格缓存，避免滑块步进反复重渲。 */
internal const val RASTER_ZOOM_BUCKET_PERCENT = 25

/** 栅格图单边像素上限，防止超高缩放把位图放到数百 MB。 */
internal const val RASTER_MAX_SIDE_PX = 6000f

/** 栅格图总像素预算（约 24M 像素，RGBA 约 96 MB 单图峰值）。 */
internal const val RASTER_MAX_TOTAL_PIXELS = 24_000_000f
