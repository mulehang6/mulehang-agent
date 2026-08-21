package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证打包的离线图表页面保持 Mermaid 标签与本地安全边界。 */
class DiagramBrowserPagePolicyTest {

    /** Mermaid 标签配置必须使用当前版本支持的全局入口，且页面不得删除标签容器。 */
    @Test
    fun keepsStrictMermaidLabelsWithoutRemovingForeignObjects() {
        val page = diagramPageContents()

        assertTrue(page.contains("securityLevel: \"strict\""))
        assertTrue(page.contains("htmlLabels: false"))
        assertFalse(page.contains("flowchart: { htmlLabels: false"))
        assertFalse(page.contains("getElementsByTagName(\"foreignObject\")"))
        assertTrue(page.contains("attributeName === \"href\""))
        assertTrue(page.contains("attributeName === \"xlink:href\""))
    }

    /** 画布和工具条共用明确的图表表面色，避免透明 JCEF 页面露出不同底色。 */
    @Test
    fun usesAnExplicitDiagramSurfaceColor() {
        val page = diagramPageContents()

        assertTrue(page.contains("--diagram-surface"))
        assertTrue(page.contains("background: var(--diagram-surface)"))
    }

    /** 页面必须阻止 Chromium 默认滚轮缩放，由宿主统一决定聊天滚动或图表缩放。 */
    @Test
    fun preventsBrowserDefaultWheelHandling() {
        val page = diagramPageContents()

        assertTrue(page.contains("root.addEventListener(\"wheel\""))
        assertTrue(page.contains("event.preventDefault();"))
        assertTrue(page.contains("{ passive: false }"))
        assertTrue(page.contains("mulehangDiagramWheelQuery"))
    }

    /** 页面必须能把渲染后的 SVG 交给 Compose，避免滚动层继续承载 JCEF 表面。 */
    @Test
    fun exposesRenderedSvgExtractionProtocol() {
        val page = diagramPageContents()

        assertTrue(page.contains("mulehangDiagramSvgQuery"))
        assertTrue(page.contains("XMLSerializer"))
        assertTrue(page.contains("requestSvg"))
    }

    /** 从测试运行时资源中读取与安装包相同的离线图表页面。 */
    private fun diagramPageContents(): String = requireNotNull(
        DiagramBrowserPagePolicyTest::class.java.classLoader.getResourceAsStream("diagram/diagram.html"),
    ).bufferedReader().use { reader -> reader.readText() }
}
