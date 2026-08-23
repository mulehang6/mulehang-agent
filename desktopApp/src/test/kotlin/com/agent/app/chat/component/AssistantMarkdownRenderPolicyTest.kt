package com.agent.app.chat.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.agent.app.design.AppMarkdownLink
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.intui.standalone.code.highlighting.SimpleCodeHighlighter
import org.jetbrains.jewel.intui.standalone.code.highlighting.SyntaxHighlightColors

/** 验证流式 Markdown、Jewel 高亮和离线图表预览之间的渲染边界。 */
@OptIn(ExperimentalJewelApi::class)
class AssistantMarkdownRenderPolicyTest {

    /** 链接应使用应用强调色，而不是 Markdown 库的默认纯蓝。 */
    @Test
    fun usesMutedAppAccentForMarkdownLinks() {
        val style = assistantMarkdownLinkStyle()

        assertEquals(AppMarkdownLink, style.color)
        assertNotEquals(Color.Blue, style.color)
        assertEquals(TextDecoration.Underline, style.textDecoration)
    }

    /** 安全 HTML 语义应先归一化，再交由 Markdown parser 处理。 */
    @Test
    fun normalizesSafeHtmlMarkupBeforeMarkdownParsing() {
        val content = normalizeAssistantMarkdown("<div><b>重点</b><br>下一行</div>")

        assertFalse(content.contains("<div"))
        assertFalse(content.contains("<br"))
        assertTrue(content.contains("**重点**"))
        assertTrue(content.contains("下一行"))
    }

    /** 已闭合的 PlantUML 围栏必须作为离线图表块提取。 */
    @Test
    fun extractsCompletedPlantUmlFenceAsDiagramBlock() {
        val blocks = splitAssistantMarkdownBlocks(
            "说明\n```plantuml\n@startuml\nAlice -> Bob: Hi\n@enduml\n```\n结尾",
        )

        assertEquals(3, blocks.size)
        assertIs<AssistantMarkdownBlock.Text>(blocks[0])
        val diagram = assertIs<AssistantMarkdownBlock.Diagram>(blocks[1])
        assertEquals(AssistantDiagramKind.PLANT_UML, diagram.kind)
        assertEquals("@startuml\nAlice -> Bob: Hi\n@enduml", diagram.source)
        assertIs<AssistantMarkdownBlock.Text>(blocks[2])
    }

    /** PlantUML 别名与 Mermaid 都应映射到各自的图表类型。 */
    @Test
    fun recognizesSupportedDiagramFenceLanguages() {
        val blocks = splitAssistantMarkdownBlocks(
            "```puml\n@startuml\n@enduml\n```\n```mermaid\ngraph TD\nA --> B\n```",
        )

        val diagrams = blocks.filterIsInstance<AssistantMarkdownBlock.Diagram>()
        assertEquals(
            listOf(AssistantDiagramKind.PLANT_UML, AssistantDiagramKind.MERMAID),
            diagrams.map(AssistantMarkdownBlock.Diagram::kind),
        )
    }

    /** 未闭合的流式 Mermaid 围栏只能作为代码显示，防止触发半成品预览。 */
    @Test
    fun keepsUnclosedStreamingDiagramFenceAsCodeBlock() {
        val streaming = parseAssistantMarkdownStreamingDocument("```mermaid\ngraph TD\nA --> B")
        val completed = parseAssistantMarkdownDocument("```mermaid\ngraph TD\nA --> B\n```")

        val streamingCode = assertIs<AssistantMarkdownBlock.Code>(streaming.blocks.single())
        assertEquals("mermaid", streamingCode.language)
        assertEquals("graph TD\nA --> B", streamingCode.source)
        assertIs<AssistantMarkdownBlock.Diagram>(completed.blocks.single())
    }

    /** 普通 fenced code 应保留语言标记，交给 Jewel 代码块显示。 */
    @Test
    fun preservesRegularFencedCodeLanguageAndSource() {
        val blocks = splitAssistantMarkdownBlocks("```python\ndef greet():\n    return \"hi\"\n```")

        val code = assertIs<AssistantMarkdownBlock.Code>(blocks.single())
        assertEquals("python", code.language)
        assertEquals("def greet():\n    return \"hi\"", code.source)
    }

    /** Jewel 的内置 Python grammar 应为关键字和字符串给出不同颜色。 */
    @Test
    fun highlightsKnownLanguageWithJewelHighlighter() = runBlocking {
        val colors = SyntaxHighlightColors.light()
        val highlighter = SimpleCodeHighlighter(
            colors = colors,
            additionalGrammars = emptyList(),
            highlightDispatcher = Dispatchers.Unconfined,
        )

        val highlighted = highlighter.highlight("def greet(): return \"hi\"", language = "python").first()

        assertTrue(highlighted.spanStyles.any { it.item.color == colors.keyword })
        assertTrue(highlighted.spanStyles.any { it.item.color == colors.string })
    }

