package com.agent.shared.state

import ai.koog.prompt.message.Message

/**
 * 聊天消息。
 */
data class ChatMessage(
    val role: Message.Role,
    val content: String,
)
