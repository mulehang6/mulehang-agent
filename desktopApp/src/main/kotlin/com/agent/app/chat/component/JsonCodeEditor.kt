package com.agent.app.chat.component

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.design.AppLine
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import org.jetbrains.jewel.ui.component.Text

/**
 * Compose 原生的 IDEA 风格 JSON 编辑器。
 *
 * 它沿用 Jewel 表面与滚动条，提供行号、JSON 词法高亮、当前行号、括号匹配和编辑快捷键，
 * 同时避免引入依赖 Application、PSI 和 EditorFactory 的完整 IntelliJ Platform 编辑器。
 */
@Composable
internal fun JsonCodeEditor(
    text: String,
    error: String?,
    onTextChange: (String) -> Unit,
    onFormat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(TextFieldValue(text)) }
    val history = remember { JsonEditorHistory(text) }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    LaunchedEffect(text) {
        if (text != value.text) {
            value = TextFieldValue(text, selection = value.selection.coerceInside(text.length))
            history.replaceCurrent(text)
        }
    }
    val currentLine = value.text.lineIndexAt(value.selection.start)
    val errorLine = jsonErrorLine(error, value.text)
    val transformation = remember(value.selection.start) { JsonSyntaxTransformation(value.selection.start) }

    JewelSurface(
        role = JewelSurfaceRole.INPUT,
        radius = 4.dp,
        borderColor = AppLine,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize().background(EDITOR_BACKGROUND)) {
            Row(
                modifier = Modifier.fillMaxSize().padding(end = 12.dp, bottom = 12.dp).verticalScroll(verticalScroll),
            ) {
                JsonLineNumberGutter(
                    lineCount = value.text.lineCountAtLeastOne(),
                    currentLine = currentLine,
                    errorLine = errorLine,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                )
                Box(
                    modifier = Modifier.weight(1f).horizontalScroll(horizontalScroll).widthIn(min = 720.dp),
                ) {
                    Box(
                        Modifier
                            .offset(y = 7.dp + 20.dp * currentLine)
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(EDITOR_CURRENT_LINE),
                    )
                    BasicTextField(
                        value = value,
                        onValueChange = { next ->
                            if (next.text != value.text) history.record(next.text)
                            value = next
                            onTextChange(next.text)
                        },
                        modifier = Modifier
                            .widthIn(min = 720.dp)
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                            .onPreviewKeyEvent { event ->
                                handleJsonEditorKey(
                                    event = event,
                                    value = value,
                                    history = history,
                                    onValueChange = { next ->
                                        value = next
                                        onTextChange(next.text)
                                    },
                                    onFormat = onFormat,
                                )
                            },
                        textStyle = EDITOR_TEXT_STYLE,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(EDITOR_CARET),
                        visualTransformation = transformation,
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(verticalScroll),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(horizontalScroll),
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 12.dp),
            )
        }
    }
}

/** 逐行绘制固定宽度 gutter，并把当前行与错误行状态保持在正文之外。 */
@Composable
private fun JsonLineNumberGutter(
    lineCount: Int,
    currentLine: Int,
    errorLine: Int?,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        repeat(lineCount) { index ->
            val lineNumber = index + 1
            Text(
                text = if (errorLine == lineNumber) "● $lineNumber" else lineNumber.toString(),
                style = EDITOR_GUTTER_STYLE.copy(
                    color = when {
                        errorLine == lineNumber -> EDITOR_ERROR
                        index == currentLine -> EDITOR_ACTIVE_GUTTER
                        else -> EDITOR_GUTTER
                    },
                    fontWeight = if (index == currentLine) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
        }
    }
}

/** 处理 IDEA 常用的格式化、缩进和撤销快捷键。 */
private fun handleJsonEditorKey(
    event: KeyEvent,
    value: TextFieldValue,
    history: JsonEditorHistory,
    onValueChange: (TextFieldValue) -> Unit,
    onFormat: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isCtrlPressed && event.isAltPressed && event.key == Key.L) {
        onFormat()
        return true
    }
    if (event.isCtrlPressed && event.key == Key.Z) {
        history.undo()?.let {
            onValueChange(TextFieldValue(it, selection = androidx.compose.ui.text.TextRange(it.length.coerceAtMost(value.selection.start))))
        }
        return true
    }
    if (event.isCtrlPressed && event.key == Key.Y) {
        history.redo()?.let {
            onValueChange(TextFieldValue(it, selection = androidx.compose.ui.text.TextRange(it.length.coerceAtMost(value.selection.start))))
        }
        return true
    }
    if (event.key == Key.Tab) {
        val next = if (event.isShiftPressed) value.unindentCurrentLine() else value.replaceSelection("    ")
        history.record(next.text)
        onValueChange(next)
        return true
    }
    if (event.key == Key.Enter) {
        val indent = value.currentLineIndent() + if (value.previousNonWhitespace() in setOf('{', '[')) "    " else ""
        val next = value.replaceSelection("\n$indent")
        history.record(next.text)
        onValueChange(next)
        return true
    }
    return false
}

