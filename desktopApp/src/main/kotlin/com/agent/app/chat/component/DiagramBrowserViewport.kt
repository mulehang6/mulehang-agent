package com.agent.app.chat.component

import org.slf4j.LoggerFactory

private val DIAGRAM_VIEWPORT_LOGGER = LoggerFactory.getLogger("com.agent.app.chat.component.DiagramBrowserViewport")

/** 浏览器图表区域内的缩放锚点，坐标相对于嵌入式浏览器组件。 */
internal data class DiagramZoomAnchor(
    val x: Float,
    val y: Float,
)

/** 向已就绪的离线图表页面发送缩放与可选鼠标锚点，不重建浏览器会话。 */
internal fun DiagramBrowserSession.setDiagramZoom(
    zoomPercent: Int,
    anchor: DiagramZoomAnchor? = null,
) {
    if (browser.isClosed) return
    val normalizedZoom = normalizeDiagramZoomPercent(zoomPercent)
    val anchorArguments = anchor.toJavaScriptArguments()
    runCatching {
        browser.executeJavaScript(
            "window.mulehangDiagram?.setZoom($normalizedZoom, $anchorArguments);",
            "",
            0,
        )
    }.onFailure { error ->
        DIAGRAM_VIEWPORT_LOGGER.debug("离线图表缩放脚本未能执行。", error)
    }
}

/** 将有限的组件坐标序列化为 JavaScript 数字，无锚点时显式传递 undefined。 */
private fun DiagramZoomAnchor?.toJavaScriptArguments(): String {
    val currentAnchor = this ?: return "undefined, undefined"
    if (!currentAnchor.x.isFinite() || !currentAnchor.y.isFinite()) return "undefined, undefined"
    return "${currentAnchor.x}, ${currentAnchor.y}"
}
