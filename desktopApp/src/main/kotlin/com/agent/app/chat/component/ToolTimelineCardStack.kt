package com.agent.app.chat.component

import com.agent.app.chat.presentation.isTerminalToolEvent
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus

/** 非终端工具在时间线中至少保留运行图标动效的时长。 */
internal const val TOOL_MINIMUM_RUNNING_DISPLAY_MILLIS = 2_000L

/**
 * 计算工具完成状态在时间线中仍需延后的毫秒数。
 *
 * 仅非终端的成功或失败结果需要等待；运行中的事件与终端事件始终立即呈现。
 */
internal fun toolCompletionDelayMillis(
    item: ToolEventItem,
    startedAtMillis: Long,
    nowMillis: Long,
): Long {
    if (item.status == ToolEventStatus.Started || isTerminalToolEvent(item)) return 0L
    val elapsedMillis = (nowMillis - startedAtMillis).coerceAtLeast(0L)
    return (TOOL_MINIMUM_RUNNING_DISPLAY_MILLIS - elapsedMillis).coerceAtLeast(0L)
}

/**
 * 返回卡片堆叠中可见的当前工具和一张下一工具预览。
 *
 * 当仍有运行工具时，从首个运行项开始；全部结束时保留最后一项，供完成反馈短暂展示。
 */
internal fun visibleToolCardStack(items: List<ToolEventItem>): List<ToolEventItem> {
    val currentIndex = items.indexOfFirst { it.status == ToolEventStatus.Started }
        .takeIf { it >= 0 }
        ?: items.lastIndex
    return items.drop(currentIndex.coerceAtLeast(0)).take(2)
}
