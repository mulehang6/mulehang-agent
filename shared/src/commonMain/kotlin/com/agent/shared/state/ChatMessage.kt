package com.agent.shared.state

/**
 * 聊天消息。
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
)
