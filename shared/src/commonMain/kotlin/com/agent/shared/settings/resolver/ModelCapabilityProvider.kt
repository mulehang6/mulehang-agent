package com.agent.shared.settings.resolver

import com.agent.shared.settings.model.ConfigProfile

/** 为特定 provider 或配置来源解析模型能力。 */
interface ModelCapabilityProvider {
    /** 匹配时返回能力；不匹配时返回 null。 */
    fun resolve(profile: ConfigProfile): ModelCapabilities?
}
