package com.agent.app.chat.state

import com.agent.shared.settings.model.ConfigProfile

/** 为低延迟内部任务选择已解析的快速模型，并在未配置时安全复用主模型。 */
internal fun fasterProfileForInternalTask(
    primaryProfile: ConfigProfile,
    fasterProfiles: Map<String, ConfigProfile>,
): ConfigProfile = fasterProfiles[primaryProfile.providerId] ?: primaryProfile
