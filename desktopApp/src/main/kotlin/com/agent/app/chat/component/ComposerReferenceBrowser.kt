package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.WorkspaceFileReference
import com.agent.app.design.AppAccent
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSelectedBackground
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.shared.agent.resource.AgentPromptCommand
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/**
 * 不带开闭动画的 `/` 命令浏览器。选中项完全由父输入框管理，保证键盘与鼠标切换时焦点稳定。
 */
@Composable
internal fun SlashCommandBrowser(
    commands: List<AgentPromptCommand>,
    selectedIndex: Int,
    onCommandSelected: (AgentPromptCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerReferenceBrowserSurface(
        title = "命令",
        footer = "↑↓ 选择 · Enter / Tab / Ctrl+. 插入 · Esc 关闭",
        modifier = modifier,
    ) {
        if (commands.isEmpty()) {
            EmptyReferenceBrowserRow("没有匹配的命令")
        } else {
            commands.forEachIndexed { index, command ->
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) AppSelectedBackground else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onCommandSelected(command) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = buildString {
                            append("/")
                            append(command.name)
                            command.argumentHint?.takeIf(String::isNotBlank)?.let { hint ->
                                append("  ")
                                append(hint)
                            }
                        },
                        style = JewelTheme.defaultTextStyle.copy(
                            color = if (selected) AppAccent else AppText,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                    Text(
                        text = command.description,
                        style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                    )
                }
            }
        }
    }
}

/** 显示并选择受工作区边界约束的 `@` 文件候选。 */
@Composable
internal fun WorkspaceFileReferenceBrowser(
    references: List<WorkspaceFileReference>,
    selectedIndex: Int,
    onReferenceSelected: (WorkspaceFileReference) -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerReferenceBrowserSurface(
        title = "引用工作区文件",
        footer = "↑↓ 选择 · Enter / Tab 插入 · Esc 关闭",
        modifier = modifier,
    ) {
        if (references.isEmpty()) {
            EmptyReferenceBrowserRow("没有匹配的工作区文件")
        } else {
            references.forEachIndexed { index, reference ->
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) AppSelectedBackground else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onReferenceSelected(reference) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "@${reference.relativePath}",
                        style = JewelTheme.defaultTextStyle.copy(
                            color = if (selected) AppAccent else AppText,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        }
    }
}

/** 为两个引用浏览器提供静态浮层表面和可滚动条目区域。 */
@Composable
private fun ComposerReferenceBrowserSurface(
    title: String,
    footer: String,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    JewelSurface(
        role = JewelSurfaceRole.FLOATING,
        radius = 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 336.dp)
                        .verticalScroll(scrollState),
                    content = content,
                )
                Text(
                    text = footer,
                    style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (scrollState.maxValue > 0) {
                VerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp, horizontal = 3.dp),
                )
            }
        }
    }
}

/** 在无匹配项时保留浏览器尺寸与键盘提示，避免输入区突然跳动。 */
@Composable
private fun EmptyReferenceBrowserRow(text: String) {
    Text(
        text = text,
        style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
    )
}
