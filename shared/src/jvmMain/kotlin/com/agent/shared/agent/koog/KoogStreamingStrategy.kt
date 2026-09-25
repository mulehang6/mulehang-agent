package com.agent.shared.agent.koog

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSessionCommon
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONObject
import ai.koog.serialization.JSONPrimitive
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.api.AgentRunTiming
import kotlinx.coroutines.flow.onEach
import com.agent.shared.agent.prompt.buildLlmModel
import com.agent.shared.agent.provider.ProviderKoogTransportAdapters
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException

/**
 * 使用 Koog agent + ToolRegistry 执行当前桌面轮次。
 *
 * 依据 Koog 文档，工具注册通过 `ToolRegistry` 注入，工具/流式事件通过 `handleEvents`
 * 转为应用侧的 `AgentStreamEvent`。
 */
@Suppress("UNUSED_PARAMETER")
internal suspend fun runWithKoogAgent(
    request: AgentRunRequest,
    toolRegistry: ToolRegistry,
    bridge: DesktopToolInteractionBridge,
    emitEvent: suspend (AgentStreamEvent) -> Unit,
    persistenceDatabase: DesktopPersistenceDatabase? = null,
): String {
    val runId = request.resumeRunId ?: request.traceId.ifBlank { UUID.randomUUID().toString() }
    val provider = persistenceDatabase?.let { database ->
        SqlDelightPersistenceStorageProvider(
            persistence = database,
            runId = runId,
            conversationId = request.sessionId,
            modelFingerprint = runModelFingerprint(request),
            toolFingerprint = runToolFingerprint(toolRegistry),
            userEntryId = request.userEntryId.takeIf(String::isNotBlank),
        ).also { it.beginRun(request.profile.model) }
    }
    val effectiveRequest = persistenceDatabase?.let { database ->
        ContextCompactionCoordinator(database).prepare(request, runId)
    } ?: request
    val statusTracker = persistenceDatabase?.let { database -> AgentRunStatusTracker(database, effectiveRequest, runId) }
    val agent = AIAgent
        .builder()
        .promptExecutor(buildPromptExecutor(effectiveRequest.profile))
        .llmModel(buildLlmModel(effectiveRequest.profile))
        .toolRegistry(toolRegistry)
        .prompt(
            buildAgentPrompt(
                profile = effectiveRequest.profile,
                reasoningEffort = effectiveRequest.reasoningEffort,
                runtimeResources = effectiveRequest.runtimeResources,
            ),
        )
        .maxIterations(effectiveRequest.profile.maxIterations)
        .graphStrategy(buildStreamingSingleRunStrategy(effectiveRequest, emitEvent, statusTracker))
        .apply {
            if (provider != null) install(Persistence) { config -> config.storage = provider }
        }
        .install {
            handleEvents {
                onToolCallStarting { context ->
                    statusTracker?.toolStarted()
                    emitEvent(
                        buildToolCallStartedEvent(
                            toolCallId = context.toolCallId,
                            toolName = context.toolName,
                            arguments = context.toolArgs,
                        ),
                    )
                }
                onToolCallCompleted { context ->
                    emitEvent(
                        buildToolCallFinishedEvent(
                            toolCallId = context.toolCallId,
                            toolName = context.toolName,
                            result = context.toolResult?.toString(),
                        ),
                    )
                }
                onToolCallFailed { context ->
                    statusTracker?.toolFailed(context.message.ifBlank { context.error?.message ?: "工具执行失败" })
                    emitEvent(
                        AgentStreamEvent.ToolCallFailed(
                            toolCallId = context.toolCallId,
                            name = context.toolName,
                            reason = context.message.ifBlank {
                                context.error?.message ?: "工具执行失败"
                            },
                        ),
                    )
                }
            }
        }
        .build()
    return try {
        val result = if (request.resumeRunId != null) {
            val checkpoint = requireNotNull(provider?.getLatestCheckpoint(runId)) {
                "没有可用的运行恢复点。"
            }
            val database = requireNotNull(persistenceDatabase)
            val adjusted = InterruptedToolCheckpointAdapter(database, runId).adapt(checkpoint)
            if (adjusted.checkpoint !== checkpoint) {
                adjusted.unknownCalls.forEach { call ->
                    val event = AgentStreamEvent.ToolCallInterrupted(
                        toolCallId = call.id,
                        name = call.name,
                        argumentsJson = call.argumentsJson,
                        partialOutput = call.partialOutput,
                        reason = call.modelResultText(),
                    )
                    AgentRunEventJournal(database, request.sessionId, runId).record(event)
                    emitEvent(event)
                }
                provider.saveCheckpoint(runId, adjusted.checkpoint)
            }
            Persistence.runFromCheckpoint(agent, request.prompt, adjusted.checkpoint, sessionId = runId)
        } else {
            agent.run(request.prompt, runId)
        }
        provider?.finishRun("COMPLETED")
        result
    } catch (cancellation: CancellationException) {
        provider?.interruptRun()
        throw cancellation
    } catch (error: Exception) {
        provider?.interruptRun()
        throw error
    }
}

