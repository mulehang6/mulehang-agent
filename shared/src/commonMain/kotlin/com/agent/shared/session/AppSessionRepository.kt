package com.agent.shared.session

import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.AgentHookSettings

/**
 * 屏蔽配置与 UI 状态持久化实现细节的仓库接口。
 */
interface AppSessionRepository {
    /**
     * 加载当前可用 profile 列表。
     */
    suspend fun loadProfiles(): List<ConfigProfile>

    /** 加载按主 Provider 解析的快速模型，未配置时返回空映射。 */
    suspend fun loadFasterProfiles(): Map<String, ConfigProfile> = emptyMap()

    /** 加载仅用户级生效的 Hook 设置。 */
    @Suppress("unused")
    suspend fun loadHookSettings(): AgentHookSettings = AgentHookSettings()

    /**
     * 加载当前项目上次记忆的 profile id。
     */
    suspend fun loadRememberedProfileId(): String?

    /**
     * 保存当前项目记忆的 profile id。
     */
    suspend fun saveRememberedProfileId(profileId: String)
}
