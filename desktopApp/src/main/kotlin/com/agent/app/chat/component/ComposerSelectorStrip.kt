@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.agent.app.chat.component

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.agent.app.design.AppLine
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.JewelSurface
import com.agent.app.design.JewelSurfaceRole
import kotlinx.coroutines.delay
import org.jetbrains.jewel.foundation.theme.JewelTheme

internal val COMPOSER_SELECTOR_GROUP_GAP = 4.dp
private val COMPOSER_SELECTOR_HORIZONTAL_PADDING = 20.dp
private val COMPOSER_SELECTOR_CHEVRON_WIDTH = 20.dp
private val COMPOSER_SELECTOR_LABEL_SAFETY_MARGIN = 2.dp
internal const val COMPOSER_SELECTOR_CARD_CLOSE_DELAY_MILLIS = 160L

/** 一个可在 Composer 紧凑条和完整悬浮卡片间复用的选择器。 */
internal data class ComposerSelectorSlot(
    val menu: ComposerMenu,
    val label: String,
)

/** 一项选择器在当前可用宽度下的可见文本与交互装饰。 */
internal data class ComposerSelectorDisplay(
    val slot: ComposerSelectorSlot,
    val label: String,
    val width: Dp,
    val showChevron: Boolean,
    val visible: Boolean,
)

/** 选择器在可用空间不足时的视觉状态，按右至左顺序依次降级。 */
internal enum class ComposerSelectorCompressionState {
    FULL,
    LABEL_ONLY,
    PREFIX,
    HIDDEN,
}

/** 单个选择器在完整、无箭头和最小前缀状态下所需的宽度。 */
internal data class ComposerSelectorWidthSpec(
    val fullWidth: Dp,
    val labelOnlyWidth: Dp,
    val minimumPrefixWidth: Dp,
)

/** 宽度分配结果，`width` 为触发器实际占用的宽度。 */
internal data class ComposerSelectorWidthAllocation(
    val state: ComposerSelectorCompressionState,
    val width: Dp,
)

/**
 * Composer 选择器组。
 *
 * 它从最右侧开始逐项收窄。出现压缩时，紧凑条仅展示可容纳的文本前缀，完整控制组通过悬浮卡片提供。
 */
@Composable
internal fun ComposerSelectorStrip(
    slots: List<ComposerSelectorSlot>,
    keepCardVisible: Boolean,
    control: @Composable (ComposerSelectorSlot, String, Boolean, Modifier, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slots.isEmpty()) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = JewelTheme.defaultTextStyle
    val stripInteractionSource = remember { MutableInteractionSource() }
    var stripHovered by remember { mutableStateOf(false) }
    var cardHovered by remember { mutableStateOf(false) }
    var cardVisible by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(stripInteractionSource)
            .onPointerEvent(PointerEventType.Enter) { stripHovered = true }
            .onPointerEvent(PointerEventType.Exit) { stripHovered = false },
    ) {
        val displays = composerSelectorDisplays(
            slots = slots,
            availableWidth = maxWidth,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            density = density,
        )
        val compressed = displays.any { display ->
            !display.showChevron || display.label != display.slot.label
        }

        LaunchedEffect(compressed, stripHovered, cardHovered, keepCardVisible) {
            if (shouldKeepComposerSelectorCardVisible(compressed, stripHovered, cardHovered, keepCardVisible)) {
                cardVisible = true
            } else {
                delay(COMPOSER_SELECTOR_CARD_CLOSE_DELAY_MILLIS)
                if (!stripHovered && !cardHovered && !keepCardVisible) cardVisible = false
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(COMPOSER_SELECTOR_GROUP_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            displays.filter { it.visible }.forEach { display ->
                control(
                    display.slot,
                    display.label,
                    display.showChevron,
                    Modifier.width(display.width),
                    compressed,
                )
            }
        }

        if (compressed && cardVisible) {
            Popup(
                popupPositionProvider = remember { ComposerSelectorCardPositionProvider() },
                onDismissRequest = { cardVisible = false },
                properties = PopupProperties(focusable = false),
            ) {
                JewelSurface(
                    role = JewelSurfaceRole.FLOATING,
                    radius = 8.dp,
                    solidColor = AppSidebarBackground,
                    borderColor = AppLine,
                    modifier = Modifier
                        .onPointerEvent(PointerEventType.Enter) { cardHovered = true }
                        .onPointerEvent(PointerEventType.Exit) { cardHovered = false },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(COMPOSER_SELECTOR_GROUP_GAP),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        slots.forEach { slot ->
                            control(slot, slot.label, true, Modifier, false)
                        }
                    }
                }
            }
        }
    }
}

/** 仅在存在压缩项且指针或菜单仍停留在完整控制组内时保留完整卡片。 */
internal fun shouldKeepComposerSelectorCardVisible(
    compressed: Boolean,
    stripHovered: Boolean,
    cardHovered: Boolean,
    menuExpanded: Boolean,
): Boolean = compressed && (stripHovered || cardHovered || menuExpanded)

/**
 * 从最右侧开始给选择器降级，避免右侧权限收窄时推挤服务商、模型和思考等级。
 *
 * 每次仅处理当前最右可见项，直到该项完全隐藏后才继续处理左侧项。
 */
