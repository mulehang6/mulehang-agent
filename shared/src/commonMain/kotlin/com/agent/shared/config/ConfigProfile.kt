package com.agent.shared.config

/**
 * 合并用户级、项目级与环境变量覆盖后的最终 profile。
 */
data class ConfigProfile(
    val id: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean,
    val layer: ConfigLayer,
)
