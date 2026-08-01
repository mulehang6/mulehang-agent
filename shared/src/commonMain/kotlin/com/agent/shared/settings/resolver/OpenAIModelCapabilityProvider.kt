package com.agent.shared.settings.resolver

import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType

/** 维护官方 OpenAI Responses GPT/Codex 模型的内置能力。 */
object OpenAIModelCapabilityProvider : ModelCapabilityProvider {
    override fun resolve(profile: ConfigProfile): ModelCapabilities? {
        if (profile.providerType != ProviderType.OPENAI_RESPONSES || !profile.isOfficialOpenAI() || !profile.isReasoningModel()) {
            return null
        }
        return modelCapabilitiesOf(
            efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            limit = profile.limit,
        )
    }

    private fun ConfigProfile.isOfficialOpenAI(): Boolean =
        baseUrl.trimEnd('/').equals("https://api.openai.com", ignoreCase = true) ||
            baseUrl.trimEnd('/').equals("https://api.openai.com/v1", ignoreCase = true)

    private fun ConfigProfile.isReasoningModel(): Boolean =
        model.contains("gpt-5", ignoreCase = true) || model.contains("codex", ignoreCase = true)
}
