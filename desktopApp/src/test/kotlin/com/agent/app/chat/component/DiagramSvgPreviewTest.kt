package com.agent.app.chat.component

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 验证 SVG 预览的尺寸解析、适配倍率和缩放锚点策略。 */
class DiagramSvgPreviewTest {

    /** 优先使用 viewBox，并保留小数宽高比。 */
    @Test
    fun readsIntrinsicSizeFromViewBox() {
        val size = diagramSvgIntrinsicSize("<svg viewBox=\"0 0 320 180\"></svg>")

        assertEquals(DiagramSvgIntrinsicSize(320f, 180f), size)
        assertEquals(320f / 180f, diagramSvgAspectRatio("<svg viewBox=\"0 0 320 180\"></svg>"))
    }

    /** 适配阶段不放大小图，超出视口的图按较短边缩小。 */
    @Test
    fun fitsVectorContentWithoutUpscaling() {
        assertEquals(1f, diagramSvgFitScale(DiagramSvgIntrinsicSize(100f, 50f), 400f, 200f))
        assertEquals(0.5f, diagramSvgFitScale(DiagramSvgIntrinsicSize(800f, 400f), 400f, 200f))
    }

    /** 缩放锚点保持同一内容位置，超出视口的偏移仍可由策略钳制。 */
    @Test
    fun keepsZoomAnchorAndComputesPanBounds() {
        val pan = diagramSvgPanAfterZoom(
            currentPan = Offset.Zero,
            currentZoomPercent = 100,
            nextZoomPercent = 200,
            anchor = Offset(300f, 100f),
            viewportWidth = 400f,
            viewportHeight = 200f,
        )

        assertEquals(-100f, pan.x)
        assertEquals(0f, pan.y)
        val bounds = diagramSvgPanBounds(
            intrinsicSize = DiagramSvgIntrinsicSize(400f, 200f),
            fitScale = 1f,
            viewportWidth = 400f,
            viewportHeight = 200f,
            zoomPercent = 200,
        )
        assertTrue(bounds.horizontal > 0f)
        assertTrue(bounds.vertical > 0f)
    }
}
