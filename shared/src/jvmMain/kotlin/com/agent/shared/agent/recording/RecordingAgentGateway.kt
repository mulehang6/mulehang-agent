package com.agent.shared.agent.recording

import com.agent.shared.agent.api.AgentGateway
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.util.UUID
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 已完成 Agent 回合的可持久化诊断记录。
 */
@Serializable
data class AgentRunRecord(
    val runId: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val provider: String,
    val model: String,
    val finalText: String,
    val reasoning: List<AgentRunReasoningRecord>,
    val tools: List<AgentRunToolRecord>,
    val failureReason: String? = null,
)

/**
 * 单段完整 reasoning 的诊断记录。
 */
@Serializable
data class AgentRunReasoningRecord(
    val summary: String?,
    val rawText: String?,
)

/**
 * 单次工具调用的诊断记录。
 */
@Serializable
data class AgentRunToolRecord(
    val toolCallId: String?,
    val name: String,
    val arguments: String?,
    val result: String? = null,
)

/**
 * 接收完整 Agent 回合记录的横切接口。
 */
fun interface AgentRunRecorder {
    /**
     * 持久化一条已终态的回合记录。
     */
    fun record(record: AgentRunRecord)
}

/**
 * 在不改变业务事件语义的前提下，为 Agent 执行附加完成态诊断记录。
 */
class RecordingAgentGateway(
    private val delegate: AgentGateway,
    private val recorder: AgentRunRecorder,
    private val clock: Clock = Clock.systemUTC(),
    private val runIdFactory: () -> String = { UUID.randomUUID().toString() },
) : AgentGateway {
    /**
     * 透传底层事件，并在执行完成、失败或抛出异常时写入一条完整记录。
     */
    override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
        val collector = AgentRunRecordCollector(
            request = request,
            runId = runIdFactory(),
            startedAtMillis = clock.millis(),
        )
        try {
            delegate.run(request).collect { event ->
                collector.accept(event)
                if (event is AgentStreamEvent.Completed || event is AgentStreamEvent.Failed) {
                    recorder.record(collector.toRecord(clock.millis()))
                }
                emit(event)
            }
        } catch (exception: Exception) {
            if (!collector.isTerminal) {
                collector.fail(exception.message ?: "执行错误")
                recorder.record(collector.toRecord(clock.millis()))
            }
            throw exception
        }
    }
}

/**
 * 将完整回合记录追加到独立 JSON Lines 文件，避免与常规应用日志混排。
 */
class JsonLinesAgentRunRecorder(
    private val directory: Path = Paths.get("logs"),
) : AgentRunRecorder {
    /**
     * 以单行 JSON 追加记录；同步写入以避免并发回合交错损坏一行内容。
     */
    @Synchronized
    override fun record(record: AgentRunRecord) {
        Files.createDirectories(directory)
        val serializedRecord = JSON.encodeToString(record)
        Files.writeString(
            directory.resolve("agent-runs.jsonl"),
            serializedRecord + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
        )
        log.info { "Agent run record: $serializedRecord" }
    }

    private companion object {
        val log = KotlinLogging.logger { }
        val JSON = Json {
            encodeDefaults = true
        }
    }
}

/**
 * 聚合一次流式执行，直到收到终态事件后才生成可记录的数据。
 */
