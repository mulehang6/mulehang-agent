package com.agent.shared.application

import com.agent.shared.config.ProfileSelectionResolver

/**
 * 应用会话加载用例骨架。
 */
class LoadAppSessionUseCase(
    private val repository: AppSessionRepository,
) {
    /**
     * 加载应用会话快照。
     */
    suspend operator fun invoke(): AppSessionSnapshot {
        val profiles = repository.loadProfiles()
        val rememberedProfileId = repository.loadRememberedProfileId()
        val activeProfile = ProfileSelectionResolver.selectActiveProfile(profiles, rememberedProfileId)
        if (activeProfile != null) {
            repository.saveRememberedProfileId(activeProfile.id)
        }
        return AppSessionSnapshot(
            profiles = profiles,
            activeProfile = activeProfile,
        )
    }
}
