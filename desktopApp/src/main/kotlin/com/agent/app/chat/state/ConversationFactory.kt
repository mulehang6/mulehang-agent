package com.agent.app.chat.state

import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.CURRENT_CONVERSATION_TREE_FORMAT_VERSION
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.resolver.ModelCapabilitiesResolver
import com.agent.shared.tool.model.PermissionPreset
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
        profileId = selectedProfile?.id,
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
    profileId: String? = null,
    reasoningEffort: ReasoningEffort,
    permissionPreset: PermissionPreset = PermissionPreset.DEFAULT,
): ChatConversationUiState = ChatConversationUiState(
    id = UUID.randomUUID().toString(),
    title = DEFAULT_CONVERSATION_TITLE,
    workspacePath = workspacePath,
    treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
    updatedAt = System.currentTimeMillis(),
    profileId = profileId,
    reasoningEffort = reasoningEffort,
    permissionPreset = permissionPreset,
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
            attachments.isEmpty() &&
            isConversationContentEmpty() &&
            executionState == ExecutionState.Idle

/**
 * 判断会话是否还没有产生任何用户可见的执行内容。
 *
 * 运行态不参与判断：真正发送消息时会先写入用户条目，再切到运行态；因此一个没有消息、
 * 工具或回答的任务即使残留了瞬时运行标记，也必须继续显示为“新建”而不是无限转圈。
 */
internal fun ChatConversationUiState.isConversationContentEmpty(): Boolean =
    items.isEmpty() &&
            history.isEmpty() &&
            entries.none(ConversationEntry::marksConversationAsUsed) &&
            pendingQuestion == null &&
            pendingApproval == null

/** 模型、推理强度和标签只是配置元数据，不应把空白任务标记为已运行。 */
internal fun ConversationEntry.marksConversationAsUsed(): Boolean = when (this) {
    is ConversationEntry.Label,
    is ConversationEntry.ModelChange,
    is ConversationEntry.ReasoningEffortChange,
        -> false

    else -> true
}

internal const val DEFAULT_CONVERSATION_TITLE = "新建对话"

internal const val CONVERSATION_TITLE_MAX_LENGTH = 24
