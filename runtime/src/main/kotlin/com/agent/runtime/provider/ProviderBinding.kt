package com.agent.runtime.provider

/**
 * 表示 runtime 最终消费的 provider binding。
 */
data class ProviderBinding(
    val providerId: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
    val options: ProviderBindingOptions = ProviderBindingOptions(),
)

/**
 * 表示一次 provider 解析后的缓存快照。
 */
data class ProviderResolutionSnapshot(
    val providerId: String,
    val providerType: ProviderType,
    val discoveredModels: List<DiscoveredModel>,
    val binding: ProviderBinding?,
)

/**
 * provider binding 可选选项，目前只有OpenAI Endpoint 两种模式
 */
data class ProviderBindingOptions(
    val openAIEndpointMode: OpenAIEndpointMode? = null
)
