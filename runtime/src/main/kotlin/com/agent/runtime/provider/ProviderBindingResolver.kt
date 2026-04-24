package com.agent.runtime.provider

/**
 * 负责把 provider profile、探测结果和模型发现结果解析为 runtime binding。
 */
class ProviderBindingResolver {

    /**
     * 根据 profile、探测结果和模型发现结果生成最终 binding。
     */
    fun resolve(
        profile: CustomProviderProfile,
        probe: ConnectionProbeResult,
        discovery: ModelDiscoveryResult,
    ): ProviderBinding {
        require(probe.isReachable) {
            "Cannot resolve binding for unreachable provider '${profile.id}'."
        }
        require(probe.providerId == profile.id && discovery.providerId == profile.id) {
            "Provider resolution inputs must belong to the same provider '${profile.id}'."
        }
        require(probe.providerType == profile.providerType && discovery.providerType == profile.providerType) {
            "Provider resolution inputs must use provider type '${profile.providerType}'."
        }

        val modelId = discovery.defaultModelId ?: discovery.models.firstOrNull()?.id
            ?: error("No discovered model available for provider '${profile.id}'.")

        return ProviderBinding(
            providerId = profile.id,
            providerType = profile.providerType,
            baseUrl = profile.baseUrl,
            apiKey = profile.apiKey,
            modelId = modelId,
        )
    }

    /**
     * 判断当前 profile 是否需要丢弃旧的模型发现结果和 binding。
     */
    fun shouldRefresh(
        previousSnapshot: ProviderResolutionSnapshot,
        currentProfile: CustomProviderProfile,
    ): Boolean {
        return previousSnapshot.providerType != currentProfile.providerType
    }
}
