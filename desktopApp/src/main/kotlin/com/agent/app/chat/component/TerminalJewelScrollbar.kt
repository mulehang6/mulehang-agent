package com.agent.app.chat.component

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import java.lang.Runnable
import javax.swing.BoundedRangeModel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import kotlin.math.roundToInt

/**
 * 在不叠加 Swing 互操作视图的前提下，使用 Jewel 的真实 [VerticalScrollbar] 显示终端历史位置。
 *
 * JediTerm 以行维护 [BoundedRangeModel]，Jewel 则以像素维护 [androidx.compose.foundation.ScrollState]。
 * 隐藏的 Compose 滚动代理只提供等比例范围；用户拖动 Jewel 滑块和终端自身滚轮都通过该比例回写另一端。
 */
@Composable
internal fun TerminalJewelScrollbar(
    scrollModel: BoundedRangeModel,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var modelSnapshot by remember(scrollModel) {
        mutableStateOf(scrollModel.toTerminalScrollSnapshot())
    }
    var proxyInitialized by remember(scrollModel) { mutableStateOf(false) }

    DisposableEffect(scrollModel) {
        val listener = ChangeListener {
            publishTerminalScrollSnapshot(scrollModel) { modelSnapshot = it }
        }
        scrollModel.addChangeListener(listener)
        onDispose { scrollModel.removeChangeListener(listener) }
    }

    val proxyViewportHeight = with(LocalDensity.current) {
        modelSnapshot.viewportSize.toDp()
    }
    val proxyContentHeight = with(LocalDensity.current) {
        modelSnapshot.contentSize.toDp()
    }

    Box(modifier = modifier) {
        // SwingPanel 默认位于 Compose 图层之上，不能把 Jewel 控件盖在它上面。这个零宽代理仅为
        // VerticalScrollbar 提供真实的 ScrollState 尺寸和位置，不会接收或遮挡终端输入。
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(0.dp)
                .height(proxyViewportHeight)
                .verticalScroll(scrollState, enabled = false),
        ) {
            Spacer(modifier = Modifier.height(proxyContentHeight))
        }
        VerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }

    LaunchedEffect(modelSnapshot, scrollState.maxValue) {
        val proxyMaximum = scrollState.maxValue
        if (!proxyMaximum.isKnownTerminalProxyMaximum()) return@LaunchedEffect

        val targetOffset = terminalProxyOffsetForModel(
            snapshot = modelSnapshot,
            proxyMaximum = proxyMaximum,
        )
        if (scrollState.value != targetOffset) scrollState.scrollTo(targetOffset)
        proxyInitialized = true
    }

    LaunchedEffect(scrollModel, scrollState) {
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collect { (proxyOffset, proxyMaximum) ->
                if (!proxyInitialized || !proxyMaximum.isKnownTerminalProxyMaximum()) return@collect
                updateTerminalScrollModel(
                    model = scrollModel,
                    fraction = terminalProxyFraction(proxyOffset, proxyMaximum),
                )
            }
    }
}

/** JediTerm 滚动模型的不可变快照，避免 Compose 直接观察 Swing 可变对象。 */
internal data class TerminalScrollSnapshot(
    val minimum: Int,
    val maximum: Int,
    val extent: Int,
    val value: Int,
) {
    /** Jewel 代理可见区域的逻辑长度。 */
    val viewportSize: Int
        get() = extent.coerceAtLeast(1)

    /** Jewel 代理总内容的逻辑长度，至少包含一个可见区域。 */
    val contentSize: Int
        get() = (maximum.toLong() - minimum)
            .coerceAtLeast(viewportSize.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
}

/** 从 Swing 的 [BoundedRangeModel] 读取一次稳定的终端滚动快照。 */
internal fun BoundedRangeModel.toTerminalScrollSnapshot(): TerminalScrollSnapshot = TerminalScrollSnapshot(
    minimum = minimum,
    maximum = maximum,
    extent = extent,
    value = value,
)

/** 将 JediTerm 当前位置换算为 0 到 1 的滚动比例。 */
internal fun terminalScrollFraction(snapshot: TerminalScrollSnapshot): Float {
    val range = terminalScrollRange(snapshot)
    if (range == 0) return 0f
    val offset = (snapshot.value.toLong() - snapshot.minimum)
        .coerceIn(0, range.toLong())
    return offset.toFloat() / range
}

/** 将 Jewel 代理位置转换为对应的 JediTerm 模型值。 */
internal fun terminalModelValueForProxyFraction(
    snapshot: TerminalScrollSnapshot,
    fraction: Float,
): Int = (snapshot.minimum.toLong() + terminalScrollRange(snapshot) * fraction.coerceIn(0f, 1f))
    .roundToInt()
    .coerceIn(snapshot.minimum, terminalScrollUpperBound(snapshot))

/** 将 JediTerm 模型位置转换为 Jewel 代理的像素偏移。 */
internal fun terminalProxyOffsetForModel(
    snapshot: TerminalScrollSnapshot,
    proxyMaximum: Int,
): Int = (terminalScrollFraction(snapshot) * proxyMaximum.coerceAtLeast(0))
    .roundToInt()
    .coerceIn(0, proxyMaximum.coerceAtLeast(0))

/** 返回 Jewel 代理位置在有效代理范围内的比例。 */
internal fun terminalProxyFraction(
    proxyOffset: Int,
    proxyMaximum: Int,
): Float {
    if (proxyMaximum <= 0) return 0f
    return proxyOffset.coerceIn(0, proxyMaximum).toFloat() / proxyMaximum
}

/** 返回 BoundedRangeModel 中可滚动的逻辑范围。 */
private fun terminalScrollRange(snapshot: TerminalScrollSnapshot): Int =
    (terminalScrollUpperBound(snapshot).toLong() - snapshot.minimum)
        .coerceAtLeast(0)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

/** 返回 BoundedRangeModel 允许的最大 value。 */
private fun terminalScrollUpperBound(snapshot: TerminalScrollSnapshot): Int =
    (snapshot.maximum.toLong() - snapshot.extent)
        .coerceAtLeast(snapshot.minimum.toLong())
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

/** ScrollState 在首次测量前以 Int.MAX_VALUE 表示未知范围，不能据此同步终端。 */
private fun Int.isKnownTerminalProxyMaximum(): Boolean = this != Int.MAX_VALUE

/** 在 Swing EDT 上发布模型变化，保证 Compose 状态不会被后台 PTY 线程写入。 */
private fun publishTerminalScrollSnapshot(
    model: BoundedRangeModel,
    publish: (TerminalScrollSnapshot) -> Unit,
) {
    val update = Runnable { publish(model.toTerminalScrollSnapshot()) }
    if (SwingUtilities.isEventDispatchThread()) update.run() else SwingUtilities.invokeLater(update)
}

/** 根据 Jewel 滑块位置回写 JediTerm，并保留其最新范围以避免异步输出造成跳位。 */
private fun updateTerminalScrollModel(
    model: BoundedRangeModel,
    fraction: Float,
) {
    val update = Runnable {
        val target = terminalModelValueForProxyFraction(
            snapshot = model.toTerminalScrollSnapshot(),
            fraction = fraction,
        )
        if (model.value != target) model.value = target
    }
    if (SwingUtilities.isEventDispatchThread()) update.run() else SwingUtilities.invokeLater(update)
}
