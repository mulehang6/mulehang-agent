package com.agent.shared.agent.status

import com.agent.shared.persistence.DesktopPersistenceDatabase

/** 为一个会话原子维护有序 TODO，跨用户消息持续保存。 */
class AgentTodoRepository(private val database: DesktopPersistenceDatabase) {
    /** 按模型看到的顺序读取当前 TODO。 */
    fun list(conversationId: String): List<AgentTodoItem> = database.read { queries ->
        queries.selectTodosForConversation(conversationId).executeAsList().map { row ->
            AgentTodoItem(
                id = row.id,
                content = row.content,
                status = AgentTodoStatus.valueOf(row.status),
                sortOrder = row.sort_order.toInt(),
                createdAt = row.created_at,
                updatedAt = row.updated_at,
            )
        }
    }

    /** 整体重写清单；保留同 ID 项的创建时间，验证失败时原清单不变。 */
    fun rewrite(conversationId: String, items: List<AgentTodoDraft>): List<AgentTodoItem> {
        require(items.size <= MAX_TODOS) { "TODO 不能超过 $MAX_TODOS 项。" }
        require(items.map(AgentTodoDraft::id).distinct().size == items.size) { "TODO ID 不能重复。" }
        items.forEach { item ->
            require(item.id.isNotBlank() && item.id.length <= 80) { "TODO ID 长度必须为 1–80。" }
            require(item.content.isNotBlank() && item.content.length <= 2_000) { "TODO 内容长度必须为 1–2000。" }
        }
        val now = System.currentTimeMillis()
        database.write { queries ->
            val previous = queries.selectTodosForConversation(conversationId).executeAsList().associateBy { it.id }
            queries.deleteTodosForConversation(conversationId)
            items.forEachIndexed { index, item ->
                queries.insertTodo(
                    conversation_id = conversationId,
                    id = item.id,
                    content = item.content.trim(),
                    status = item.status.name,
                    sort_order = index.toLong(),
                    created_at = previous[item.id]?.created_at ?: now,
                    updated_at = now,
                )
            }
        }
        return list(conversationId)
    }

    /** 只更新现有项目的状态，未知 ID 不会隐式创建新项目。 */
    fun updateStatus(conversationId: String, id: String, status: AgentTodoStatus): List<AgentTodoItem> {
        val now = System.currentTimeMillis()
        database.write { queries ->
            val existing = queries.selectTodosForConversation(conversationId).executeAsList()
            require(existing.any { it.id == id }) { "找不到 TODO：$id" }
            queries.deleteTodosForConversation(conversationId)
            existing.forEach { item ->
                queries.insertTodo(
                    conversation_id = conversationId,
                    id = item.id,
                    content = item.content,
                    status = if (item.id == id) status.name else item.status,
                    sort_order = item.sort_order,
                    created_at = item.created_at,
                    updated_at = if (item.id == id) now else item.updated_at,
                )
            }
        }
        return list(conversationId)
    }

    private companion object {
        const val MAX_TODOS = 100
    }
}

/** 整体重写时提交的项目内容。 */
data class AgentTodoDraft(val id: String, val content: String, val status: AgentTodoStatus)
