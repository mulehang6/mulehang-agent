package com.agent.shared.config

import kotlinx.serialization.Serializable

/**
 * 单个 agent profile 配置项。
 */
@Serializable
data class AgentProfile(
    val id: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean? = null,
) {
    /**
     * 配置未显式关闭时默认启用。
     */
    fun isEnabled(): Boolean = enabled ?: true
}
