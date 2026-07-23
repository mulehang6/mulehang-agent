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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
    var terminalVisible by remember { mutableStateOf(false) }
    var sidebarVisible by remember { mutableStateOf(SIDEBAR_VISIBLE_BY_DEFAULT) }
    var sidebarVisibleAtPointerPress by remember { mutableStateOf(false) }
    var railFeedback by remember { mutableStateOf<String?>(null) }
    var sidebarBounds by remember { mutableStateOf(Rect.Zero) }
    var sidebarOrigin by remember { mutableStateOf(Offset.Zero) }
    val workspaceBackdropState = rememberWorkspaceBackdropState()
    val activeConversation = state.ui.activeConversationOrNull

    LaunchedEffect(railFeedback) {
        if (railFeedback != null) {
            delay(2.4.seconds)
            railFeedback = null
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
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .captureWorkspaceBackdrop(workspaceBackdropState),
            ) {
                ChatHeader(
                    sidebarVisible = sidebarVisible,
                    onToggleSidebar = { sidebarVisible = !sidebarVisible },
                    windowState = desktopWindowState,
                    windowChromeMode = windowChromeMode,
                    onTitleBarClientPointerEvent = onTitleBarClientPointerEvent,
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
                        terminalVisible = terminalVisible,
                        onCloseTerminal = { terminalVisible = false },
                        compact = compact,
                        modifier = Modifier.weight(1f),
                    )
                    if (!compact) {
                        ToolRail(
                            activeGlyph = resolveActiveRailGlyph(
                                activeRailView = RightRailGlyph.CODE,
                                filterToolActivityOnly = false,
                                terminalVisible = terminalVisible,
                            ),
                            onToolClick = { glyph ->
                                if (glyph == RightRailGlyph.TERMINAL) {
                                    if (activeConversation == null) {
                                        railFeedback = "请先选择工作区"
                                    } else {
                                        terminalVisible = !terminalVisible
                                        railFeedback = null
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
        AnimatedContent(
            targetState = railFeedback,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = if (compact) 16.dp else 54.dp),
        ) { message ->
            if (message != null) {
                RailFeedbackCard(message = message)
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
