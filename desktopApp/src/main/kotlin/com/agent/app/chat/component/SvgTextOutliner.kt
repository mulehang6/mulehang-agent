package com.agent.app.chat.component

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.PathIterator
import java.io.StringReader
import java.io.StringWriter
import java.math.BigDecimal
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * 将 Skia SVGDOM 无法绘制的 SVG 文字转为轮廓路径。
 *
 * 输出仍是普通 SVG 路径，因此缩放时不会转为位图；不支持的 HTML 或路径文字会显式失败，避免显示缺字图表。
 */
internal fun outlineDiagramSvgText(svg: String): String = SvgTextOutliner.outline(svg)

/** 承载可恢复的 SVG 文本轮廓化失败。 */
internal class SvgTextOutliningException(
    detail: String,
    cause: Throwable? = null,
) : IllegalArgumentException(detail, cause)

/** 解析 SVG 文本节点，并使用 JDK 字体轮廓替代它们。 */
private object SvgTextOutliner {
    /** 将输入 SVG 中的所有 `<text>` 元素转换为一个或多个 `<path>`。 */
    fun outline(svg: String): String = try {
        val document = parseSvg(svg)
        rejectUnsupportedTextElements(document)
        textElements(document).forEach(::outlineTextElement)
        serializeSvg(document)
    } catch (error: SvgTextOutliningException) {
        throw error
    } catch (error: Throwable) {
        throw SvgTextOutliningException("SVG 文本无法转换为矢量路径。", error)
    }

    /** 使用禁止外部实体的 XML 配置解析本地 SVG。 */
    private fun parseSvg(svg: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature(DISALLOW_DOCTYPE_DECLARATION_FEATURE, true)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    }.newDocumentBuilder().parse(InputSource(StringReader(svg)))

    /** 明确拒绝无法在纯矢量管线中保持语义的标签节点。 */
    private fun rejectUnsupportedTextElements(document: org.w3c.dom.Document) {
        if (document.getElementsByTagNameNS(SVG_NAMESPACE, "foreignObject").length > 0) {
            throw SvgTextOutliningException("SVG 包含 HTML 标签，无法作为纯矢量图表显示。")
        }
        if (document.getElementsByTagNameNS(SVG_NAMESPACE, "textPath").length > 0) {
            throw SvgTextOutliningException("SVG 包含沿路径排版的文字，无法安全转换为纯矢量标签。")
        }
    }

    /** 将动态 NodeList 拷贝为稳定列表，防止替换节点时漏掉后续文字。 */
    private fun textElements(document: org.w3c.dom.Document): List<Element> = buildList {
        val nodes = document.getElementsByTagNameNS(SVG_NAMESPACE, "text")
        for (index in 0 until nodes.length) {
            (nodes.item(index) as? Element)?.let(::add)
        }
    }

    /** 将一个文字元素替换为继承原变换和绘制属性的路径组。 */
    private fun outlineTextElement(text: Element) {
        if (text.hasAttribute("rotate")) {
            throw SvgTextOutliningException("SVG 包含逐字旋转文字，无法安全转换为纯矢量标签。")
        }
        val document = text.ownerDocument
        val group = document.createElementNS(SVG_NAMESPACE, "g")
        copyNonTextLayoutAttributes(text, group)

        val cursor = TextCursor(
            x = readCoordinate(text, "x", 0f),
            y = readCoordinate(text, "y", 0f),
        )
        cursor.x += readLength(text, "dx", inheritedTextStyle(text).fontSize)
        cursor.y += readLength(text, "dy", inheritedTextStyle(text).fontSize)
        appendRuns(
            parent = text,
            inheritedStyle = inheritedTextStyle(text),
            cursor = cursor,
            group = group,
        )
        text.parentNode.replaceChild(group, text)
    }

    /** 递归展开直接文本和 `<tspan>`，使每个文本 run 都拥有明确的路径。 */
    private fun appendRuns(
        parent: Element,
        inheritedStyle: SvgTextStyle,
        cursor: TextCursor,
        group: Element,
    ) {
        val children = parent.childNodes
        for (index in 0 until children.length) {
            when (val child = children.item(index)) {
                is Element -> when (child.localName) {
                    "tspan" -> appendTspan(child, inheritedStyle, cursor, group)
                    else -> throw SvgTextOutliningException("SVG 包含不支持的文字子元素 <${child.localName}>。")
                }

                else -> if (child.nodeType == Node.TEXT_NODE && child.nodeValue.isRenderableSvgText()) {
                    appendPathForText(
                        text = child.nodeValue,
                        style = inheritedStyle,
                        cursor = cursor,
                        group = group,
                        textLength = readLength(parent, "textLength", inheritedStyle.fontSize).takeIf { it > 0f },
                    )
                }
            }
        }
    }

