package com.agent.shared.state

/**
 * 时间线中的聊天消息项。
 */
data class ChatMessageItem(
    val message: ChatMessage,
) : ConversationItem {
    override val kind: ConversationItem.Kind = ConversationItem.Kind.ChatMessage
}
