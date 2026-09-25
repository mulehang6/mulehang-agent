package com.agent.shared.agent.koog

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import com.agent.shared.persistence.DesktopPersistenceDatabase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 检查点与当前运行不兼容或内容损坏时阻止自动重跑。 */
class UnavailableAgentCheckpointException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/**
 * 把 Koog 的连续检查点保存为每轮唯一的 RECOVERY 行。
 *
 * USER_TURN 是用户时间线快照，不经此 provider 读写。完成 tombstone 只删除恢复点。
 */
class SqlDelightPersistenceStorageProvider(
    private val persistence: DesktopPersistenceDatabase,
    private val runId: String,
    private val conversationId: String,
    private val modelFingerprint: String,
    private val toolFingerprint: String,
    private val userEntryId: String? = null,
) : PersistenceStorageProvider<Unit> {
    /** 返回本轮恢复点；Koog 的过滤参数目前没有对应的业务维度。 */
    override suspend fun getCheckpoints(sessionId: String, filter: Unit?): List<AgentCheckpointData> {
        requireSession(sessionId)
        return persistence.read { queries ->
            queries.selectRecoveryCheckpointsForRun(runId).executeAsList().map { row ->
                if (
                    row.koog_version != KOOG_VERSION ||
                    row.strategy_fingerprint != STRATEGY_FINGERPRINT ||
                    row.model_fingerprint != modelFingerprint ||
                    row.tool_fingerprint != toolFingerprint ||
                    row.payload_version != PAYLOAD_VERSION
                ) {
                    throw UnavailableAgentCheckpointException("运行环境已变化，当前轮次无法从检查点恢复。")
                }
                try {
                    decodeCheckpoint(row.payload_json)
                } catch (error: Exception) {
                    throw UnavailableAgentCheckpointException("检查点内容损坏，当前轮次无法恢复。", error)
                }
            }
        }
    }

    /** 严格序列化、反序列化后才覆盖旧恢复点，避免写入无法读取的状态。 */
    override suspend fun saveCheckpoint(sessionId: String, agentCheckpointData: AgentCheckpointData) {
        requireSession(sessionId)
        if (agentCheckpointData.isTombstone()) {
            persistence.write { queries -> queries.deleteRecoveryCheckpointsForRun(runId) }
            return
        }
        val encoded = encodeCheckpoint(agentCheckpointData)
        val now = System.currentTimeMillis()
        persistence.write { queries ->
            queries.deleteRecoveryCheckpointsForRun(runId)
            queries.insertCheckpoint(
                id = agentCheckpointData.checkpointId,
                conversation_id = conversationId,
                run_id = runId,
                entry_id = null,
                kind = RECOVERY_KIND,
                koog_version = KOOG_VERSION,
                strategy_fingerprint = STRATEGY_FINGERPRINT,
                model_fingerprint = modelFingerprint,
                tool_fingerprint = toolFingerprint,
                payload_version = PAYLOAD_VERSION,
                payload_json = encoded,
                recovery_state = "READY",
                created_at = now,
                updated_at = now,
            )
        }
    }

    /** 读取最新恢复点。 */
    override suspend fun getLatestCheckpoint(sessionId: String, filter: Unit?): AgentCheckpointData? =
        getCheckpoints(sessionId, filter).firstOrNull()

    /** 写入 Agent run 的关系化元数据；会话必须已永久保存。 */
    fun beginRun(modelId: String) {
        val now = System.currentTimeMillis()
        persistence.write { queries ->
            val existing = queries.selectAgentRun(runId).executeAsOneOrNull()
            if (existing != null) {
                if (existing.conversation_id != conversationId || existing.model_id != modelId) {
                    throw UnavailableAgentCheckpointException("运行所属会话或模型已变化，无法恢复。")
                }
                queries.upsertAgentRun(
                    id = runId,
                    conversation_id = conversationId,
                    user_entry_id = existing.user_entry_id,
                    state = "RUNNING",
                    model_id = existing.model_id,
                    strategy_fingerprint = STRATEGY_FINGERPRINT,
                    tool_fingerprint = toolFingerprint,
                    started_at = existing.started_at,
                    updated_at = now,
                    finished_at = null,
                    error_json = null,
                )
                return@write
            }
            queries.upsertAgentRun(
                id = runId,
                conversation_id = conversationId,
                user_entry_id = userEntryId,
                state = "RUNNING",
                model_id = modelId,
                strategy_fingerprint = STRATEGY_FINGERPRINT,
                tool_fingerprint = toolFingerprint,
                started_at = now,
                updated_at = now,
                finished_at = null,
                error_json = null,
            )
        }
    }

    /** 记录终态并清理不再可恢复的临时检查点。 */
    fun finishRun(state: String, error: String? = null) {
        val now = System.currentTimeMillis()
        persistence.write { queries ->
            val existing = queries.selectAgentRun(runId).executeAsOneOrNull() ?: return@write
            queries.upsertAgentRun(
                id = runId,
                conversation_id = conversationId,
                user_entry_id = existing.user_entry_id,
                state = state,
                model_id = existing.model_id,
                strategy_fingerprint = STRATEGY_FINGERPRINT,
                tool_fingerprint = toolFingerprint,
                started_at = existing.started_at,
                updated_at = now,
                finished_at = now,
                error_json = error,
            )
            queries.deleteRecoveryCheckpointsForRun(runId)
        }
    }

    /** 暂停保留本轮唯一恢复点，状态记录为可继续。 */
    fun interruptRun() {
        val now = System.currentTimeMillis()
        persistence.write { queries ->
            val existing = queries.selectAgentRun(runId).executeAsOneOrNull() ?: return@write
            queries.upsertAgentRun(
                id = runId,
                conversation_id = conversationId,
                user_entry_id = existing.user_entry_id,
                state = "INTERRUPTED",
                model_id = existing.model_id,
                strategy_fingerprint = STRATEGY_FINGERPRINT,
                tool_fingerprint = toolFingerprint,
                started_at = existing.started_at,
                updated_at = now,
                finished_at = null,
                error_json = null,
            )
        }
    }

    /** 阻止一个 Koog session 读取或覆盖其他运行的检查点。 */
    private fun requireSession(sessionId: String) {
        require(sessionId == runId) { "Koog session 与持久化 Agent run 不一致。" }
    }

    private companion object {
        const val KOOG_VERSION = "1.1.1"
        const val STRATEGY_FINGERPRINT = "single_run_streaming:v1"
        const val RECOVERY_KIND = "RECOVERY"
        const val PAYLOAD_VERSION = 1L
        val json = Json { encodeDefaults = true }

        /** 序列化后立即回读，确保数据经持久化边界不会改变。 */
        fun encodeCheckpoint(checkpoint: AgentCheckpointData): String {
            val encoded = json.encodeToString(checkpoint)
            val decoded = decodeCheckpoint(encoded)
            check(json.parseToJsonElement(encoded) == json.parseToJsonElement(json.encodeToString(decoded))) {
                "Koog 检查点序列化结果不可完整回读。"
            }
            return encoded
        }

        /** 恢复只接受包含图节点的完整检查点。 */
        fun decodeCheckpoint(encoded: String): AgentCheckpointData = json.decodeFromString<AgentCheckpointData>(encoded).also {
            check(it.graphProperties?.nodePath?.isNotBlank() == true) { "Koog 检查点缺少图节点。" }
        }
    }
}
