package com.agent.shared.application

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
        TODO("Implement startup session loading and remembered profile restoration.")
    }
}
