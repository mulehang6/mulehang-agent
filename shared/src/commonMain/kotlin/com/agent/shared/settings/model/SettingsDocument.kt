package com.agent.shared.settings.model

import kotlinx.serialization.Serializable

/**
 * settings.json 文档模型。
 */
@Serializable
data class SettingsDocument(
    val providers: List<ProviderProfile> = emptyList(),
    val fasterModel: FasterModelProfile? = null,
    /** 全局 Agent 循环上限；`-1` 表示使用 Koog 可表达的最大值。 */
    val maxIterations: Int? = null,
    /** 仅用户级生效的命令 Hook 配置。 */
    @Serializable(with = AgentHookSettingsSerializer::class)
    val hooks: AgentHookSettings = AgentHookSettings(),
    val agentResources: AgentResourceSettings = AgentResourceSettings(),
)
