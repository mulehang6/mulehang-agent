package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证打包的 Mermaid 后台工作器保持官方语义与本地安全边界。 */
class DiagramBrowserPagePolicyTest {

    /** Mermaid 标签配置必须使用当前版本支持的全局入口，并拒绝不能安全轮廓化的 HTML 标签。 */
    @Test
    fun keepsStrictMermaidLabelsAndSanitizesSvg() {
        val page = diagramPageContents()

        assertTrue(page.contains("securityLevel: \"strict\""))
        assertTrue(page.contains("htmlLabels: false"))
        assertFalse(page.contains("flowchart: { htmlLabels: false"))
        assertTrue(page.contains("node.localName === \"foreignObject\""))
        assertTrue(page.contains("throw workerError(\"unsupported\""))
        assertTrue(page.contains("script, iframe, object, embed, link"))
        assertTrue(page.contains("name === \"href\""))
        assertTrue(page.contains("name === \"xlink:href\""))
    }

    /** 自动主题只调图元配色，不能把一张不透明背景层写入输出 SVG。 */
    @Test
    fun keepsAutomaticMermaidBackgroundTransparent() {
        val page = diagramPageContents()

        assertTrue(page.contains("background: \"transparent\""))
        assertFalse(page.contains("--diagram-surface"))
        assertFalse(page.contains("background: var(--diagram-surface)"))
    }

    /** 自动 Mermaid 主题应把浏览器排版与最终 JVM 轮廓化统一到无衬线字体策略。 */
    @Test
    fun usesSansSerifForAutomaticMermaidLabelsWithoutOverridingSourceFont() {
        val page = diagramPageContents()

        assertTrue(page.contains("const APPLICATION_FONT_FAMILY = \"sans-serif\""))
        assertTrue(page.contains("themeVariables.fontFamily = APPLICATION_FONT_FAMILY"))
        assertTrue(page.contains("configuration.fontFamily = APPLICATION_FONT_FAMILY"))
        assertTrue(page.contains("hasMermaidSourceFontConfiguration"))
        assertTrue(page.contains("if (!sourceSetsFont)"))
    }

    /** 后台页不处理鼠标和滚轮，也不保留旧的浏览器滚轮桥接。 */
    @Test
    fun omitsViewportAndWheelBridge() {
        val page = diagramPageContents()

        assertFalse(page.contains("addEventListener(\"wheel\""))
        assertFalse(page.contains("mulehangDiagramWheelQuery"))
        assertFalse(page.contains("setDiagramZoom"))
        assertFalse(page.contains("pointerdown"))
    }

    /** 页面必须向 JVM 返回样式已经固化的 SVG，避免 Skia 忽略 Mermaid 的 CSS 而变成纯黑。 */
    @Test
    fun exposesStyledSvgWorkerProtocol() {
        val page = diagramPageContents()

        assertTrue(page.contains("mulehangMermaidRenderQuery"))
        assertTrue(page.contains("XMLSerializer"))
        assertTrue(page.contains("materializeComputedStyles"))
        assertTrue(page.contains("node.removeAttribute(\"style\")"))
        assertTrue(page.contains("ensureViewBox"))
        assertTrue(page.contains("hasMermaidSourceThemeConfiguration"))
    }

    /** 从测试运行时资源中读取与安装包相同的离线工作器页面。 */
    private fun diagramPageContents(): String = requireNotNull(
        DiagramBrowserPagePolicyTest::class.java.classLoader.getResourceAsStream("diagram/mermaid-worker.html"),
    ).bufferedReader().use { reader -> reader.readText() }
}
