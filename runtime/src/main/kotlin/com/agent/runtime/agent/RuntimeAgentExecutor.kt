package com.agent.runtime.agent

import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.additionalPropertiesOf
import ai.koog.prompt.streaming.StreamFrame
import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.core.RuntimeAgentExecutionFailure
import com.agent.runtime.core.RuntimeAgentRunRequest
import com.agent.runtime.core.RuntimeCapabilityBridgeFailure
import com.agent.runtime.core.RuntimeFailed
import com.agent.runtime.core.RuntimeEvent
import com.agent.runtime.core.RuntimeInfoEvent
import com.agent.runtime.core.RuntimeProviderResolutionFailure
import com.agent.runtime.core.RuntimeRequestContext
import com.agent.runtime.core.RuntimeResult
import com.agent.runtime.core.RuntimeSession
import com.agent.runtime.core.RuntimeSuccess
import com.agent.runtime.core.RuntimeToolCallPayload
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ai.koog.prompt.message.Message
import ai.koog.serialization.JSONElement
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import ai.koog.serialization.kotlinx.toKotlinxJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/**
 * 表示 agent 执行过程中可被 runtime CLI 逐条消费的更新。
 */
sealed interface RuntimeAgentExecutionUpdate

/**
 * 表示 agent streaming 中产生的一条 runtime event。
 */
data class RuntimeAgentEventUpdate(
    val event: RuntimeEvent,
) : RuntimeAgentExecutionUpdate

/**
 * 表示 agent 执行完成后的最终 runtime result。
 */
data class RuntimeAgentResultUpdate(
    val result: RuntimeResult,
) : RuntimeAgentExecutionUpdate

/**
 * 负责把 runtime 的 agent.run 请求翻译到真实 Koog agent 或 Koog streaming 执行。
 */
