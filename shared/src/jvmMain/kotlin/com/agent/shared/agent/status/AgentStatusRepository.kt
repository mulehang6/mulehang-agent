package com.agent.shared.agent.status

import com.agent.shared.persistence.DesktopPersistenceDatabase
import kotlinx.serialization.json.Json

/** 从关系化状态记录读取详情，供 UI 与模型消息对照展示。 */
class AgentStatusRepository(private val database: DesktopPersistenceDatabase) {
    /** 读取最近一次已实际附加到模型上下文的状态文本。 */
    fun latest(conversationId: String): SavedAgentStatus? = database.read { queries ->
        queries.selectLatestStatusSnapshot(conversationId).executeAsOneOrNull()?.let { row ->
            SavedAgentStatus(
                snapshot = Json.decodeFromString<AgentStatusSnapshot>(row.payload_json),
                modelMessageText = row.model_message_text,
            )
        }
    }
}

/** 可直接在状态详情中展示的原始状态消息及结构化字段。 */
data class SavedAgentStatus(val snapshot: AgentStatusSnapshot, val modelMessageText: String)
