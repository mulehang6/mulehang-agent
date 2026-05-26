package com.agent.shared.application

import com.agent.shared.config.ConfigProfile

/**
 * 应用启动时提供给 UI 的会话快照。
 */
data class AppSessionSnapshot(
    val profiles: List<ConfigProfile>,
    val activeProfile: ConfigProfile?,
)
