package com.agent.app.chat.component

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 扩展页锚点顺序与屏幕呈现顺序一致，标识不依赖标题翻译。 */
internal enum class ExtensionAnchor(val label: String) {
    OVERVIEW("概览"), PACKAGES("扩展包"), SKILLS("Skills"), PROMPTS("Prompts"),
    MCP("MCP"), HOOKS("Hooks"), DIAGNOSTICS("资源诊断"),
}

/** 根据实际标题位置计算当前区域；滚动到底部时选中最后一组。 */
internal fun activeExtensionAnchor(
    positions: Map<ExtensionAnchor, Int>,
    scroll: Int,
    maximum: Int,
): ExtensionAnchor {
    if (positions.isEmpty()) return ExtensionAnchor.OVERVIEW
    if (maximum > 0 && scroll >= maximum - 1) return positions.maxBy { it.value }.key
    return positions.filterValues { it <= scroll + 8 }.maxByOrNull { it.value }?.key
        ?: ExtensionAnchor.OVERVIEW
}

/** 宽窄导航共享滚动定位，所有坐标均为滚动内容中的像素。 */
@Stable
internal class SettingsAnchorState(val scroll: ScrollState) {
    val positions = mutableStateMapOf<ExtensionAnchor, Int>()
    var viewportTop by mutableFloatStateOf(0f)
    var viewportWidth by mutableIntStateOf(0)
        private set
    private var pendingReading: Pair<ExtensionAnchor, Int>? = null
    val active: ExtensionAnchor
        get() = activeExtensionAnchor(positions, scroll.value, scroll.maxValue)

    /** 宽度变化前保留当前段落及段落内偏移，重排后用新坐标恢复。 */
    fun updateViewport(width: Int, top: Float) {
        if (viewportWidth != 0 && viewportWidth != width && pendingReading == null) {
            val entry = positions.filterValues { it <= scroll.value + 8 }.maxByOrNull { it.value }
            pendingReading = entry?.let { it.key to (scroll.value - it.value) }
        }
        viewportTop = top
        viewportWidth = width
    }

    /** 下一帧的所有动态区块坐标更新后恢复阅读位置。 */
    suspend fun restoreReading() {
        val (anchor, offset) = pendingReading ?: return
        pendingReading = null
        scroll.scrollTo(((positions[anchor] ?: 0) + offset).coerceIn(0, scroll.maxValue))
    }

    /** 在真实布局完成后定位，重复点击或手动滚动可取消旧动画。 */
    suspend fun navigate(anchor: ExtensionAnchor, animate: Boolean) {
        val target = (positions[anchor] ?: 0).coerceIn(0, scroll.maxValue)
        if (animate && !prefersReducedMotion()) scroll.animateScrollTo(target, tween(180))
        else scroll.scrollTo(target)
    }
}

internal val LocalSettingsAnchors = staticCompositionLocalOf<SettingsAnchorState?> { null }
internal val LocalSettingsCompact = staticCompositionLocalOf { false }

/** 一个有稳定锚点的连续内容组，不增加额外卡片表面。 */
@Composable
internal fun ExtensionAnchorSection(anchor: ExtensionAnchor, content: @Composable ColumnScope.() -> Unit) {
    val state = LocalSettingsAnchors.current
    Column(
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
            if (state != null) {
                state.positions[anchor] = (coordinates.positionInRoot().y - state.viewportTop + state.scroll.value)
                    .roundToInt().coerceAtLeast(0)
            }
        },
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}
