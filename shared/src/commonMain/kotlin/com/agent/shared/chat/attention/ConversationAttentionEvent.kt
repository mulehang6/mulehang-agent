package com.agent.shared.chat.attention

/** 需要会话所有者关注的运行事件。 */
enum class ConversationAttentionType { APPROVAL, QUESTION, FAILED, COMPLETED }

/** 关系表中的关注事件；行动类事件在完成处理前保持待关注。 */
data class ConversationAttentionEvent(
    val id: String,
    val conversationId: String,
    val entryId: String?,
    val type: ConversationAttentionType,
    val title: String,
    val body: String,
    val actionResolved: Boolean,
    val readAt: Long?,
    val createdAt: Long,
)
