package com.agent.shared.config

import kotlinx.serialization.Serializable

/**
 * settings.json 文档模型。
 */
@Serializable
data class SettingsDocument(
    val profiles: List<AgentProfile> = emptyList(),
)