class RuntimeAgentExecutor(
    private val conversationMemory: RuntimeConversationMemory = RuntimeConversationMemory(),
    private val assembleAgent: suspend (ProviderBinding, CapabilitySet) -> AssembledAgent =
        AgentAssembly(conversationMemory = conversationMemory)::assemble,
    private val assembleStreamingAgent: suspend (ProviderBinding, CapabilitySet, suspend (RuntimeEvent) -> Unit) -> AssembledAgent =
        { binding, capabilitySet, emitRuntimeEvent ->
            var sawStreamingTextDelta = false
            AgentAssembly(
                conversationMemory = conversationMemory,
                installFeatures = {
                    handleEvents {
                        onLLMStreamingFrameReceived { eventContext ->
                            val frame = eventContext.streamFrame
                            val runtimeEvent = frame.toRuntimeEventOrNull(
                                includeTextComplete = !sawStreamingTextDelta,
                            )
                            if (runtimeEvent != null) {
                                emitRuntimeEvent(runtimeEvent)
                            }
                            when (frame) {
                                is StreamFrame.TextDelta -> sawStreamingTextDelta = true
                                is StreamFrame.TextComplete,
                                is StreamFrame.End -> sawStreamingTextDelta = false
                                else -> Unit
                            }
                        }
                        onToolCallStarting { eventContext ->
                            emitRuntimeEvent(eventContext.toRuntimeToolEvent(status = "running", message = "agent.tool.started"))
                        }
                        onToolCallCompleted { eventContext ->
                            emitRuntimeEvent(
                                eventContext.toRuntimeToolEvent(
                                    status = "completed",
                                    message = "agent.tool.completed",
                                    output = eventContext.toolResult,
                                ),
                            )
                        }
                        onToolCallFailed { eventContext ->
                            emitRuntimeEvent(
                                eventContext.toRuntimeToolEvent(
                                    status = "error",
                                    message = "agent.tool.failed",
                                    error = eventContext.message,
                                ),
                            )
                        }
                    }
                },
            ).assemble(binding, capabilitySet)
        },
    private val runner: (suspend (AssembledAgent, String) -> String)? = null,
    private val streamRunner: ((AssembledAgent, String) -> Flow<StreamFrame>)? = null,
    private val compatibilityStreamRunner: CompatibilityStreamRunner = DeepSeekThinkingCompatibilityStreamRunner(),
) {

    /**
     * 执行一次 runtime agent 请求，并把 streaming 更新聚合为统一 runtime result。
     */
    suspend fun execute(
        session: RuntimeSession,
        context: RuntimeRequestContext,
        request: RuntimeAgentRunRequest,
        binding: ProviderBinding,
        capabilitySet: CapabilitySet,
    ): RuntimeResult {
        val events = mutableListOf<RuntimeEvent>()
        var finalResult: RuntimeResult? = null

        stream(session, context, request, binding, capabilitySet).collect { update ->
            when (update) {
                is RuntimeAgentEventUpdate -> events.add(update.event)
                is RuntimeAgentResultUpdate -> finalResult = update.result
            }
        }

        return when (val result = finalResult) {
            is RuntimeSuccess -> result.copy(events = events)
            is RuntimeFailed -> result.copy(events = events)
            null -> RuntimeFailed(
                failure = RuntimeAgentExecutionFailure(message = "agent execution produced no result"),
                events = events,
            )
        }
    }

    /**
     * 执行一次 runtime agent 请求，并在 Koog streaming frame 到达时立即产出 runtime event。
     */
    fun stream(
        session: RuntimeSession,
        context: RuntimeRequestContext,
        request: RuntimeAgentRunRequest,
        binding: ProviderBinding,
        capabilitySet: CapabilitySet,
    ): Flow<RuntimeAgentExecutionUpdate> = flow {
        try {
            if (runner != null && streamRunner == null) {
                val assembledAgent = assembleAgent(binding, capabilitySet)
                val result = executeLegacyRunner(
                    session = session,
                    context = context,
                    prompt = request.prompt,
                    assembledAgent = assembledAgent,
                    runner = runner,
                )
                when (result) {
                    is RuntimeSuccess -> result.events.forEach { event -> emit(RuntimeAgentEventUpdate(event)) }
                    is RuntimeFailed -> result.events.forEach { event -> emit(RuntimeAgentEventUpdate(event)) }
                }
                emit(RuntimeAgentResultUpdate(result))
                return@flow
            }

            if (streamRunner != null) {
                val assembledAgent = assembleAgent(binding, capabilitySet)
                val textOutput = StringBuilder()
                val reasoningOutput = StringBuilder()
                emit(RuntimeAgentEventUpdate(RuntimeInfoEvent(message = "agent.run.started", payload = JsonPrimitive(context.requestId))))
                streamRunner(assembledAgent, request.prompt).collect { frame ->
                    applyStreamFrame(frame, textOutput, reasoningOutput)
                }
                persistTurnIfNeeded(
                    sessionId = session.id,
                    userPrompt = request.prompt,
                    assistantResponse = textOutput.toString(),
                    reasoningResponse = reasoningOutput.toString().takeIf { it.isNotEmpty() },
                )
                emit(RuntimeAgentEventUpdate(RuntimeInfoEvent(message = "agent.run.completed", payload = JsonPrimitive(session.id))))
                emit(RuntimeAgentResultUpdate(RuntimeSuccess(output = JsonPrimitive(textOutput.toString()))))
                return@flow
            }

            val provisionalAgent = assembleAgent(binding, capabilitySet)
            val hasTools = provisionalAgent.toolRegistry.tools.isNotEmpty()
            if (!hasTools) {
                val runtimePrompt = buildRuntimePrompt(
                    userPrompt = request.prompt,
                    binding = binding,
                    history = conversationMemory.loadHistory(session.id),
                    capabilitySet = capabilitySet,
                )
                val textOutput = StringBuilder()
                val reasoningOutput = StringBuilder()
                emit(RuntimeAgentEventUpdate(RuntimeInfoEvent(message = "agent.run.started", payload = JsonPrimitive(context.requestId))))
                try {
                    val stream = if (compatibilityStreamRunner.supports(binding = provisionalAgent.binding, hasTools = false)) {
                        compatibilityStreamRunner.stream(binding = provisionalAgent.binding, prompt = runtimePrompt)
                    } else {
                        provisionalAgent.promptExecutor.executeStreaming(
                            prompt = runtimePrompt,
                            model = provisionalAgent.llmModel,
                            tools = provisionalAgent.toolRegistry.tools.map { it.descriptor },
                        )
                    }
                    stream.collect { frame ->
                        applyStreamFrame(frame, textOutput, reasoningOutput)
                    }
                } catch (error: Throwable) {
                    if (!error.looksLikeUnsupportedStreamingReasoning()) {
                        throw error
                    }
                    val fallbackOutput = fallbackNonStreamingRunner(
                        assembledAgent = provisionalAgent,
                        prompt = request.prompt,
                        sessionId = session.id,
                    )
                    textOutput.clear()
                    textOutput.append(fallbackOutput)
                    emitTextDelta(fallbackOutput)
                }
                persistTurnIfNeeded(
                    sessionId = session.id,
                    userPrompt = request.prompt,
                    assistantResponse = textOutput.toString(),
                    reasoningResponse = reasoningOutput.toString().takeIf { it.isNotEmpty() },
                )
                emit(RuntimeAgentEventUpdate(RuntimeInfoEvent(message = "agent.run.completed", payload = JsonPrimitive(session.id))))
                emit(RuntimeAgentResultUpdate(RuntimeSuccess(output = JsonPrimitive(textOutput.toString()))))
                return@flow
            }

            val assembledAgent = assembleStreamingAgent(
                binding,
                capabilitySet,
            ) { event ->
                emit(RuntimeAgentEventUpdate(event))
            }
            emit(RuntimeAgentEventUpdate(RuntimeInfoEvent(message = "agent.run.started", payload = JsonPrimitive(context.requestId))))
            val output = assembledAgent.agent.run(request.prompt, session.id)
            emit(RuntimeAgentEventUpdate(RuntimeInfoEvent(message = "agent.run.completed", payload = JsonPrimitive(session.id))))
            emit(RuntimeAgentResultUpdate(RuntimeSuccess(output = JsonPrimitive(output))))
        } catch (error: IllegalArgumentException) {
            emit(
                RuntimeAgentResultUpdate(
                    RuntimeFailed(
                        failure = RuntimeProviderResolutionFailure(
                            message = error.message ?: "provider resolution failed",
                            cause = error,
                        ),
                    ),
                ),
            )
        } catch (error: IllegalStateException) {
            emit(
                RuntimeAgentResultUpdate(
                    RuntimeFailed(
                        failure = RuntimeCapabilityBridgeFailure(
                            message = error.message ?: "capability bridge failed",
                            cause = error,
                        ),
                    ),
                ),
            )
        } catch (error: Throwable) {
            emit(
                RuntimeAgentResultUpdate(
                    RuntimeFailed(
                        failure = RuntimeAgentExecutionFailure(
                            message = binding.agentExecutionFailureMessage(error),
                            cause = error,
                        ),
                    ),
                ),
            )
        }
    }

    /**
     * 使用真正安装了 ChatMemory feature 的 Koog agent 执行非 streaming 回退。
     */
    private suspend fun fallbackNonStreamingRunner(
        assembledAgent: AssembledAgent,
        prompt: String,
        sessionId: String,
    ): String = assembledAgent.agent.run(prompt, sessionId)

    /**
     * 保留单元测试和非 streaming runner 的旧执行路径。
     */
    private suspend fun executeLegacyRunner(
        session: RuntimeSession,
        context: RuntimeRequestContext,
        prompt: String,
        assembledAgent: AssembledAgent,
        runner: suspend (AssembledAgent, String) -> String,
    ): RuntimeResult {
        val output = runner(assembledAgent, prompt)
        persistTurnIfNeeded(
            sessionId = session.id,
            userPrompt = prompt,
            assistantResponse = output,
        )
        return RuntimeSuccess(
            events = listOf(
                RuntimeInfoEvent(message = "agent.run.started", payload = JsonPrimitive(context.requestId)),
                RuntimeInfoEvent(message = "agent.run.completed", payload = JsonPrimitive(session.id)),
            ),
            output = JsonPrimitive(output),
        )
    }

    /**
     * 以幂等方式把一次成功对话轮次落入共享 memory，避免与底层 feature 的自动持久化重复。
     */
    private suspend fun persistTurnIfNeeded(
        sessionId: String,
        userPrompt: String,
        assistantResponse: String,
        reasoningResponse: String? = null,
    ) {
        val history = conversationMemory.loadHistory(sessionId)
        val latestUserIndex = history.indexOfLast { it is Message.User }
        val latestUser = history.getOrNull(latestUserIndex) as? Message.User
        val turnMessages = history.drop((latestUserIndex + 1).coerceAtLeast(0))
        val latestAssistantForTurn = turnMessages.lastOrNull { it is Message.Assistant } as? Message.Assistant
        val latestReasoningForTurn = turnMessages.lastOrNull { it is Message.Reasoning } as? Message.Reasoning
        val turnAlreadyPersisted = latestUser?.content == userPrompt &&
            latestReasoningForTurn?.content == reasoningResponse &&
            latestAssistantForTurn?.content == assistantResponse
        if (!turnAlreadyPersisted) {
            conversationMemory.appendTurn(
                sessionId = sessionId,
                userPrompt = userPrompt,
                assistantMessages = buildList {
                    reasoningResponse?.let { reasoning ->
                        add(
                            Message.Reasoning(
                                content = reasoning,
                                metaInfo = ai.koog.prompt.message.ResponseMetaInfo.create(kotlin.time.Clock.System),
                            ),
                        )
                    }
                    add(
                        Message.Assistant(
                            content = assistantResponse,
                            metaInfo = ai.koog.prompt.message.ResponseMetaInfo.create(kotlin.time.Clock.System),
                        ),
                    )
                },
            )
        }
    }

    /**
     * 发出普通回答文本增量。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<RuntimeAgentExecutionUpdate>.emitTextDelta(delta: String) {
        emit(
            RuntimeAgentEventUpdate(
                RuntimeInfoEvent(
                    message = "agent.text.delta",
                    channel = "text",
                    delta = delta,
                ),
            ),
        )
    }

    /**
     * 根据 streaming frame 同步更新文本缓冲并向外转发可见事件。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<RuntimeAgentExecutionUpdate>.applyStreamFrame(
        frame: StreamFrame,
        textOutput: StringBuilder,
        reasoningOutput: StringBuilder,
    ) {
        when (frame) {
            is StreamFrame.TextDelta -> {
                textOutput.append(frame.text)
                emitTextDelta(frame.text)
            }
            is StreamFrame.TextComplete -> if (textOutput.isEmpty()) {
                textOutput.append(frame.text)
                emitTextDelta(frame.text)
            }
            is StreamFrame.ReasoningDelta -> {
                val delta = frame.text ?: frame.summary
                if (delta != null) {
                    reasoningOutput.append(delta)
                    emitReasoningDelta(delta)
                }
            }
            is StreamFrame.ToolCallDelta,
            is StreamFrame.ToolCallComplete,
            is StreamFrame.End,
            is StreamFrame.ReasoningComplete,
            -> Unit
        }
    }

    /**
     * 发出模型 reasoning/thinking 增量。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<RuntimeAgentExecutionUpdate>.emitReasoningDelta(delta: String) {
        emit(
            RuntimeAgentEventUpdate(
                RuntimeInfoEvent(
                    message = "agent.reasoning.delta",
                    channel = "thinking",
                    delta = delta,
                ),
            ),
        )
    }

    /**
     * 在 Responses API 明确不可用时，给出可操作的 chat/completions 切换提示。
     */
    private fun ProviderBinding.agentExecutionFailureMessage(error: Throwable): String {
        val message = error.message ?: "agent execution failed"
        if (usesOpenAIResponsesEndpoint() && message.looksLikeUnsupportedResponsesEndpoint()) {
            return "OpenAI Responses API request failed. Switch this provider to chat/completions if it does not support Responses. Cause: $message"
        }
        return message
    }

    /**
     * 判断当前 binding 是否会按 OpenAI Responses endpoint 执行。
     */
    private fun ProviderBinding.usesOpenAIResponsesEndpoint(): Boolean = providerType == ProviderType.OPENAI_RESPONSES

    /**
     * 保守识别 Responses endpoint 不存在或不支持的常见错误文本。
     */
    private fun String.looksLikeUnsupportedResponsesEndpoint(): Boolean {
        val normalized = lowercase()
        return "responses" in normalized &&
            ("404" in normalized || "not found" in normalized || "unsupported" in normalized)
    }

    /**
     * 识别 Koog 0.8.0 Responses streaming 对 reasoning_text subtype 的已知反序列化缺口。
     */
    private fun Throwable.looksLikeUnsupportedStreamingReasoning(): Boolean {
        val message = message ?: return false
        return "reasoning_text" in message && "OutputContent" in message
    }
}

