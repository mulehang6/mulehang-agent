package com.agent.shared.session

import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.AgentHookSettings

/**
 * 应用启动时提供给 UI 的会话快照。
 */
data class AppSessionSnapshot(
    val profiles: List<ConfigProfile>,
    val activeProfile: ConfigProfile?,
    /** 按主 Provider 解析的快速模型，用于标题和自动审批等低延迟内部任务。 */
    val fasterProfiles: Map<String, ConfigProfile> = emptyMap(),
    /** 仅来自用户级 settings 的命令 Hook 设置。 */
    val hookSettings: AgentHookSettings = AgentHookSettings(),
)
