@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.agent.app.bootstrap.WindowChromeMode
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AirSidebarSurface
import com.agent.app.design.AppBackground
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.captureWorkspaceBackdrop
import com.agent.app.design.rememberWorkspaceBackdropState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal const val SIDEBAR_VISIBLE_BY_DEFAULT = false
internal const val APP_FEEDBACK_BOTTOM_PADDING_DP = 24
private const val APP_FEEDBACK_POINTER_OFFSET_DP = 12

/** 应用级反馈及其可选的鼠标锚点。 */
internal data class AppFeedbackState(
    val message: String,
    val anchor: Offset?,
    val token: Long = 0L,
)

/** 保留可用的鼠标位置；为空时由全局 toast 使用默认底部位置。 */
internal fun feedbackToastAnchor(pointerPosition: Offset?): Offset? = pointerPosition

/** 为每次反馈分配递增标识，确保重复文案也会重新开始展示计时。 */
internal fun nextAppFeedbackToken(currentToken: Long): Long = currentToken + 1L

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
    var terminalTabs by remember { mutableStateOf(TerminalTabsState()) }
    var terminalPanelVisible by remember { mutableStateOf(false) }
    val terminalSessions = remember { TerminalSessionStore() }
    var sidebarVisible by remember { mutableStateOf(SIDEBAR_VISIBLE_BY_DEFAULT) }
    var sidebarVisibleAtPointerPress by remember { mutableStateOf(false) }
    var appFeedback by remember { mutableStateOf<AppFeedbackState?>(null) }
    var appFeedbackToken by remember { mutableStateOf(0L) }
    var pendingTerminalTabCloseId by remember { mutableStateOf<Long?>(null) }
    val showAppFeedback: (AppFeedbackState) -> Unit = { feedback ->
        appFeedbackToken = nextAppFeedbackToken(appFeedbackToken)
        appFeedback = feedback.copy(token = appFeedbackToken)
    }
    var sidebarBounds by remember { mutableStateOf(Rect.Zero) }
    var sidebarOrigin by remember { mutableStateOf(Offset.Zero) }
    val workspaceBackdropState = rememberWorkspaceBackdropState()
    val activeConversation = state.ui.activeConversationOrNull

    DisposableEffect(terminalSessions) {
        onDispose { terminalSessions.closeAll() }
    }

    LaunchedEffect(appFeedback?.token) {
        if (appFeedback != null) {
            delay(2.4.seconds)
            appFeedback = null
        }
    }

    LaunchedEffect(pendingTerminalTabCloseId, terminalPanelVisible) {
        val tabId = pendingTerminalTabCloseId
        if (tabId != null && !terminalPanelVisible) {
            delay((TERMINAL_PANEL_EXIT_DURATION_MILLIS.toLong() + TERMINAL_PANEL_CLOSE_DELAY_MILLIS).milliseconds)
            if (pendingTerminalTabCloseId == tabId && !terminalPanelVisible) {
                terminalSessions.close(tabId)
                terminalTabs = terminalTabs.resetAfterTerminalWindowClosed()
                pendingTerminalTabCloseId = null
            }
        }
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
                        terminalTabs = terminalTabs,
                        terminalPanelVisible = terminalPanelVisible,
                        terminalSessions = terminalSessions,
                        onSelectTerminalTab = { tabId ->
                            terminalTabs = terminalTabs.selectTab(tabId)
                        },
                        onAddTerminalTab = {
                            activeConversation?.let { conversation ->
                                pendingTerminalTabCloseId = null
                                terminalTabs = terminalTabs.addTab(conversation.workspacePath)
                                terminalSessions.create(terminalTabs.tabs.last())
                                terminalPanelVisible = true
                            }
                        },
                        onCloseTerminalTab = { tabId ->
                            if (terminalPanelVisible && shouldDeferTerminalTabClose(terminalTabs)) {
                                pendingTerminalTabCloseId = tabId
                                terminalPanelVisible = false
                            } else {
                                terminalSessions.close(tabId)
                                terminalTabs = terminalTabs.closeTab(tabId)
                                terminalPanelVisible = terminalTabs.hasActiveTab()
                            }
                        },
                        onCloseOtherTerminalTabs = { keptTabId ->
                            terminalSessions.closeAllExcept(keptTabId)
                            terminalTabs = terminalTabs.retainOnly(keptTabId)
                        },
                        compact = compact,
                        modifier = Modifier.weight(1f),
                    )
                    if (!compact) {
                        ToolRail(
                            activeGlyph = resolveActiveRailGlyph(
                                activeRailView = RightRailGlyph.CODE,
                                filterToolActivityOnly = false,
                                terminalVisible = terminalPanelVisible && terminalTabs.hasActiveTab(),
                            ),
                            onToolClick = { glyph ->
                                if (glyph == RightRailGlyph.TERMINAL) {
                                    if (activeConversation == null) {
                                        showAppFeedback(
                                            AppFeedbackState(message = "请先选择工作区", anchor = null),
                                        )
                                    } else {
                                        when (
                                            terminalRailAction(
                                                panelVisible = terminalPanelVisible,
                                                hasActiveTab = terminalTabs.hasActiveTab(),
                                            )
                                        ) {
                                            TerminalRailAction.CREATE_AND_SHOW -> {
                                                pendingTerminalTabCloseId = null
                                                terminalTabs = terminalTabs.addTab(activeConversation.workspacePath)
                                                val newTerminalTab = terminalTabs.tabs.last()
                                                terminalSessions.create(newTerminalTab)
                                                terminalPanelVisible = true
                                            }

                                            TerminalRailAction.SHOW -> {
                                                pendingTerminalTabCloseId = null
                                                terminalPanelVisible = true
                                            }
                                            TerminalRailAction.HIDE -> terminalPanelVisible = false
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
            val sidebarEdgeGapDp = if (compact) 8.dp else 12.dp
            val sidebarEdgeGapPx = with(LocalDensity.current) { sidebarEdgeGapDp.roundToPx() }
            AnimatedVisibility(
                visible = sidebarVisible,
                enter = slideInHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialOffsetX = { width -> sidebarHiddenOffsetPx(width, sidebarEdgeGapPx) },
                ),
                exit = slideOutHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    targetOffsetX = { width -> sidebarHiddenOffsetPx(width, sidebarEdgeGapPx) },
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = sidebarEdgeGapDp,
                        top = 56.dp,
                        bottom = sidebarEdgeGapDp,
                    )
                    .width(airSidebarWidthDp(compact).dp)
                    .fillMaxHeight(),
            ) {
                AirSidebarSurface(
                    backdropState = workspaceBackdropState,
                    sidebarOrigin = sidebarOrigin,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            sidebarOrigin = coordinates.positionInRoot()
                            sidebarBounds = coordinates.boundsInRoot()
                        },
                ) {
                    TaskSidebar(
                        state = state,
                        compact = compact,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        appFeedback?.let { feedback ->
            val anchor = feedbackToastAnchor(feedback.anchor)
            val pointerOffsetPx = with(LocalDensity.current) { APP_FEEDBACK_POINTER_OFFSET_DP.dp.toPx() }
            AnimatedContent(
                targetState = feedback.message,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                modifier = if (anchor == null) {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = APP_FEEDBACK_BOTTOM_PADDING_DP.dp)
                } else {
                    Modifier.align(Alignment.TopStart)
                },
            ) { message ->
                AppFeedbackToast(
                    message = message,
                    modifier = if (anchor == null) {
                        Modifier
                    } else {
                        Modifier.graphicsLayer {
                            translationX = anchor.x + pointerOffsetPx
                            translationY = anchor.y + pointerOffsetPx
                        }
                    },
                )
            }
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
