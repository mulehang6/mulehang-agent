@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.PANEL_TAB_ICON_SIZE
import com.agent.app.design.TerminalTabActiveBackground
import com.agent.app.design.TerminalTabHoverBackground
import com.agent.app.design.TerminalTabSelectedBorder
import com.agent.app.design.iconKey
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 终端标签在悬浮和选中状态下使用的圆角半径。 */
internal val TERMINAL_TAB_CORNER_RADIUS = 8.dp

/** 终端标签条中单个标签的固定高度。 */
internal val TERMINAL_TAB_HEIGHT = 28.dp

/** 根据标签状态返回圆角标签的静态背景色；hover 不使用额外动画。 */
internal fun terminalTabBackground(selected: Boolean, hovered: Boolean): Color = when {
    selected -> TerminalTabActiveBackground
    hovered -> TerminalTabHoverBackground
    else -> Color.Transparent
}

/** 返回按方向循环后的终端标签 ID，供键盘左右方向键选择。 */
internal fun adjacentTerminalTabId(
    tabs: List<TerminalTab>,
    activeTabId: Long?,
    direction: Int,
): Long? {
    if (tabs.isEmpty() || direction == 0) return activeTabId
    val activeIndex = tabs.indexOfFirst { it.id == activeTabId }.takeIf { it >= 0 } ?: 0
    val nextIndex = Math.floorMod(activeIndex + direction, tabs.size)
    return tabs[nextIndex].id
}

/** 渲染带圆角 hover 的紧凑终端标签栏，并保留鼠标和键盘交互。 */
@Composable
internal fun TerminalTabStrip(
    tabs: List<TerminalTab>,
    activeTabId: Long?,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onContextMenuRequested: (Long, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            TerminalTabItem(
                tab = tab,
                selected = tab.id == activeTabId,
                tabs = tabs,
                activeTabId = activeTabId,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onContextMenuRequested = onContextMenuRequested,
            )
        }
    }
}

/** 渲染一个终端标签并处理选择、关闭、右键菜单及方向键切换。 */
@Composable
private fun TerminalTabItem(
    tab: TerminalTab,
    selected: Boolean,
    tabs: List<TerminalTab>,
    activeTabId: Long?,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onContextMenuRequested: (Long, Offset) -> Unit,
) {
    var hovered by remember(tab.id) { mutableStateOf(false) }
    var tabOrigin by remember(tab.id) { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(TERMINAL_TAB_CORNER_RADIUS)
    val background = terminalTabBackground(selected = selected, hovered = hovered)
    val border = if (selected) TerminalTabSelectedBorder else Color.Transparent

    Row(
        modifier = Modifier
            .height(TERMINAL_TAB_HEIGHT)
            .widthIn(max = 220.dp)
            .clip(shape)
            .background(background)
            .border(width = 1.dp, color = border, shape = shape)
            .onGloballyPositioned { tabOrigin = it.positionInRoot() }
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) {
                    val pointer = event.changes.firstOrNull()?.position ?: Offset.Zero
                    onContextMenuRequested(tab.id, tabOrigin + pointer)
                }
            }
            .onPreviewKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }
                if (event.type != KeyEventType.KeyDown || direction == 0) {
                    false
                } else {
                    adjacentTerminalTabId(tabs, activeTabId, direction)?.let(onSelectTab)
                    true
                }
            }
            .clickable { onSelectTab(tab.id) }
            .focusable()
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            key = RightRailGlyph.TERMINAL.iconKey,
            contentDescription = "终端",
            modifier = Modifier.size(PANEL_TAB_ICON_SIZE),
        )
        Text(
            text = tab.title,
            modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconActionButton(
            key = AllIconsKeys.Actions.Cancel,
            contentDescription = "关闭 ${tab.title}",
            onClick = { onCloseTab(tab.id) },
            modifier = Modifier.size(24.dp),
        )
    }
}
