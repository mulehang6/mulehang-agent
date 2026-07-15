package com.agent.app.chat.state

import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.session.AppSessionSnapshot
import java.util.UUID

/**
 * 基于当前项目路径和 profile 快照建立初始 UI 状态。
 */
internal fun initialUiState(
    snapshot: AppSessionSnapshot,
    projectPath: String,
): ChatWindowUiState {
    if (projectPath.isBlank()) {
        return ChatWindowUiState(
            tasks = emptyList(),
            activeTaskId = "",
            selectedProfileId = snapshot.activeProfile?.id ?: snapshot.profiles.firstOrNull()?.id,
        )
    }
    val initialConversation = newConversation(
        workspacePath = projectPath,
        contextWindow = snapshot.activeProfile?.let(::resolveContextWindow),
    )
    return ChatWindowUiState(
        tasks = listOf(initialConversation),
        activeTaskId = initialConversation.id,
        selectedProfileId = snapshot.activeProfile?.id ?: snapshot.profiles.firstOrNull()?.id,
    )
}

/**
 * 创建一条空白会话。
 */
internal fun newConversation(
    workspacePath: String,
    contextWindow: Int?,
): ChatConversationUiState = ChatConversationUiState(
    id = UUID.randomUUID().toString(),
    title = DEFAULT_CONVERSATION_TITLE,
    workspacePath = workspacePath,
    contextUsageFraction = estimateContextUsage(
        items = emptyList(),
        attachmentCount = 0,
        contextWindow = contextWindow,
    ),
)

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

internal const val DEFAULT_CONVERSATION_TITLE = "新对话"

private const val CONVERSATION_TITLE_MAX_LENGTH = 24
