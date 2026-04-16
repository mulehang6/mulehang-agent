package com.agent.provider

/**
 * 表示发现到的远端模型条目。
 */
data class DiscoveredModel(
    val id: String,
)

/**
 * 表示模型发现后的统一结果。
 */
data class ModelDiscoveryResult(
    val providerId: String,
    val providerType: ProviderType,
    val models: List<DiscoveredModel>,
    val defaultModelId: String? = null,
)

/**
 * 表示按提供商类型执行模型发现的适配器。
 */
interface ModelDiscoveryAdapter {
    val providerType: ProviderType

    /**
     * 对给定 provider profile 执行模型发现。
     */
    suspend fun discover(profile: CustomProviderProfile): ModelDiscoveryResult
}

/**
 * 负责按提供商类型解析模型发现适配器。
 */
class ModelDiscoveryAdapters(
    adapters: List<ModelDiscoveryAdapter>,
) {
    private val adaptersByType: Map<ProviderType, ModelDiscoveryAdapter> = adapters.associateBy { it.providerType }

    /**
     * 返回与提供商类型匹配的模型发现适配器。
     */
    fun resolve(providerType: ProviderType): ModelDiscoveryAdapter {
        return adaptersByType[providerType]
            ?: error("No model discovery adapter registered for provider type '$providerType'.")
    }
}
