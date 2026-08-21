package com.agent.app.chat.component

import java.awt.AWTEvent
import java.awt.Component
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseWheelEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import org.cef.browser.CefBrowser

private const val DIAGRAM_BROWSER_WHEEL_INPUT_TYPE = "wheel"
private const val DIAGRAM_BROWSER_WHEEL_BOUNDARY_TOLERANCE_PX = 2
private const val DIAGRAM_BROWSER_WHEEL_HANDOFF_DELAY_NANOS = 120_000_000L

/** 浏览器页面上报的一次带方向的 DOM 滚轮输入。 */
internal data class DiagramBrowserWheelInput(
    val controlDown: Boolean,
    val deltaY: Float,
    val x: Float?,
    val y: Float?,
)

/** 解析并校验页面经 JCEF MessageRouter 上报的图表滚轮输入。 */
internal fun parseDiagramBrowserWheelInput(request: String): DiagramBrowserWheelInput? {
    val payload = runCatching { Json.parseToJsonElement(request).jsonObject }.getOrNull() ?: return null
    val type = (payload["type"] as? JsonPrimitive)?.contentOrNull
    if (type != DIAGRAM_BROWSER_WHEEL_INPUT_TYPE) return null
    val controlDown = (payload["controlDown"] as? JsonPrimitive)?.booleanOrNull ?: return null
    val deltaY = payload.finiteFloat("deltaY") ?: return null
    return DiagramBrowserWheelInput(
        controlDown = controlDown,
        deltaY = deltaY,
        x = payload.finiteFloat("x"),
        y = payload.finiteFloat("y"),
    )
}

/** 从 JSON 对象中读取一个有限的浮点字段，缺失和非法值均返回空。 */
private fun JsonObject.finiteFloat(name: String): Float? = (this[name] as? JsonPrimitive)
    ?.doubleOrNull
    ?.toFloat()
    ?.takeIf(Float::isFinite)

/** 将单个图表会话的 DOM 滚轮消息绑定到 Compose，并阻止原生副本穿透到外层时间线。 */
@Suppress("unused")
internal class DiagramBrowserInputBridge(
    private val browser: CefBrowser,
    private val browserComponent: Component,
    private val onWheel: (DiagramBrowserWheelInput) -> Unit,
) : AutoCloseable {
    private var installed = false

    /** 注册当前浏览器的 DOM 回调和共享的原生滚轮拦截器。 */
    fun install() {
        if (installed) return
        DiagramBrowserRuntime.registerWheelCallback(browser, onWheel)
        DiagramBrowserNativeWheelInterceptor.register(browserComponent)
        installed = true
    }

    /** 在 Compose 移除图表时撤销当前会话的回调和共享原生拦截器登记。 */
    override fun close() {
        if (!installed) return
        DiagramBrowserRuntime.unregisterWheelCallback(browser, onWheel)
        DiagramBrowserNativeWheelInterceptor.unregister(browserComponent)
        installed = false
    }
}

/** 所有图表共用的一份原生滚轮拦截器，避免同一事件被每个图表重复检查。 */
private object DiagramBrowserNativeWheelInterceptor {
    private val browserComponents = linkedSetOf<Component>()
    private val nativeWheelListener = AWTEventListener(::consumeNativeWheel)
    private var listenerInstalled = false
    private var lastDiagramWheelNanos: Long? = null

    /** 登记一个可接收图表滚轮的浏览器组件。 */
    fun register(browserComponent: Component): Unit = synchronized(browserComponents) {
        if (!browserComponents.add(browserComponent) || listenerInstalled) return
        Toolkit.getDefaultToolkit().addAWTEventListener(nativeWheelListener, AWTEvent.MOUSE_WHEEL_EVENT_MASK)
        listenerInstalled = true
    }

    /** 取消登记一个不再显示的浏览器组件。 */
    fun unregister(browserComponent: Component): Unit = synchronized(browserComponents) {
        browserComponents.remove(browserComponent)
        if (browserComponents.isNotEmpty() || !listenerInstalled) return
        Toolkit.getDefaultToolkit().removeAWTEventListener(nativeWheelListener)
        listenerInstalled = false
        lastDiagramWheelNanos = null
    }

    /** 仅消费指针位于任一图表上的原生滚轮副本，避免外层 Compose 同时滚动。 */
    private fun consumeNativeWheel(event: AWTEvent) {
        val wheelEvent = event as? MouseWheelEvent ?: return
        val pointerLocation = MouseInfo.getPointerInfo()?.location ?: return
        val captureWheel = synchronized(browserComponents) {
            val isPointerOverBrowser = browserComponents.any { browserComponent ->
                browserComponent.containsScreenPoint(pointerLocation)
            }
            val currentNanos = System.nanoTime()
            if (isPointerOverBrowser) lastDiagramWheelNanos = currentNanos
            shouldRetainDiagramWheelCapture(
                isPointerOverBrowser = isPointerOverBrowser,
                currentNanos = currentNanos,
                lastDiagramWheelNanos = lastDiagramWheelNanos,
            )
        }
        if (shouldConsumeDiagramWheel(wheelEvent.isConsumed, captureWheel)) {
            wheelEvent.consume()
        }
    }
}

/** 判断屏幕指针是否处于图表浏览器的边缘容差范围内。 */
private fun Component.containsScreenPoint(pointerLocation: Point): Boolean = runCatching {
    if (!isShowing) return@runCatching false
    isPointerWithinDiagramBrowserBounds(Rectangle(locationOnScreen, size), pointerLocation)
}.getOrDefault(false)

/** 判断屏幕指针是否位于图表浏览器边界或其小范围容差内。 */
internal fun isPointerWithinDiagramBrowserBounds(
    browserBounds: Rectangle,
    pointerLocation: Point,
): Boolean = Rectangle(browserBounds)
    .apply {
        grow(DIAGRAM_BROWSER_WHEEL_BOUNDARY_TOLERANCE_PX, DIAGRAM_BROWSER_WHEEL_BOUNDARY_TOLERANCE_PX)
    }
    .contains(pointerLocation)

/** 在刚离开图表的短暂手势交接期内，继续由图表独占原生滚轮副本。 */
internal fun shouldRetainDiagramWheelCapture(
    isPointerOverBrowser: Boolean,
    currentNanos: Long,
    lastDiagramWheelNanos: Long?,
): Boolean {
    if (isPointerOverBrowser) return true
    val previousNanos = lastDiagramWheelNanos ?: return false
    val elapsedNanos = currentNanos - previousNanos
    return elapsedNanos in 0..DIAGRAM_BROWSER_WHEEL_HANDOFF_DELAY_NANOS
}

/** 仅在原生事件尚未消费且当前滚轮手势归属图表时阻止其继续分发。 */
internal fun shouldConsumeDiagramWheel(
    eventConsumed: Boolean,
    isDiagramWheelCaptured: Boolean,
): Boolean = !eventConsumed && isDiagramWheelCaptured
