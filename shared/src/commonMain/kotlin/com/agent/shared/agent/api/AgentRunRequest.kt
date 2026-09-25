package com.agent.shared.agent.api

import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.AgentHookSettings
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
    /** 低延迟内部任务共用的快速模型；为空时审批必须回退到人工确认。 */
    val fasterProfile: ConfigProfile? = null,
    /** 会话稳定标识，用于 Hook 的 SessionStart 与 SessionEnd 生命周期。 */
    val sessionId: String = "",
    /** 本次运行冻结的全局 Hook 设置。 */
    val hookSettings: AgentHookSettings = AgentHookSettings(),
    /** 当前用户消息的有序输入片段；未指定时兼容旧的纯文本调用。 */
    val inputParts: List<UserInputPart> = listOf(UserInputPart.Text(prompt)),
    /** 本轮固定使用的资源快照投影，重载只影响之后新建的请求。 */
    val runtimeResources: AgentRuntimeResources = AgentRuntimeResources(),
    /** 关联本地准备与模型请求的匿名诊断标识。 */
    val traceId: String = "",
    /** 仅在继续既有运行时传入；普通新轮次总是创建独立 Koog run。 */
    val resumeRunId: String? = null,
    /** 本轮用户消息的条目 ID，用于关联状态快照。 */
    val userEntryId: String = "",
    /** 发送时的上下文窗口估计，实际 usage 到达后可覆盖。 */
    val contextUsageFraction: Float? = null,
    val contextWindow: Int? = null,
    /** 本轮冻结的自动压缩触发阈值。 */
    val contextCompactionThresholdPercent: Int = 80,
    /** 已在同一轮重试前压缩，避免再次把摘要当原始历史压缩。 */
    val contextAlreadyCompacted: Boolean = false,
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
