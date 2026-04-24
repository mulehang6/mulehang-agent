package com.agent.runtime.provider

/**
 * 表示用户维护的 custom provider 连接配置。
 */
data class CustomProviderProfile(
    val id: String,
    val baseUrl: String,
    val apiKey: String,
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
)
