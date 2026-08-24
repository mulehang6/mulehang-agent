@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** 设置与终端首次并排显示时，设置面板占据的高度比例。 */
internal const val DEFAULT_SETTINGS_TERMINAL_SPLIT_FRACTION = 0.52f

/** 设置或终端加入已打开的右侧区域时使用的空间过渡时长。 */
internal const val SETTINGS_TERMINAL_PANEL_ENTER_DURATION_MILLIS = 220

/** 设置或终端离开仍打开的右侧区域时使用的空间过渡时长。 */
internal const val SETTINGS_TERMINAL_PANEL_EXIT_DURATION_MILLIS = 180

/** 设置与终端之间分隔条的可拖拽命中高度。 */
internal val SETTINGS_TERMINAL_DIVIDER_HEIGHT = 8.dp

/** 保存可中断分隔条交互在重组期间的局部状态。 */
private class SettingsTerminalDividerInteractionState {
    var hovered by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var pressed by mutableStateOf(false)
    var pointerX by mutableFloatStateOf(0f)
}

/** 设置与终端四种可达布局状态。 */
internal enum class SettingsTerminalLayoutMode {
    HIDDEN,
    SETTINGS,
    TERMINAL,
    SPLIT,
    ;
}

/** 根据两个 Island 的可见性解析唯一的目标布局状态。 */
internal fun settingsTerminalLayoutMode(
    settingsVisible: Boolean,
    terminalVisible: Boolean,
): SettingsTerminalLayoutMode = when {
    settingsVisible && terminalVisible -> SettingsTerminalLayoutMode.SPLIT
    settingsVisible -> SettingsTerminalLayoutMode.SETTINGS
    terminalVisible -> SettingsTerminalLayoutMode.TERMINAL
    else -> SettingsTerminalLayoutMode.HIDDEN
}

/** 返回目标布局进入或完全退出右侧区域时的空间过渡时长。 */
internal fun settingsTerminalPanelTransitionDuration(targetMode: SettingsTerminalLayoutMode): Int =
    if (targetMode == SettingsTerminalLayoutMode.HIDDEN) {
        SETTINGS_TERMINAL_PANEL_EXIT_DURATION_MILLIS
    } else {
        SETTINGS_TERMINAL_PANEL_ENTER_DURATION_MILLIS
    }

/** 判断布局状态是否需要设置 Island。 */
internal fun SettingsTerminalLayoutMode.showsSettings(): Boolean =
    this == SettingsTerminalLayoutMode.SETTINGS || this == SettingsTerminalLayoutMode.SPLIT

/** 判断布局状态是否需要终端 Island。 */
internal fun SettingsTerminalLayoutMode.showsTerminal(): Boolean =
    this == SettingsTerminalLayoutMode.TERMINAL || this == SettingsTerminalLayoutMode.SPLIT

/** 判断布局状态是否需要显示并启用上下分隔条。 */
internal fun SettingsTerminalLayoutMode.showsDivider(): Boolean = this == SettingsTerminalLayoutMode.SPLIT

/** 返回设置 Island 在可分配高度中的目标比例。 */
internal fun settingsTerminalSettingsShare(
    mode: SettingsTerminalLayoutMode,
    splitFraction: Float,
): Float = when (mode) {
    SettingsTerminalLayoutMode.HIDDEN,
    SettingsTerminalLayoutMode.TERMINAL,
    -> 0f

    SettingsTerminalLayoutMode.SETTINGS -> 1f
    SettingsTerminalLayoutMode.SPLIT -> splitFraction.coerceIn(0f, 1f)
}

/** 拖拽分隔条时直接采用目标比例，避免动画插值造成光标与布局脱节。 */
internal fun settingsTerminalRenderedShare(
    desiredShare: Float,
    animatedShare: Float,
    dividerDragging: Boolean,
): Float = if (dividerDragging) desiredShare else animatedShare

