package com.agent.shared.agent

import com.agent.shared.config.ConfigProfile
import com.agent.shared.state.PermissionPreset

/**
 * 描述一次消息发送所需的最小运行参数。
 */
data class AgentRunRequest(
    val prompt: String,
    val profile: ConfigProfile,
    val reasoningEffort: ReasoningEffort? = ReasoningEffort.MEDIUM,
    val history: List<AgentConversationHistoryMessage> = emptyList(),
    val workspacePath: String = "",
    val permissionPreset: PermissionPreset = PermissionPreset.DEFAULT,
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
    MAX("max"),
}
