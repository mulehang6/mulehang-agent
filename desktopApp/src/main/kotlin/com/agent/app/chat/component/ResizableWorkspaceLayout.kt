package com.agent.app.chat.component

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agent.app.design.AppLine
import com.agent.app.design.AppMuted
import java.awt.Cursor

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
        val effectiveTerminalHeightPx = clampTerminalHeight(
            terminalHeightPx,
            availableHeightPx,
            minimumTerminalHeightPx,
            minimumWorkspaceHeightPx,
        )
        LaunchedEffect(availableHeightPx, compact) {
            terminalHeightPx = clampTerminalHeight(
                terminalHeightPx,
                availableHeightPx,
                minimumTerminalHeightPx,
                minimumWorkspaceHeightPx,
            )
        }

        if (!terminalVisible) {
            workspace(Modifier.fillMaxSize())
            return@BoxWithConstraints
        }

        Column(modifier = Modifier.fillMaxSize()) {
            workspace(Modifier.weight(1f).fillMaxWidth())
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dividerHeight)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                    .pointerInput(availableHeightPx, compact) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.12f)
                        .height(3.dp)
                        .background(AppMuted.copy(alpha = 0.52f), RoundedCornerShape(50)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppLine.copy(alpha = 0.25f)),
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
