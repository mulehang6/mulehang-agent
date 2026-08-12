@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.agent.app.design.DividerHighlightAxis
import com.agent.app.design.PointerFollowingDividerHighlight
import java.awt.Cursor

private const val STACK_VISIBILITY_EPSILON = 0.01f
private const val STACK_PANEL_MOTION_DURATION_MILLIS = 220

/** 退出过渡未结束时继续保留 Island，避免内容在高度收缩前突兀消失。 */
internal fun shouldKeepStackPanelRendered(
    targetVisible: Boolean,
    motionProgress: Float,
): Boolean = targetVisible || motionProgress > STACK_VISIBILITY_EPSILON

/** 设置与终端同时打开时使用的纵向可调整 Island 布局。 */
@Composable
internal fun SettingsTerminalStackLayout(
    settingsVisible: Boolean,
    terminalVisible: Boolean,
    modifier: Modifier = Modifier,
    settings: @Composable (Modifier) -> Unit,
    terminal: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        var settingsRendered by remember { mutableStateOf(settingsVisible) }
        var terminalRendered by remember { mutableStateOf(terminalVisible) }
        val settingsMotionProgress by animateFloatAsState(
            targetValue = if (settingsVisible) 1f else 0f,
            animationSpec = tween(STACK_PANEL_MOTION_DURATION_MILLIS),
            label = "settings-panel-stack-motion",
        )
        val terminalMotionProgress by animateFloatAsState(
            targetValue = if (terminalVisible) 1f else 0f,
            animationSpec = tween(STACK_PANEL_MOTION_DURATION_MILLIS),
            label = "terminal-panel-stack-motion",
        )
        LaunchedEffect(settingsVisible) {
            if (settingsVisible) settingsRendered = true
        }
        LaunchedEffect(terminalVisible) {
            if (terminalVisible) terminalRendered = true
        }
        LaunchedEffect(settingsVisible, settingsMotionProgress) {
            if (!shouldKeepStackPanelRendered(settingsVisible, settingsMotionProgress)) settingsRendered = false
        }
        LaunchedEffect(terminalVisible, terminalMotionProgress) {
            if (!shouldKeepStackPanelRendered(terminalVisible, terminalMotionProgress)) terminalRendered = false
        }

        if (!settingsRendered) {
            terminal(Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        if (!terminalRendered) {
            settings(Modifier.fillMaxSize())
            return@BoxWithConstraints
        }

        val dividerHeight = 10.dp
        val totalHeight = with(density) { maxHeight.toPx() }
        val dividerHeightPx = with(density) { dividerHeight.toPx() }
        val minimumSettingsHeightPx = with(density) { 280.dp.toPx() }
        val minimumTerminalHeightPx = with(density) { 180.dp.toPx() }
        var settingsHeightPx by remember { mutableFloatStateOf((totalHeight - dividerHeightPx) * 0.52f) }
        var dividerHovered by remember { mutableStateOf(false) }
        var dividerDragging by remember { mutableStateOf(false) }
        var dividerPressed by remember { mutableStateOf(false) }
        var dividerPointerX by remember { mutableFloatStateOf(0f) }
        val settingsHeight = clampStackPanelHeight(
            requestedHeightPx = settingsHeightPx,
            availableHeightPx = totalHeight - dividerHeightPx,
            minimumPanelHeightPx = minimumSettingsHeightPx,
            otherMinimumPanelHeightPx = minimumTerminalHeightPx,
        )
        val expandedSettingsHeight = settingsHeight +
                (totalHeight - dividerHeightPx - settingsHeight) * (1f - terminalMotionProgress)

        Column(modifier = Modifier.fillMaxSize().clipToBounds()) {
            settings(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { (expandedSettingsHeight * settingsMotionProgress).coerceAtLeast(0f).toDp() }),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dividerHeight * settingsMotionProgress * terminalMotionProgress)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                    .onPointerEvent(PointerEventType.Enter) { event ->
                        dividerHovered = true
                        dividerPointerX = event.changes.firstOrNull()?.position?.x ?: dividerPointerX
                    }
                    .onPointerEvent(PointerEventType.Move) { event ->
                        dividerPointerX = event.changes.firstOrNull()?.position?.x ?: dividerPointerX
                    }
                    .onPointerEvent(PointerEventType.Exit) { dividerHovered = false }
                    .onPointerEvent(PointerEventType.Press) { event ->
                        dividerPressed = true
                        dividerPointerX = event.changes.firstOrNull()?.position?.x ?: dividerPointerX
                    }
                    .onPointerEvent(PointerEventType.Release) { dividerPressed = false }
                    .pointerInput(totalHeight) {
                        detectDragGestures(
                            onDragStart = { position ->
                                dividerDragging = true
                                dividerPressed = true
                                dividerPointerX = position.x
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
                            dividerPointerX = change.position.x
                            settingsHeightPx = clampStackPanelHeight(
                                requestedHeightPx = settingsHeightPx + dragAmount.y,
                                availableHeightPx = totalHeight - dividerHeightPx,
                                minimumPanelHeightPx = minimumSettingsHeightPx,
                                otherMinimumPanelHeightPx = minimumTerminalHeightPx,
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                PointerFollowingDividerHighlight(
                    axis = DividerHighlightAxis.Horizontal,
                    pointerPositionPx = dividerPointerX,
                    visible = dividerHovered || dividerDragging,
                    pressed = dividerPressed || dividerDragging,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            terminal(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 0.dp),
            )
        }
    }
}

/** 将纵向分栏目标尺寸限制为两个 Island 都可操作的范围。 */
internal fun clampStackPanelHeight(
    requestedHeightPx: Float,
    availableHeightPx: Float,
    minimumPanelHeightPx: Float,
    otherMinimumPanelHeightPx: Float,
): Float {
    val upperBound = (availableHeightPx - otherMinimumPanelHeightPx).coerceAtLeast(0f)
    val lowerBound = minimumPanelHeightPx.coerceAtMost(upperBound)
    return requestedHeightPx.coerceIn(lowerBound, upperBound)
}
