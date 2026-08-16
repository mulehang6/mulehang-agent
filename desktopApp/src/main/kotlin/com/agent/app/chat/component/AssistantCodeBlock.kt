@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import com.agent.app.design.AppPanelBackground
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

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
 * 原生显示带语言标记的代码块，并允许用户选择或一键复制其中的源码。
 */
@Composable
internal fun AssistantCodeBlock(
    language: String?,
    source: String,
) {
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 8.dp,
        solidColor = AppPanelBackground,
        borderColor = AppLine.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                language?.let {
                    Text(
                        text = it,
                        style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                    )
                }
                CopyCodeButton(source = source)
            }
            SelectionContainer {
                Text(
                    text = highlightCode(source, language),
                    style = JewelTheme.defaultTextStyle.copy(
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

/** 将完整代码块源码写入系统剪贴板，供所有围栏代码和 PlantUML 视图复用。 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CopyCodeButton(source: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    ActionButton(
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(StringSelection(source)))
            }
        },
        tooltip = { Text("复制代码") },
    ) {
        Icon(AllIconsKeys.Actions.Copy, "复制代码")
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
