package com.agent.app.chat.component

import java.awt.Font
import java.awt.font.TextAttribute
import java.util.Locale

/**
 * 将 SVG/CSS 字体栈解析为能完整显示当前标签的 JDK 字体。
 *
 * Mermaid 会输出浏览器字体栈；JDK 不会像浏览器那样逐项尝试，因此这里显式选择可覆盖整段文字的项。
 */
internal object DiagramSvgFontResolver {
    /**
     * 按 CSS 声明顺序选择可显示全部文字的字体，并始终以应用的 SansSerif 作为最终回退。
     */
    fun resolve(
        cssFontFamilies: String,
        text: String,
        fontStyle: Int,
        fontSize: Float,
        letterSpacing: Float,
    ): Font {
        val candidates = cssFontFamilies.toCssFontFamilies()
            .map(String::toAwtFontFamily)
            .ifEmpty { listOf(APPLICATION_SANS_SERIF_FONT_FAMILY) }
            .plus(APPLICATION_SANS_SERIF_FONT_FAMILY)
            .distinctBy { family -> family.lowercase(Locale.ROOT) }
            .map { family -> Font(family, fontStyle, 1).deriveFont(fontSize) }
        val font = candidates.firstOrNull { candidate -> candidate.canDisplayUpTo(text) == -1 }
            ?: candidates.last()
        return font.withLetterSpacing(letterSpacing, fontSize)
    }
}

/** PlantUML 与 SVG 轮廓化共享的 JDK 逻辑无衬线字体。 */
internal const val APPLICATION_SANS_SERIF_FONT_FAMILY = "SansSerif"

/** 将逗号分隔、可包含引号的 CSS 字体栈拆为单独的字体名称。 */
private fun String.toCssFontFamilies(): List<String> {
    val families = mutableListOf<String>()
    val token = StringBuilder()
    var quote: Char? = null
    for (character in this) {
        when (val currentQuote = quote) {
            null -> when (character) {
                '\'', '"' -> {
                    quote = character
                    token.append(character)
                }

                ',' -> {
                    token.toFontFamilyOrNull()?.let(families::add)
                    token.clear()
                }

                else -> token.append(character)
            }

            else -> {
                token.append(character)
                if (character == currentQuote) quote = null
            }
        }
    }
    token.toFontFamilyOrNull()?.let(families::add)
    return families
}

/** 清理单个 CSS 字体项外层的空白与成对引号。 */
private fun StringBuilder.toFontFamilyOrNull(): String? = toString()
    .trim()
    .removeSurrounding("\"")
    .removeSurrounding("'")
    .trim()
    .takeIf(String::isNotEmpty)

/** 将 CSS 通用字体族映射为与 PlantUML 一致的 JDK 逻辑字体名称。 */
private fun String.toAwtFontFamily(): String = when (lowercase(Locale.ROOT)) {
    "sans-serif", "sans serif", "sansserif" -> APPLICATION_SANS_SERIF_FONT_FAMILY
    "serif" -> Font.SERIF
    "monospace", "monospaced" -> Font.MONOSPACED
    else -> this
}

/** 按 SVG 字距需求派生 AWT 字体；零字距不额外创建字体对象。 */
private fun Font.withLetterSpacing(letterSpacing: Float, fontSize: Float): Font = if (letterSpacing == 0f) {
    this
} else {
    deriveFont(mapOf(TextAttribute.TRACKING to letterSpacing / fontSize))
}
