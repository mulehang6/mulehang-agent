package com.agent.shared.application

import com.agent.shared.config.ConfigProfile

/**
 * 屏蔽配置与 UI 状态持久化实现细节的仓库接口。
 */
interface AppSessionRepository {
    /**
     * 加载当前可用 profile 列表。
     */
    suspend fun loadProfiles(): List<ConfigProfile>

    /**
     * 加载当前项目上次记忆的 profile id。
     */
    suspend fun loadRememberedProfileId(): String?

    /**
     * 保存当前项目记忆的 profile id。
     */
    suspend fun saveRememberedProfileId(profileId: String)
}
