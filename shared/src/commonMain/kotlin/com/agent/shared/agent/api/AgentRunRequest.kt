package com.agent.shared.agent.api

import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.tool.model.PermissionPreset

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
    /** AUTO 模式独立审批模型；为空时必须回退到人工审批。 */
    val approvalProfile: ConfigProfile? = null,
)

/**
 * 推理强度档位。
 */
enum class ReasoningEffort(
    val wireValue: String,
) {
    NONE("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max"),
}
