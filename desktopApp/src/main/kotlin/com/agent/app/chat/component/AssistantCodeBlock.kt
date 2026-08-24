@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
)
@file:Suppress("UnstableApiUsage")

package com.agent.app.chat.component

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
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
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * 原生显示带语言标记的代码块，并允许用户选择或一键复制其中的源码。
 */
@Composable
internal fun AssistantCodeBlock(
    language: String?,
    source: String,
) {
    val codeHighlighter = LocalCodeHighlighter.current
    val highlightedSource by remember(codeHighlighter, language, source) {
        codeHighlighter.highlight(source, language.orEmpty())
    }.collectAsState(initial = AnnotatedString(source))

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
                    text = highlightedSource,
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
