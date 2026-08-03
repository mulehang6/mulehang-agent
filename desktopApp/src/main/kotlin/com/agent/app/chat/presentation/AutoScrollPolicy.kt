package com.agent.app.chat.presentation

import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ConversationItem
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem

/**
 * 仅当用户仍停在底部附近时，才让时间线自动跟随最新内容。
 */
internal fun shouldAutoScrollToLatest(
    lastVisibleIndex: Int?,
    totalItems: Int,
    trailingThreshold: Int = TIMELINE_AUTO_SCROLL_THRESHOLD_ITEMS,
): Boolean {
    if (lastVisibleIndex == null) {
        return true
    }
    val threshold = trailingThreshold.coerceAtLeast(0)
    return lastVisibleIndex >= timelineAutoScrollAnchorIndex(totalItems) - threshold
}

/**
 * 根据当前滚动位置和是否刚追加内容，决定是否继续保持跟随最新内容。
 */
internal fun nextAutoScrollFollowState(
    currentFollowLatest: Boolean,
    lastVisibleIndex: Int?,
    totalItems: Int,
    previousTotalItems: Int,
    trailingThreshold: Int = TIMELINE_AUTO_SCROLL_THRESHOLD_ITEMS,
): Boolean {
    if (shouldAutoScrollToLatest(lastVisibleIndex, totalItems, trailingThreshold)) {
        return true
    }
    return currentFollowLatest && totalItems > previousTotalItems
}

/**
 * 用户主动提交非空草稿时，忽略先前阅读位置并重新跟随最新消息。
 */
internal fun shouldForceScrollToLatestAfterSubmit(draft: String): Boolean = draft.isNotBlank()

/**
 * 返回时间线自动滚动锚点。
 */
internal fun timelineAutoScrollAnchorIndex(totalItems: Int): Int =
    totalItems.coerceAtLeast(0)

/**
 * 估算单个时间线项的字符总量，用作自动跟随滚动的内容指纹。
 */
internal fun itemContentSize(item: ConversationItem): Int = when (item) {
    is ChatMessageItem -> item.message.content.length
    is ReasoningItem -> (item.rawText ?: item.displayText).length
    is ToolEventItem -> listOf(
        item.preview,
        item.toolName,
        item.operationIntent,
        item.resultPreview,
        item.errorMessage,
    ).sumOf { content -> content?.length ?: 0 }
}

internal const val TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX = 200

private const val TIMELINE_AUTO_SCROLL_THRESHOLD_ITEMS = 1
