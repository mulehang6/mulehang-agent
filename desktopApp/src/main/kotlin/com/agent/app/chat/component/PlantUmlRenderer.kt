package com.agent.app.chat.component

import java.io.ByteArrayOutputStream
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.PathIterator
import java.util.Locale
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader
import kotlin.math.roundToInt

/**
 * 在当前 JVM 内将 PlantUML 文本渲染为 SVG，不启动浏览器、不访问网络。
 */
internal fun renderPlantUmlToSvg(source: String): String {
    val output = ByteArrayOutputStream()
    SourceStringReader(applyPlantUmlDarkTheme(source)).outputImage(
        output,
        FileFormatOption(FileFormat.SVG),
    )
    return outlineSvgTextAsPaths(normalizePlantUmlSvgColors(output.toString(Charsets.UTF_8)))
}

/** 将 SVG 文字转为字体轮廓，避开 Skia SVGDOM 在 Windows 下无法绘制中文文本的问题。 */
internal fun outlineSvgTextAsPaths(svg: String): String = SVG_TEXT_ELEMENT.replace(svg) { match ->
    val attributes = svgAttributes(match.groupValues[1])
    val text = decodeSvgText(match.groupValues[2])
    runCatching {
        val x = attributes["x"]?.toFloatOrNull() ?: return@runCatching null
        val y = attributes["y"]?.toFloatOrNull() ?: return@runCatching null
        val path = svgPathData(
            svgFont(attributes).createGlyphVector(SVG_FONT_RENDER_CONTEXT, text).getOutline(x, y),
        )
        if (path.isBlank()) return@runCatching null
        "<path ${svgPathStyle(attributes)}d=\"$path\"/>"
    }.getOrNull() ?: match.value
}

/** 归一化 PlantUML 未覆盖图元的 SVG 配色，使其融入应用深色主题。 */
internal fun normalizePlantUmlSvgColors(svg: String): String = SVG_LIGHT_FILL.replace(svg) { match ->
    val color = match.groupValues[2]
    if (isLightSvgColor(color)) "${match.groupValues[1]}#2B2D30${match.groupValues[3]}" else match.value
}.let { normalized ->
    SVG_DARK_STROKE.replace(normalized) { match ->
        val color = match.groupValues[2]
        if (isDarkSvgColor(color)) "${match.groupValues[1]}#9BA9C2${match.groupValues[3]}" else match.value
    }
}

/**
 * 将应用的深色和字体覆盖放在用户主题之后，避免 `!theme plain` 重置中文字体造成缺字方块。
 */
internal fun applyPlantUmlDarkTheme(source: String): String {
    val endMatch = PLANT_UML_END.find(source)
    return if (endMatch != null) {
        source.replaceRange(endMatch.range.first, endMatch.range.first, "\n$PLANT_UML_DARK_THEME\n")
    } else {
        source.replaceFirst(PLANT_UML_START, "$0\n$PLANT_UML_DARK_THEME")
    }
}

