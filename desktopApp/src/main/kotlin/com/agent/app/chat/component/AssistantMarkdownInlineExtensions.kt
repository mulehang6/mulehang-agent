package com.agent.app.chat.component

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.agent.app.design.AppText

/** 深色聊天背景中突出 `==高亮==` 内容的低干扰底色。 */
internal val AssistantMarkdownHighlightBackground = Color(0xFF5C4B12)

/** 判断一段 Markdown 是否包含需要由原生文本组件处理的行内扩展。 */
internal fun containsAssistantMarkdownInlineExtensions(content: String): Boolean =
    INLINE_EXTENSION_PATTERNS.any { pattern -> pattern.containsMatchIn(content) }

/**
 * 将富文本库尚未解析的下划线、高亮、上下标语法转换为 Compose 的带样式文本。
 *
 * 此函数只处理已经识别出扩展语法的文本，普通 Markdown 仍交由原有 CommonMark renderer 渲染。
 */
internal fun renderAssistantMarkdownInlineExtensions(content: String): AnnotatedString {
    val normalizedListMarkers = content.replace(LIST_ITEM_MARKER, "• ")
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < normalizedListMarkers.length) {
            val next = findNextInlineExtension(normalizedListMarkers, cursor) ?: break
            append(normalizedListMarkers.substring(cursor, next.match.range.first))
            withStyle(next.style) {
                append(requireNotNull(next.match.groups[1]?.value))
            }
            cursor = next.match.range.last + 1
        }
        append(normalizedListMarkers.substring(cursor))
    }
}

/** 将扩展 Markdown 呈现为可选中的原生 Compose 文本。 */
@Composable
internal fun AssistantMarkdownInlineExtensionsText(content: String) {
    SelectionContainer {
        Text(
            text = renderAssistantMarkdownInlineExtensions(content),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AppText,
                lineHeight = 23.sp,
            ),
        )
    }
}

/** 找到当前位置起最靠前的一项受支持行内扩展。 */
private fun findNextInlineExtension(content: String, startIndex: Int): AssistantMarkdownInlineExtension? =
    INLINE_EXTENSION_PATTERNS.mapNotNull { pattern ->
        pattern.find(content, startIndex)?.let { match ->
            AssistantMarkdownInlineExtension(
                match = match,
                style = styleForInlineExtension(pattern),
            )
        }
    }.minByOrNull { extension -> extension.match.range.first }

/** 将扩展匹配规则映射为对应的 Compose 文本样式。 */
private fun styleForInlineExtension(pattern: Regex): SpanStyle = when (pattern) {
    HTML_UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    HIGHLIGHT -> SpanStyle(background = AssistantMarkdownHighlightBackground)
    SUBSCRIPT -> SpanStyle(
        baselineShift = BaselineShift.Subscript,
        fontSize = 0.75.em,
    )

    SUPERSCRIPT -> SpanStyle(
        baselineShift = BaselineShift.Superscript,
        fontSize = 0.75.em,
    )

    else -> error("Unsupported assistant Markdown inline extension")
}

/** 记录正则匹配及其应用到内容部分的 Compose 样式。 */
private data class AssistantMarkdownInlineExtension(
    val match: MatchResult,
    val style: SpanStyle,
)

private val HTML_UNDERLINE = Regex("<\\s*u\\s*>([^<\\r\\n]+)<\\s*/\\s*u\\s*>", RegexOption.IGNORE_CASE)
private val HIGHLIGHT = Regex("==([^=\\r\\n]+)==")
private val SUBSCRIPT = Regex("(?<!~)~([^~\\r\\n]+)~(?!~)")
private val SUPERSCRIPT = Regex("(?<!\\^)\\^([^\\^\\r\\n]+)\\^(?!\\^)")
private val LIST_ITEM_MARKER = Regex("(?m)^\\h*[-*]\\h+")
private val INLINE_EXTENSION_PATTERNS = listOf(HTML_UNDERLINE, HIGHLIGHT, SUBSCRIPT, SUPERSCRIPT)
