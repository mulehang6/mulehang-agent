package com.agent.shared.agent.koog

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 压缩摘要的版本化载荷；原始条目始终由会话仓库保留。 */
@Serializable
internal data class SavedContextCompaction(
    val version: Int = 1,
    val summary: String,
    val prefixCount: Int,
    val prefixDigest: String,
)

/** 在模型运行前生成或重用最近摘要，失败时继续使用原始历史。 */
internal class ContextCompactionCoordinator(
    private val database: DesktopPersistenceDatabase,
    private val generate: suspend (AgentRunRequest, List<AgentConversationHistoryMessage>) -> String =
        KoogContextCompactionGenerator()::generate,
) {
    /** 当前恢复图已冻结 prompt，不重新压缩。 */
    suspend fun prepare(request: AgentRunRequest, runId: String): AgentRunRequest {
        if (request.resumeRunId != null || request.sessionId.isBlank() || request.contextAlreadyCompacted) return request
        val plan = planContextCompaction(request)
        if (plan == null) return reuseSavedSummary(request)
        val prefix = request.history.take(plan.prefixCount)
        val summary = try {
            generate(request, prefix).trim().takeIf(String::isNotBlank) ?: return request
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return request
        }
        val saved = SavedContextCompaction(
            summary = summary,
            prefixCount = plan.prefixCount,
            prefixDigest = digest(prefix),
        )
        database.write { queries ->
            queries.insertCompaction(
                id = UUID.randomUUID().toString(),
                conversation_id = request.sessionId,
                run_id = runId.takeIf { queries.selectAgentRun(it).executeAsOneOrNull() != null },
                through_entry_id = null,
                payload_version = 1,
                summary_json = json.encodeToString(saved),
                source_token_count = plan.sourceTokens.toLong(),
                retained_token_count = plan.retainedTokens.toLong(),
                created_at = System.currentTimeMillis(),
            )
        }
        return withSummary(request, saved)
    }

    /** 路径变更或回退导致前缀变化时拒绝沿用旧摘要。 */
    private fun reuseSavedSummary(request: AgentRunRequest): AgentRunRequest {
        val row = database.read { it.selectLatestCompaction(request.sessionId).executeAsOneOrNull() }
            ?: return request
        val saved = runCatching { json.decodeFromString<SavedContextCompaction>(row.summary_json) }.getOrNull()
            ?: return request
        if (saved.version != 1 || saved.prefixCount > request.history.size || saved.prefixCount <= 0) return request
        if (digest(request.history.take(saved.prefixCount)) != saved.prefixDigest) return request
        return withSummary(request, saved)
    }

    /** 将摘要作为内部历史开头，并完整保留最近轮次。 */
    private fun withSummary(request: AgentRunRequest, saved: SavedContextCompaction): AgentRunRequest = request.copy(
        contextAlreadyCompacted = true,
        history = listOf(AgentConversationHistoryMessage.User("Earlier conversation summary:\n${saved.summary}")) +
            request.history.drop(saved.prefixCount),
        contextUsageFraction = request.contextWindow?.takeIf { it > 0 }?.let { window ->
            (estimateHistoryTokens(
                listOf(AgentConversationHistoryMessage.User("Earlier conversation summary:\n${saved.summary}")) +
                    request.history.drop(saved.prefixCount),
            ).toFloat() / window).coerceAtLeast(0f)
        } ?: request.contextUsageFraction,
    )

    /** 对原始前缀做稳定摘要签名，防止跨分支误用。 */
    private fun digest(history: List<AgentConversationHistoryMessage>): String = MessageDigest.getInstance("SHA-256")
        .digest(renderCompactionSource(history).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val json = Json { encodeDefaults = true }
    }
}
