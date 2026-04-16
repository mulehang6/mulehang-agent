package com.agent.provider

/**
 * 表示连接探测后的统一结果。
 */
data class ConnectionProbeResult(
    val providerId: String,
    val providerType: ProviderType,
    val isReachable: Boolean,
)

/**
 * 表示按提供商类型执行连接探测的适配器。
 */
interface ConnectionProbeAdapter {
    val providerType: ProviderType

    /**
     * 对给定 provider profile 执行连接探测。
     */
    suspend fun probe(profile: CustomProviderProfile): ConnectionProbeResult
}

/**
 * 负责按提供商类型解析连接探测适配器。
 */
class ConnectionProbeAdapters(
    adapters: List<ConnectionProbeAdapter>,
) {
    private val adaptersByType: Map<ProviderType, ConnectionProbeAdapter> = adapters.associateBy { it.providerType }

    /**
     * 返回与提供商类型匹配的探测适配器。
     */
    fun resolve(providerType: ProviderType): ConnectionProbeAdapter {
        return adaptersByType[providerType]
            ?: error("No connection probe adapter registered for provider type '$providerType'.")
    }
}
