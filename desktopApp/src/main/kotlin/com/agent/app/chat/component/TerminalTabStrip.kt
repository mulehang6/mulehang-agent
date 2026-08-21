@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.iconKey

/** 渲染 IDEA Islands 风格终端标签，并保留 Jewel 的选择、关闭与右键菜单。 */
@Composable
internal fun TerminalTabStrip(
    tabs: List<TerminalTab>,
    activeTabId: Long?,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onContextMenuRequested: (Long, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabData = tabs.map { tab ->
        var tabOrigin by remember(tab.id) { mutableStateOf(Offset.Zero) }
        IslandsTab(
            label = tab.title,
            selected = tab.id == activeTabId,
            iconKey = RightRailGlyph.TERMINAL.iconKey,
            closable = true,
            onClose = { onCloseTab(tab.id) },
            onClick = { onSelectTab(tab.id) },
            modifier = Modifier
                .onGloballyPositioned { tabOrigin = it.positionInRoot() }
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) {
                        val pointer = event.changes.firstOrNull()?.position ?: Offset.Zero
                        onContextMenuRequested(tab.id, tabOrigin + pointer)
                    }
                },
        )
    }
    IslandsTabStrip(
        tabs = tabData,
        modifier = modifier.fillMaxWidth(),
    )
}