/**
 * 把 Koog tool lifecycle 上下文翻译为 runtime 可消费的结构化工具事件。
 */
internal fun ai.koog.agents.core.feature.handler.tool.ToolCallEventContext.toRuntimeToolEvent(
    status: String,
    message: String,
    output: JSONElement? = null,
    error: String? = null,
): RuntimeEvent = RuntimeInfoEvent(
    message = message,
    channel = "tool",
    delta = toolName,
    payload = Json.encodeToJsonElement(
        RuntimeToolCallPayload(
            toolCallId = toolCallId ?: eventId,
            toolName = toolName,
            status = status,
            input = toolArgs.toKotlinxJsonObject(),
            output = output?.toKotlinxJsonElement(),
            error = error,
        ),
    ),
)

/**
 * 把 Koog streaming frame 转换为 runtime 可见事件；工具执行状态走专门的 lifecycle 事件。
 */
private fun StreamFrame.toRuntimeEventOrNull(includeTextComplete: Boolean = true): RuntimeEvent? = when (this) {
    is StreamFrame.TextDelta -> RuntimeInfoEvent(
        message = "agent.text.delta",
        channel = "text",
        delta = text,
    )
    is StreamFrame.TextComplete -> if (includeTextComplete) {
        RuntimeInfoEvent(
            message = "agent.text.delta",
            channel = "text",
            delta = text,
        )
    } else {
        null
    }
    is StreamFrame.ReasoningDelta -> {
        val value = text ?: summary ?: return null
        RuntimeInfoEvent(
            message = "agent.reasoning.delta",
            channel = "thinking",
            delta = value,
        )
    }
    else -> null
}

