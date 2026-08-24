@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.agent.app.design.DividerHighlightAxis
import com.agent.app.design.PointerFollowingDividerHighlight
import java.awt.Cursor

/** 桌面左侧任务 Island 的默认占位宽度。 */
internal const val TASK_SIDEBAR_DESKTOP_WIDTH_DP = 292

/** 紧凑桌面左侧任务 Island 的默认占位宽度。 */
internal const val TASK_SIDEBAR_COMPACT_WIDTH_DP = 224

/** 鼠标或键盘可压缩到的左侧 Island 最小宽度。 */
internal const val TASK_SIDEBAR_MIN_WIDTH_DP = 160

/** 左侧 Island 分隔条的可点击和拖动宽度。 */
internal val TASK_SIDEBAR_DIVIDER_WIDTH = 8.dp

/** Islands 正文的统一外沿和分栏视觉间距。 */
internal val ISLANDS_LAYOUT_GAP = 8.dp

/** 返回当前窗口级别下左侧任务 Island 的默认宽度。 */
internal fun taskSidebarDefaultWidthDp(compact: Boolean): Int =
    if (compact) TASK_SIDEBAR_COMPACT_WIDTH_DP else TASK_SIDEBAR_DESKTOP_WIDTH_DP

/**
 * 将左侧 Island 宽度限制为主区域仍可使用的范围。
 *
 * 当窗口已经窄于两个最小宽度之和时，分栏最多占可用空间的一半，保证 Island 与工作区会一起
 * 压缩而不是自动隐藏或互相覆盖。
 */
internal fun clampTaskSidebarWidth(
    requestedWidthPx: Float,
    availableWidthPx: Float,
    minimumSidebarWidthPx: Float,
    minimumWorkspaceWidthPx: Float,
): Float {
    val compressedSidebarWidthPx = availableWidthPx.coerceAtLeast(0f) * 0.5f
    val upperBound = (availableWidthPx - minimumWorkspaceWidthPx)
        .coerceAtLeast(compressedSidebarWidthPx)
    val lowerBound = minimumSidebarWidthPx.coerceAtMost(upperBound)
    return requestedWidthPx.coerceIn(lowerBound, upperBound)
}

/** 返回左侧 Island 开合动画期间工作区应获得的宽度。 */
internal fun workspaceWidthDuringTaskSidebarMotion(
    totalWidthPx: Float,
    sidebarContainerWidthPx: Float,
    progress: Float,
): Float = (totalWidthPx - sidebarContainerWidthPx * progress.coerceIn(0f, 1f)).coerceAtLeast(0f)

/** 返回左侧 Island 开合动画期间分栏实际占用的宽度。 */
internal fun taskSidebarContainerWidthDuringMotion(
    sidebarContainerWidthPx: Float,
    progress: Float,
): Float = sidebarContainerWidthPx * progress.coerceIn(0f, 1f)

/** 返回左侧 Island 在开合期间的整体横向位移，保持内容宽度不参与动画。 */
internal fun taskSidebarTranslationXDuringMotion(
    sidebarContainerWidthPx: Float,
    progress: Float,
): Float {
    val hiddenWidthPx = sidebarContainerWidthPx - taskSidebarContainerWidthDuringMotion(
        sidebarContainerWidthPx,
        progress,
    )
    return if (hiddenWidthPx == 0f) 0f else -hiddenWidthPx
}

/** 以按下时宽度和完整累计位移计算拖拽中的目标宽度，避免逐帧重组丢失位移。 */
internal fun taskSidebarWidthAfterDrag(
    dragStartWidthPx: Float,
    accumulatedDragXPx: Float,
): Float = dragStartWidthPx + accumulatedDragXPx

/**
 * 将左侧任务栏作为真实分栏插入工作区，而不是覆盖在其上方。
 *
 * 打开和关闭时，工作区宽度与 Island 的整体横向位移同步变化。Island 始终以完整宽度测量，再由
 * 父容器裁剪，避免收起时文字随窄宽度重排。宽度只保留在当前组合生命周期，既不持久化也不影响
 * 下次启动。
 */
