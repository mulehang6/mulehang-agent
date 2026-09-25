package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.chat.attention.ConversationAttentionEvent
import com.agent.shared.chat.attention.ConversationAttentionRepository
import com.agent.shared.chat.attention.ConversationAttentionType

/** 将运行事件写入关注表，并维护当前窗口的未查看标识。 */
internal class ChatAttentionController(private val window: ChatWindowState) {
    /** 窗口失焦或最小化时，当前会话也需要系统提醒。 */
    var windowVisibleAndFocused: Boolean = true
        private set

    /** 同步系统窗口可见性；重新看到当前会话即清理已处理的提示。 */
    fun setWindowVisibleAndFocused(visible: Boolean) {
        windowVisibleAndFocused = visible
        if (visible) window.ui.activeTaskId.takeIf(String::isNotBlank)?.let(::markViewed)
    }

    /** 加载关系表中仍需关注的事件。 */
    fun hydrate(
        conversation: ChatConversationUiState,
        unread: List<ConversationAttentionEvent>,
    ): ChatConversationUiState = conversation.copy(
        attentionEvents = unread.filter { it.conversationId == conversation.id },
    )

    /** 在流事件归并到条目树之后保存定位目标。 */
    fun record(conversationId: String, event: AgentStreamEvent) {
        val details = when (event) {
            is AgentStreamEvent.ApprovalRequested -> Triple(
                ConversationAttentionType.APPROVAL, "等待审批", event.request.summary,
            )
            is AgentStreamEvent.QuestionRequested -> Triple(
                ConversationAttentionType.QUESTION, "等待回答",
                event.request.effectiveQuestions.firstOrNull()?.question.orEmpty(),
            )
            is AgentStreamEvent.Failed -> Triple(ConversationAttentionType.FAILED, "任务失败", event.reason)
            is AgentStreamEvent.Completed -> Triple(ConversationAttentionType.COMPLETED, "任务已完成", event.text)
            else -> return
        }
        val repository = window.attentionRepository ?: return
        val conversation = window.findConversationOrNull(conversationId) ?: return
        val attention = repository.create(
            conversationId = conversationId,
            entryId = conversation.activeEntryId,
            type = details.first,
            title = details.second,
            body = details.third,
        )
        val isVisible = windowVisibleAndFocused && window.ui.activeTaskId == conversationId
        if (isVisible) repository.markViewed(conversationId)
        refresh(repository, conversationId)
        if (!isVisible) runCatching { window.onAttentionNotification(attention) }
    }

    /** 用户进入会话时仅消除已查看的终态，行动请求仍需真正处理。 */
    fun markViewed(conversationId: String) {
        val repository = window.attentionRepository ?: return
        repository.markViewed(conversationId)
        refresh(repository, conversationId)
    }

    /** 用户提交回答或审批后清除对应的待处理提醒。 */
    fun resolve(conversationId: String, type: ConversationAttentionType) {
        val repository = window.attentionRepository ?: return
        val events = window.findConversationOrNull(conversationId)?.attentionEvents.orEmpty()
        events.filter { it.type == type }.forEach { repository.resolve(it.id) }
        refresh(repository, conversationId)
    }

    /** 把数据库中仍待关注的事件投影到侧栏。 */
    private fun refresh(repository: ConversationAttentionRepository, conversationId: String) {
        val remaining = repository.unread().filter { it.conversationId == conversationId }
        window.mutateConversation(conversationId) { it.copy(attentionEvents = remaining) }
    }
}