/**
 * 构造 runtime 的单轮 prompt；当 provider 显式开启 thinking 时，同时附带 provider 级参数。
 */
internal fun buildRuntimePrompt(
    userPrompt: String,
    binding: ProviderBinding,
    history: List<Message> = emptyList(),
    capabilitySet: CapabilitySet = CapabilitySet(adapters = emptyList()),
): Prompt {
    val basePrompt = prompt("runtime-agent") {
        system(buildAgentSystemPrompt(capabilitySet))
        messages(history)
        user(userPrompt)
    }

    val params = binding.toThinkingParams() ?: return basePrompt
    return basePrompt.withParams(params)
}

/**
 * 把仓库侧的 thinking 开关翻译为各 provider 真正理解的请求参数。
 */
internal fun ProviderBinding.toThinkingParams(): LLMParams? {
    if (!enableThinking) {
        return null
    }

    return when (providerType) {
        ProviderType.OPENAI_COMPATIBLE -> OpenAIChatParams(
            additionalProperties = if (usesDeepSeekThinkingMode()) {
                additionalPropertiesOf("thinking" to mapOf("type" to "enabled"))
            } else {
                null
            },
        )

        ProviderType.OPENAI_RESPONSES -> OpenAIChatParams(
            reasoningEffort = ReasoningEffort.MEDIUM,
        )

        ProviderType.ANTHROPIC -> AnthropicParams(
            thinking = AnthropicThinking.Enabled(budgetTokens = 1_024),
        )

        ProviderType.GEMINI -> GoogleParams(
            thinkingConfig = GoogleThinkingConfig(includeThoughts = true),
        )
    }
}

