package com.agent.shared.settings.resolver

import com.agent.shared.settings.model.ConfigProfile

/** 优先使用 settings.json 为模型显式声明的能力。 */
object ConfiguredModelCapabilityProvider : ModelCapabilityProvider {
    override fun resolve(profile: ConfigProfile): ModelCapabilities? =
        profile.reasoningEfforts?.let { efforts ->
            modelCapabilitiesOf(
                efforts = efforts,
                limit = profile.limit,
                defaultReasoningEffort = profile.defaultReasoningEffort,
            )
        }
}
