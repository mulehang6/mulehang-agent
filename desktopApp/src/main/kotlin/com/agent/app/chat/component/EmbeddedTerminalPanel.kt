@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppMuted
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.OffsetPopupPositionProvider
import com.agent.app.design.TerminalSurfaceBackground
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 返回终端标题栏关闭图标的可访问文案，明确其不会终止终端会话。 */
internal fun terminalPanelHideActionLabel(): String = "收起终端"

/**
 * 在主工作区右侧承载可切换的交互式 PowerShell 终端标签页。
 */
@Composable
internal fun EmbeddedTerminalPanel(
    tabs: TerminalTabsState,
    sessions: TerminalSessionStore,
    onSelectTab: (Long) -> Unit,
    onAddTab: () -> Unit,
    onCloseTab: (Long) -> Unit,
    onCloseOtherTabs: (Long) -> Unit,
    onHidePanel: () -> Unit,
    onFocus: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val terminalPalette = LocalDesktopPalette.current.terminal
    val terminalChromeBackground = TerminalSurfaceBackground
    val activeTab = tabs.tabs.firstOrNull { it.id == tabs.activeTabId }
    val activeSession = activeTab?.let { sessions.session(it.id) }
    val activeComponent = activeSession?.component
    val density = LocalDensity.current
    val contextMenuLabels = terminalTabContextMenuLabels()
    var terminalOrigin by remember { mutableStateOf(Offset.Zero) }
    var contextMenuTabId by remember { mutableStateOf<Long?>(null) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val contextMenuPositionProvider = remember(contextMenuOffset, density) {
        OffsetPopupPositionProvider(
            offset = contextMenuOffset,
            density = density.density,
        )
    }

    LaunchedEffect(tabs.activeTabId) {
        sessions.focusActiveIfNeeded(tabs.activeTabId)
    }
    JewelSurface(
        role = JewelSurfaceRole.PANEL,
        radius = 14.dp,
        borderWidth = 0.dp,
        solidColor = TerminalSurfaceBackground,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .onPointerEvent(PointerEventType.Press) { onFocus() }
            .onGloballyPositioned { terminalOrigin = it.positionInRoot() },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(terminalChromeBackground)
                    .padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            TerminalTabStrip(
                tabs = tabs.tabs,
                activeTabId = tabs.activeTabId,
                onSelectTab = onSelectTab,
                onCloseTab = onCloseTab,
                onContextMenuRequested = { tabId, pointerInRoot ->
                    contextMenuTabId = tabId
                    contextMenuOffset = with(density) {
                        DpOffset(
                            x = (pointerInRoot.x - terminalOrigin.x).toDp(),
                            y = (pointerInRoot.y - terminalOrigin.y).toDp(),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionButton(
                    onClick = onAddTab,
                    tooltip = { Text("新建终端") },
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Icon(AllIconsKeys.General.Add, "新建终端")
                }
            }
            ActionButton(
                onClick = onHidePanel,
                tooltip = { Text(terminalPanelHideActionLabel()) },
            ) {
                Icon(AllIconsKeys.Actions.Cancel, terminalPanelHideActionLabel())
            }
        }
            Box(
                modifier = Modifier.fillMaxSize().background(TerminalSurfaceBackground),
            ) {
                if (activeComponent != null) {
                    SwingPanel(
                        factory = { activeComponent },
                        modifier = Modifier.fillMaxSize(),
                        background = TerminalSurfaceBackground,
                        update = { synchronizeTerminalInteropBackground(it, terminalPalette) },
                    )
                } else {
                    Text(
                        text = activeSession?.errorMessage ?: "无法启动 PowerShell",
                        modifier = Modifier.padding(12.dp),
                        style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                    )
                }
            }
        }
        if (contextMenuTabId != null) {
            PopupMenu(
                onDismissRequest = {
                    contextMenuTabId = null
                    true
                },
                popupPositionProvider = contextMenuPositionProvider,
                modifier = Modifier.width(152.dp),
            ) {
                selectableItem(selected = false, onClick = {
                    contextMenuTabId = null
                    onAddTab()
                }) { Text(contextMenuLabels[0]) }
                selectableItem(selected = false, onClick = {
                    contextMenuTabId?.let(onCloseTab)
                    contextMenuTabId = null
                }) { Text(contextMenuLabels[1]) }
                selectableItem(selected = false, onClick = {
                    contextMenuTabId?.let(onCloseOtherTabs)
                    contextMenuTabId = null
                }) { Text(contextMenuLabels[2]) }
            }
        }
    }
}

/**
 * 返回终端标签右键菜单的固定操作文案。
 */
internal fun terminalTabContextMenuLabels(): List<String> = listOf("新建终端", "关闭当前终端", "关闭其他终端")
