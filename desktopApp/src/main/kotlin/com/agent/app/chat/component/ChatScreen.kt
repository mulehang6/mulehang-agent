@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.agent.app.bootstrap.WindowChromeMode
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppBackground
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.captureWorkspaceBackdrop
import com.agent.app.design.rememberWorkspaceBackdropState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

internal const val SIDEBAR_VISIBLE_BY_DEFAULT = false
/**
 * 按原型重构后的桌面主界面。
 */
@Composable
internal fun WindowScope.ChatScreen(
    state: ChatWindowState,
    desktopWindowState: WindowState,
    windowChromeMode: WindowChromeMode,
    onTitleBarClientPointerEvent: (() -> Unit)?,
    onCloseRequest: () -> Unit,
) {
    val terminalPanel = rememberTerminalPanelController()
    var sidebarVisible by remember { mutableStateOf(SIDEBAR_VISIBLE_BY_DEFAULT) }
    var sidebarVisibleAtPointerPress by remember { mutableStateOf(false) }
    var appFeedback by remember { mutableStateOf<AppFeedbackState?>(null) }
    var appFeedbackToken by remember { mutableStateOf(0L) }
    val showAppFeedback: (AppFeedbackState) -> Unit = { feedback ->
        appFeedbackToken = nextAppFeedbackToken(appFeedbackToken)
        appFeedback = feedback.copy(token = appFeedbackToken)
    }
    var sidebarBounds by remember { mutableStateOf(Rect.Zero) }
    var sidebarOrigin by remember { mutableStateOf(Offset.Zero) }
    val workspaceBackdropState = rememberWorkspaceBackdropState()
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

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(AppBackground),
    ) {
        val compact = isCompactDesktopLayout(maxWidth.value.toInt())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPointerEvent(
                    eventType = PointerEventType.Press,
                    pass = PointerEventPass.Initial,
                ) {
                    sidebarVisibleAtPointerPress = sidebarVisible
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
                        sidebarVisible = false
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
                modifier = Modifier
                    .fillMaxSize()
                    .captureWorkspaceBackdrop(workspaceBackdropState),
            ) {
                ChatHeader(
                    state = state,
                    sidebarVisible = sidebarVisible,
                    onToggleSidebar = { sidebarVisible = !sidebarVisible },
                    windowState = desktopWindowState,
                    windowChromeMode = windowChromeMode,
                    onTitleBarClientPointerEvent = onTitleBarClientPointerEvent,
                    onGlobalFeedback = showAppFeedback,
                    onGlobalPointerPosition = { pointerPosition ->
                        appFeedback?.takeIf { it.anchor != null }?.let { feedback ->
                            appFeedback = feedback.copy(anchor = feedbackToastAnchor(pointerPosition))
                        }
                    },
                    onCloseRequest = onCloseRequest,
                )
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
                    WorkspacePanel(
                        state = state,
                        activeRailView = RightRailGlyph.CODE,
                        filterToolActivityOnly = false,
                        terminalTabs = terminalPanel.tabs,
                        terminalPanelVisible = terminalPanel.visible,
                        terminalSessions = terminalPanel.sessions,
                        onSelectTerminalTab = terminalPanel::select,
                        onAddTerminalTab = {
                            activeConversation?.let { conversation ->
                                terminalPanel.add(conversation.workspacePath)
                            }
                        },
                        onCloseTerminalTab = terminalPanel::close,
                        onCloseOtherTerminalTabs = terminalPanel::closeOthers,
                        onHideTerminalPanel = terminalPanel::hide,
                        compact = compact,
                        modifier = Modifier.weight(1f),
                    )
                    if (!compact) {
                        ToolRail(
                            activeGlyph = resolveActiveRailGlyph(
                                activeRailView = RightRailGlyph.CODE,
                                filterToolActivityOnly = false,
                                terminalVisible = terminalPanel.visible && terminalPanel.tabs.hasActiveTab(),
                            ),
                            onToolClick = { glyph ->
                                if (glyph == RightRailGlyph.TERMINAL) {
                                    if (activeConversation == null) {
                                        showAppFeedback(
                                            AppFeedbackState(message = "请先选择工作区", anchor = null),
                                        )
                                    } else {
                                        terminalPanel.toggleFromRail(activeConversation.workspacePath)
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
                backdropState = workspaceBackdropState,
                sidebarOrigin = sidebarOrigin,
                onSidebarPositioned = { origin, bounds ->
                    sidebarOrigin = origin
                    sidebarBounds = bounds
                },
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
