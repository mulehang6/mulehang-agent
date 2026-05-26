package com.agent.shared.state

/**
 * 聊天消息角色。
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

/**
 * 聊天消息。
 */
data class ChatMessage(
    val role: MessageRole,
    val content: String,
)
