package com.agent.shared.chat.persistence

import com.agent.shared.agent.status.AgentTodoItem
import kotlinx.serialization.Serializable

/** 用户消息发送前的持久状态；空 before 表示该消息创建了会话。 */
@Serializable
data class UserTurnSnapshot(
    val version: Int = 1,
    val before: PersistedTask?,
    /** 空列表表示发送前无 TODO；null 兼容尚未记录 TODO 的旧回退点。 */
    val todos: List<AgentTodoItem>? = null,
)
