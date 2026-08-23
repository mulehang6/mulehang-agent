package com.agent.app.chat.component

import java.awt.Font
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证 SVG 标签会在进入 Skia SVGDOM 前转换为实际可绘制的矢量路径。 */
class SvgTextOutlinerTest {

    /** 英文和中文标签都应变为路径，并在 SVGDOM 的离屏画布中实际显示。 */
    @Test
    fun outlinesMultilingualTextIntoVisibleVectorPaths() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="120" viewBox="0 0 240 120">
              <rect width="240" height="120" fill="#31343C"/>
              <text x="24" y="52" fill="#F4F7FC" font-family="sans-serif" font-size="24">Hello 中文</text>
            </svg>
        """.trimIndent()

        val outlined = outlineDiagramSvgText(svg)

        assertFalse(outlined.contains("<text"))
        assertTrue(outlined.contains("<path"))
        assertTrue(outlined.contains("fill=\"#F4F7FC\""))
        assertTrue(hasBrightPixels(outlined))
    }

    /** Mermaid 默认字体栈缺少中文时必须落到与 PlantUML 相同的 SansSerif。 */
    @Test
    fun resolvesMermaidDefaultFontStackToSansSerifForChineseLabels() {
        val text = "登录 Login"
        val font = DiagramSvgFontResolver.resolve(
            cssFontFamilies = "\"trebuchet ms\", verdana, arial, sans-serif",
            text = text,
            fontStyle = Font.PLAIN,
            fontSize = 24f,
            letterSpacing = 0f,
        )
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="120" viewBox="0 0 240 120">
              <rect width="240" height="120" fill="#31343C"/>
              <text x="24" y="52" fill="#F4F7FC" font-family="'trebuchet ms', verdana, arial, sans-serif" font-size="24">$text</text>
            </svg>
        """.trimIndent()

        val outlined = outlineDiagramSvgText(svg)

        assertEquals(APPLICATION_SANS_SERIF_FONT_FAMILY, font.family)
        assertEquals(-1, font.canDisplayUpTo(text))
        assertFalse(outlined.contains("<text"))
        assertTrue(hasBrightPixels(outlined))
    }

    /** 显式但缺少中文的字体不能产生方块，必须安全回退到 SansSerif。 */
    @Test
    fun fallsBackToSansSerifWhenExplicitArialCannotDisplayChinese() {
        val text = "中文"

        val font = DiagramSvgFontResolver.resolve(
            cssFontFamilies = "Arial",
            text = text,
            fontStyle = Font.PLAIN,
            fontSize = 24f,
            letterSpacing = 0f,
        )

        assertEquals(APPLICATION_SANS_SERIF_FONT_FAMILY, font.family)
        assertEquals(-1, font.canDisplayUpTo(text))
    }

    /** 可完整显示文字的显式 SansSerif 应保持优先，不被额外替换。 */
    @Test
    fun retainsExplicitSansSerifForChineseLabels() {
        val text = "中文 English"

        val font = DiagramSvgFontResolver.resolve(
            cssFontFamilies = APPLICATION_SANS_SERIF_FONT_FAMILY,
            text = text,
            fontStyle = Font.BOLD,
            fontSize = 24f,
            letterSpacing = 0f,
        )

        assertEquals(APPLICATION_SANS_SERIF_FONT_FAMILY, font.family)
        assertEquals(-1, font.canDisplayUpTo(text))
    }

    /** Mermaid 常见的多行 tspan 应保留为多段路径。 */
    @Test
    fun outlinesMultilineTspanRuns() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="120" viewBox="0 0 240 120">
              <text x="120" y="36" fill="#F4F7FC" font-size="16" text-anchor="middle">
                <tspan x="120" dy="0">第一行</tspan>
                <tspan x="120" dy="1.2em">second line</tspan>
              </text>
            </svg>
        """.trimIndent()

        val outlined = outlineDiagramSvgText(svg)

        assertEquals(2, "<path".toRegex().findAll(outlined).count())
    }

    /** HTML 标签不能在纯 SVGDOM 路径中可靠显示，必须明确回退。 */
    @Test
    fun rejectsHtmlForeignObjectInsteadOfDroppingItsLabel() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg">
              <foreignObject width="100" height="20"><div>label</div></foreignObject>
            </svg>
        """.trimIndent()

        assertFailsWith<SvgTextOutliningException> { outlineDiagramSvgText(svg) }
    }

    /** 在离屏 SVGDOM 上寻找白色字形像素，证明测试没有只检查字符串替换。 */
    private fun hasBrightPixels(svg: String): Boolean {
        SVGDOM(Data.makeFromBytes(svg.encodeToByteArray())).use { document ->
            Surface.makeRasterN32Premul(240, 120).use { surface ->
                document.setContainerSize(240f, 120f)
                document.render(surface.canvas)
                Bitmap().use { bitmap ->
                    bitmap.allocN32Pixels(240, 120, false)
                    surface.readPixels(bitmap, 0, 0)
                    return (0 until 120).any { y ->
                        (0 until 240).any { x -> bitmap.getColor(x, y).isBright() }
                    }
                }
            }
        }
    }
}

/** 判断 ARGB 像素是否属于测试 SVG 的明亮文字轮廓。 */
private fun Int.isBright(): Boolean =
    ((this ushr 16) and 0xFF) > 200 &&
        ((this ushr 8) and 0xFF) > 200 &&
        (this and 0xFF) > 200