/** 保存有限编辑历史；程序化格式化会替换当前项而不破坏上一项。 */
internal class JsonEditorHistory(initialText: String) {
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var current = initialText

    /** 记录一次来自用户的正文变化。 */
    fun record(text: String) {
        if (text == current) return
        undo.addLast(current)
        while (undo.size > MAX_HISTORY_SIZE) undo.removeFirst()
        current = text
        redo.clear()
    }

    /** 返回撤销后的正文；没有历史时返回 null。 */
    fun undo(): String? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        current = previous
        return previous
    }

    /** 返回重做后的正文；没有可重做内容时返回 null。 */
    fun redo(): String? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        current = next
        return next
    }

    /** 接受父状态的规范化正文，同时保留其前一个版本用于撤销。 */
    fun replaceCurrent(text: String) {
        if (text == current) return
        record(text)
    }
}

/** 使用等长转换为 JSON token 着色，并突出光标相邻括号的匹配项。 */
private class JsonSyntaxTransformation(private val cursor: Int) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val brackets = matchingJsonBrackets(source, cursor)
        val highlighted = buildAnnotatedString {
            append(source)
            jsonTokenSpans(source).forEach { span -> addStyle(span.style, span.start, span.end) }
            brackets.forEach { index ->
                if (index in source.indices) addStyle(SpanStyle(background = EDITOR_BRACKET_MATCH), index, index + 1)
            }
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

/** JSON 词法 span；解析合法性仍由独立 MCP 转换层负责。 */
private data class JsonTokenSpan(val start: Int, val end: Int, val style: SpanStyle)

/** 识别字符串、数字和字面量，足以提供稳定且不改变偏移的编辑高亮。 */
private fun jsonTokenSpans(text: String): List<JsonTokenSpan> {
    val spans = mutableListOf<JsonTokenSpan>()
    var index = 0
    while (index < text.length) {
        when {
            text[index] == '"' -> {
                val start = index++
                var escaped = false
                while (index < text.length) {
                    val character = text[index++]
                    if (character == '"' && !escaped) break
                    escaped = character == '\\' && !escaped
                    if (character != '\\') escaped = false
                }
                val isKey = text.drop(index).firstOrNull { !it.isWhitespace() } == ':'
                spans += JsonTokenSpan(start, index, SpanStyle(color = if (isKey) EDITOR_KEY else EDITOR_STRING))
            }

            text[index].isDigit() || text[index] == '-' -> {
                val start = index++
                while (index < text.length && (text[index].isDigit() || text[index] in ".eE+-")) index += 1
                spans += JsonTokenSpan(start, index, SpanStyle(color = EDITOR_NUMBER))
            }

            text.startsWith("true", index) || text.startsWith("false", index) || text.startsWith("null", index) -> {
                val literal = listOf("true", "false", "null").first { text.startsWith(it, index) }
                spans += JsonTokenSpan(index, index + literal.length, SpanStyle(color = EDITOR_LITERAL))
                index += literal.length
            }

            else -> index += 1
        }
    }
    return spans
}

/** 查找光标相邻括号及其配对位置；字符串内括号不会参与。 */
private fun matchingJsonBrackets(text: String, cursor: Int): Set<Int> {
    val candidate = listOf(cursor - 1, cursor).firstOrNull { it in text.indices && text[it] in "{}[]" } ?: return emptySet()
    val open = text[candidate] in "[{"
    val opening = if (text[candidate] in "{}") '{' else '['
    val closing = if (opening == '{') '}' else ']'
    val step = if (open) 1 else -1
    var depth = 0
    var index = candidate
    var inString = false
    var escaped = false
    while (index in text.indices) {
        val character = text[index]
        if (character == '"' && !escaped) inString = !inString
        if (!inString) {
            if (character == opening) depth += if (open) 1 else -1
            if (character == closing) depth += if (open) -1 else 1
            if (depth == 0 && index != candidate) return setOf(candidate, index)
        }
        escaped = character == '\\' && !escaped
        if (character != '\\') escaped = false
        index += step
    }
    return setOf(candidate)
}

/** 从序列化器错误中提取一基行号，其他业务校验错误不伪造位置。 */
internal fun jsonErrorLine(error: String?, text: String): Int? {
    if (error == null) return null
    ERROR_LINE_REGEX.find(error)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
    val offset = ERROR_OFFSET_REGEX.find(error)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
    return text.take(offset.coerceIn(0, text.length)).count { it == '\n' } + 1
}

/** 在当前选区插入内容，并把光标放到新内容末端。 */
private fun TextFieldValue.replaceSelection(replacement: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val nextText = text.replaceRange(start, end, replacement)
    val nextCursor = start + replacement.length
    return TextFieldValue(nextText, selection = androidx.compose.ui.text.TextRange(nextCursor))
}

/** Shift+Tab 最多移除当前行开头的四个空格。 */
private fun TextFieldValue.unindentCurrentLine(): TextFieldValue {
    val lineStart = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0)) + 1
    val removable = text.substring(lineStart, selection.start.coerceAtLeast(lineStart)).takeWhile { it == ' ' }.length.coerceAtMost(4)
    if (removable == 0) return this
    val nextText = text.removeRange(lineStart, lineStart + removable)
    return TextFieldValue(nextText, selection = androidx.compose.ui.text.TextRange((selection.start - removable).coerceAtLeast(lineStart)))
}