internal fun composerSelectorWidthAllocations(
    specs: List<ComposerSelectorWidthSpec>,
    availableWidth: Dp,
): List<ComposerSelectorWidthAllocation> {
    if (specs.isEmpty()) return emptyList()

    val allocations = MutableList(specs.size) { index ->
        ComposerSelectorWidthAllocation(
            state = ComposerSelectorCompressionState.FULL,
            width = specs[index].fullWidth,
        )
    }
    var activeIndex = specs.lastIndex

    while (activeIndex >= 0) {
        val spaceForActiveItem = availableWidth - composerSelectorRequiredWidth(specs, activeIndex)
        val spec = specs[activeIndex]
        when {
            spaceForActiveItem >= spec.fullWidth -> return allocations
            spaceForActiveItem >= spec.labelOnlyWidth -> {
                allocations[activeIndex] = ComposerSelectorWidthAllocation(
                    state = ComposerSelectorCompressionState.LABEL_ONLY,
                    width = spec.labelOnlyWidth,
                )
                return allocations
            }

            spaceForActiveItem >= spec.minimumPrefixWidth -> {
                allocations[activeIndex] = ComposerSelectorWidthAllocation(
                    state = ComposerSelectorCompressionState.PREFIX,
                    width = spaceForActiveItem,
                )
                return allocations
            }

            else -> {
                allocations[activeIndex] = ComposerSelectorWidthAllocation(
                    state = ComposerSelectorCompressionState.HIDDEN,
                    width = 0.dp,
                )
                activeIndex--
            }
        }
    }

    return allocations
}

/** 返回当前处理项左侧完整项和内部间距所占的宽度。 */
private fun composerSelectorRequiredWidth(
    specs: List<ComposerSelectorWidthSpec>,
    activeIndex: Int,
): Dp {
    if (activeIndex == 0) return 0.dp
    val items = specs.take(activeIndex).fold(0.dp) { total, spec -> total + spec.fullWidth }
    return items + COMPOSER_SELECTOR_GROUP_GAP * activeIndex
}

/** 由完整标签、已分配宽度和文本度量值构造稳定的紧凑展示信息。 */
private fun composerSelectorDisplays(
    slots: List<ComposerSelectorSlot>,
    availableWidth: Dp,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
    density: androidx.compose.ui.unit.Density,
): List<ComposerSelectorDisplay> {
    val specs = slots.map { slot ->
        with(density) {
            val textWidth = textMeasurer.measure(AnnotatedString(slot.label), textStyle).size.width.toDp()
            val firstCharacterWidth = textMeasurer
                .measure(AnnotatedString(slot.label.take(1)), textStyle)
                .size
                .width
                .toDp()
            ComposerSelectorWidthSpec(
                fullWidth = textWidth + COMPOSER_SELECTOR_HORIZONTAL_PADDING +
                        COMPOSER_SELECTOR_CHEVRON_WIDTH + COMPOSER_SELECTOR_LABEL_SAFETY_MARGIN,
                labelOnlyWidth = textWidth + COMPOSER_SELECTOR_HORIZONTAL_PADDING + COMPOSER_SELECTOR_LABEL_SAFETY_MARGIN,
                minimumPrefixWidth = firstCharacterWidth + COMPOSER_SELECTOR_HORIZONTAL_PADDING +
                        COMPOSER_SELECTOR_LABEL_SAFETY_MARGIN,
            )
        }
    }
    return slots.zip(composerSelectorWidthAllocations(specs, availableWidth)).map { (slot, allocation) ->
        val visible = allocation.state != ComposerSelectorCompressionState.HIDDEN
        val showChevron = allocation.state == ComposerSelectorCompressionState.FULL
        val textWidth = with(density) {
            (allocation.width - COMPOSER_SELECTOR_HORIZONTAL_PADDING -
                    if (showChevron) COMPOSER_SELECTOR_CHEVRON_WIDTH else 0.dp)
                .coerceAtLeast(0.dp)
                .roundToPx()
        }
        ComposerSelectorDisplay(
            slot = slot,
            label = composerSelectorDisplayLabel(
                slot = slot,
                allocation = allocation,
                maxTextWidthPixels = textWidth,
                textMeasurer = textMeasurer,
                textStyle = textStyle,
            ),
            width = allocation.width,
            showChevron = showChevron,
            visible = visible,
        )
    }
}

/** 完整标签状态不重新测量裁切，避免临界像素下丢失模型名称末尾字符。 */
private fun composerSelectorDisplayLabel(
    slot: ComposerSelectorSlot,
    allocation: ComposerSelectorWidthAllocation,
    maxTextWidthPixels: Int,
    textMeasurer: TextMeasurer,
    textStyle: TextStyle,
): String = when (allocation.state) {
    ComposerSelectorCompressionState.FULL,
    ComposerSelectorCompressionState.LABEL_ONLY -> slot.label

    ComposerSelectorCompressionState.PREFIX -> composerSelectorLabelPrefix(slot.label, maxTextWidthPixels) { candidate ->
        textMeasurer.measure(AnnotatedString(candidate), textStyle).size.width
    }

    ComposerSelectorCompressionState.HIDDEN -> ""
}

/** 仅保留可完整绘制的字符前缀，避免窄宽度下显示半个字形或省略号。 */
internal fun composerSelectorLabelPrefix(
    label: String,
    maxWidthPixels: Int,
    measureWidthPixels: (String) -> Int,
): String {
    if (label.isEmpty() || maxWidthPixels <= 0) return ""
    if (measureWidthPixels(label) <= maxWidthPixels) return label
    for (end in label.length downTo 1) {
        val prefix = label.substring(0, end)
        if (measureWidthPixels(prefix) <= maxWidthPixels) return prefix
    }
    return ""
}

/** 将完整控制卡片覆盖在紧凑选择器组同一行，作为其临时的完整替代。 */
internal class ComposerSelectorCardPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        return anchorBounds.topLeft
    }
}
