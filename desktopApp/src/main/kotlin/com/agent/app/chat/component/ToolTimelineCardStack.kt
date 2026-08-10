package com.agent.app.chat.component

import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus

/** 工具事件完成后立即呈现真实结果，不人为延后非终端工具。 */
@Suppress("UNUSED_PARAMETER")
internal fun toolCompletionDelayMillis(
    item: ToolEventItem,
    startedAtMillis: Long,
    nowMillis: Long,
): Long = 0L

/**
 * 返回卡片堆叠中可见的当前工具和一张下一工具预览。
 *
 * 当仍有运行工具时，从首个运行项开始；全部结束且存在失败时优先展示失败项，
 * 否则保留最后一项供成功反馈短暂展示。
 */
internal fun visibleToolCardStack(items: List<ToolEventItem>): List<ToolEventItem> {
    val currentIndex = items.indexOfFirst { it.status == ToolEventStatus.Started }
        .takeIf { it >= 0 }
        ?: items.indexOfLast { it.status == ToolEventStatus.Failed }.takeIf { it >= 0 }
        ?: items.lastIndex
    return items.drop(currentIndex.coerceAtLeast(0)).take(2)
}