    /** 应用 tspan 局部样式与坐标，再继续解析其文字内容。 */
    private fun appendTspan(
        tspan: Element,
        inheritedStyle: SvgTextStyle,
        cursor: TextCursor,
        group: Element,
    ) {
        if (tspan.hasAttribute("rotate")) {
            throw SvgTextOutliningException("SVG 包含逐字旋转文字，无法安全转换为纯矢量标签。")
        }
        val style = inheritedStyle.withOverrides(tspan)
        if (tspan.hasAttribute("x")) cursor.x = readCoordinate(tspan, "x", cursor.x)
        if (tspan.hasAttribute("y")) cursor.y = readCoordinate(tspan, "y", cursor.y)
        cursor.x += readLength(tspan, "dx", style.fontSize)
        cursor.y += readLength(tspan, "dy", style.fontSize)
        appendRuns(tspan, style, cursor, group)
    }

    /** 根据当前字体、锚点和目标长度生成并附加一个 SVG 路径。 */
    private fun appendPathForText(
        text: String,
        style: SvgTextStyle,
        cursor: TextCursor,
        group: Element,
        textLength: Float?,
    ) {
        val font = style.toAwtFont(text)
        val glyphVector = font.createGlyphVector(FONT_RENDER_CONTEXT, text)
        val advance = glyphVector.getGlyphPosition(glyphVector.numGlyphs).x.toFloat()
        val anchoredX = cursor.x - style.textAnchor.offsetFor(advance)
        val baselineY = cursor.y + style.baselineOffset(font)
        var outline = glyphVector.getOutline(anchoredX, baselineY)
        if (textLength != null && advance > 0f) {
            val factor = textLength / advance
            outline = AffineTransform.getTranslateInstance(cursor.x.toDouble(), 0.0)
                .apply { scale(factor.toDouble(), 1.0) }
                .apply { translate(-cursor.x.toDouble(), 0.0) }
                .createTransformedShape(outline)
        }
        group.appendChild(group.ownerDocument.createElementNS(SVG_NAMESPACE, "path").apply {
            setAttribute("d", outline.toSvgPathData())
            style.applyPaintAttributes(this)
        })
        cursor.x = anchoredX + (textLength ?: advance)
    }

    /** 复制仍然适用于路径组的变换、裁剪、链接和视觉属性。 */
    private fun copyNonTextLayoutAttributes(source: Element, target: Element) {
        for (index in 0 until source.attributes.length) {
            val attribute = source.attributes.item(index)
            if (attribute.nodeName.lowercase() !in TEXT_LAYOUT_ATTRIBUTE_NAMES) {
                target.setAttribute(attribute.nodeName, attribute.nodeValue)
            }
        }
    }

    /** 从 SVG 树的根到当前元素解析可继承的文字样式。 */
    private fun inheritedTextStyle(element: Element): SvgTextStyle {
        val ancestors = generateSequence(element.parentNode) { it.parentNode }
            .filterIsInstance<Element>()
            .toList()
            .asReversed()
        return ancestors.fold(DEFAULT_TEXT_STYLE) { style, ancestor -> style.withOverrides(ancestor) }
            .withOverrides(element)
    }

    /** 解析一个可选数值坐标，SVG 列表坐标仅使用第一个值。 */
    private fun readCoordinate(element: Element, name: String, default: Float): Float =
        readLength(element, name, DEFAULT_TEXT_STYLE.fontSize).takeIf { it.isFinite() } ?: default

    /** 解析像素、em、pt 或裸数字长度，未知格式视为零。 */
    private fun readLength(element: Element, name: String, fontSize: Float): Float =
        element.propertyValue(name)?.firstSvgLength(fontSize) ?: 0f

    /** 使用安全输出配置将 DOM 序列化回独立 SVG。 */
    private fun serializeSvg(document: org.w3c.dom.Document): String {
        val writer = StringWriter()
        TransformerFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
        }.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }
}

