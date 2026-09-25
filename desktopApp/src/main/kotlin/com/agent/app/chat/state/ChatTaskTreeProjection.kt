package com.agent.app.chat.state

import com.agent.shared.chat.attention.ConversationAttentionEvent
import com.agent.shared.chat.attention.ConversationAttentionType
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState

/** 根据运行和未读关注事件选择侧栏标识。 */
internal fun taskStatusFor(conversation: ChatConversationUiState): ChatTaskStatus {
    val attentionTypes = conversation.attentionEvents.map(ConversationAttentionEvent::type)
    val isBlankPlaceholder = conversation.isConversationContentEmpty() &&
        (conversation.executionState == ExecutionState.Idle || conversation.title == DEFAULT_CONVERSATION_TITLE)
    return when {
        ConversationAttentionType.APPROVAL in attentionTypes ||
            ConversationAttentionType.QUESTION in attentionTypes -> ChatTaskStatus.WAITING
        ConversationAttentionType.FAILED in attentionTypes -> ChatTaskStatus.FAILED
        ConversationAttentionType.COMPLETED in attentionTypes -> ChatTaskStatus.DONE
        conversation.executionState == ExecutionState.Paused ||
            conversation.executionState == ExecutionState.Interrupted -> ChatTaskStatus.PAUSED
        isBlankPlaceholder -> ChatTaskStatus.NEW
        taskGroupFor(conversation) == ChatTaskGroup.RUNNING -> ChatTaskStatus.RUNNING
        else -> ChatTaskStatus.NONE
    }
}

/** 从真实会话生成侧栏任务条目。 */
internal fun toTaskListItem(conversation: ChatConversationUiState): ChatTaskListItemUiState = ChatTaskListItemUiState(
    id = conversation.id,
    title = conversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE },
    subtitle = buildTaskSubtitle(conversation),
    group = taskGroupFor(conversation),
    status = taskStatusFor(conversation),
    titleState = conversation.titleState,
    parentConversationId = conversation.parentConversationId,
    subtreeUpdatedAt = conversation.updatedAt,
    treeFormatVersion = conversation.treeFormatVersion,
    titleRegenerationInProgress = conversation.titleRegenerationInProgress,
)

/** 缺失父节点自然提升为根，子树按最近活动时间倒序。 */
internal fun buildConversationForest(conversations: List<ChatConversationUiState>): List<ChatTaskTreeNodeUiState> {
    val byId = conversations.associateBy(ChatConversationUiState::id)
    val childrenByParent = conversations
        .filter { conversation -> conversation.parentConversationId in byId }
        .groupBy(ChatConversationUiState::parentConversationId)

    fun buildNode(conversation: ChatConversationUiState, ancestors: Set<String>): ChatTaskTreeNodeUiState {
        val nextAncestors = ancestors + conversation.id
        val children = childrenByParent[conversation.id]
            .orEmpty()
            .filterNot { child -> child.id in nextAncestors }
            .map { child -> buildNode(child, nextAncestors) }
            .sortedByDescending(ChatTaskTreeNodeUiState::subtreeUpdatedAt)
        val subtreeUpdatedAt = maxOf(conversation.updatedAt, children.maxOfOrNull { it.subtreeUpdatedAt } ?: 0L)
        return ChatTaskTreeNodeUiState(
            task = toTaskListItem(conversation).copy(subtreeUpdatedAt = subtreeUpdatedAt),
            children = children,
            subtreeUpdatedAt = subtreeUpdatedAt,
        )
    }

    return conversations
        .filter { conversation -> conversation.parentConversationId !in byId }
        .map { conversation -> buildNode(conversation, emptySet()) }
        .sortedByDescending(ChatTaskTreeNodeUiState::subtreeUpdatedAt)
}

/** 最近一条用户消息的首行优先用作任务副标题。 */
internal fun buildTaskSubtitle(conversation: ChatConversationUiState): String = conversation.items
    .asReversed()
    .filterIsInstance<ChatMessageItem>()
    .firstOrNull { it.message.role == ChatRole.User }
    ?.message
    ?.content
    ?.lineSequence()
    ?.firstOrNull(String::isNotBlank)
    ?.trim()
    ?.take(TASK_SUBTITLE_MAX_LENGTH)
    ?: buildWorkspaceLabel(conversation.workspacePath, conversation.workspaceName)

private const val TASK_SUBTITLE_MAX_LENGTH = 52
