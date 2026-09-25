package com.agent.shared.agent.koog

import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** 仅在首次模型请求尚未启动工具时，压缩历史并重试一次。 */
internal suspend fun runWithContextOverflowRetry(
    request: AgentRunRequest,
    database: DesktopPersistenceDatabase?,
    emitEvent: suspend (AgentStreamEvent) -> Unit,
    compact: (suspend (AgentRunRequest, String) -> AgentRunRequest)? = null,
    run: suspend (AgentRunRequest, suspend (AgentStreamEvent) -> Unit) -> String,
): String {
    var toolStarted = false
    try {
        return run(request) { event ->
            if (event is AgentStreamEvent.ToolCallStarted) toolStarted = true
            emitEvent(event)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        if ((database == null && compact == null) || request.resumeRunId != null || toolStarted || !isContextOverflow(error)) throw error
        val retryRunId = UUID.randomUUID().toString()
        val compacted = compact?.invoke(request.copy(contextUsageFraction = 1f), retryRunId)
            ?: ContextCompactionCoordinator(requireNotNull(database)).prepare(
                request.copy(contextUsageFraction = 1f), retryRunId,
            )
        if (compacted.history == request.history ||
            estimateHistoryTokens(compacted.history) >= estimateHistoryTokens(request.history)
        ) throw error
        request.traceId.takeIf(String::isNotBlank)?.let { previousRunId ->
            database?.let { AgentRunRecoveryRepository(it).abandonRun(previousRunId) }
        }
        emitEvent(AgentStreamEvent.Status("上下文已压缩，正在重试一次。"))
        return run(compacted.copy(traceId = retryRunId)) { event -> emitEvent(event) }
    }
}

/** 只识别提供商明确说明上下文窗口或 token 上限的错误。 */
internal fun isContextOverflow(error: Throwable): Boolean = generateSequence(error) { it.cause }
    .take(8)
    .mapNotNull(Throwable::message)
    .any(::isContextOverflowMessage)

/** UI 只在提供商明确报告上下文超限时提供同一轮的恢复入口。 */
fun isContextOverflowMessage(message: String): Boolean {
    val normalized = message.lowercase()
    return listOf(
        "context_length_exceeded",
        "maximum context length",
        "context window exceeded",
        "exceeds the context window",
        "too many tokens",
        "input tokens exceed",
    ).any(normalized::contains)
}