/** 固定本轮模型与推理配置，拒绝以变化后的设置恢复旧图状态。 */
private fun runModelFingerprint(request: AgentRunRequest): String = sha256(
    listOf(
        request.profile.providerType.name,
        request.profile.providerId,
        request.profile.baseUrl,
        request.profile.model,
        request.reasoningEffort?.wireValue.orEmpty(),
        request.profile.maxIterations.toString(),
        request.profile.requestBody.toString(),
        request.profile.reasoningBodyByEffort.toSortedMap().toString(),
    ).joinToString("\n"),
)

/** 工具名、参数描述和类型共同决定可恢复工具集合的指纹。 */
private fun runToolFingerprint(registry: ToolRegistry): String = sha256(
    registry.tools.sortedBy { it.name }.joinToString("\n") { tool ->
        "${tool.name}|${tool.descriptor}|${tool.argsType}|${tool.resultType}"
    },
)

/** 生成稳定的十六进制 SHA-256 指纹。 */
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

/**
 * 从完整的结构化参数构建工具开始事件，避免预览截断破坏终端命令和操作意图。
 */
internal fun buildToolCallStartedEvent(
    toolCallId: String?,
    toolName: String,
    arguments: JSONObject,
): AgentStreamEvent.ToolCallStarted {
    val serializedArguments = arguments.toString()
    if (toolName != "run_powershell") {
        return AgentStreamEvent.ToolCallStarted(
            toolCallId = toolCallId,
            name = toolName,
            argumentsPreview = serializedArguments.toPreview(),
            argumentsJson = serializedArguments,
        )
    }
    val script = arguments.stringArgument("script")
    val operationIntent = arguments.stringArgument("operation_intent")
        ?: extractToolOperationIntent(toolName, serializedArguments)
    return AgentStreamEvent.ToolCallStarted(
        toolCallId = toolCallId,
        name = toolName,
        argumentsPreview = (script ?: serializedArguments).toPreview(),
        operationIntent = operationIntent,
        argumentsJson = serializedArguments,
    )
}

/**
 * 读取 JSON 对象中的非空字符串参数。
 */
private fun JSONObject.stringArgument(name: String): String? =
    (entries[name] as? JSONPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotBlank)

/**
 * 同时构建面向模型上下文的紧凑预览和面向时间线的完整工具输出事件。
 */
internal fun buildToolCallFinishedEvent(
    toolCallId: String?,
    toolName: String,
    result: String?,
): AgentStreamEvent.ToolCallFinished = AgentStreamEvent.ToolCallFinished(
    toolCallId = toolCallId,
    name = toolName,
    resultPreview = result?.toPreview(),
    resultDisplay = result,
)

/**
 * 从 Koog 的工具参数预览中提取终端工具的模型操作意图；其他工具不附加该字段。
 */