/** 返回当前行已有的前导空格。 */
private fun TextFieldValue.currentLineIndent(): String {
    val lineStart = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0)) + 1
    return text.substring(lineStart, selection.start.coerceAtLeast(lineStart)).takeWhile { it == ' ' }
}

/** 返回光标前最近的非空白字符。 */
private fun TextFieldValue.previousNonWhitespace(): Char? = text.take(selection.start).lastOrNull { !it.isWhitespace() }

/** 将文本偏移转换为零基行索引。 */
private fun String.lineIndexAt(offset: Int): Int = take(offset.coerceIn(0, length)).count { it == '\n' }

/** 空文本也显示第一行。 */
private fun String.lineCountAtLeastOne(): Int = count { it == '\n' } + 1

/** 外部格式化后把选区限制在新文本长度内。 */
private fun androidx.compose.ui.text.TextRange.coerceInside(length: Int): androidx.compose.ui.text.TextRange =
    androidx.compose.ui.text.TextRange(start.coerceIn(0, length), end.coerceIn(0, length))

private const val MAX_HISTORY_SIZE = 200
private val ERROR_LINE_REGEX = Regex("(?:line|行)\\s*(\\d+)", RegexOption.IGNORE_CASE)
private val ERROR_OFFSET_REGEX = Regex("(?:offset|位置)\\s*(\\d+)", RegexOption.IGNORE_CASE)
private val EDITOR_BACKGROUND = Color(0xFF1E1F22)
private val EDITOR_CURRENT_LINE = Color(0xFF26282C)
private val EDITOR_CARET = Color(0xFFA8B7C7)
private val EDITOR_GUTTER = Color(0xFF606366)
private val EDITOR_ACTIVE_GUTTER = Color(0xFFA4A7A9)
private val EDITOR_ERROR = Color(0xFFE05555)
private val EDITOR_KEY = Color(0xFFC77DBB)
private val EDITOR_STRING = Color(0xFF6AAB73)
private val EDITOR_NUMBER = Color(0xFF2AACB8)
private val EDITOR_LITERAL = Color(0xFFCF8E6D)
private val EDITOR_BRACKET_MATCH = Color(0xFF3B514D)
private val EDITOR_TEXT_STYLE = TextStyle(
    color = AppText,
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)
private val EDITOR_GUTTER_STYLE = EDITOR_TEXT_STYLE.copy(fontSize = 12.sp)
