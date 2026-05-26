package com.agent.shared.config

/**
 * 根据记忆状态与启用列表选择当前活动 profile。
 */
object ProfileSelectionResolver {

    /**
     * 选择当前可用的活动 profile。
     */
    fun selectActiveProfile(
        profiles: List<ConfigProfile>,
        rememberedProfileId: String?,
    ): ConfigProfile? {
        TODO("Implement profile restoration and fallback selection.")
    }
}
