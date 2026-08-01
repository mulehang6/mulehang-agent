package com.agent.app.chat.component

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText

/** 围栏代码的关键字颜色。 */
internal val CodeKeywordColor = Color(0xFFC9A7FF)

/** 围栏代码的字符串颜色。 */
internal val CodeStringColor = Color(0xFFA8D38E)

private val CodeCommentColor = Color(0xFF79818F)

/**
 * 对常见 Agent 输出语言应用轻量词法高亮；未知语言保持可读的等宽纯文本。
 */
internal fun highlightCode(source: String, language: String?): AnnotatedString {
    val keywords = language?.lowercase()?.let(::keywordsFor).orEmpty()
    if (keywords.isEmpty()) return AnnotatedString(source)

    val builder = AnnotatedString.Builder(source)
    CODE_TOKEN.findAll(source).forEach { match ->
        val token = match.value
        val style = when {
            token.startsWith('"') || token.startsWith('\'') || token.startsWith('`') -> SpanStyle(color = CodeStringColor)
            token.startsWith("//") || token.startsWith('#') -> SpanStyle(color = CodeCommentColor)
            token in keywords -> SpanStyle(color = CodeKeywordColor)
            else -> null
        }
        style?.let { builder.addStyle(it, match.range.first, match.range.last + 1) }
    }
    return builder.toAnnotatedString()
}

/**
 * 原生显示带语言标记的代码块，并允许用户选择复制其中的源码。
 */
@Composable
internal fun AssistantCodeBlock(
    language: String?,
    source: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF24272E),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppLine.copy(alpha = 0.75f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            language?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(color = AppMuted),
                )
            }
            SelectionContainer {
                Text(
                    text = highlightCode(source, language),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    ),
                )
            }
        }
    }
}

private val CODE_TOKEN = Regex(
    pattern = "\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'|`(?:\\\\.|[^`])*`|//[^\\r\\n]*|#[^\\r\\n]*|\\b[A-Za-z_][A-Za-z0-9_]*\\b",
)

private fun keywordsFor(language: String): Set<String> = when (language) {
    "python", "py" -> setOf("and", "as", "async", "await", "class", "def", "elif", "else", "except", "False", "finally", "for", "from", "if", "import", "in", "is", "lambda", "None", "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield")
    "kotlin", "kt", "java", "javascript", "js", "typescript", "ts" -> setOf("abstract", "as", "async", "await", "break", "case", "catch", "class", "const", "continue", "data", "do", "else", "enum", "extends", "false", "final", "finally", "for", "fun", "function", "if", "implements", "import", "in", "interface", "is", "new", "null", "object", "open", "override", "package", "private", "protected", "public", "return", "sealed", "static", "suspend", "this", "throw", "true", "try", "val", "var", "void", "when", "while")
    "json" -> setOf("true", "false", "null")
    "bash", "sh", "shell", "zsh", "sql", "xml", "html" -> setOf("case", "do", "done", "else", "esac", "fi", "for", "function", "if", "in", "select", "then", "while", "SELECT", "FROM", "WHERE", "JOIN", "INSERT", "UPDATE", "DELETE", "CREATE", "TABLE")
    else -> emptySet()
}
