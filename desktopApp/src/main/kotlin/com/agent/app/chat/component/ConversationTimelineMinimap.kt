@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import com.agent.app.design.LocalDesktopPalette
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/** T3 式轮次导航轨；它只控制滚动，不修改条目 leaf、持久末端或草稿。 */
@Composable
internal fun ConversationTimelineMinimap(
    turns: List<TimelineTurnPresentation>,
    activeIndex: Int,
    railHeight: Dp,
    onNavigate: (index: Int, immediate: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (turns.size < 2) return
    var hoveredIndex by remember(turns) { mutableStateOf<Int?>(null) }
    var hasFocus by remember { mutableStateOf(false) }
    var keyboardNavigationActive by remember { mutableStateOf(false) }
    var keyboardIndex by remember(turns) { mutableIntStateOf(activeIndex.coerceIn(turns.indices)) }
    LaunchedEffect(activeIndex, hasFocus, keyboardNavigationActive) {
        if (!hasFocus || !keyboardNavigationActive) keyboardIndex = activeIndex.coerceIn(turns.indices)
    }
    BoxWithConstraints(
        modifier = modifier
            .width(32.dp)
            .height(railHeight)
            .onFocusChanged {
                hasFocus = it.hasFocus
                if (!it.hasFocus) keyboardNavigationActive = false
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val nextIndex = when (event.key) {
                    Key.DirectionUp -> (keyboardIndex - 1).coerceAtLeast(0)
                    Key.DirectionDown -> (keyboardIndex + 1).coerceAtMost(turns.lastIndex)
                    Key.MoveHome -> 0
                    Key.MoveEnd -> turns.lastIndex
                    Key.Enter, Key.Spacebar -> keyboardIndex
                    else -> return@onPreviewKeyEvent false
                }
                keyboardNavigationActive = true
                keyboardIndex = nextIndex
                onNavigate(nextIndex, true)
                true
            }
            .focusable(),
    ) {
        val tickHitHeight = 14.dp
        val travel = timelineTickTravelDp(
            railHeightDp = maxHeight.value,
            turnCount = turns.size,
            tickHitHeightDp = tickHitHeight.value,
        ).dp
        val startY = ((maxHeight - tickHitHeight - travel) / 2).coerceAtLeast(0.dp)
        val keyboardPreviewIndex = keyboardIndex.takeIf { hasFocus && keyboardNavigationActive }
        val emphasisIndex = hoveredIndex ?: keyboardPreviewIndex
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(turns, startY, travel) {
                    detectTapGestures { offset ->
                        keyboardNavigationActive = false
                        onNavigate(
                            timelineTurnIndexAtOffset(
                                offsetY = offset.y - startY.toPx(),
                                railHeight = travel.toPx(),
                                turnCount = turns.size,
                            ),
                            false,
                        )
                    }
                },
        )
        Canvas(Modifier.fillMaxSize()) {
            turns.indices.forEach { index ->
                val centerY = (startY + tickHitHeight / 2 + travel * index.toFloat() / turns.lastIndex).toPx()
                drawLine(
                    color = if (emphasisIndex != null && index == emphasisIndex) {
                        AppText
                    } else {
                        AppMuted.copy(alpha = 0.62f)
                    },
                    start = Offset(0f, centerY),
                    end = Offset(timelineTickWidthDp(index, emphasisIndex).dp.toPx(), centerY),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        turns.forEachIndexed { index, _ ->
            val y = startY + travel * index.toFloat() / turns.lastIndex.toFloat()
            TimelineTickHitTarget(
                modifier = Modifier.offset(y = y),
                onHoverChange = { hovered ->
                    if (hovered) {
                        keyboardNavigationActive = false
                        hoveredIndex = index
                    } else if (hoveredIndex == index) {
                        hoveredIndex = null
                    }
                },
                onClick = {
                    keyboardNavigationActive = false
                    keyboardIndex = index
                    onNavigate(index, false)
                },
            )
        }
        val previewIndex = hoveredIndex ?: keyboardPreviewIndex
        previewIndex?.let { index ->
            val previewAnchorY = startY + tickHitHeight / 2 + travel * index.toFloat() / turns.lastIndex
            TimelineTurnPreviewPopup(
                turn = turns[index],
                anchorFraction = previewAnchorY.value / maxHeight.value.coerceAtLeast(1f),
            )
        }
    }
}

/** 为单个 2dp 刻度提供足够大的悬停与点击区域。 */
@Composable
private fun TimelineTickHitTarget(
    onHoverChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    LaunchedEffect(hovered) { onHoverChange(hovered) }
    Box(
        modifier = modifier
            .width(32.dp)
            .height(14.dp)
            .hoverable(interactionSource)
            .pointerInput(onClick) { detectTapGestures { onClick() } },
    )
}

/** 在刻度右侧展示当前用户输入和本轮最后一条助手正文。 */
@Composable
private fun TimelineTurnPreviewPopup(
    turn: TimelineTurnPresentation,
    anchorFraction: Float,
) {
    val palette = LocalDesktopPalette.current
    Popup(
        popupPositionProvider = TimelinePreviewPositionProvider(anchorFraction),
        properties = PopupProperties(focusable = false),
    ) {
        JewelSurface(
            role = JewelSurfaceRole.FLOATING,
            radius = 10.dp,
            solidColor = palette.panelBackground,
            borderColor = palette.popupBorder,
            modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = turn.userText.lineSequence().firstOrNull().orEmpty().ifBlank { "空消息" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = JewelTheme.defaultTextStyle.copy(color = AppText, fontWeight = FontWeight.SemiBold),
                )
                turn.assistantText?.let { assistant ->
                    Text(
                        text = assistant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                    )
                }
            }
        }
    }
}

/** 将预览浮层对齐到当前刻度，并始终约束在应用窗口内。 */
private class TimelinePreviewPositionProvider(
    private val anchorFraction: Float,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val anchorY = anchorBounds.top + (anchorBounds.height * anchorFraction).roundToInt()
        val x = (anchorBounds.right + 10).coerceAtMost(windowSize.width - popupContentSize.width - 8)
        val y = (anchorY - popupContentSize.height / 2)
            .coerceIn(8, (windowSize.height - popupContentSize.height - 8).coerceAtLeast(8))
        return IntOffset(x.coerceAtLeast(8), y)
    }
}
