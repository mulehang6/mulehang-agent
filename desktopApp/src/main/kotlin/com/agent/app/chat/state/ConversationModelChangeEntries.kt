package com.agent.app.chat.state

import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.ConversationEntry

/** 在当前条目路径上记录模型切换，并维护会话的选择投影。 */
internal fun recordModelChangeEntry(
    conversation: ChatConversationUiState,
    profileId: String?,
    entryId: String,
    createdAt: Long,
): ChatConversationUiState {
    if (conversation.treeFormatVersion <= 0) return conversation.copy(profileId = profileId)
    val entry = ConversationEntry.ModelChange(entryId, conversation.activeEntryId, createdAt, profileId)
    return conversation.withAppendedEntryProjection(conversation.entries + entry, entry.id)
        .copy(profileId = profileId)
}

/** 在当前条目路径上记录推理强度切换。 */
internal fun recordReasoningEffortChangeEntry(
    conversation: ChatConversationUiState,
    effort: ReasoningEffort,
    entryId: String,
    createdAt: Long,
): ChatConversationUiState {
    if (conversation.treeFormatVersion <= 0) return conversation.copy(reasoningEffort = effort)
    val entry = ConversationEntry.ReasoningEffortChange(
        id = entryId,
        parentId = conversation.activeEntryId,
        createdAt = createdAt,
        reasoningEffort = effort.name,
    )
    return conversation.withAppendedEntryProjection(conversation.entries + entry, entry.id)
        .copy(reasoningEffort = effort)
}