/**
 * DeepSeek 的 OpenAI-compatible thinking mode 需要通过 `thinking={type=enabled}` 透传到请求体。
 */
internal fun ProviderBinding.usesDeepSeekThinkingMode(): Boolean {
    val normalizedBaseUrl = baseUrl.lowercase()
    val normalizedProviderId = providerId.lowercase()
    val normalizedModelId = modelId.lowercase()
    return "deepseek" in normalizedBaseUrl ||
        "deepseek" in normalizedProviderId ||
        normalizedModelId.startsWith("deepseek")
}

/**
 * 构造包含能力目录的系统提示，确保模型知道自己有哪些工具以及边界。
 */
internal fun buildAgentSystemPrompt(capabilitySet: CapabilitySet): String {
    val toolCatalog = buildList {
        capabilitySet.toolAdapters().forEach { adapter ->
            add("- ${adapter.descriptor.id} [${adapter.descriptor.kind}/${adapter.descriptor.riskLevel}]: ${adapter.description}")
        }
        capabilitySet.httpAdapters().forEach { adapter ->
            add("- ${adapter.descriptor.id} [${adapter.descriptor.kind}/${adapter.descriptor.riskLevel}]: ${adapter.method} ${adapter.path}. ${adapter.description}")
        }
        capabilitySet.mcpAdapters().forEach { adapter ->
            add("- ${adapter.descriptor.id} [${adapter.descriptor.kind}/${adapter.descriptor.riskLevel}]: MCP tool bridge over ${adapter.transport.description}.")
        }
        capabilitySet.builtInFileTools().forEach { tool ->
            val description = when (tool.descriptor.id) {
                "__list_directory__" -> "List directories and files inside the workspace root."
                "__read_file__" -> "Read text files inside the workspace root."
                "__write_file__" -> "Write or overwrite text files inside the workspace root."
                "edit_file" -> "Apply targeted edits to existing files inside the workspace root."
                else -> "Access the workspace-scoped file system."
            }
            add("- ${tool.descriptor.id} [${tool.descriptor.kind}/${tool.descriptor.riskLevel}]: $description Workspace root: ${tool.workspaceRoot}")
        }
    }
    if (toolCatalog.isEmpty()) {
        return "$DEFAULT_AGENT_SYSTEM_PROMPT You currently have no external tools available. Answer directly and do not claim tool access you do not have."
    }

    return buildString {
        append(DEFAULT_AGENT_SYSTEM_PROMPT)
        append(
            " Answer the user's actual request first. Default to answering directly without tools. " +
                "Do not proactively list tools, policies, or capabilities unless the user asked for them. " +
                "Do not call workspace tools for greetings, small talk, exact-format replies, translation, or when the user explicitly asks not to inspect the project. " +
                "Only inspect the workspace when the answer depends on repository facts, local files, implementation details, or the user asks you to read, edit, search, build, test, or otherwise operate on the project. " +
                "If a repository-specific question cannot be answered because the user explicitly prohibited inspection, say that limitation and answer only from existing context. " +
                "Use tools only when the request actually requires repository inspection, external information, file access, or side effects. " +
                "The tool catalog is informational; do not call a tool just because it is listed. " +
                "If the user asks what tools you have, answer from the catalog below and do not invent extra tools.\n\n"
        )
        append("Available tools:\n")
        append(toolCatalog.joinToString(separator = "\n"))
        append("\n\nRisk levels: LOW = read-only or no persistent side effects; MID = modifies workspace contents; HIGH = external or stronger side effects.")
    }
}

/**
 * 统一复用当前 runtime agent 的基础系统提示，避免兼容层和 Koog 主链路出现语义漂移。
 */
internal const val DEFAULT_AGENT_SYSTEM_PROMPT =
    "You are a helpful assistant. Reply in the same language as the user's latest message unless the user asks for another language."
