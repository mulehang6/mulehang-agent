package com.agent.app.chat.state

import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.resolver.ModelCapabilitiesResolver
import java.util.UUID

/**
 * 基于当前项目路径和 profile 快照建立初始 UI 状态。
 */
internal fun initialUiState(
    snapshot: AppSessionSnapshot,
    projectPath: String,
): ChatWindowUiState {
    val selectedProfile = snapshot.activeProfile ?: snapshot.profiles.firstOrNull()
    if (projectPath.isBlank()) {
        return ChatWindowUiState(
            tasks = emptyList(),
            activeTaskId = "",
            selectedProfileId = selectedProfile?.id,
        )
    }
    val initialConversation = newConversation(
        workspacePath = projectPath,
        contextWindow = selectedProfile?.let(::resolveContextWindow),
        reasoningEffort = selectedProfile?.let(::defaultReasoningEffortFor) ?: ReasoningEffort.MEDIUM,
    )
    return ChatWindowUiState(
        tasks = listOf(initialConversation),
        activeTaskId = initialConversation.id,
        selectedProfileId = selectedProfile?.id,
    )
}

/**
 * 创建一条空白会话。
 */
internal fun newConversation(
    workspacePath: String,
    contextWindow: Int?,
    reasoningEffort: ReasoningEffort,
): ChatConversationUiState = ChatConversationUiState(
    id = UUID.randomUUID().toString(),
    title = DEFAULT_CONVERSATION_TITLE,
    workspacePath = workspacePath,
    reasoningEffort = reasoningEffort,
    contextUsageFraction = estimateContextUsage(
        items = emptyList(),
        attachmentCount = 0,
        contextWindow = contextWindow,
    ),
)

/**
 * 返回 profile 在新会话中应展示的 reasoning 默认档位。
 */
internal fun defaultReasoningEffortFor(profile: ConfigProfile): ReasoningEffort =
    ModelCapabilitiesResolver.resolve(profile).defaultReasoningEffort ?: ReasoningEffort.MEDIUM

/**
 * 根据首条用户消息生成本地短标题。
 */
internal fun buildConversationTitle(prompt: String): String {
    val firstLine = prompt
        .lineSequence()
        .map { line -> line.trim().replace(Regex("\\s+"), " ") }
        .firstOrNull(String::isNotBlank)
        .orEmpty()
    return firstLine.take(CONVERSATION_TITLE_MAX_LENGTH).ifBlank { DEFAULT_CONVERSATION_TITLE }
}

/**
 * 判断会话是否仍是未使用过的默认空会话。
 */
internal fun ChatConversationUiState.isEmptyDefaultConversation(): Boolean =
    title == DEFAULT_CONVERSATION_TITLE &&
            items.isEmpty() &&
            attachments.isEmpty() &&
            history.isEmpty() &&
            pendingQuestion == null &&
            pendingApproval == null &&
            executionState == ExecutionState.Idle

internal const val DEFAULT_CONVERSATION_TITLE = "新建对话"

private const val CONVERSATION_TITLE_MAX_LENGTH = 24
