package com.agent.shared.agent.koog

import com.agent.shared.persistence.DesktopPersistenceDatabase

/** 查询会话最近一次中断运行及其恢复点，供桌面继续按钮使用。 */
class AgentRunRecoveryRepository(private val database: DesktopPersistenceDatabase) {
    /** 读取当前未终结的运行；缺失恢复点时同时说明是否曾启动工具。 */
    fun interruptedRun(conversationId: String): InterruptedAgentRun? = database.read { queries ->
        val run = queries.selectActiveRunForConversation(conversationId).executeAsOneOrNull()
            ?: return@read null
        if (run.state !in setOf("INTERRUPTED", "RUNNING")) return@read null
        InterruptedAgentRun(
            id = run.id,
            userEntryId = run.user_entry_id,
            hasCheckpoint = queries.selectRecoveryCheckpointsForRun(run.id).executeAsList().isNotEmpty(),
            hasToolCalls = queries.selectToolInvocationsForRun(run.id).executeAsList().isNotEmpty(),
        )
    }

    /** 安全重新启动尚未到第一个节点的运行前，终结其空恢复记录。 */
    fun abandonRun(runId: String) {
        val now = System.currentTimeMillis()
        database.write { queries ->
            val run = queries.selectAgentRun(runId).executeAsOneOrNull() ?: return@write
            queries.upsertAgentRun(
                id = run.id,
                conversation_id = run.conversation_id,
                user_entry_id = run.user_entry_id,
                state = "RESTARTED",
                model_id = run.model_id,
                strategy_fingerprint = run.strategy_fingerprint,
                tool_fingerprint = run.tool_fingerprint,
                started_at = run.started_at,
                updated_at = now,
                finished_at = now,
                error_json = null,
            )
        }
    }
}

/** 一个待继续运行的最小关系化标识和安全性信息。 */
data class InterruptedAgentRun(
    val id: String,
    val userEntryId: String?,
    val hasCheckpoint: Boolean,
    val hasToolCalls: Boolean,
)