/** 已解析但尚未转换为路径的 SVG 文本样式。 */
private data class SvgTextStyle(
    val fontFamily: String = APPLICATION_SANS_SERIF_FONT_FAMILY,
    val fontSize: Float = 16f,
    val fontWeight: Int = Font.PLAIN,
    val italic: Boolean = false,
    val textAnchor: SvgTextAnchor = SvgTextAnchor.START,
    val baseline: String? = null,
    val letterSpacing: Float = 0f,
    val fill: String? = null,
    val fillOpacity: String? = null,
    val opacity: String? = null,
    val stroke: String? = null,
    val strokeOpacity: String? = null,
    val strokeWidth: String? = null,
    val paintOrder: String? = null,
) {
    /** 叠加当前元素中显式声明的文字和绘制属性。 */
    fun withOverrides(element: Element): SvgTextStyle = copy(
        fontFamily = element.propertyValue("font-family") ?: fontFamily,
        fontSize = element.propertyValue("font-size")?.firstSvgLength(fontSize)?.takeIf { it > 0f } ?: fontSize,
        fontWeight = element.propertyValue("font-weight")?.toAwtFontWeight() ?: fontWeight,
        italic = element.propertyValue("font-style")?.equals("italic", ignoreCase = true) ?: italic,
        textAnchor = element.propertyValue("text-anchor")?.toSvgTextAnchor() ?: textAnchor,
        baseline = element.propertyValue("dominant-baseline") ?: element.propertyValue("alignment-baseline") ?: baseline,
        letterSpacing = element.propertyValue("letter-spacing")?.firstSvgLength(fontSize) ?: letterSpacing,
        fill = element.propertyValue("fill") ?: fill,
        fillOpacity = element.propertyValue("fill-opacity") ?: fillOpacity,
        opacity = element.propertyValue("opacity") ?: opacity,
        stroke = element.propertyValue("stroke") ?: stroke,
        strokeOpacity = element.propertyValue("stroke-opacity") ?: strokeOpacity,
        strokeWidth = element.propertyValue("stroke-width") ?: strokeWidth,
        paintOrder = element.propertyValue("paint-order") ?: paintOrder,
    )

    /** 创建可完整显示当前文字、且具备字距属性的 AWT 字体。 */
    fun toAwtFont(text: String): Font {
        val style = fontWeight or if (italic) Font.ITALIC else Font.PLAIN
        return DiagramSvgFontResolver.resolve(
            cssFontFamilies = fontFamily,
            text = text,
            fontStyle = style,
            fontSize = fontSize,
            letterSpacing = letterSpacing,
        )
    }

    /** 将 SVG 的基线语义换算为 AWT 的字形基线。 */
    fun baselineOffset(font: Font): Float {
        if (baseline?.lowercase() !in setOf("middle", "central")) return 0f
        val metrics = font.getLineMetrics("Ag", FONT_RENDER_CONTEXT)
        return (metrics.ascent - metrics.descent) / 2f
    }

    /** 把已解析的绘制属性写入路径，避免依赖 SVGDOM 的 CSS 支持。 */
    fun applyPaintAttributes(path: Element) {
        fill?.let { path.setAttribute("fill", it) }
        fillOpacity?.let { path.setAttribute("fill-opacity", it) }
        opacity?.let { path.setAttribute("opacity", it) }
        stroke?.let { path.setAttribute("stroke", it) }
        strokeOpacity?.let { path.setAttribute("stroke-opacity", it) }
        strokeWidth?.let { path.setAttribute("stroke-width", it) }
        paintOrder?.let { path.setAttribute("paint-order", it) }
    }
}

/** SVG 文本锚点到 AWT 字形起点的偏移方式。 */
private enum class SvgTextAnchor {
    START,
    MIDDLE,
    END,
    ;

    /** 返回给定字形总 advance 的起点偏移。 */
    fun offsetFor(advance: Float): Float = when (this) {
        START -> 0f
        MIDDLE -> advance / 2f
        END -> advance
    }
}

/** SVG 文字运行的可变笔位置。 */
private data class TextCursor(
    var x: Float,
    var y: Float,
)

/** 将 SVG 属性或内联 style 中的属性读取为同一种文本样式值。 */
private fun Element.propertyValue(name: String): String? = getAttribute(name)
    .takeIf(String::isNotBlank)
    ?: getAttribute("style")
        .split(';')
        .mapNotNull { declaration ->
            declaration.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
        }
        .firstOrNull { it.first.equals(name, ignoreCase = true) }
        ?.second

