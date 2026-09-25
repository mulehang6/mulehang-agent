package com.agent.shared.agent.koog

import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.persistence.DesktopPersistenceDatabase
import com.agent.shared.persistence.db.MulehangDatabaseQueries
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 将运行中的工具调用、部分输出和工具审计实时关联到当前 Agent run。 */
internal class AgentRunEventJournal(
    private val database: DesktopPersistenceDatabase,
    private val conversationId: String,
    private val runId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** 每个事件独立提交，暂停或崩溃时仍能识别未完成的工具调用。 */
    fun record(event: AgentStreamEvent) {
        if (runId.isBlank()) return
        database.write { queries ->
            if (queries.selectAgentRun(runId).executeAsOneOrNull() == null) return@write
            when (event) {
                is AgentStreamEvent.ToolCallStarted -> start(queries, event)
                is AgentStreamEvent.ToolOutputDelta -> appendOutput(queries, event)
                is AgentStreamEvent.ToolCallFinished -> finish(queries, event, "COMPLETED", event.resultDisplay ?: event.resultPreview)
                is AgentStreamEvent.ToolCallFailed -> finish(queries, event, "FAILED", event.reason)
                is AgentStreamEvent.ToolCallInterrupted -> interrupt(queries, event)
                else -> Unit
            }
        }
    }

    /** 合成结果与原调用、部分输出同列保存；重复转发时保持幂等。 */
    private fun interrupt(queries: MulehangDatabaseQueries, event: AgentStreamEvent.ToolCallInterrupted) {
        val previous = queries.selectToolInvocationsForRun(runId).executeAsList().lastOrNull { row ->
            row.tool_name == event.name && (event.toolCallId == null || row.id == "$runId:${event.toolCallId}")
        }
        if (previous?.state == "INTERRUPTED") return
        val now = clock()
        val invocationId = previous?.id ?: event.toolCallId?.let { "$runId:$it" } ?: UUID.randomUUID().toString()
        queries.upsertToolInvocation(
            id = invocationId,
            conversation_id = conversationId,
            run_id = runId,
            entry_id = previous?.entry_id,
            tool_name = event.name,
            arguments_version = 1L,
            arguments_json = event.argumentsJson,
            state = "INTERRUPTED",
            result_version = 1L,
            result_json = buildJsonObject { put("text", event.reason) }.toString(),
            partial_output = event.partialOutput,
            started_at = previous?.started_at ?: now,
            finished_at = now,
        )
        audit(queries, invocationId, "TOOL_INTERRUPTED", buildJsonObject {
            put("name", event.name)
            put("reason", event.reason)
        }.toString(), now)
    }

    /** 以原始参数和稳定工具调用 ID 保存待完成调用。 */
    private fun start(queries: MulehangDatabaseQueries, event: AgentStreamEvent.ToolCallStarted) {
        val now = clock()
        val invocationId = event.toolCallId?.let { "$runId:$it" } ?: UUID.randomUUID().toString()
        queries.upsertToolInvocation(
            id = invocationId,
            conversation_id = conversationId,
            run_id = runId,
            entry_id = null,
            tool_name = event.name,
            arguments_version = 1L,
            arguments_json = event.argumentsJson ?: buildJsonObject {
                put("preview", event.argumentsPreview.orEmpty())
            }.toString(),
            state = "RUNNING",
            result_version = null,
            result_json = null,
            partial_output = null,
            started_at = now,
            finished_at = null,
        )
        audit(queries, invocationId, "TOOL_STARTED", buildJsonObject {
            put("name", event.name)
            put("arguments", event.argumentsJson ?: event.argumentsPreview.orEmpty())
        }.toString(), now)
    }

    /** 将终端部分输出附在未完成调用上，供中断后展示不确定结果。 */
    private fun appendOutput(queries: MulehangDatabaseQueries, event: AgentStreamEvent.ToolOutputDelta) {
        val invocation = openInvocation(queries, event.toolCallId, event.name) ?: return
        queries.upsertToolInvocation(
            id = invocation.id,
            conversation_id = conversationId,
            run_id = runId,
            entry_id = invocation.entry_id,
            tool_name = invocation.tool_name,
            arguments_version = invocation.arguments_version,
            arguments_json = invocation.arguments_json,
            state = "RUNNING",
            result_version = null,
            result_json = null,
            partial_output = (invocation.partial_output.orEmpty() + event.text).takeLast(MAX_PARTIAL_OUTPUT_CHARS),
            started_at = invocation.started_at,
            finished_at = null,
        )
    }

    /** 结束最近的匹配调用；缺失开始事件时保留一条可审计的孤立结果。 */
    private fun finish(
        queries: MulehangDatabaseQueries,
        event: AgentStreamEvent,
        state: String,
        result: String?,
    ) {
        val name = when (event) {
            is AgentStreamEvent.ToolCallFinished -> event.name
            is AgentStreamEvent.ToolCallFailed -> event.name
            else -> return
        }
        val toolCallId = when (event) {
            is AgentStreamEvent.ToolCallFinished -> event.toolCallId
            is AgentStreamEvent.ToolCallFailed -> event.toolCallId
        }
        val invocation = openInvocation(queries, toolCallId, name)
        val now = clock()
        val invocationId = invocation?.id ?: UUID.randomUUID().toString()
        queries.upsertToolInvocation(
            id = invocationId,
            conversation_id = conversationId,
            run_id = runId,
            entry_id = invocation?.entry_id,
            tool_name = name,
            arguments_version = 1L,
            arguments_json = invocation?.arguments_json ?: "{}",
            state = state,
            result_version = 1L,
            result_json = buildJsonObject { put("text", result.orEmpty()) }.toString(),
            partial_output = invocation?.partial_output,
            started_at = invocation?.started_at ?: now,
            finished_at = now,
        )
        audit(queries, invocationId, "TOOL_$state", buildJsonObject {
            put("name", name)
            put("result", result.orEmpty())
        }.toString(), now)
    }

    /** 从本轮待完成调用中寻找同 ID 或同名的最近一项。 */
    private fun openInvocation(
        queries: MulehangDatabaseQueries,
        toolCallId: String?,
        name: String,
    ) = queries.selectToolInvocationsForRun(runId).executeAsList()
        .lastOrNull { row ->
            row.finished_at == null && row.tool_name == name &&
                (toolCallId == null || row.id == "$runId:$toolCallId")
        }

    /** 保存一个有类型且带版本号的审计事件。 */
    private fun audit(queries: MulehangDatabaseQueries, invocationId: String, type: String, payload: String, now: Long) {
        queries.insertToolAudit(
            id = UUID.randomUUID().toString(),
            conversation_id = conversationId,
            run_id = runId,
            invocation_id = invocationId,
            event_type = type,
            payload_version = 1L,
            payload_json = payload,
            created_at = now,
        )
    }

    private companion object {
        const val MAX_PARTIAL_OUTPUT_CHARS = 100_000
    }
}
