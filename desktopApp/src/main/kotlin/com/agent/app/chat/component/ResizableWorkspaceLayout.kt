@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.agent.app.design.AppLine
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
 * 将期望的终端高度约束到终端和主区域都可用的范围内。
 */
internal fun clampTerminalHeight(
    requestedHeightPx: Float,
    availableHeightPx: Float,
    minimumTerminalHeightPx: Float,
    minimumWorkspaceHeightPx: Float,
): Float {
    val upperBound = (availableHeightPx - minimumWorkspaceHeightPx).coerceAtLeast(0f)
    val lowerBound = minimumTerminalHeightPx.coerceAtMost(upperBound)
    return requestedHeightPx.coerceIn(lowerBound, upperBound)
}

/** 终端整体移动期间，主工作区为终端窗口让出的当前高度。 */
internal fun workspaceHeightDuringTerminalMotion(
    totalHeightPx: Float,
    terminalContainerHeightPx: Float,
    progress: Float,
): Float = (totalHeightPx - terminalContainerHeightPx * progress).coerceAtLeast(0f)

/** 终端窗口在开合期间的垂直布局位移；零表示已完整停靠在底部。 */
internal fun terminalPanelTranslationYPx(
    terminalContainerHeightPx: Float,
    progress: Float,
): Float = terminalContainerHeightPx * (1f - progress.coerceIn(0f, 1f))

/** 终端开合过程中容器应占据的实际布局高度。 */
internal fun terminalContainerHeightDuringMotion(
    terminalContainerHeightPx: Float,
    progress: Float,
): Float = terminalContainerHeightPx * progress.coerceIn(0f, 1f)

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
        val dividerHeight = 10.dp
        val dividerHeightPx = with(density) { dividerHeight.toPx() }
        val availableHeightPx = with(density) { maxHeight.toPx() } - dividerHeightPx
        val minimumTerminalHeightPx = with(density) { (if (compact) 150.dp else 180.dp).toPx() }
        val minimumWorkspaceHeightPx = with(density) { (if (compact) 220.dp else 280.dp).toPx() }
        val defaultTerminalHeightPx = with(density) { (if (compact) 200.dp else 260.dp).toPx() }
        var terminalHeightPx by remember { mutableFloatStateOf(defaultTerminalHeightPx) }
        var dividerHovered by remember { mutableStateOf(false) }
        var dividerDragging by remember { mutableStateOf(false) }
        var dividerPointerX by remember { mutableFloatStateOf(0f) }
        val effectiveTerminalHeightPx = clampTerminalHeight(
            terminalHeightPx,
            availableHeightPx,
            minimumTerminalHeightPx,
            minimumWorkspaceHeightPx,
        )
        val terminalContainerHeightPx = dividerHeightPx + effectiveTerminalHeightPx
        val terminalMotionProgress by animateFloatAsState(
            targetValue = if (terminalVisible) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (terminalVisible) {
                    TERMINAL_PANEL_ENTER_DURATION_MILLIS
                } else {
                    TERMINAL_PANEL_EXIT_DURATION_MILLIS
                },
            ),
            label = "terminal-panel-window-motion",
        )
        val workspaceHeightPx = workspaceHeightDuringTerminalMotion(
            totalHeightPx = with(density) { maxHeight.toPx() },
            terminalContainerHeightPx = terminalContainerHeightPx,
            progress = terminalMotionProgress,
        )
        val animatedTerminalContainerHeightPx = terminalContainerHeightDuringMotion(
            terminalContainerHeightPx = terminalContainerHeightPx,
            progress = terminalMotionProgress,
        )
        LaunchedEffect(availableHeightPx, compact) {
            terminalHeightPx = clampTerminalHeight(
                terminalHeightPx,
                availableHeightPx,
                minimumTerminalHeightPx,
                minimumWorkspaceHeightPx,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            workspace(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { workspaceHeightPx.toDp() }),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { animatedTerminalContainerHeightPx.toDp() }),
            ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dividerHeight)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                            .onPointerEvent(PointerEventType.Enter) { event ->
                                dividerHovered = true
                                dividerPointerX = event.changes.firstOrNull()?.position?.x ?: dividerPointerX
                            }
                            .onPointerEvent(PointerEventType.Move) { event ->
                                dividerPointerX = event.changes.firstOrNull()?.position?.x ?: dividerPointerX
                            }
                            .onPointerEvent(PointerEventType.Exit) { dividerHovered = false }
                            .pointerInput(availableHeightPx, compact) {
                                detectDragGestures(
                                    onDragStart = { position ->
                                        dividerDragging = true
                                        dividerPointerX = position.x
                                    },
                                    onDragEnd = { dividerDragging = false },
                                    onDragCancel = { dividerDragging = false },
                                ) { change, dragAmount ->
                                    change.consume()
                                    dividerPointerX = change.position.x
                                    terminalHeightPx = clampTerminalHeight(
                                        terminalHeightPx - dragAmount.y,
                                        availableHeightPx,
                                        minimumTerminalHeightPx,
                                        minimumWorkspaceHeightPx,
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        PointerFollowingDividerHighlight(
                            axis = DividerHighlightAxis.Horizontal,
                            pointerPositionPx = dividerPointerX,
                            visible = dividerHovered || dividerDragging,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    terminal(
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { effectiveTerminalHeightPx.toDp() }),
                    )
            }
        }
    }
}