    /** 未知语言应稳定回退成可复制、未着色的纯文本。 */
    @Test
    fun fallsBackToPlainTextForUnknownLanguage() = runBlocking {
        val source = "custom syntax"
        val highlighter = SimpleCodeHighlighter(
            colors = SyntaxHighlightColors.light(),
            additionalGrammars = emptyList(),
            highlightDispatcher = Dispatchers.Unconfined,
        )

        val highlighted = highlighter.highlight(source, language = "unknown-language").first()

        assertEquals(source, highlighted.text)
        assertTrue(highlighted.spanStyles.isEmpty())
    }

    /** 图表页面可携带 query 和 fragment，但不得加载资源根目录以外或联网地址。 */
    @Test
    fun permitsOnlyPackagedDiagramResourceUrls() {
        val resourceDirectory = Path.of("D:/mulehang/diagram").toAbsolutePath().normalize()
        val pageUrl = resourceDirectory.resolve("mermaid-worker.html").toUri().toString() +
            "?requestId=1&theme=dark&themePolicy=auto#encoded-source"

        assertTrue(isAllowedDiagramResourceUrl(pageUrl, resourceDirectory))
        assertTrue(
            isAllowedDiagramResourceUrl(
                resourceDirectory.resolve("mermaid/mermaid.min.js").toUri().toString(),
                resourceDirectory,
            ),
        )
        assertFalse(isAllowedDiagramResourceUrl("https://cdn.example.com/mermaid.js", resourceDirectory))
        assertFalse(
            isAllowedDiagramResourceUrl(
                resourceDirectory.resolve("../outside.html").toUri().toString(),
                resourceDirectory,
            ),
        )
    }

    /** 无法定位离线资源时，拦截器必须拒绝全部请求，不能退化为当前工作目录。 */
    @Test
    fun rejectsAllUrlsWhenDiagramResourceDirectoryIsUnavailable() {
        assertFalse(isAllowedDiagramResourceUrl("about:blank", resourceDirectory = null))
        assertFalse(
            isAllowedDiagramResourceUrl(
                Path.of("blocked-resource.html").toAbsolutePath().toUri().toString(),
                resourceDirectory = null,
            ),
        )
    }

    /** Mermaid 工作器结果必须保留 SVG 或带类别的回退原因。 */
    @Test
    fun keepsSvgAndRecoverableFailureSeparate() {
        val svg = "<svg viewBox=\"0 0 20 10\"/>"
        val syntaxFailure = DiagramPreviewFailure(
            kind = DiagramFailureKind.MERMAID_SYNTAX,
            detail = "unexpected token",
        )
        val rendered = DiagramRenderResult.Success(svg)
        val failed = DiagramRenderResult.Failure(syntaxFailure)

        assertEquals(svg, assertIs<DiagramRenderResult.Success>(rendered).svg)
        assertEquals(syntaxFailure, assertIs<DiagramRenderResult.Failure>(failed).failure)
        assertTrue(syntaxFailure.fallbackMessage().contains("Mermaid 语法"))
    }

    /** 安装包资源优先，开发运行则从 classpath 的本地 `diagram/` 目录读取 Mermaid。 */
    @Test
    fun locatesOfflineDiagramResourcesForPackageAndDevelopment() {
        val packageRoot = Files.createTempDirectory("mulehang-package-diagram")
        val developmentRoot = Files.createTempDirectory("mulehang-development-diagram")
        val missingPackageRoot = Files.createTempDirectory("mulehang-missing-diagram")
        try {
            val packageDiagram = createCompleteDiagramResources(packageRoot)
            val developmentDiagram = createCompleteDiagramResources(developmentRoot)

            assertEquals(
                packageDiagram,
                DiagramBrowserResourcePolicy.locateDiagramResourceDirectory(
                    packageResourcesDirectory = packageRoot,
                    classpathDiagramPage = developmentDiagram.resolve("mermaid-worker.html").toUri().toURL(),
                ),
            )
            assertEquals(
                developmentDiagram,
                DiagramBrowserResourcePolicy.locateDiagramResourceDirectory(
                    packageResourcesDirectory = missingPackageRoot,
                    classpathDiagramPage = developmentDiagram.resolve("mermaid-worker.html").toUri().toURL(),
                ),
            )
            assertEquals(Path.of("mermaid", "mermaid.min.js"), DiagramBrowserResourcePolicy.diagramMermaidEntryRelativePath())
        } finally {
            deleteDirectory(packageRoot)
            deleteDirectory(developmentRoot)
            deleteDirectory(missingPackageRoot)
        }
    }

