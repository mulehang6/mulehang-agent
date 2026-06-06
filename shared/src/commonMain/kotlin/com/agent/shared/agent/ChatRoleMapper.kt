package com.agent.shared.agent

import ai.koog.prompt.message.Message
import com.agent.shared.state.ChatRole

/**
 * 将 Koog 消息角色转换为应用层角色。
 */
internal fun Message.Role.toChatRole(): ChatRole = when (this) {
    Message.Role.System -> ChatRole.System
    Message.Role.User -> ChatRole.User
    Message.Role.Assistant -> ChatRole.Assistant
}

/**
 * 将应用层角色转换为 Koog 消息角色。
 */
internal fun ChatRole.toKoogRole(): Message.Role = when (this) {
    ChatRole.System -> Message.Role.System
    ChatRole.User -> Message.Role.User
    ChatRole.Assistant -> Message.Role.Assistant
}