/** 从 SVG 数字或长度列表中读取第一个数值。 */
private fun String.firstSvgLength(fontSize: Float): Float? {
    val value = trim().substringBefore(',').substringBefore(' ').trim()
    val number = LENGTH_NUMBER.find(value)?.value?.toFloatOrNull() ?: return null
    return when {
        value.endsWith("em", ignoreCase = true) -> number * fontSize
        value.endsWith("ex", ignoreCase = true) -> number * fontSize / 2f
        value.endsWith("pt", ignoreCase = true) -> number * 96f / 72f
        else -> number
    }
}

/** 将 CSS 字重映射为 AWT 的普通或粗体字重。 */
private fun String.toAwtFontWeight(): Int = when (trim().lowercase()) {
    "bold", "bolder", "600", "700", "800", "900" -> Font.BOLD
    else -> Font.PLAIN
}

/** 将 CSS 文本锚点映射为本地枚举。 */
private fun String.toSvgTextAnchor(): SvgTextAnchor? = when (trim().lowercase()) {
    "start" -> SvgTextAnchor.START
    "middle" -> SvgTextAnchor.MIDDLE
    "end" -> SvgTextAnchor.END
    else -> null
}

/** 忽略 SVG 缩进产生的空白文本节点，同时保留真实空格。 */
private fun String?.isRenderableSvgText(): Boolean = !isNullOrEmpty() && any { !it.isWhitespace() }

/** 将 AWT 路径迭代器转为 SVG `d` 属性。 */
private fun java.awt.Shape.toSvgPathData(): String {
    val coordinates = DoubleArray(6)
    val builder = StringBuilder()
    val iterator = getPathIterator(null)
    while (!iterator.isDone) {
        when (iterator.currentSegment(coordinates)) {
            PathIterator.SEG_MOVETO -> builder.append("M ${coordinates[0].svgNumber()} ${coordinates[1].svgNumber()}")
            PathIterator.SEG_LINETO -> builder.append(" L ${coordinates[0].svgNumber()} ${coordinates[1].svgNumber()}")
            PathIterator.SEG_QUADTO -> builder.append(
                " Q ${coordinates[0].svgNumber()} ${coordinates[1].svgNumber()} ${coordinates[2].svgNumber()} ${coordinates[3].svgNumber()}",
            )

            PathIterator.SEG_CUBICTO -> builder.append(
                " C ${coordinates[0].svgNumber()} ${coordinates[1].svgNumber()} ${coordinates[2].svgNumber()} ${coordinates[3].svgNumber()} ${coordinates[4].svgNumber()} ${coordinates[5].svgNumber()}",
            )

            PathIterator.SEG_CLOSE -> builder.append(" Z")
        }
        iterator.next()
    }
    return builder.toString()
}

/** 以不受当前区域设置影响的十进制形式输出 SVG 数字。 */
private fun Double.svgNumber(): String {
    require(isFinite()) { "SVG 路径包含非有限坐标。" }
    return BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
}

/** SVG 文本属性不会传递给替代路径组。 */
private val TEXT_LAYOUT_ATTRIBUTE_NAMES = setOf(
    "alignment-baseline",
    "dominant-baseline",
    "dx",
    "dy",
    "font-family",
    "font-size",
    "font-style",
    "font-weight",
    "letter-spacing",
    "lengthadjust",
    "rotate",
    "text-anchor",
    "textlength",
    "word-spacing",
    "x",
    "y",
)

/** SVG 规范的命名空间。 */
private const val SVG_NAMESPACE = "http://www.w3.org/2000/svg"

/** JAXP 用来禁用文档类型声明的标准实现特性名。 */
@Suppress("HttpUrlsUsage")
private const val DISALLOW_DOCTYPE_DECLARATION_FEATURE = "http://apache.org/xml/features/disallow-doctype-decl"

/** AWT 字形轮廓使用的稳定抗锯齿和分数度量上下文。 */
private val FONT_RENDER_CONTEXT = FontRenderContext(AffineTransform(), true, true)

/** SVG 文本的无样式默认值。 */
private val DEFAULT_TEXT_STYLE = SvgTextStyle()

/** 长度开头的数字部分。 */
private val LENGTH_NUMBER = Regex("[-+]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)")
