@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agent.app.design.DividerHighlightAxis
import com.agent.app.design.PointerFollowingDividerHighlight
import java.awt.Cursor

/** 终端面板打开时的展开与淡入时长。 */
internal const val TERMINAL_PANEL_ENTER_DURATION_MILLIS = 420

/** 终端面板收起或关闭时的收缩与淡出时长。 */
internal const val TERMINAL_PANEL_EXIT_DURATION_MILLIS = 360

/** 退出动画完成后额外等待一帧再释放最后一个终端会话。 */
internal const val TERMINAL_PANEL_CLOSE_DELAY_MILLIS = 32L

/**
 * 将期望的终端宽度约束到终端和主区域都可用的范围内。
 */
internal fun clampTerminalWidth(
    requestedWidthPx: Float,
    availableWidthPx: Float,
    minimumTerminalWidthPx: Float,
    minimumWorkspaceWidthPx: Float,
): Float {
    val upperBound = (availableWidthPx - minimumWorkspaceWidthPx).coerceAtLeast(0f)
    val lowerBound = minimumTerminalWidthPx.coerceAtMost(upperBound)
    return requestedWidthPx.coerceIn(lowerBound, upperBound)
}

/** 终端整体移动期间，主工作区为右侧终端让出的当前宽度。 */
internal fun workspaceWidthDuringTerminalMotion(
    totalWidthPx: Float,
    terminalContainerWidthPx: Float,
    progress: Float,
): Float = (totalWidthPx - terminalContainerWidthPx * progress).coerceAtLeast(0f)

/** 终端开合过程中容器应占据的实际布局高度。 */
internal fun terminalContainerWidthDuringMotion(
    terminalContainerWidthPx: Float,
    progress: Float,
): Float = terminalContainerWidthPx * progress.coerceIn(0f, 1f)

/** 首次组合先保持右侧区域收起一帧，确保设置和终端都能触发入场动画。 */
internal fun sidePanelMotionTarget(isReadyForMotion: Boolean, panelVisible: Boolean): Float =
    if (isReadyForMotion && panelVisible) 1f else 0f

/**
 * 主工作区与终端之间的桌面分割布局。
 */
@Composable
internal fun ResizableWorkspaceLayout(
    terminalVisible: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    workspace: @Composable (Modifier) -> Unit,
    terminal: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val dividerWidth = 8.dp
        val dividerWidthPx = with(density) { dividerWidth.toPx() }
        val availableWidthPx = with(density) { maxWidth.toPx() } - dividerWidthPx
        val minimumTerminalWidthPx = with(density) { (if (compact) 260.dp else 320.dp).toPx() }
        val minimumWorkspaceWidthPx = with(density) { (if (compact) 300.dp else 420.dp).toPx() }
        val defaultTerminalWidthPx = availableWidthPx * 0.5f
        var terminalWidthPx by remember { mutableFloatStateOf(defaultTerminalWidthPx) }
        var dividerHovered by remember { mutableStateOf(false) }
        var dividerDragging by remember { mutableStateOf(false) }
        var dividerPressed by remember { mutableStateOf(false) }
        var dividerPointerY by remember { mutableFloatStateOf(0f) }
        var readyForSidePanelMotion by remember { mutableStateOf(false) }
        val effectiveTerminalWidthPx = clampTerminalWidth(
            terminalWidthPx,
            availableWidthPx,
            minimumTerminalWidthPx,
            minimumWorkspaceWidthPx,
        )
        val terminalContainerWidthPx = dividerWidthPx + effectiveTerminalWidthPx
        val terminalMotionProgress by animateFloatAsState(
            targetValue = sidePanelMotionTarget(
                isReadyForMotion = readyForSidePanelMotion,
                panelVisible = terminalVisible,
            ),
            animationSpec = tween(
                durationMillis = if (terminalVisible) {
                    TERMINAL_PANEL_ENTER_DURATION_MILLIS
                } else {
                    TERMINAL_PANEL_EXIT_DURATION_MILLIS
                },
            ),
            label = "terminal-panel-window-motion",
        )
        val workspaceWidthPx = workspaceWidthDuringTerminalMotion(
            totalWidthPx = with(density) { maxWidth.toPx() },
            terminalContainerWidthPx = terminalContainerWidthPx,
            progress = terminalMotionProgress,
        )
        val animatedTerminalContainerWidthPx = terminalContainerWidthDuringMotion(
            terminalContainerWidthPx = terminalContainerWidthPx,
            progress = terminalMotionProgress,
        )
        LaunchedEffect(availableWidthPx, compact) {
            terminalWidthPx = clampTerminalWidth(
                terminalWidthPx,
                availableWidthPx,
                minimumTerminalWidthPx,
                minimumWorkspaceWidthPx,
            )
        }
        LaunchedEffect(Unit) {
            readyForSidePanelMotion = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            workspace(
                Modifier
                    .fillMaxHeight()
                    .width(with(density) { workspaceWidthPx.toDp() }),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(with(density) { animatedTerminalContainerWidthPx.toDp() }),
            ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(dividerWidth)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                            .onPointerEvent(PointerEventType.Enter) { event ->
                                dividerHovered = true
                                dividerPointerY = event.changes.firstOrNull()?.position?.y ?: dividerPointerY
                            }
                            .onPointerEvent(PointerEventType.Move) { event ->
                                dividerPointerY = event.changes.firstOrNull()?.position?.y ?: dividerPointerY
                            }
                            .onPointerEvent(PointerEventType.Exit) { dividerHovered = false }
                            .onPointerEvent(PointerEventType.Press) { event ->
                                dividerPressed = true
                                dividerPointerY = event.changes.firstOrNull()?.position?.y ?: dividerPointerY
                            }
                            .onPointerEvent(PointerEventType.Release) { dividerPressed = false }
                            .pointerInput(availableWidthPx, compact) {
                                detectDragGestures(
                                    onDragStart = { position ->
                                        dividerDragging = true
                                        dividerPressed = true
                                        dividerPointerY = position.y
                                    },
                                    onDragEnd = {
                                        dividerDragging = false
                                        dividerPressed = false
                                    },
                                    onDragCancel = {
                                        dividerDragging = false
                                        dividerPressed = false
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    dividerPointerY = change.position.y
                                    terminalWidthPx = clampTerminalWidth(
                                        terminalWidthPx - dragAmount.x,
                                        availableWidthPx,
                                        minimumTerminalWidthPx,
                                        minimumWorkspaceWidthPx,
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        PointerFollowingDividerHighlight(
                            axis = DividerHighlightAxis.Vertical,
                            pointerPositionPx = dividerPointerY,
                            visible = dividerHovered || dividerDragging,
                            pressed = dividerPressed || dividerDragging,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    terminal(
                        Modifier
                            .fillMaxHeight()
                            .width(with(density) { effectiveTerminalWidthPx.toDp() }),
                    )
            }
        }
    }
}
