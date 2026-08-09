package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.agent.app.design.AppMarkdownLink
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM

/** 验证流式 Markdown 与图表的轻量渲染边界。 */
class AssistantMarkdownRenderPolicyTest {

    /** 链接应使用克制的应用强调色，而不是富文本库的默认纯蓝。 */
    @Test
    fun `should use muted app accent for markdown links`() {
        val style = assistantMarkdownLinkStyle()

        assertEquals(AppMarkdownLink, style.color)
        assertNotEquals(Color.Blue, style.color)
        assertEquals(TextDecoration.Underline, style.textDecoration)
    }

    /** 未完成的流式回复不能把不完整图表交给图表引擎。 */
    @Test
    fun `should defer diagram rendering until assistant response is complete`() {
        assertFalse(shouldRenderMarkdownDiagram(isStreaming = true))
        assertTrue(shouldRenderMarkdownDiagram(isStreaming = false))
    }

    /** 安全的 HTML 语义应转成 Markdown，而不是在 Compose Desktop 中原样打印标签。 */
    @Test
    fun `should normalize safe html markup before markdown parsing`() {
        val content = normalizeAssistantMarkdown("<div><b>重点</b><br>下一行</div>")

        assertFalse(content.contains("<div"))
        assertFalse(content.contains("<br"))
        assertTrue(content.contains("**重点**"))
        assertTrue(content.contains("下一行"))
    }

    /** 完整的 PlantUML 围栏应从普通 Markdown 中抽出，供原生图表组件单独绘制。 */
    @Test
    fun `should extract complete plantuml fences as diagram blocks`() {
        val blocks = splitAssistantMarkdownBlocks(
            "说明\n```plantuml\n@startuml\nAlice -> Bob: Hi\n@enduml\n```\n结尾",
        )

        assertEquals(3, blocks.size)
        assertIs<AssistantMarkdownBlock.Text>(blocks[0])
        assertIs<AssistantMarkdownBlock.PlantUml>(blocks[1])
        assertEquals("@startuml\nAlice -> Bob: Hi\n@enduml", (blocks[1] as AssistantMarkdownBlock.PlantUml).source)
        assertIs<AssistantMarkdownBlock.Text>(blocks[2])
    }

    /** 普通 fenced code 应保留语言标记，交由原生高亮组件渲染。 */
    @Test
    fun `should extract fenced code language and source`() {
        val blocks = splitAssistantMarkdownBlocks("```python\ndef greet():\n    return \"hi\"\n```")

        val code = assertIs<AssistantMarkdownBlock.Code>(blocks.single())
        assertEquals("python", code.language)
        assertEquals("def greet():\n    return \"hi\"", code.source)
    }

    /** Mermaid 在流式阶段仅作为代码显示，围栏闭合后仍保持相同的代码块模型。 */
    @Test
    fun `should keep streaming mermaid fences as code blocks`() {
        val streaming = parseAssistantMarkdownStreamingDocument("```mermaid\ngraph TD\nA --> B")
        val completed = parseAssistantMarkdownDocument("```mermaid\ngraph TD\nA --> B\n```")

        assertEquals(
            assertIs<AssistantMarkdownBlock.Code>(completed.blocks.single()).copy(source = "graph TD\nA --> B"),
            assertIs<AssistantMarkdownBlock.Code>(streaming.blocks.single()),
        )
    }

    /** Python 关键字与字符串必须取得不同于普通文本的高亮颜色。 */
    @Test
    fun `should highlight python keywords and strings`() {
        val highlighted = highlightCode("def greet(): return \"hi\"", language = "python")

        assertTrue(highlighted.spanStyles.any { it.item.color == CodeKeywordColor })
        assertTrue(highlighted.spanStyles.any { it.item.color == CodeStringColor })
    }

    /** 图片、脚注与定义列表应脱离富文本库的缺失语法，交给原生组件分别呈现。 */
    @Test
    fun `should extract image footnotes and definition list extensions`() {
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

        val image = assertIs<AssistantMarkdownBlock.Image>(document.blocks.filterIsInstance<AssistantMarkdownBlock.Image>().single())
        assertEquals("示例图片", image.alt)
        assertEquals("https://example.com/image.png", image.url)
        assertEquals(listOf("first", "note"), document.footnotes.map(AssistantFootnote::id))
        assertTrue(document.blocks.any { it is AssistantMarkdownBlock.DefinitionList && it.term == "Markdown" })
        assertFalse(document.blocks.filterIsInstance<AssistantMarkdownBlock.Text>().joinToString("\n") { it.content }.contains("[^note]"))
    }

    /** 受限 HTML 的颜色语义应保留；脚本与任意 CSS 不属于原生安全子集。 */
    @Test
    fun `should extract safe html color span without allowing scripts`() {
        val document = parseAssistantMarkdownDocument("<span style=\"color: red\">红色文字</span><script>alert(1)</script>")

        val span = assertIs<AssistantMarkdownBlock.HtmlSpan>(document.blocks.single())
        assertEquals("红色文字", span.content)
        assertEquals("red", span.colorName)
    }

