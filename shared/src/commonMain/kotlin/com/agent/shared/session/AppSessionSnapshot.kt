package com.agent.shared.session

import com.agent.shared.settings.model.ConfigProfile

/**
 * 应用启动时提供给 UI 的会话快照。
 */
data class AppSessionSnapshot(
    val profiles: List<ConfigProfile>,
    val activeProfile: ConfigProfile?,
    val approvalProfiles: Map<String, ConfigProfile> = emptyMap(),
)
