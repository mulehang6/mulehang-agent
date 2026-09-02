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
    /** 当前用户消息的有序输入片段；未指定时兼容旧的纯文本调用。 */
    val inputParts: List<UserInputPart> = listOf(UserInputPart.Text(prompt)),
    /** 本轮固定使用的资源快照投影，重载只影响之后新建的请求。 */
    val runtimeResources: AgentRuntimeResources = AgentRuntimeResources(),
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