@Composable
internal fun TaskSidebarSplitLayout(
    visible: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    sidebar: @Composable (Modifier) -> Unit,
    workspace: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val reducedMotion = prefersReducedMotion()
    val motionEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    BoxWithConstraints(modifier = modifier) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val dividerWidthPx = with(density) { TASK_SIDEBAR_DIVIDER_WIDTH.toPx() }
        val availableSidebarWidthPx = (totalWidthPx - dividerWidthPx).coerceAtLeast(0f)
        val minimumSidebarWidthPx = with(density) { TASK_SIDEBAR_MIN_WIDTH_DP.dp.toPx() }
        val minimumWorkspaceWidthPx = with(density) { (if (compact) 240.dp else 360.dp).toPx() }
        val defaultSidebarWidthPx = with(density) { taskSidebarDefaultWidthDp(compact).dp.toPx() }
        var sidebarWidthPx by remember { mutableFloatStateOf(defaultSidebarWidthPx) }
        var readyForMotion by remember { mutableStateOf(false) }
        var resizing by remember { mutableStateOf(false) }
        val effectiveSidebarWidthPx = clampTaskSidebarWidth(
            requestedWidthPx = sidebarWidthPx,
            availableWidthPx = availableSidebarWidthPx,
            minimumSidebarWidthPx = minimumSidebarWidthPx,
            minimumWorkspaceWidthPx = minimumWorkspaceWidthPx,
        )
        val sidebarContainerWidthPx = effectiveSidebarWidthPx + dividerWidthPx
        val motionProgress by animateFloatAsState(
            targetValue = if (readyForMotion && visible) 1f else 0f,
            animationSpec = if (reducedMotion) {
                snap()
            } else {
                tween(
                    durationMillis = if (visible) {
                        TASK_SIDEBAR_ENTER_DURATION_MILLIS
                    } else {
                        TASK_SIDEBAR_EXIT_DURATION_MILLIS
                    },
                    easing = motionEasing,
                )
            },
            label = "task-sidebar-split-motion",
        )
        val layoutProgress = if (resizing) 1f else motionProgress
        val workspaceWidthPx = workspaceWidthDuringTaskSidebarMotion(
            totalWidthPx = totalWidthPx,
            sidebarContainerWidthPx = sidebarContainerWidthPx,
            progress = layoutProgress,
        )
        val sidebarTranslationX = taskSidebarTranslationXDuringMotion(
            sidebarContainerWidthPx = sidebarContainerWidthPx,
            progress = layoutProgress,
        )
        val shouldComposeSidebar = visible || layoutProgress > 0f
        val updateSidebarWidth: (Float) -> Unit = { requestedWidth ->
            sidebarWidthPx = clampTaskSidebarWidth(
                requestedWidthPx = requestedWidth,
                availableWidthPx = availableSidebarWidthPx,
                minimumSidebarWidthPx = minimumSidebarWidthPx,
                minimumWorkspaceWidthPx = minimumWorkspaceWidthPx,
            )
        }

        LaunchedEffect(availableSidebarWidthPx, compact) {
            updateSidebarWidth(sidebarWidthPx)
        }
        LaunchedEffect(Unit) {
            readyForMotion = true
        }

        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            workspace(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(with(density) { workspaceWidthPx.toDp() }),
            )
            if (shouldComposeSidebar) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxHeight()
                        .width(with(density) { sidebarContainerWidthPx.toDp() })
                        .graphicsLayer { translationX = sidebarTranslationX },
                ) {
                    sidebar(
                        Modifier
                            .fillMaxHeight()
                            .width(with(density) { effectiveSidebarWidthPx.toDp() }),
                    )
                    TaskSidebarResizeDivider(
                        sidebarWidthPx = effectiveSidebarWidthPx,
                        onWidthChange = updateSidebarWidth,
                        onResizeStateChange = { resizing = it },
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** 用鼠标和键盘调整左侧任务 Island 宽度的可访问分隔条。 */
@Composable
private fun TaskSidebarResizeDivider(
    sidebarWidthPx: Float,
    onWidthChange: (Float) -> Unit,
    onResizeStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var interaction by remember { mutableStateOf(TaskSidebarDividerInteractionState()) }
    var dragStartWidthPx by remember { mutableFloatStateOf(0f) }
    var accumulatedDragXPx by remember { mutableFloatStateOf(0f) }
    val sidebarWidthDp = with(density) { sidebarWidthPx.toDp().value.toInt() }
    val keyboardStepPx = with(density) { TASK_SIDEBAR_KEYBOARD_STEP_DP.dp.toPx() }
    val currentSidebarWidth by rememberUpdatedState(sidebarWidthPx)
    val currentOnWidthChange by rememberUpdatedState(onWidthChange)
    val currentOnResizeStateChange by rememberUpdatedState(onResizeStateChange)

    Box(
        modifier = modifier
            .width(TASK_SIDEBAR_DIVIDER_WIDTH)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .semantics {
                contentDescription = "调整任务侧栏宽度"
                stateDescription = "当前宽度 $sidebarWidthDp dp；使用左右方向键调整"
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onWidthChange(sidebarWidthPx - keyboardStepPx)
                        true
                    }

                    Key.DirectionRight -> {
                        onWidthChange(sidebarWidthPx + keyboardStepPx)
                        true
                    }

                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Enter) { event ->
                interaction = interaction.copy(
                    hovered = true,
                    pointerY = event.changes.firstOrNull()?.position?.y ?: interaction.pointerY,
                )
            }
            .onPointerEvent(PointerEventType.Move) { event ->
                interaction = interaction.copy(
                    pointerY = event.changes.firstOrNull()?.position?.y ?: interaction.pointerY,
                )
            }
            .onPointerEvent(PointerEventType.Exit) { interaction = interaction.copy(hovered = false) }
            .onPointerEvent(PointerEventType.Press) { event ->
                interaction = interaction.copy(
                    pressed = true,
                    pointerY = event.changes.firstOrNull()?.position?.y ?: interaction.pointerY,
                )
            }
            .onPointerEvent(PointerEventType.Release) { interaction = interaction.copy(pressed = false) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        interaction = interaction.copy(
                            dragging = true,
                            pressed = true,
                            pointerY = position.y,
                        )
                        currentOnResizeStateChange(true)
                        dragStartWidthPx = currentSidebarWidth
                        accumulatedDragXPx = 0f
                    },
                    onDragEnd = {
                        interaction = interaction.copy(dragging = false, pressed = false)
                        currentOnResizeStateChange(false)
                    },
                    onDragCancel = {
                        interaction = interaction.copy(dragging = false, pressed = false)
                        currentOnResizeStateChange(false)
                    },
                ) { change, dragAmount ->
                    change.consume()
                    interaction = interaction.copy(pointerY = change.position.y)
                    accumulatedDragXPx += dragAmount.x
                    currentOnWidthChange(taskSidebarWidthAfterDrag(dragStartWidthPx, accumulatedDragXPx))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        PointerFollowingDividerHighlight(
            axis = DividerHighlightAxis.Vertical,
            pointerPositionPx = interaction.pointerY,
            visible = interaction.hovered || interaction.dragging,
            pressed = interaction.pressed || interaction.dragging,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 保存左侧分隔条当前的短暂指针状态，避免与右侧分隔条重复维护同组 Compose 状态。 */
private data class TaskSidebarDividerInteractionState(
    val hovered: Boolean = false,
    val dragging: Boolean = false,
    val pressed: Boolean = false,
    val pointerY: Float = 0f,
)

private const val TASK_SIDEBAR_ENTER_DURATION_MILLIS = 220
private const val TASK_SIDEBAR_EXIT_DURATION_MILLIS = 180
private const val TASK_SIDEBAR_KEYBOARD_STEP_DP = 16
