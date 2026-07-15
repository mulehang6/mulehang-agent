package com.agent.shared.chat.model

/**
 * 聊天消息。
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
)
