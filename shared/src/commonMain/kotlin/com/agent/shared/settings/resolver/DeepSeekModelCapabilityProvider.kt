package com.agent.shared.settings.resolver

import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ModelLimit
import com.agent.shared.settings.model.ProviderType

/** 维护 DeepSeek chat-completions 模型的内置能力。 */
object DeepSeekModelCapabilityProvider : ModelCapabilityProvider {
    override fun resolve(profile: ConfigProfile): ModelCapabilities? {
        if (profile.providerType != ProviderType.OPENAI_CHAT_COMPLETIONS || !profile.isDeepSeekProfile()) return null
        return modelCapabilitiesOf(
            efforts = listOf(ReasoningEffort.HIGH, ReasoningEffort.MAX),
            limit = profile.limit ?: ModelLimit(context = 1_000_000, output = 384_000),
        )
    }

    private fun ConfigProfile.isDeepSeekProfile(): Boolean =
        model.startsWith("deepseek", ignoreCase = true) || baseUrl.contains("deepseek", ignoreCase = true)
}
