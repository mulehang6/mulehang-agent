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

    /** PlantUML 源码应在本地转换为 SVG，不依赖浏览器或网络服务。 */
    @Test
    fun `should render plantuml to local svg`() {
        val svg = renderPlantUmlToSvg("@startuml\nAlice -> Bob: Hi\n@enduml")

        assertTrue(svg.startsWith("<svg"))
    }
}