private val PLANT_UML_START = Regex("(?im)^\\s*@start[A-Za-z0-9_]*[^\\r\\n]*$")
private val PLANT_UML_END = Regex("(?im)^\\s*@end[A-Za-z0-9_]*[^\\r\\n]*$")
private val SVG_TEXT_ELEMENT = Regex("""<text\b([^>]*)>(.*?)</text>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val SVG_ATTRIBUTE = Regex("""([\w:-]+)\s*=\s*(['"])(.*?)\2""")
private val SVG_ENTITY = Regex("""&#(?:x[0-9a-f]+|\d+);|&(amp|lt|gt|quot|apos);""", RegexOption.IGNORE_CASE)
private val SVG_LIGHT_FILL = Regex("""(?i)(\bfill\s*[:=]\s*["']?)(#[0-9a-f]{6}|#[0-9a-f]{3})(["']?)""")
private val SVG_DARK_STROKE = Regex("""(?i)(\bstroke\s*[:=]\s*["']?)(#[0-9a-f]{6}|#[0-9a-f]{3})(["']?)""")
private val SVG_FONT_RENDER_CONTEXT = FontRenderContext(AffineTransform(), true, true)
private val SVG_PATH_STYLE_ATTRIBUTES = listOf(
    "fill-opacity",
    "stroke",
    "stroke-opacity",
    "stroke-width",
    "opacity",
    "transform",
    "clip-path",
    "style",
)
/** Windows 中文图表与 Compose 界面保持一致，避免回退到不含汉字的逻辑字体。 */
private const val PLANT_UML_FONT_NAME = "Microsoft YaHei UI"

private const val PLANT_UML_DARK_THEME = """
skinparam backgroundColor transparent
skinparam defaultFontName $PLANT_UML_FONT_NAME
skinparam defaultFontColor #E7EAF0
skinparam defaultBorderColor #6E7A92
skinparam ArrowColor #9BA9C2
skinparam NoteBackgroundColor #31343C
skinparam NoteBorderColor #6E7A92
skinparam NoteFontColor #E7EAF0
skinparam ActorBackgroundColor #2B2D30
skinparam ActorBorderColor #6E7A92
skinparam ActorFontColor #E7EAF0
skinparam ParticipantBackgroundColor #2B2D30
skinparam ParticipantBorderColor #6E7A92
skinparam ParticipantFontColor #E7EAF0
skinparam ClassBackgroundColor #2B2D30
skinparam ClassBorderColor #6E7A92
skinparam ClassFontColor #E7EAF0
skinparam ComponentBackgroundColor #2B2D30
skinparam ComponentBorderColor #6E7A92
skinparam ComponentFontColor #E7EAF0
skinparam DatabaseBackgroundColor #2B2D30
skinparam DatabaseBorderColor #6E7A92
skinparam DatabaseFontColor #E7EAF0
skinparam PackageBackgroundColor transparent
skinparam PackageBorderColor #6E7A92
skinparam PackageFontColor #E7EAF0
skinparam StateBackgroundColor #2B2D30
skinparam StateBorderColor #6E7A92
skinparam StateFontColor #E7EAF0
skinparam ActivityBackgroundColor #2B2D30
skinparam ActivityBorderColor #6E7A92
skinparam ActivityFontColor #E7EAF0
skinparam ActivityDiamondBackgroundColor #2B2D30
skinparam ActivityDiamondBorderColor #6E7A92
skinparam ActivityDiamondFontColor #E7EAF0
skinparam SequenceGroupBackgroundColor #2B2D30
skinparam SequenceGroupBorderColor #6E7A92
skinparam SequenceGroupFontColor #E7EAF0
skinparam SequenceGroupHeaderFontColor #E7EAF0
skinparam usecase {
  BackgroundColor #2B2D30
  BorderColor #6E7A92
  FontColor #E7EAF0
}
"""

/** 读取 SVG 元素属性，保留属性名原样的大小写无关语义。 */
private fun svgAttributes(rawAttributes: String): Map<String, String> = SVG_ATTRIBUTE.findAll(rawAttributes).associate {
    it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[3]
}

/** 将 PlantUML 在 SVG 中使用的 XML 实体还原为 Java 字符串。 */
private fun decodeSvgText(encoded: String): String = SVG_ENTITY.replace(encoded) { match ->
    when (val entity = match.value.lowercase(Locale.ROOT)) {
        "&amp;" -> "&"
        "&lt;" -> "<"
        "&gt;" -> ">"
        "&quot;" -> "\""
        "&apos;" -> "'"
        else -> entity.removePrefix("&#").removeSuffix(";").let { number ->
            val radix = if (number.startsWith("x")) 16 else 10
            val codePoint = number.removePrefix("x").toInt(radix)
            String(Character.toChars(codePoint))
        }
    }
}

/** 判断 SVG 色值是否是默认图元使用的浅色表面。 */
private fun isLightSvgColor(color: String): Boolean = svgColorChannels(color).minOrNull()?.let { it >= 0xC0 } == true

/** 判断 SVG 色值是否是默认图元使用的近黑色连线。 */
private fun isDarkSvgColor(color: String): Boolean = svgColorChannels(color).maxOrNull()?.let { it <= 0x60 } == true

/** 将三位或六位十六进制 SVG 颜色转换为 RGB 通道。 */
private fun svgColorChannels(color: String): IntArray {
    val digits = color.removePrefix("#")
    val expanded = if (digits.length == 3) digits.map { "$it$it" }.joinToString("") else digits
    return intArrayOf(
        expanded.substring(0, 2).toInt(16),
        expanded.substring(2, 4).toInt(16),
        expanded.substring(4, 6).toInt(16),
    )
}

/** 根据 SVG 文字属性构建等价的 AWT 字体，确保轮廓位置与 PlantUML 一致。 */
private fun svgFont(attributes: Map<String, String>): Font {
    val style = when {
        attributes["font-weight"]?.equals("bold", ignoreCase = true) == true &&
            attributes["font-style"]?.equals("italic", ignoreCase = true) == true -> Font.BOLD or Font.ITALIC

        attributes["font-weight"]?.equals("bold", ignoreCase = true) == true -> Font.BOLD
        attributes["font-style"]?.equals("italic", ignoreCase = true) == true -> Font.ITALIC
        else -> Font.PLAIN
    }
    val family = attributes["font-family"]
        ?.substringBefore(',')
        ?.trim()
        ?.trim('\'', '\"')
        ?.ifBlank { null }
        ?: PLANT_UML_FONT_NAME
    val size = attributes["font-size"]?.toFloatOrNull()?.takeIf { it > 0f } ?: 14f
    return Font(family, style, size.roundToInt().coerceAtLeast(1)).deriveFont(size)
}

/** 将 Java2D 的字形轮廓序列化为 Skia 可绘制的 SVG path。 */
private fun svgPathData(shape: java.awt.Shape): String {
    val iterator = shape.getPathIterator(null)
    val coordinates = DoubleArray(6)
    return buildString {
        while (!iterator.isDone) {
            when (iterator.currentSegment(coordinates)) {
                PathIterator.SEG_MOVETO -> appendSvgCommand("M", coordinates, 2)
                PathIterator.SEG_LINETO -> appendSvgCommand("L", coordinates, 2)
                PathIterator.SEG_QUADTO -> appendSvgCommand("Q", coordinates, 4)
                PathIterator.SEG_CUBICTO -> appendSvgCommand("C", coordinates, 6)
                PathIterator.SEG_CLOSE -> append("Z ")
            }
            iterator.next()
        }
    }.trim()
}

/** 追加单个 SVG 路径指令，并固定为与区域设置无关的小数格式。 */
private fun StringBuilder.appendSvgCommand(command: String, coordinates: DoubleArray, count: Int) {
    append(command)
    repeat(count) { index ->
        append(' ')
        append("%.3f".format(Locale.ROOT, coordinates[index]))
    }
    append(' ')
}

/** 保留文字节点会影响轮廓可见性的绘制属性。 */
private fun svgPathStyle(attributes: Map<String, String>): String = buildString {
    append("fill=\"#E7EAF0\" ")
    SVG_PATH_STYLE_ATTRIBUTES.forEach { name ->
        attributes[name]?.let { value -> append("$name=\"$value\" ") }
    }
}
