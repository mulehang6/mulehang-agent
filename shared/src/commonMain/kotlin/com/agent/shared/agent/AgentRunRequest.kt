package com.agent.shared.agent

import com.agent.shared.config.ConfigProfile

/**
 * 描述一次消息发送所需的最小运行参数。
 */
data class AgentRunRequest(
    val prompt: String,
    val profile: ConfigProfile,
    val reasoningEffort: ReasoningEffort? = ReasoningEffort.MEDIUM,
)

/**
 * 推理强度档位。
 */
enum class ReasoningEffort(
    val wireValue: String,
) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}
