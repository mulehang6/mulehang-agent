package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 验证 Batik 栅格化保留 SVG 文字像素，并在同一缩放档位内稳定复用目标宽度。
 *
 * 回归背景：Skia 的 SVGDOM 不绘制 `<text>`，图表文字全部丢失，改由 Batik 栅格化兜底。
 */
class DiagramSvgRasterizerTest {

    /** 含文字的 SVG 与去掉文字的同构 SVG 栅格化结果必须不同，否则说明文字层仍被丢弃。 */
    @Test
    fun rasterizesSvgTextIntoSameCanvasPixels() {
        val withText = renderDiagramSvgToPngBytes(SVG_WITH_TEXT, 400f)
        val withoutText = renderDiagramSvgToPngBytes(SVG_WITHOUT_TEXT, 400f)

        assertTrue(withText.size > 100, "栅格化输出不应为空。")
        assertNotEquals(withText.contentToString(), withoutText.contentToString())
        assertEquals(400, org.jetbrains.skia.Image.makeFromEncoded(withText).width)
    }

    /** 同一缩放档位复用同一目标宽度，跨过档位边界才需要重新栅格化。 */
    @Test
    fun groupsTargetWidthByZoomBucket() {
        assertEquals(
            diagramSvgRasterTargetWidthPx(viewportWidthPx = 800f, zoomPercent = 100),
            diagramSvgRasterTargetWidthPx(viewportWidthPx = 800f, zoomPercent = 124),
        )
        assertNotEquals(
            diagramSvgRasterTargetWidthPx(viewportWidthPx = 800f, zoomPercent = 125),
            diagramSvgRasterTargetWidthPx(viewportWidthPx = 800f, zoomPercent = 124),
        )
    }

    /** 细长纵向图在高缩放目标宽度下，位图长边必须落在单边预算内。 */
    @Test
    fun clampsTallSvgRasterWithinSideBudget() {
        val clamped = clampDiagramSvgRasterWidth(targetWidthPx = 40000f, aspectRatio = 0.3f)
        val height = clamped / 0.3f

        assertTrue(clamped <= RASTER_MAX_SIDE_PX, "宽度超预算：$clamped")
        assertTrue(height <= RASTER_MAX_SIDE_PX + 1f, "高度超预算：$height")
        assertEquals(RASTER_MAX_SIDE_PX * 0.3f, clamped, absoluteTolerance = 0.01f)
    }

    /** 方形图在高缩放目标宽度下，总面积必须落在像素预算内。 */
    @Test
    fun clampsSquareSvgRasterWithinTotalBudget() {
        val clamped = clampDiagramSvgRasterWidth(targetWidthPx = 80000f, aspectRatio = 1f)

        assertTrue(clamped * clamped <= RASTER_MAX_TOTAL_PIXELS + 1f, "面积超预算：${clamped * clamped}")
        assertTrue(clamped <= RASTER_MAX_SIDE_PX, "宽度超预算：$clamped")
    }

    private companion object {
        val SVG_WITH_TEXT = """
            <svg xmlns="http://www.w3.org/2000/svg" width="400" height="200" viewBox="0 0 400 200">
              <rect width="400" height="200" fill="#191A1C"/>
              <rect x="60" y="60" width="160" height="60" fill="#31343C"/>
              <text x="70" y="105" font-family="sans-serif" font-size="32" fill="#F4F7FC">HELLO</text>
            </svg>
        """.trimIndent()

        val SVG_WITHOUT_TEXT = """
            <svg xmlns="http://www.w3.org/2000/svg" width="400" height="200" viewBox="0 0 400 200">
              <rect width="400" height="200" fill="#191A1C"/>
              <rect x="60" y="60" width="160" height="60" fill="#31343C"/>
            </svg>
        """.trimIndent()
    }
}
