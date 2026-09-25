package com.agent.shared.agent.koog

import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.GraphCheckpointProperties
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.kotlinx.toKoogJSONElement
import ai.koog.serialization.kotlinx.toKoogJSONObject
import com.agent.shared.persistence.DesktopPersistenceDatabase
import com.agent.shared.tool.interaction.InteractionRequestRepository
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 工具可能已产生副作用时，改写恢复点让 Koog 从合成结果后继续，避免直接重跑工具。 */
internal class InterruptedToolCheckpointAdapter(
    private val database: DesktopPersistenceDatabase,
    private val runId: String,
) {
    /** 返回替代恢复点与结果未知的工具；安全节点或没有工具调用时保持原恢复点。 */
    fun adapt(checkpoint: AgentCheckpointData): InterruptedToolCheckpoint {
        val graph = requireNotNull(checkpoint.graphProperties) { "恢复点缺少图节点。" }
        if (graph.nodePath.substringAfterLast('/') !in MODEL_NODE_NAMES) {
            return InterruptedToolCheckpoint(checkpoint, emptyList())
        }
        val assistant = checkpoint.messageHistory.lastOrNull { it is Message.Assistant } as? Message.Assistant
            ?: return InterruptedToolCheckpoint(checkpoint, emptyList())
        val calls = assistant.parts.filterIsInstance<MessagePart.Tool.Call>()
        if (calls.isEmpty()) return InterruptedToolCheckpoint(checkpoint, emptyList())
        val invocations = database.read { queries -> queries.selectToolInvocationsForRun(runId).executeAsList() }
        val singleCall = calls.singleOrNull()
        val currentInvocation = singleCall?.let { call ->
            invocations.lastOrNull { row ->
                row.tool_name == call.tool && (call.id == null || row.id == "$runId:${call.id}")
            }
        }
        if (calls.size == 1 &&
            InteractionRequestRepository(database).hasUnconsumedAnswerForTool(runId, requireNotNull(singleCall).tool) &&
            currentInvocation?.state !in setOf("COMPLETED", "FAILED")
        ) {
            // 只重入单个仍在等待答复的工具；任何已完成工具都不能被重复执行。
            return InterruptedToolCheckpoint(checkpoint, emptyList())
        }
        val unknown = mutableListOf<InterruptedToolCall>()
        val results = calls.map { call ->
            val invocation = invocations.lastOrNull { row ->
                row.tool_name == call.tool && (call.id == null || row.id == "$runId:${call.id}")
            }
            val finished = invocation?.state == "COMPLETED" || invocation?.state == "FAILED"
            val output = if (finished) {
                invocation.result_json?.let(::resultText).orEmpty()
            } else {
                val interrupted = InterruptedToolCall(
                    id = call.id,
                    name = call.tool,
                    argumentsJson = call.argsJson.toString(),
                    partialOutput = invocation?.partial_output.orEmpty(),
                )
                unknown += interrupted
                interrupted.modelResultText()
            }
            ReceivedToolResult(
                id = call.id,
                tool = call.tool,
                toolArgs = call.argsJson.toKoogJSONObject(),
                toolDescription = null,
                output = output,
                resultKind = if (invocation?.state == "COMPLETED") ToolResultKind.Success else ToolResultKind.Failure(null),
                result = null,
            )
        }
        val nodePath = graph.nodePath.substringBeforeLast('/') + "/nodeExecuteTool"
        val synthetic = AgentCheckpointData(
            checkpointId = UUID.randomUUID().toString(),
            createdAt = checkpoint.createdAt,
            messageHistory = checkpoint.messageHistory,
            llmParams = checkpoint.llmParams,
            version = checkpoint.version + 1,
            graphProperties = GraphCheckpointProperties(
                nodePath = nodePath,
                lastOutput = Json.encodeToJsonElement(ReceivedToolResults.serializer(), ReceivedToolResults(results))
                    .toKoogJSONElement(),
            ),
            properties = checkpoint.properties,
            llmModel = checkpoint.llmModel,
            tools = checkpoint.tools,
            storage = checkpoint.storage,
            agentIterations = checkpoint.agentIterations,
        )
        return InterruptedToolCheckpoint(synthetic, unknown)
    }

    /** 读取持久化工具结果的文本字段，损坏结果交给模型按未知处理。 */
    private fun resultText(payload: String): String = runCatching {
        Json.parseToJsonElement(payload).jsonObject.getValue("text").jsonPrimitive.content
    }.getOrDefault("工具结果记录不可读取；结果未知，请检查外部状态。")

    private companion object {
        val MODEL_NODE_NAMES = setOf("call_llm_streaming", "send_tool_results_streaming")
    }
}

/** 已转换的 Koog 恢复点及需要在 UI 标为结果未知的调用。 */
internal data class InterruptedToolCheckpoint(
    val checkpoint: AgentCheckpointData,
    val unknownCalls: List<InterruptedToolCall>,
)

/** 中断时无法确认副作用的原始工具调用和已收到的输出。 */
internal data class InterruptedToolCall(
    val id: String?,
    val name: String,
    val argumentsJson: String,
    val partialOutput: String,
) {
    /** 给模型的结构化合成结果，明确要求检查外部状态。 */
    fun modelResultText(): String = buildString {
        appendLine("工具执行被中断，结果未知。不要直接重跑；先检查外部状态。")
        appendLine("原调用：$name $argumentsJson")
        append("部分输出：${partialOutput.ifBlank { "无" }}")
    }
}
