package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证图表预览的尺寸、缩放、拖拽和主题保留策略。 */
class DiagramPreviewPolicyTest {

    /** 小图、常规图和超长图应分别落在最小值、内容值和最大值。 */
    @Test
    fun calculatesViewportHeightWithinReadableBounds() {
        assertEquals(DIAGRAM_LOADING_VIEWPORT_HEIGHT_DP, diagramViewportHeightDp(720f, null))
        assertEquals(DIAGRAM_MIN_VIEWPORT_HEIGHT_DP, diagramViewportHeightDp(720f, 8f))
        assertEquals(DIAGRAM_MAX_CARD_HEIGHT_DP - DIAGRAM_TOOLBAR_HEIGHT_DP, diagramViewportHeightDp(720f, 0.1f))
        assertEquals(480f, diagramViewportHeightDp(720f, 1.5f))
    }

    /** 50%、100%、200% 和 500% 的边界应让低倍率不可拖拽、高倍率恰好覆盖视口。 */
    @Test
    fun boundsPanForEachZoomRangeBoundary() {
        assertEquals(DiagramPanBounds(0f, 0f), diagramPanBounds(400f, 200f, 50))
        assertEquals(DiagramPanBounds(0f, 0f), diagramPanBounds(400f, 200f, 100))
        assertEquals(DiagramPanBounds(200f, 100f), diagramPanBounds(400f, 200f, 200))
        assertEquals(DiagramPanBounds(800f, 400f), diagramPanBounds(400f, 200f, 500))
        assertEquals(200f, clampDiagramPanOffset(260f, 200f))
        assertEquals(-200f, clampDiagramPanOffset(-260f, 200f))
    }

    /** 输入提交时超界值钳制，空值和非数字恢复当前有效缩放。 */
    @Test
    fun normalizesZoomInputWithoutAcceptingInvalidValues() {
        assertEquals(50, normalizeDiagramZoomInput("49", 100))
        assertEquals(150, normalizeDiagramZoomInput("150", 100))
        assertEquals(500, normalizeDiagramZoomInput("501", 100))
        assertEquals(120, normalizeDiagramZoomInput("", 120))
        assertEquals(120, normalizeDiagramZoomInput("12x", 120))
        assertTrue(isDiagramZoomInputCandidate("500"))
        assertFalse(isDiagramZoomInputCandidate("20%"))
    }

    /** 页面未握手时控件不可用，重渲染后的默认缩放始终为 100%。 */
    @Test
    fun enablesZoomOnlyAfterPageReadyAndUsesDefaultForNewRender() {
        assertFalse(isDiagramPreviewReady(DiagramPreviewState.PageLoaded))
        assertTrue(isDiagramPreviewReady(DiagramPreviewState.Ready(1.6f)))
        assertEquals(100, DIAGRAM_DEFAULT_ZOOM_PERCENT)
    }

    /** 普通 DOM 滚轮的正负像素增量必须原样保留给聊天时间线。 */
    @Test
    fun preservesDomWheelDirectionWhenForwardingToTimeline() {
        assertEquals(
            DiagramWheelIntent.TimelineScroll(48f),
            diagramWheelIntent(
                controlDown = false,
                currentZoomPercent = 100,
                scrollDelta = 48f,
            ),
        )
        assertEquals(
            DiagramWheelIntent.TimelineScroll(-48f),
            diagramWheelIntent(
                controlDown = false,
                currentZoomPercent = 100,
                scrollDelta = -48f,
            ),
        )
        assertEquals(
            DiagramWheelIntent.Ignored,
            diagramWheelIntent(
                controlDown = false,
                currentZoomPercent = 100,
                scrollDelta = Float.NaN,
            ),
        )
    }

    /** Ctrl+滚轮只改变当前图表缩放，普通滚轮只产生时间线滚动意图。 */
    @Test
    fun routesWheelInputBetweenTimelineAndDiagramZoom() {
        val timelineIntent = diagramWheelIntent(
            controlDown = false,
            currentZoomPercent = 100,
            scrollDelta = 48f,
        )
        val zoomInIntent = diagramWheelIntent(
            controlDown = true,
            currentZoomPercent = 100,
            scrollDelta = -48f,
        )
        val zoomOutIntent = diagramWheelIntent(
            controlDown = true,
            currentZoomPercent = 100,
            scrollDelta = 48f,
        )

        assertEquals(DiagramWheelIntent.TimelineScroll(48f), timelineIntent)
        assertEquals(DiagramWheelIntent.Zoom(110), zoomInIntent)
        assertEquals(DiagramWheelIntent.Zoom(90), zoomOutIntent)
        assertEquals(110, diagramZoomPercentAfterWheel(100, -480f))
        assertEquals(
            DiagramWheelIntent.Zoom(500),
            diagramWheelIntent(
                controlDown = true,
                currentZoomPercent = 500,
                scrollDelta = -48f,
            ),
        )
    }

    /** 源码与渲染按钮只在当前图表卡片内切换，不引入侧栏状态。 */
    @Test
    fun togglesDiagramDisplayModeInsideCurrentCard() {
        assertEquals(DiagramPreviewDisplayMode.SOURCE, DiagramPreviewDisplayMode.RENDERED.toggled())
        assertEquals(DiagramPreviewDisplayMode.RENDERED, DiagramPreviewDisplayMode.SOURCE.toggled())
    }

    /** Mermaid 只有在源码未声明主题时才继承应用自动配色。 */
    @Test
    fun preservesMermaidSourceThemeConfiguration() {
        assertTrue(shouldApplyMermaidAutomaticTheme("flowchart TD\nA --> B"))
        assertFalse(
            shouldApplyMermaidAutomaticTheme(
                "%%{init: { 'theme': 'forest' }}%%\nflowchart TD\nA --> B",
            ),
        )
        assertFalse(
            shouldApplyMermaidAutomaticTheme(
                "---\nconfig:\n  themeVariables:\n    primaryColor: '#fff'\n---\nflowchart TD\nA --> B",
            ),
        )
    }
}
