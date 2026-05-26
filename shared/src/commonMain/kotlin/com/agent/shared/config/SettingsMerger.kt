package com.agent.shared.config

/**
 * 负责把用户级、项目级与环境变量覆盖合并为最终 profile 列表。
 */
object SettingsMerger {

    /**
     * 生成最终 profile 列表。
     */
    fun merge(
        user: SettingsDocument?,
        project: SettingsDocument?,
        environment: Map<String, String>,
    ): List<ResolvedAgentProfile> {
        TODO("Implement layered settings merge: env > project > user > defaults.")
    }
}
