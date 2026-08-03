package com.agent.app.chat.component

import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationItem

/**
 * 刚发送、等待在时间线目标位置执行一次进入动效的用户消息。
 */
internal data class PendingMessageEntry(
    val id: Long,
    val content: String,
)

/**
 * 用户消息原位进入时应用到图层的视觉值。
 */
internal data class MessageEntryVisuals(
    val alpha: Float,
    val scale: Float,
    val translationY: Float,
)

/** 进入动效的起始缩放，明显小于最终尺寸才能看出"展开"。 */
internal const val MESSAGE_ENTRY_INITIAL_SCALE = 0.9f

/** 淡入相对位移提前完成的倍率，避免整段动画都是半透明的糊感。 */
private const val MESSAGE_ENTRY_FADE_RATE = 2f

/**
 * 把标准化动画进度映射为淡入、展开和向上移动的图层值。
 *
 * 进度允许超过 1，弹性收尾的轻微过冲会让卡片略微越过终点再落定。
 */
internal fun messageEntryVisuals(
    progress: Float,
    travelDistancePx: Float,
): MessageEntryVisuals = MessageEntryVisuals(
    alpha = (progress * MESSAGE_ENTRY_FADE_RATE).coerceIn(0f, 1f),
    scale = MESSAGE_ENTRY_INITIAL_SCALE + progress * (1f - MESSAGE_ENTRY_INITIAL_SCALE),
    translationY = (1f - progress) * travelDistancePx,
)

/**
 * 返回最后一条内容匹配的用户消息，避免重复文本让历史消息重新执行进入动效。
 */
internal fun latestMatchingUserMessage(
    items: List<ConversationItem>,
    content: String?,
): ChatMessageItem? {
    if (content == null) return null
    return items.lastOrNull { item ->
        item is ChatMessageItem &&
                item.message.role == ChatRole.User &&
                item.message.content == content
    } as? ChatMessageItem
}