/** 将用户拖动后的设置比例限制在两个面板的最小可用高度内。 */
internal fun clampSettingsTerminalSplitFraction(
    requestedFraction: Float,
    availableHeightPx: Float,
    minimumSettingsHeightPx: Float,
    minimumTerminalHeightPx: Float,
): Float {
    if (availableHeightPx <= 0f || minimumSettingsHeightPx + minimumTerminalHeightPx > availableHeightPx) {
        return DEFAULT_SETTINGS_TERMINAL_SPLIT_FRACTION
    }
    val lowerBound = (minimumSettingsHeightPx / availableHeightPx).coerceIn(0f, 1f)
    val upperBound = (1f - minimumTerminalHeightPx / availableHeightPx).coerceIn(lowerBound, 1f)
    return requestedFraction.coerceIn(lowerBound, upperBound)
}

/**
 * 使用可中断的空间过渡承载设置与终端，并在双面板状态提供纵向可调整分栏。
 */
@Composable
internal fun SettingsTerminalStackLayout(
    settingsVisible: Boolean,
    terminalVisible: Boolean,
    modifier: Modifier = Modifier,
    settings: @Composable (Modifier) -> Unit,
    terminal: @Composable (Modifier) -> Unit,
) {
    val targetMode = settingsTerminalLayoutMode(settingsVisible, terminalVisible)
    var retainedMode by remember { mutableStateOf(SettingsTerminalLayoutMode.HIDDEN) }

    LaunchedEffect(targetMode) {
        if (targetMode == SettingsTerminalLayoutMode.HIDDEN) {
            delay(SETTINGS_TERMINAL_PANEL_EXIT_DURATION_MILLIS.milliseconds)
            retainedMode = SettingsTerminalLayoutMode.HIDDEN
        } else {
            retainedMode = targetMode
        }
    }

    val visualMode = if (targetMode == SettingsTerminalLayoutMode.HIDDEN) retainedMode else targetMode
    val panelTransitionDuration = settingsTerminalPanelTransitionDuration(targetMode)
    val panelEnter = fadeIn(
        animationSpec = tween(
            durationMillis = SETTINGS_TERMINAL_PANEL_ENTER_DURATION_MILLIS,
            easing = FastOutSlowInEasing,
        ),
    )
    val panelExit = fadeOut(
        animationSpec = tween(
            durationMillis = SETTINGS_TERMINAL_PANEL_EXIT_DURATION_MILLIS,
            easing = FastOutSlowInEasing,
        ),
    )

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        val dividerHeightPx = with(density) { SETTINGS_TERMINAL_DIVIDER_HEIGHT.toPx() }
        val minimumSettingsHeightPx = with(density) { 280.dp.toPx() }
        val minimumTerminalHeightPx = with(density) { 180.dp.toPx() }
        val totalHeightPx = with(density) { maxHeight.toPx() }
        val splitAvailableHeightPx = (totalHeightPx - dividerHeightPx).coerceAtLeast(0f)
        var splitFraction by remember { mutableFloatStateOf(DEFAULT_SETTINGS_TERMINAL_SPLIT_FRACTION) }
        val dividerInteraction = remember { SettingsTerminalDividerInteractionState() }
        val desiredSettingsShare = settingsTerminalSettingsShare(visualMode, splitFraction)
        val desiredDividerVisibility = if (visualMode.showsDivider()) 1f else 0f
        val animatedSettingsShare by animateFloatAsState(
            targetValue = desiredSettingsShare,
            animationSpec = if (dividerInteraction.dragging) {
                snap()
            } else {
                tween(durationMillis = panelTransitionDuration, easing = FastOutSlowInEasing)
            },
            label = "settings-terminal-settings-share",
        )
        val animatedDividerVisibility by animateFloatAsState(
            targetValue = desiredDividerVisibility,
            animationSpec = tween(durationMillis = panelTransitionDuration, easing = FastOutSlowInEasing),
            label = "settings-terminal-divider-visibility",
        )

        LaunchedEffect(splitAvailableHeightPx) {
            splitFraction = clampSettingsTerminalSplitFraction(
                requestedFraction = splitFraction,
                availableHeightPx = splitAvailableHeightPx,
                minimumSettingsHeightPx = minimumSettingsHeightPx,
                minimumTerminalHeightPx = minimumTerminalHeightPx,
            )
        }

        val renderedSettingsShare = settingsTerminalRenderedShare(
            desiredShare = desiredSettingsShare,
            animatedShare = animatedSettingsShare,
            dividerDragging = dividerInteraction.dragging,
        )
        val renderedDividerVisibility = if (dividerInteraction.dragging) desiredDividerVisibility else animatedDividerVisibility
        val renderedDividerHeightPx = dividerHeightPx * renderedDividerVisibility
        val renderedAvailableHeightPx = (totalHeightPx - renderedDividerHeightPx).coerceAtLeast(0f)
        val settingsHeightPx = renderedAvailableHeightPx * renderedSettingsShare
        val terminalHeightPx = (renderedAvailableHeightPx - settingsHeightPx).coerceAtLeast(0f)
        val terminalY = settingsHeightPx + renderedDividerHeightPx
        val settingsVisibleForAnimation = targetMode.showsSettings() ||
            (targetMode == SettingsTerminalLayoutMode.HIDDEN && retainedMode.showsSettings())
        val terminalVisibleForAnimation = targetMode.showsTerminal() ||
            (targetMode == SettingsTerminalLayoutMode.HIDDEN && retainedMode.showsTerminal())

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = settingsVisibleForAnimation,
                enter = panelEnter,
                exit = panelExit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(with(density) { settingsHeightPx.toDp() }),
            ) {
                settings(Modifier.fillMaxSize())
            }
            if (renderedDividerHeightPx > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(with(density) { renderedDividerHeightPx.toDp() })
                        .offset(y = with(density) { settingsHeightPx.toDp() })
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                        .onPointerEvent(PointerEventType.Enter) { event ->
                            dividerInteraction.hovered = true
                            dividerInteraction.pointerX = event.changes.firstOrNull()?.position?.x ?: dividerInteraction.pointerX
                        }
                        .onPointerEvent(PointerEventType.Move) { event ->
                            dividerInteraction.pointerX = event.changes.firstOrNull()?.position?.x ?: dividerInteraction.pointerX
                        }
                        .onPointerEvent(PointerEventType.Exit) { dividerInteraction.hovered = false }
                        .onPointerEvent(PointerEventType.Press) { event ->
                            dividerInteraction.pressed = true
                            dividerInteraction.pointerX = event.changes.firstOrNull()?.position?.x ?: dividerInteraction.pointerX
                        }
                        .onPointerEvent(PointerEventType.Release) { dividerInteraction.pressed = false }
                        .pointerInput(splitAvailableHeightPx, targetMode) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    dividerInteraction.dragging = true
                                    dividerInteraction.pressed = true
                                    dividerInteraction.pointerX = position.x
                                },
                                onDragEnd = {
                                    dividerInteraction.dragging = false
                                    dividerInteraction.pressed = false
                                },
                                onDragCancel = {
                                    dividerInteraction.dragging = false
                                    dividerInteraction.pressed = false
                                },
                            ) { change, dragAmount ->
                                if (targetMode != SettingsTerminalLayoutMode.SPLIT) return@detectDragGestures
                                change.consume()
                                dividerInteraction.pointerX = change.position.x
                                splitFraction = clampSettingsTerminalSplitFraction(
                                    requestedFraction = splitFraction + dragAmount.y / splitAvailableHeightPx,
                                    availableHeightPx = splitAvailableHeightPx,
                                    minimumSettingsHeightPx = minimumSettingsHeightPx,
                                    minimumTerminalHeightPx = minimumTerminalHeightPx,
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PointerFollowingDividerHighlight(
                        axis = DividerHighlightAxis.Horizontal,
                        pointerPositionPx = dividerInteraction.pointerX,
                        visible = targetMode.showsDivider() && (dividerInteraction.hovered || dividerInteraction.dragging),
                        pressed = dividerInteraction.pressed || dividerInteraction.dragging,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            AnimatedVisibility(
                visible = terminalVisibleForAnimation,
                enter = panelEnter,
                exit = panelExit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(with(density) { terminalHeightPx.toDp() })
                    .offset(y = with(density) { terminalY.toDp() }),
            ) {
                terminal(Modifier.fillMaxSize())
            }
        }
    }
}
