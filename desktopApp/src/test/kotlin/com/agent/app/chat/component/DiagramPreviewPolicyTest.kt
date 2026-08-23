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

    /** SVG 尚在生成时控件不可用，重渲染后的默认缩放始终为 100%。 */
    @Test
    fun enablesZoomOnlyAfterSvgReadyAndUsesDefaultForNewRender() {
        assertFalse(isDiagramPreviewReady(DiagramPreviewState.GeneratingMermaid))
        assertTrue(isDiagramPreviewReady(DiagramPreviewState.Ready(1.6f)))
        assertEquals(100, DIAGRAM_DEFAULT_ZOOM_PERCENT)
    }

    /** Ctrl+滚轮仍按固定步长缩放；普通滚轮已不再走图表输入路径。 */
    @Test
    fun zoomsOnlyForCtrlWheel() {
        assertFalse(shouldDiagramHandleScroll(isCtrlPressed = false, scrollDelta = 48f))
        assertTrue(shouldDiagramHandleScroll(isCtrlPressed = true, scrollDelta = -48f))
        assertFalse(shouldDiagramHandleScroll(isCtrlPressed = true, scrollDelta = Float.NaN))
        assertEquals(110, diagramZoomPercentAfterWheel(100, -480f))
        assertEquals(500, diagramZoomPercentAfterWheel(500, -48f))
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