    /** JCEF 必须使用当前 JBR 随附的 helper，而不是回退为主 Java 进程。 */
    @Test
    fun locatesJcefHelperFromJbrRuntimeLayout() {
        val runtimeRoot = Files.createTempDirectory("mulehang-jbr")
        val missingRuntimeRoot = Files.createTempDirectory("mulehang-missing-jbr")
        try {
            val helper = Files.createDirectories(runtimeRoot.resolve("bin"))
                .resolve("jcef_helper.exe")
            Files.writeString(helper, "helper")

            assertEquals(
                helper.toAbsolutePath().normalize(),
                locateMermaidJcefHelperPath(runtimeRoot),
            )
            assertEquals(null, locateMermaidJcefHelperPath(missingRuntimeRoot))
        } finally {
            deleteDirectory(runtimeRoot)
            deleteDirectory(missingRuntimeRoot)
        }
    }

    /** 图片、脚注与定义列表应提取为原生 Markdown 扩展块。 */
    @Test
    fun extractsImageFootnotesAndDefinitionListExtensions() {
        val document = parseAssistantMarkdownDocument(
            """
            这里引用了脚注[^first]和另一个脚注[^note]。

            ![示例图片](https://example.com/image.png "图片标题")

            Markdown
            : 一种轻量级标记语言

            [^first]: 第一条脚注。
            [^note]: 第二条脚注。
            """.trimIndent(),
        )

        val image = document.blocks.filterIsInstance<AssistantMarkdownBlock.Image>().single()
        assertEquals("示例图片", image.alt)
        assertEquals("https://example.com/image.png", image.url)
        assertEquals(listOf("first", "note"), document.footnotes.map(AssistantFootnote::id))
        assertTrue(document.blocks.any { it is AssistantMarkdownBlock.DefinitionList && it.term == "Markdown" })
        assertFalse(document.blocks.filterIsInstance<AssistantMarkdownBlock.Text>().joinToString("\n") { it.content }.contains("[^note]"))
    }

    /** HTML 颜色白名单保留文字；脚本不得进入渲染模型。 */
    @Test
    fun extractsSafeHtmlColorSpanWithoutScripts() {
        val document = parseAssistantMarkdownDocument("<span style=\"color: red\">红色文字</span><script>alert(1)</script>")

        val span = assertIs<AssistantMarkdownBlock.HtmlSpan>(document.blocks.single())
        assertEquals("红色文字", span.content)
        assertEquals("red", span.colorName)
    }

    /** 引用中的 details/summary 应提取为原生折叠块。 */
    @Test
    fun extractsQuotedHtmlDetailsAsCollapsibleBlock() {
        val document = parseAssistantMarkdownDocument(
            """
            > <details>
            > <summary>点击展开查看详情</summary>
            >
            > 折叠内容
            > </details>
            """.trimIndent(),
        )

        assertEquals("Details", document.blocks.single()::class.simpleName)
    }

    /** PlantUML 必须在本地生成 SVG，不能依赖浏览器端或网络引擎。 */
    @Test
    fun rendersPlantUmlToLocalSvg() {
        val svg = renderPlantUmlToSvg("@startuml\nAlice -> Bob: Hi\n@enduml", isDark = true)

        assertTrue(svg.contains("<svg"))
        assertFalse(svg.contains("Syntax Error"))
    }

    /** 自动主题只注入最小颜色配置，不再改写字体或 SVG 输出。 */
    @Test
    fun appliesMinimalPlantUmlThemeForBothAppearances() {
        val source = "@startuml\nAlice -> Bob: Hi\n@enduml"

        val dark = applyPlantUmlTheme(source, isDark = true)
        val light = applyPlantUmlTheme(source, isDark = false)

        assertTrue(dark.contains("backgroundColor transparent"))
        assertTrue(dark.contains("defaultFontColor #E7EAF0"))
        assertTrue(light.contains("defaultFontColor #1F2329"))
        assertFalse(dark.contains("activityDiagram {\n  BackgroundColor"))
        assertFalse(light.contains("activityDiagram {\n  BackgroundColor"))
        assertFalse(dark.contains("defaultFontName"))
    }

    /** 用户显式选择 PlantUML 主题时，应用不能再覆盖其配色。 */
    @Test
    fun preservesExplicitPlantUmlTheme() {
        val source = "@startuml\n!theme plain\nAlice -> Bob: Hi\n@enduml"

        assertEquals(source, applyPlantUmlTheme(source, isDark = true))
    }

    /** 为资源定位测试创建包含本地页面和 Mermaid 入口的最小目录。 */
    private fun createCompleteDiagramResources(root: Path): Path {
        val directory = Files.createDirectories(root.resolve("diagram"))
        Files.writeString(directory.resolve("mermaid-worker.html"), "<!doctype html>")
        Files.createDirectories(directory.resolve("mermaid"))
        Files.writeString(directory.resolve("mermaid/mermaid.min.js"), "window.mermaid = {};")
        return directory.toAbsolutePath().normalize()
    }

    /** 删除测试创建的临时目录，避免将本地资源定位验证残留在系统临时文件夹。 */
    private fun deleteDirectory(root: Path) {
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