    /** 单个 `$...$` 公式应离开 CommonMark 文本流，交由原生 LaTeX 组件绘制。 */
    @Test
    fun `should extract inline latex formula`() {
        val document = parseAssistantMarkdownDocument("质能方程 ${'$'}E = mc^2${'$'}")

        assertEquals("InlineMath", document.blocks[1]::class.simpleName)
    }

    /** `$$...$$` 块级公式应成为单独的原生公式块。 */
    @Test
    fun `should extract display latex formula`() {
        val document = parseAssistantMarkdownDocument("${'$'}${'$'}\\n\\int_0^\\infty e^{-x^2} \\, dx\\n${'$'}${'$'}")

        assertEquals("DisplayMath", document.blocks.single()::class.simpleName)
    }

    /** 引用中的 `details/summary` 也必须被提取为可点击的原生折叠块。 */
    @Test
    fun `should extract quoted html details as collapsible block`() {
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

    /** PlantUML 源码应在本地转换为 SVG，缩放时保持矢量清晰。 */
    @Test
    fun `should render plantuml to local svg`() {
        val svg = renderPlantUmlToSvg("@startuml\nAlice -> Bob: Hi\n@enduml")

        assertTrue(svg.startsWith("<svg"))
    }

    /** PlantUML 默认输出应注入深色应用主题，而不是产生纯白画布。 */
    @Test
    fun `should apply dark theme to plantuml source`() {
        val themed = applyPlantUmlDarkTheme("@startuml\nAlice -> Bob: Hi\n@enduml")

        assertTrue(themed.contains("backgroundColor transparent"))
        assertTrue(themed.contains("defaultFontName Microsoft YaHei"))
        assertTrue(themed.contains("defaultFontColor #E7EAF0"))
        assertTrue(themed.contains("ActorBackgroundColor #2B2D30"))
        assertTrue(themed.contains("ActorFontColor #E7EAF0"))
        assertTrue(themed.contains("ClassBackgroundColor #2B2D30"))
        assertTrue(themed.contains("ClassFontColor #E7EAF0"))
        assertTrue(themed.contains("ComponentBackgroundColor #2B2D30"))
        assertTrue(themed.contains("ComponentFontColor #E7EAF0"))
        assertTrue(themed.contains("DatabaseBackgroundColor #2B2D30"))
        assertTrue(themed.contains("DatabaseFontColor #E7EAF0"))
        assertTrue(themed.contains("PackageBackgroundColor transparent"))
        assertTrue(themed.contains("PackageFontColor #E7EAF0"))
        assertTrue(themed.contains("StateBackgroundColor #2B2D30"))
        assertTrue(themed.contains("StateFontColor #E7EAF0"))
        assertTrue(themed.contains("ActivityBackgroundColor #2B2D30"))
        assertTrue(themed.contains("ActivityFontColor #E7EAF0"))
        assertTrue(themed.contains("ActivityDiamondBackgroundColor #2B2D30"))
        assertTrue(themed.contains("ActivityDiamondFontColor #E7EAF0"))
        assertTrue(themed.contains("SequenceGroupBackgroundColor #2B2D30"))
        assertTrue(themed.contains("SequenceGroupFontColor #E7EAF0"))
        assertTrue(themed.contains("SequenceGroupHeaderFontColor #E7EAF0"))
        assertTrue(themed.contains("skinparam usecase"))
        assertTrue(themed.contains("BackgroundColor #2B2D30"))
        assertTrue(themed.contains("FontColor #E7EAF0"))
    }

    /** Chen 与思维导图的起始指令也必须注入统一主题。 */
    @Test
    fun `should apply dark theme to every plantuml start directive`() {
        val chen = applyPlantUmlDarkTheme("@startchen\nentity CUSTOMER\n@endchen")
        val mindMap = applyPlantUmlDarkTheme("@startmindmap\n* 在线商店\n@endmindmap")

        assertTrue(chen.contains("defaultFontColor #E7EAF0"))
        assertTrue(mindMap.contains("defaultFontColor #E7EAF0"))
    }

    /** 未被 skinparam 覆盖的默认浅色图元与黑色连线应在 SVG 阶段归一化。 */
    @Test
    fun `should normalize unthemed svg shape colors`() {
        val svg = normalizePlantUmlSvgColors("<rect fill=\"#FEFECE\" stroke=\"#181818\"/>")

        assertTrue(svg.contains("fill=\"#2B2D30\""))
        assertTrue(svg.contains("stroke=\"#9BA9C2\""))
    }

    /** 内置 C4 标准库应能解析部署图宏，不能退化为 PlantUML 错误图。 */
    @Test
    fun `should render C4 deployment standard library`() {
        val svg = renderPlantUmlToSvg(
            """
            @startuml
            !include <C4/C4_Deployment>
            Deployment_Node(device, "用户设备", "Laptop") {
                Container(browser, "浏览器", "Chrome")
            }
            @enduml
            """.trimIndent(),
        )

        assertFalse(svg.contains("[From string"))
        assertFalse(svg.contains("Syntax Error"))
    }

    /** C4 部署图的嵌套节点、数据库与关系声明必须能完整渲染。 */
    @Test
    fun `should render nested C4 deployment diagram`() {
        val svg = renderPlantUmlToSvg(
            """
            @startuml
            !include <C4/C4_Deployment>
            title C4：Deployment Diagram
            LAYOUT_WITH_LEGEND()

            Deployment_Node(userDevice, "用户设备", "Laptop / Mobile") {
                Container(browser, "Web Browser", "Chrome / Safari", "访问在线商店")
            }

            Deployment_Node(cloud, "云环境", "Public Cloud") {
                Deployment_Node(cluster, "Kubernetes Cluster", "Kubernetes") {
                    Deployment_Node(ingress, "Ingress", "Nginx") {
                        Container(web, "Web Frontend", "React", "前端应用")
                    }

                    Deployment_Node(appPod, "Application Pod", "Docker") {
                        Container(api, "Order API", "Spring Boot", "订单服务")
                    }

                    Deployment_Node(dataPod, "Data Pod", "Managed Database") {
                        ContainerDb(db, "Order Database", "PostgreSQL", "订单数据")
                    }
                }
            }

            Rel(browser, web, "访问", "HTTPS")
            Rel(web, api, "调用", "HTTPS")
            Rel(api, db, "读写", "JDBC")
            @enduml
            """.trimIndent(),
        )

        assertFalse(svg.contains("[From string"))
        assertFalse(svg.contains("Syntax Error"))
    }

    /** 中文图表必须将标签转换为 SVG 路径，避免绘制器遗漏文本节点。 */
    @Test
    fun `should outline CJK uml labels into svg paths`() {
        val svg = renderPlantUmlToSvg("@startuml\nAlice -> Bob: 审批请求\n@enduml")

        assertTrue(svg.contains("<path"))
        assertFalse(svg.contains("<text"))
    }

    /** 活动图节点必须使用深色表面，避免默认白底与应用浅色文字失去对比。 */
    @Test
    fun `should render activity diagram with visible dark theme labels`() {
        val svg = renderPlantUmlToSvg("@startuml\nstart\n:打开购物网站;\nif (已登录?) then (是)\n:填写收货地址;\nendif\nstop\n@enduml")

        assertTrue(svg.contains("#2B2D30"))
        assertTrue(svg.contains("#E7EAF0"))
        assertTrue(svg.contains("<path"))
        assertTrue(svg.contains("<path fill=\"#E7EAF0\""))
        assertFalse(svg.contains("<text"))
    }

    /** 转为轮廓后的中文标签必须可由 Skia SVGDOM 实际绘制。 */
    @Test
    fun `should draw outlined CJK svg labels through skia`() {
        val outlinedSvg = outlineSvgTextAsPaths("""
            <svg xmlns="http://www.w3.org/2000/svg" width="160" height="64">
              <text x="8" y="40" fill="#181818" font-family="Microsoft YaHei UI" font-size="28">打开购物网站</text>
            </svg>
        """.trimIndent())
        assertFalse(outlinedSvg.contains("<text"))
        assertTrue(outlinedSvg.contains("fill=\"#E7EAF0\""))
        Surface.makeRasterN32Premul(160, 64).use { surface ->
            SVGDOM(Data.makeFromBytes(outlinedSvg.encodeToByteArray())).use { document ->
            document.setContainerSize(160f, 64f)
            document.render(surface.canvas)
                surface.makeImageSnapshot().use { snapshot ->
                val png = snapshot.encodeToData()!!.bytes
                val image = ImageIO.read(ByteArrayInputStream(png))
                assertTrue(
                    (0 until image.height).any { y ->
                        (0 until image.width).any { x -> (image.getRGB(x, y) ushr 24) != 0 }
                    },
                )
            }
            }
        }
    }

    /** 图像尺寸应从 SVG 的 viewBox 按真实比例参与适配，确保大图完整显示。 */
    @Test
    fun `should fit plantuml dimensions proportionally`() {
        val intrinsicSize = svgIntrinsicSize("<svg width=\"2400px\" height=\"1600px\" viewBox=\"0 0 2400 1600\">")

        assertEquals(PlantUmlIntrinsicSize(width = 2400f, height = 1600f), intrinsicSize)
        assertEquals(0.2f, plantUmlFitScale(intrinsicSize, viewportWidth = 480f, viewportHeight = 460f))
    }

    /** 适配模式允许缩小大型图，而原始比例与缩放上限仍保持受控。 */
    @Test
    fun `should constrain plantuml viewer zoom around its fit scale`() {
        assertEquals(0.1f, plantUmlZoomedScale(scale = 0.2f, multiplier = 0.1f, minimumScale = 0.1f))
        assertEquals(3f, plantUmlZoomedScale(scale = 2.9f, multiplier = 2f, minimumScale = 0.1f))
    }
}
