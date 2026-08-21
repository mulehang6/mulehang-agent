package com.agent.app.chat.component

import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 验证图表页面经 JCEF MessageRouter 上报的滚轮消息。 */
class DiagramBrowserInputBridgeTest {

    /** 有效的页面滚轮输入应保留修饰键、方向和鼠标锚点。 */
    @Test
    fun parsesValidDomWheelInput() {
        assertEquals(
            DiagramBrowserWheelInput(
                controlDown = true,
                deltaY = -48.5f,
                x = 120f,
                y = 45.25f,
            ),
            parseDiagramBrowserWheelInput(
                """{"type":"wheel","controlDown":true,"deltaY":-48.5,"x":120,"y":45.25}""",
            ),
        )
    }

    /** 无坐标的有效输入仍可滚动或以默认中心锚点缩放。 */
    @Test
    fun acceptsWheelInputWithoutAnchorCoordinates() {
        assertEquals(
            DiagramBrowserWheelInput(
                controlDown = false,
                deltaY = 48f,
                x = null,
                y = null,
            ),
            parseDiagramBrowserWheelInput(
                """{"type":"wheel","controlDown":false,"deltaY":48,"extra":"ignored"}""",
            ),
        )
    }

    /** 非法、未知和非有限的页面输入不得进入图表交互策略。 */
    @Test
    fun rejectsInvalidDomWheelInput() {
        assertNull(parseDiagramBrowserWheelInput("""{"type":"drag","controlDown":false,"deltaY":1}"""))
        assertNull(parseDiagramBrowserWheelInput("""{"type":"wheel","controlDown":false,"deltaY":"down"}"""))
        assertNull(parseDiagramBrowserWheelInput("""{"type":"wheel","controlDown":false,"deltaY":null}"""))
        assertNull(parseDiagramBrowserWheelInput("not-json"))
    }

    /** 只有图表范围内且未消费的原生滚轮副本才应被拦截。 */
    @Test
    fun consumesOnlyUnconsumedWheelEventsOverTheDiagram() {
        assertTrue(shouldConsumeDiagramWheel(eventConsumed = false, isDiagramWheelCaptured = true))
        assertFalse(shouldConsumeDiagramWheel(eventConsumed = true, isDiagramWheelCaptured = true))
        assertFalse(shouldConsumeDiagramWheel(eventConsumed = false, isDiagramWheelCaptured = false))
    }

    /** 指针停在图表相邻像素时仍应稳定识别为图表输入。 */
    @Test
    fun retainsWheelCaptureAcrossDiagramBrowserEdge() {
        val browserBounds = Rectangle(20, 20, 100, 100)

        assertTrue(isPointerWithinDiagramBrowserBounds(browserBounds, Point(120, 50)))
        assertFalse(isPointerWithinDiagramBrowserBounds(browserBounds, Point(122, 50)))
    }

    /** 快速越过图表边界时，当前滚轮手势应短暂保留给图表，避免两层滚动竞争。 */
    @Test
    fun retainsWheelGestureDuringBoundaryHandoff() {
        val gestureStartNanos = 1_000_000_000L

        assertTrue(
            shouldRetainDiagramWheelCapture(
                isPointerOverBrowser = true,
                currentNanos = gestureStartNanos,
                lastDiagramWheelNanos = null,
            ),
        )
        assertTrue(
            shouldRetainDiagramWheelCapture(
                isPointerOverBrowser = false,
                currentNanos = gestureStartNanos + 100_000_000L,
                lastDiagramWheelNanos = gestureStartNanos,
            ),
        )
        assertFalse(
            shouldRetainDiagramWheelCapture(
                isPointerOverBrowser = false,
                currentNanos = gestureStartNanos + 120_000_001L,
                lastDiagramWheelNanos = gestureStartNanos,
            ),
        )
        assertFalse(
            shouldRetainDiagramWheelCapture(
                isPointerOverBrowser = false,
                currentNanos = gestureStartNanos,
                lastDiagramWheelNanos = null,
            ),
        )
    }
}
