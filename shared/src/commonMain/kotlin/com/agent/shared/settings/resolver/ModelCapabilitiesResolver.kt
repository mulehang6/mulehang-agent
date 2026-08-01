package com.agent.shared.settings.resolver

import com.agent.shared.settings.model.ConfigProfile

/**
 * 按优先级解析运行时模型能力，避免 UI 与发送层散落 provider 判断。
 */
object ModelCapabilitiesResolver {
    private val providers = listOf(
        ConfiguredModelCapabilityProvider,
        DeepSeekModelCapabilityProvider,
        OpenAIModelCapabilityProvider,
    )

    /**
     * 返回首个匹配 provider 的能力；没有匹配时返回空能力。
     */
    fun resolve(profile: ConfigProfile): ModelCapabilities =
        providers.firstNotNullOfOrNull { provider -> provider.resolve(profile) } ?: noModelCapabilities
}
