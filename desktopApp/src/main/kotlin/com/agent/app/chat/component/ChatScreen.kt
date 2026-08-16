@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.AppBackground
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.RightRailGlyph
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import java.nio.file.Path

internal const val SIDEBAR_VISIBLE_BY_DEFAULT = false

/** 当前获得输入焦点的工作区 Island。 */
internal enum class WorkspaceIslandFocus {
    NONE,
    CHAT,
    SETTINGS,
    TERMINAL,
}

/** 点击 Island 外区域后清除右侧 Island 焦点。 */
internal fun workspaceFocusAfterExternalPress(): WorkspaceIslandFocus = WorkspaceIslandFocus.NONE

/** 关闭一个 Island 后，将焦点交给仍可见的另一 Island 或聊天区。 */
internal fun workspaceFocusAfterPanelClosed(
    settingsVisible: Boolean,
    terminalVisible: Boolean,
): WorkspaceIslandFocus = when {
    terminalVisible -> WorkspaceIslandFocus.TERMINAL
    settingsVisible -> WorkspaceIslandFocus.SETTINGS
    else -> WorkspaceIslandFocus.CHAT
}
/**
 * 按原型重构后的桌面主界面。
 */
@Composable
internal fun ChatScreen(
    state: ChatWindowState,
    sidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
    projectRoot: Path?,
    userHome: Path,
    themeMode: DesktopThemeMode,
    onThemeChanged: (DesktopThemeMode) -> Unit,
    onSettingsChanged: () -> Unit,
) {
    val terminalPanel = rememberTerminalPanelController(LocalDesktopPalette.current.terminal)
    var sidebarVisibleAtPointerPress by remember { mutableStateOf(false) }
    var appFeedback by remember { mutableStateOf<AppFeedbackState?>(null) }
    var appFeedbackToken by remember { mutableStateOf(0L) }
    var settingsVisible by remember { mutableStateOf(false) }
    var islandFocus by remember { mutableStateOf(WorkspaceIslandFocus.NONE) }
    val settingsUiState = remember { SettingsPanelUiState() }
    val showAppFeedback: (AppFeedbackState) -> Unit = { feedback ->
        appFeedbackToken = nextAppFeedbackToken(appFeedbackToken)
        appFeedback = feedback.copy(token = appFeedbackToken)
    }
    var sidebarBounds by remember { mutableStateOf(Rect.Zero) }
    val activeConversation = state.ui.activeConversationOrNull

    LaunchedEffect(appFeedback?.token) {
        if (appFeedback != null) {
            delay(2.4.seconds)
            appFeedback = null
        }
    }

    LaunchedEffect(terminalPanel.visible) {
        terminalPanel.closePendingTabAfterExit()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = isCompactDesktopLayout(maxWidth.value.toInt())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .onPointerEvent(
                    eventType = PointerEventType.Press,
                    pass = PointerEventPass.Initial,
                ) {
                    sidebarVisibleAtPointerPress = sidebarVisible
                    islandFocus = workspaceFocusAfterExternalPress()
                }
                .onPointerEvent(
                    eventType = PointerEventType.Release,
                    pass = PointerEventPass.Final,
                ) { event ->
                    val pointerPosition = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    if (
                        shouldDismissSidebar(
                            sidebarVisibleAtPointerPress = sidebarVisibleAtPointerPress,
                            sidebarVisibleOnRelease = sidebarVisible,
                            sidebarBounds = sidebarBounds,
                            pointerPosition = pointerPosition,
                        )
                    ) {
                        onToggleSidebar()
                    }
                }
                .onPointerEvent(PointerEventType.Move) { event ->
                    val pointerPosition = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    appFeedback?.takeIf { feedback -> feedback.anchor != null }?.let { feedback ->
                        appFeedback = feedback.copy(anchor = feedbackToastAnchor(pointerPosition))
                    }
                },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (!compact) {
                        ToolRailPlaceholder(
                            modifier = Modifier
                                .width(TOOL_RAIL_WIDTH_DP.dp)
                                .fillMaxHeight(),
                        )
                    }
                    val terminalVisible = terminalPanel.visible && terminalPanel.tabs.hasActiveTab() && activeConversation != null
                    WorkspacePanel(
                        state = state,
                        activeRailView = RightRailGlyph.CODE,
                        filterToolActivityOnly = false,
                        terminalTabs = terminalPanel.tabs,
                        terminalPanelVisible = terminalPanel.visible,
                        terminalSessions = terminalPanel.sessions,
                        onSelectTerminalTab = terminalPanel::select,
                        onAddTerminalTab = { activeConversation?.let { terminalPanel.add(it.workspacePath) } },
                        onCloseTerminalTab = terminalPanel::close,
                        onCloseOtherTerminalTabs = terminalPanel::closeOthers,
                        onHideTerminalPanel = {
                            terminalPanel.hide()
                            islandFocus = workspaceFocusAfterPanelClosed(
                                settingsVisible = settingsVisible,
                                terminalVisible = false,
                            )
                        },
                        sidePanelVisible = settingsVisible || terminalVisible,
                        sidePanel = { sideModifier ->
                            val settings = @Composable { settingsModifier: Modifier ->
                                SettingsPanel(
                                    projectRoot = projectRoot,
                                    userHome = userHome,
                                    themeMode = themeMode,
                                    focused = islandFocus == WorkspaceIslandFocus.SETTINGS,
                                    onThemeChanged = onThemeChanged,
                                    onFocus = { islandFocus = WorkspaceIslandFocus.SETTINGS },
                                    onClose = {
                                        settingsVisible = false
                                        islandFocus = workspaceFocusAfterPanelClosed(
                                            settingsVisible = false,
                                            terminalVisible = terminalVisible,
                                        )
                                    },
                                    onSettingsSaved = onSettingsChanged,
                                    uiState = settingsUiState,
                                    modifier = settingsModifier,
                                )
                            }
                            val terminal = @Composable { terminalModifier: Modifier ->
                                EmbeddedTerminalPanel(
                                    tabs = terminalPanel.tabs,
                                    sessions = terminalPanel.sessions,
                                    onSelectTab = terminalPanel::select,
                                    onAddTab = { activeConversation?.let { terminalPanel.add(it.workspacePath) } },
                                    onCloseTab = terminalPanel::close,
                                    onCloseOtherTabs = terminalPanel::closeOthers,
                                    onHidePanel = {
                                        terminalPanel.hide()
                                        islandFocus = workspaceFocusAfterPanelClosed(
                                            settingsVisible = settingsVisible,
                                            terminalVisible = false,
                                        )
                                    },
                                    onFocus = { islandFocus = WorkspaceIslandFocus.TERMINAL },
                                    modifier = terminalModifier,
                                )
                            }
                            SettingsTerminalStackLayout(
                                settingsVisible = settingsVisible,
                                terminalVisible = terminalVisible,
                                modifier = sideModifier,
                                settings = settings,
                                terminal = terminal,
                            )
                        },
                        compact = compact,
                        modifier = Modifier.weight(1f),
                    )
                    if (!compact) {
                        ToolRail(
                            activeGlyph = when (islandFocus) {
                                WorkspaceIslandFocus.SETTINGS -> RightRailGlyph.SETTINGS
                                WorkspaceIslandFocus.TERMINAL -> RightRailGlyph.TERMINAL
                                WorkspaceIslandFocus.NONE,
                                WorkspaceIslandFocus.CHAT,
                                -> RightRailGlyph.CODE
                            },
                            onToolClick = { glyph ->
                                if (glyph == RightRailGlyph.SETTINGS) {
                                    settingsVisible = !settingsVisible
                                    islandFocus = if (settingsVisible) WorkspaceIslandFocus.SETTINGS else WorkspaceIslandFocus.CHAT
                                } else if (glyph == RightRailGlyph.TERMINAL) {
                                    if (activeConversation == null) {
                                        showAppFeedback(
                                            AppFeedbackState(message = "请先选择工作区", anchor = null),
                                        )
                                    } else {
                                        val openingTerminal = !terminalVisible
                                        terminalPanel.toggleFromRail(activeConversation.workspacePath)
                                        islandFocus = when {
                                            openingTerminal -> WorkspaceIslandFocus.TERMINAL
                                            settingsVisible -> WorkspaceIslandFocus.SETTINGS
                                            else -> WorkspaceIslandFocus.CHAT
                                        }
                                        appFeedback = null
                                    }
                                }
                            },
                            modifier = Modifier
                                .width(TOOL_RAIL_WIDTH_DP.dp)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
            ChatSidebarOverlay(
                state = state,
                compact = compact,
                visible = sidebarVisible,
                onSidebarPositioned = { bounds -> sidebarBounds = bounds },
            )
        }
        appFeedback?.let { feedback ->
            AppFeedbackOverlay(feedback)
        }
    }
}

/**
 * 判断窗口是否应采用收敛侧栏与工具区的紧凑布局。
 */
internal fun isCompactDesktopLayout(widthDp: Int): Boolean = widthDp < 980

/**
 * 返回 Air 浮动侧栏宽度；该值不参与主工作区宽度分配。
 */
internal fun airSidebarWidthDp(compact: Boolean): Int = if (compact) 224 else 292

/**
 * 返回侧栏完全移出左侧窗口边界时的水平偏移。
 */
internal fun sidebarHiddenOffsetPx(
    sidebarWidthPx: Int,
    edgeGapPx: Int,
): Int = -(sidebarWidthPx + edgeGapPx)

/**
 * 判断指针释放位置是否位于已打开侧栏之外。
 */
internal fun shouldDismissSidebar(
    sidebarVisibleAtPointerPress: Boolean,
    sidebarVisibleOnRelease: Boolean,
    sidebarBounds: Rect,
    pointerPosition: Offset,
): Boolean =
    sidebarVisibleAtPointerPress &&
            sidebarVisibleOnRelease &&
            !sidebarBounds.contains(pointerPosition)

/**
 * 解析右侧工具栏当前应高亮的按钮。
 */
internal fun resolveActiveRailGlyph(
    activeRailView: RightRailGlyph,
    filterToolActivityOnly: Boolean,
    terminalVisible: Boolean,
): RightRailGlyph = when {
    terminalVisible -> RightRailGlyph.TERMINAL
    filterToolActivityOnly -> RightRailGlyph.FILTER
    else -> activeRailView
}
