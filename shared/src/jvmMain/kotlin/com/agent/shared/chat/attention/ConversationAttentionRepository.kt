package com.agent.shared.chat.attention

import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.util.UUID

/** 持久化关注事件并按已查看与行动完成两个维度分别清理。 */
class ConversationAttentionRepository(private val database: DesktopPersistenceDatabase) {
    /** 记录运行事件，保留指向具体会话条目的定位信息。 */
    fun create(
        conversationId: String,
        entryId: String?,
        type: ConversationAttentionType,
        title: String,
        body: String,
    ): ConversationAttentionEvent {
        val event = ConversationAttentionEvent(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            entryId = entryId,
            type = type,
            title = title,
            body = body,
            actionResolved = false,
            readAt = null,
            createdAt = System.currentTimeMillis(),
        )
        database.write { queries ->
            queries.insertAttentionEvent(
                id = event.id,
                conversation_id = event.conversationId,
                entry_id = event.entryId,
                type = event.type.name,
                title = event.title,
                body = event.body,
                action_resolved = false,
                read_at = null,
                created_at = event.createdAt,
            )
        }
        return event
    }

    /** 返回尚需侧栏提示的事件。 */
    fun unread(): List<ConversationAttentionEvent> = database.read { queries ->
        queries.selectUnreadAttentionEvents().executeAsList().map { row ->
            ConversationAttentionEvent(
                id = row.id,
                conversationId = row.conversation_id,
                entryId = row.entry_id,
                type = ConversationAttentionType.valueOf(row.type),
                title = row.title,
                body = row.body,
                actionResolved = row.action_resolved,
                readAt = row.read_at,
                createdAt = row.created_at,
            )
        }
    }

    /** 查看会话后清除终态事件；未处理的审批和提问继续保留。 */
    fun markViewed(conversationId: String) {
        database.write { queries -> queries.markAttentionViewed(System.currentTimeMillis(), conversationId) }
    }

    /** 用户实际处理审批或问题后清除对应行动事件。 */
    fun resolve(eventId: String) {
        database.write { queries -> queries.resolveAttentionEvent(System.currentTimeMillis(), eventId) }
    }
}
