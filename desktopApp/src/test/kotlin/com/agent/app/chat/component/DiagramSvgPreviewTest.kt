package com.agent.app.chat.component

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /** 100% 按可用画布适配，小图可放大，且保留稳定内边距。 */
    @Test
    fun fitsVectorContentAtBaseZoomIncludingUpscaling() {
        assertEquals(4f, diagramSvgFitScale(DiagramSvgIntrinsicSize(100f, 50f), 400f, 200f))
        assertEquals(0.5f, diagramSvgFitScale(DiagramSvgIntrinsicSize(800f, 400f), 400f, 200f))
        assertEquals(
            3.36f,
            diagramSvgFitScale(
                intrinsicSize = DiagramSvgIntrinsicSize(100f, 50f),
                viewportWidth = 400f,
                viewportHeight = 200f,
                contentInset = 16f,
            ),
        )
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

    /** 全局倍率只参与最终绘制计算，不会改写图表自行保存的缩放值。 */
    @Test
    fun combinesGlobalScaleWithLocalZoomWithoutChangingLocalZoom() {
        val localZoomPercent = 150

        assertEquals(
            1.5f,
            diagramSvgRenderScale(
                fitScale = 1f,
                zoomPercent = localZoomPercent,
                globalScalePercent = 100,
            ),
        )
        assertEquals(
            3f,
            diagramSvgRenderScale(
                fitScale = 1f,
                zoomPercent = localZoomPercent,
                globalScalePercent = 200,
            ),
        )
        assertTrue(
            diagramSvgPanBounds(
                intrinsicSize = DiagramSvgIntrinsicSize(400f, 200f),
                fitScale = 1f,
                viewportWidth = 400f,
                viewportHeight = 200f,
                zoomPercent = 100,
                globalScalePercent = 200,
            ).horizontal > 0f,
        )
    }

    /** 普通滚轮必须留给会话时间线，只有 Ctrl 加滚轮才由图表缩放消费。 */
    @Test
    fun handlesOnlyCtrlWheelInsideDiagram() {
        assertFalse(shouldDiagramHandleScroll(isCtrlPressed = false, scrollDelta = 120f))
        assertFalse(shouldDiagramHandleScroll(isCtrlPressed = true, scrollDelta = 0f))
        assertTrue(shouldDiagramHandleScroll(isCtrlPressed = true, scrollDelta = -120f))
    }
}
