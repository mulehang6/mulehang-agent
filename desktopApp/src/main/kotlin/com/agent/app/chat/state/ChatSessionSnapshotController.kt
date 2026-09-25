package com.agent.app.chat.state

import com.agent.shared.session.AppSessionSnapshot

/** 更新配置并重新投影会话的模型能力与上下文窗口。 */
internal fun ChatWindowState.applySessionSnapshot(nextSnapshot: AppSessionSnapshot) {
    snapshot = nextSnapshot
    val selectedProfileId = ui.selectedProfileId
        ?.takeIf { profileId -> nextSnapshot.profiles.any { it.id == profileId } }
        ?: nextSnapshot.activeProfile?.id
        ?: nextSnapshot.profiles.firstOrNull()?.id
    ui = ui.copy(
        selectedProfileId = selectedProfileId,
        newReasoningEffort = nextSnapshot.profiles.firstOrNull { it.id == selectedProfileId }
            ?.let { profile -> resolvedReasoningEffort(profile, ui.newReasoningEffort) }
            ?: ui.newReasoningEffort,
        tasks = ui.tasks.map { conversation ->
            val boundProfile = conversation.profileId?.let { profileId ->
                nextSnapshot.profiles.firstOrNull { it.id == profileId }
            }
            // 未绑定 profile 的会话跟随窗口默认配置。
            val effectiveProfile = boundProfile
                ?: nextSnapshot.profiles.firstOrNull { it.id == selectedProfileId }
                ?: nextSnapshot.activeProfile
            conversation.copy(
                profileId = conversation.profileId?.takeIf { boundProfile != null },
                reasoningEffort = effectiveProfile?.let { profile ->
                    resolvedReasoningEffort(profile, conversation.reasoningEffort)
                } ?: conversation.reasoningEffort,
            ).withRecalculatedContextUsage(effectiveProfile?.let(::contextWindowFor))
        },
    )
    persistenceCoordinator?.schedule(ui.tasks)
}
