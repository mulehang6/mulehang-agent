package com.agent.shared.settings.model

import kotlinx.serialization.Serializable

/**
 * settings.json 文档模型。
 */
@Serializable
data class SettingsDocument(
    val providers: List<ProviderProfile> = emptyList(),
    val fasterModel: FasterModelProfile? = null,
    val agentResources: AgentResourceSettings = AgentResourceSettings(),
)
