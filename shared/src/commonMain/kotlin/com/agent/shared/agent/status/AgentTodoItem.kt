package com.agent.shared.agent.status

import kotlinx.serialization.Serializable

/** Agent TODO 的可持久化进度。 */
@Serializable
enum class AgentTodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
}

/** 单个会话跨用户轮次保留的 Agent TODO。 */
@Serializable
data class AgentTodoItem(
    val id: String,
    val content: String,
    val status: AgentTodoStatus,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