private class AgentRunRecordCollector(
    private val request: AgentRunRequest,
    private val runId: String,
    private val startedAtMillis: Long,
) {
    private val text = StringBuilder()
    private val pendingReasoningSummary = StringBuilder()
    private val pendingReasoningRawText = StringBuilder()
    private val reasoning = mutableListOf<AgentRunReasoningRecord>()
    private val tools = mutableListOf<MutableAgentRunToolRecord>()
    private var finalText: String? = null
    private var failureReason: String? = null

    /** 当前回合是否已收到成功或失败终态。 */
    val isTerminal: Boolean
        get() = finalText != null || failureReason != null

    /**
     * 合并单个流事件；不会触发外部 I/O。
     */
    fun accept(event: AgentStreamEvent) {
        when (event) {
            AgentStreamEvent.Started,
            is AgentStreamEvent.QuestionRequested,
            is AgentStreamEvent.ApprovalRequested,
            is AgentStreamEvent.Status,
            is AgentStreamEvent.ToolOutputDelta,
            -> Unit

            is AgentStreamEvent.TextDelta -> text.append(event.text)
            is AgentStreamEvent.ReasoningDelta -> appendReasoning(event.summary, event.rawText)
            is AgentStreamEvent.ReasoningCompleted -> completeReasoning(event.summary, event.rawText)
            is AgentStreamEvent.ToolCallStarted -> tools += MutableAgentRunToolRecord(
                toolCallId = event.toolCallId,
                name = event.name,
                arguments = event.argumentsPreview,
            )

            is AgentStreamEvent.ToolCallFinished -> tools
                .lastOrNull { tool ->
                    tool.result == null &&
                        (tool.toolCallId == event.toolCallId ||
                            (event.toolCallId == null && tool.name == event.name))
                }
                ?.apply { result = event.resultDisplay ?: event.resultPreview }
                ?: tools.add(
                    MutableAgentRunToolRecord(
                        toolCallId = event.toolCallId,
                        name = event.name,
                        arguments = null,
                        result = event.resultDisplay ?: event.resultPreview,
                    ),
                )

            is AgentStreamEvent.Completed -> {
                finalText = event.text.ifBlank { text.toString() }
                flushPendingReasoning()
            }

            is AgentStreamEvent.Failed -> {
                failureReason = event.reason
                flushPendingReasoning()
            }
        }
    }

    /**
     * 标记未通过流事件表达的异常失败。
     */
    fun fail(reason: String) {
        failureReason = reason
        flushPendingReasoning()
    }

    /**
     * 生成完整、不可变的回合记录。
     */
    fun toRecord(finishedAtMillis: Long): AgentRunRecord = AgentRunRecord(
        runId = runId,
        startedAtMillis = startedAtMillis,
        finishedAtMillis = finishedAtMillis,
        provider = request.profile.providerId,
        model = request.profile.model,
        finalText = finalText ?: text.toString(),
        reasoning = reasoning.toList(),
        tools = tools.map(MutableAgentRunToolRecord::toRecord),
        failureReason = failureReason,
    )

    /**
     * 累加仍在流式传输的 reasoning 片段。
     */
    private fun appendReasoning(summary: String?, rawText: String?) {
        summary?.let(pendingReasoningSummary::append)
        rawText?.let(pendingReasoningRawText::append)
    }

    /**
     * 使用 provider 返回的完整 reasoning 收尾当前片段。
     */
    private fun completeReasoning(summary: String?, rawText: String?) {
        reasoning += AgentRunReasoningRecord(
            summary = summary ?: pendingReasoningSummary.toString().takeIf(String::isNotBlank),
            rawText = rawText ?: pendingReasoningRawText.toString().takeIf(String::isNotBlank),
        )
        pendingReasoningSummary.clear()
        pendingReasoningRawText.clear()
    }

    /**
     * 在缺少 reasoning 完成事件时保留已经收集到的片段。
     */
    private fun flushPendingReasoning() {
        val summary = pendingReasoningSummary.toString().takeIf(String::isNotBlank)
        val rawText = pendingReasoningRawText.toString().takeIf(String::isNotBlank)
        if (summary != null || rawText != null) {
            reasoning += AgentRunReasoningRecord(summary = summary, rawText = rawText)
        }
        pendingReasoningSummary.clear()
        pendingReasoningRawText.clear()
    }
}

/**
 * 记录聚合期间可被工具完成事件补齐的可变工具状态。
 */
private data class MutableAgentRunToolRecord(
    val toolCallId: String?,
    val name: String,
    val arguments: String?,
    var result: String? = null,
) {
    /**
     * 转换为持久化时使用的不可变记录。
     */
    fun toRecord(): AgentRunToolRecord = AgentRunToolRecord(
        toolCallId = toolCallId,
        name = name,
        arguments = arguments,
        result = result,
    )
}
