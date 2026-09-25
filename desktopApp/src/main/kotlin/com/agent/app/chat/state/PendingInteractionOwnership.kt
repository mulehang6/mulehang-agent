package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.tool.interaction.SavedInteractionRequest

/** 恢复内存归属记录缺失时，从尚待处理的会话中找回提问目标。 */
internal fun ChatWindowState.resolvePendingQuestionConversationId(): String? =
    pendingQuestionConversationId ?: ui.tasks.firstOrNull { it.pendingQuestion != null }?.id

/** 恢复内存归属记录缺失时，从尚待处理的会话中找回审批目标。 */
internal fun ChatWindowState.resolvePendingApprovalConversationId(): String? =
    pendingApprovalConversationId ?: ui.tasks.firstOrNull { it.pendingApproval != null }?.id

/** 轮次结束后清除内存中的挂起请求归属。 */
internal fun ChatWindowState.clearPendingInteractionOwnership(conversationId: String) {
    if (pendingQuestionConversationId == conversationId) pendingQuestionConversationId = null
    if (pendingApprovalConversationId == conversationId) pendingApprovalConversationId = null
}

/** 从关系表恢复卡片内容，同时维持中断运行的可继续状态。 */
internal fun ChatWindowState.withRestoredPendingInteraction(
    conversation: ChatConversationUiState,
): ChatConversationUiState {
    if (conversation.executionState != ExecutionState.Interrupted &&
        conversation.executionState != ExecutionState.Paused
    ) return conversation
    val saved = interactionRequestRepository?.pending(conversation.id) ?: return conversation
    val event = when (saved) {
        is SavedInteractionRequest.Question -> AgentStreamEvent.QuestionRequested(saved.request)
        is SavedInteractionRequest.Approval -> AgentStreamEvent.ApprovalRequested(saved.request)
    }
    return reduceAgentEvent(conversation, event, contextWindowForConversation(conversation))
        .copy(executionState = conversation.executionState)
}