internal fun extractToolOperationIntent(toolName: String, argumentsPreview: String): String? {
    if (toolName != "run_powershell") return null
    return OPERATION_INTENT_ARGUMENT_PATTERN.find(argumentsPreview)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

private val OPERATION_INTENT_ARGUMENT_PATTERN =
    Regex("\"?operation_intent\"?\\s*[=:]\\s*[\"']?([^,}\\n\"']+)")

/**
 * 构建带流式 LLM 节点的单轮策略，保留 Koog 的工具执行节点与生命周期事件。
 */
private fun buildStreamingSingleRunStrategy(
    request: AgentRunRequest,
    emitEvent: suspend (AgentStreamEvent) -> Unit,
    statusTracker: AgentRunStatusTracker?,
): AIAgentGraphStrategy<String, String> = strategy("single_run_streaming") {
    val nodeCallLlm by node<String, Message.Assistant>("call_llm_streaming") { message ->
        llm.writeSession {
            appendPrompt {
                buildConversationMessages(
                    history = request.history,
                    prompt = message,
                    inputParts = request.inputParts,
                ).forEach(::message)
            }
            requestStreamingAssistantMessage(request, emitEvent, statusTracker)
        }
    }
    val nodeExecuteTool by nodeExecuteTools()
    val nodeSendToolResult by node<ReceivedToolResults, Message.Assistant>("send_tool_results_streaming") { toolResults ->
        llm.writeSession {
            appendPrompt {
                user {
                    toolResults.toolResults.forEach { toolResult ->
                        toolResult(toolResult.toMessagePart())
                    }
                }
            }
            requestStreamingAssistantMessage(request, emitEvent, statusTracker)
        }
    }

    edge(nodeStart forwardTo nodeCallLlm)
    edge(nodeCallLlm forwardTo nodeExecuteTool onToolCalls { true })
    edge(
        nodeCallLlm forwardTo nodeFinish
                onCondition { assistant -> assistant.shouldFinishReactLoop() }
                transformed { assistant -> requireNotNull(assistant.finalTextForReactLoop()) },
    )
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(
        nodeSendToolResult forwardTo nodeFinish
                onCondition { assistant -> assistant.shouldFinishReactLoop() }
                transformed { assistant -> requireNotNull(assistant.finalTextForReactLoop()) },
    )
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
}

/**
 * 请求流式响应，并在收敛出 assistant message 后回写到当前 prompt。
 *
 * Koog 的 `requestLLM()` 会自动执行这一步，而 `requestLLMStreaming()` 不会；自定义流式
 * 节点需要显式补齐，确保后续工具结果回传时能保留前置 assistant/tool_calls。
 */
private suspend fun AIAgentLLMWriteSessionCommon.requestStreamingAssistantMessage(
    request: AgentRunRequest,
    emitEvent: suspend (AgentStreamEvent) -> Unit,
    statusTracker: AgentRunStatusTracker?,
): Message.Assistant {
    statusTracker?.pendingSnapshot()?.let { (snapshot, messageText) ->
        appendPrompt { user { text(messageText) } }
        statusTracker.persist(snapshot, messageText)
        emitEvent(AgentStreamEvent.StatusSnapshotUpdated(snapshot, messageText))
    }
    val timing = AgentRunTiming(request.traceId)
    timing.mark("model_request")
    var firstFrame = true
    val response = collectAssistantMessageFromStream(
        frames = (ProviderKoogTransportAdapters.streamFramesOrNull(this@requestStreamingAssistantMessage, request)
            ?: requestLLMStreaming()).onEach {
            if (firstFrame) {
                firstFrame = false
                timing.mark("first_model_frame")
            }
        },
        emitEvent = emitEvent,
    )
    val inputTokens = response.metaInfo.inputTokensCount?.toLong()
    val outputTokens = response.metaInfo.outputTokensCount?.toLong()
    statusTracker?.updateUsage(inputTokens)
    if (inputTokens != null || outputTokens != null) {
        emitEvent(AgentStreamEvent.UsageUpdated(inputTokens, outputTokens, request.contextWindow))
    }
    rewritePrompt { currentPrompt ->
        appendAssistantMessageToPrompt(
            currentPrompt = currentPrompt,
            response = response,
            clock = clock,
        )
    }
    return response
}
