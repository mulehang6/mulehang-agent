package com.agent.shared.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSessionCommon
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Koog 1.0.0 接入点，负责执行单轮消息并转换为应用事件。
 */
class KoogAgentGateway(
    private val interactionBridge: DesktopToolInteractionBridge = RejectingDesktopToolInteractionBridge,
    private val streamRunner: (suspend (request: AgentRunRequest) -> Flow<StreamFrame>)? = null,
    private val executionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val agentRunner: suspend (
        request: AgentRunRequest,
        toolRegistry: ToolRegistry,
        bridge: DesktopToolInteractionBridge,
        emitEvent: suspend (AgentStreamEvent) -> Unit,
    ) -> String = ::runWithKoogAgent,
) : AgentGateway {
    /**
     * 运行一次消息请求。
     */
    override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = if (streamRunner != null) {
        runLegacyStream(request, streamRunner)
    } else {
        channelFlow {
            send(AgentStreamEvent.Started)
            val bridge = eventEmittingBridge(::send)
            val registry = DesktopToolRegistryFactory(
                workspacePath = request.workspacePath,
                permissionPreset = request.permissionPreset,
                interactionBridge = bridge,
            ).create()
            launch(executionDispatcher) {
                try {
                    val result = agentRunner(
                        request,
                        registry,
                        bridge,
                        ::send,
                    )
                    send(AgentStreamEvent.Completed(result))
                } catch (e: Exception) {
                    send(AgentStreamEvent.Failed(e.message ?: "执行错误"))
                } finally {
                    close()
                }
            }
            awaitClose()
        }
    }

    /**
     * 将 ask_user / approval 桥接为 UI 可消费事件。
     */
    private fun eventEmittingBridge(
        emitEvent: suspend (AgentStreamEvent) -> Unit,
    ): DesktopToolInteractionBridge = object : DesktopToolInteractionBridge {
        override suspend fun requestQuestion(request: QuestionRequest): String {
            emitEvent(AgentStreamEvent.QuestionRequested(request))
            return interactionBridge.requestQuestion(request)
        }

        override suspend fun requestApproval(request: ApprovalRequest): Boolean {
            emitEvent(AgentStreamEvent.ApprovalRequested(request))
            return interactionBridge.requestApproval(request)
        }
    }
}

/**
 * 兼容旧测试的 StreamFrame 到应用事件映射。
 */
private fun runLegacyStream(
    request: AgentRunRequest,
    streamRunner: suspend (request: AgentRunRequest) -> Flow<StreamFrame>,
): Flow<AgentStreamEvent> = flow {
    emit(AgentStreamEvent.Started)
    val textBuffer = StringBuilder()
    val announcedToolCalls = mutableSetOf<String>()

    try {
        streamRunner(request).collect { frame ->
            when (frame) {
                is StreamFrame.TextDelta -> {
                    textBuffer.append(frame.text)
                    emit(AgentStreamEvent.TextDelta(frame.text))
                }

                is StreamFrame.TextComplete -> {
                    if (textBuffer.isEmpty()) {
                        textBuffer.append(frame.text)
                    }
                }

                is StreamFrame.ToolCallDelta -> {
                    val toolName = frame.name ?: return@collect
                    val toolKey = frame.id ?: "${frame.index}:$toolName"
                    if (announcedToolCalls.add(toolKey)) {
                        emit(
                            AgentStreamEvent.ToolCallStarted(
                                name = toolName,
                                argumentsPreview = frame.content?.toPreview(),
                            ),
                        )
                    }
                }

                is StreamFrame.ToolCallComplete -> {
                    val toolKey = frame.id ?: "${frame.index}:${frame.name}"
                    if (announcedToolCalls.add(toolKey)) {
                        emit(
                            AgentStreamEvent.ToolCallStarted(
                                name = frame.name,
                                argumentsPreview = frame.content.toPreview(),
                            ),
                        )
                    }
                    emit(
                        AgentStreamEvent.ToolCallFinished(
                            name = frame.name,
                            resultPreview = frame.content.toPreview(),
                        ),
                    )
                }

                is StreamFrame.ReasoningDelta -> emit(
                    AgentStreamEvent.ReasoningDelta(
                        summary = frame.summary,
                        rawText = frame.text,
                    ),
                )

                is StreamFrame.ReasoningComplete -> emit(
                    AgentStreamEvent.ReasoningCompleted(
                        summary = frame.summary?.joinToString(separator = ""),
                        rawText = frame.content.joinToString(separator = ""),
                    ),
                )

                is StreamFrame.End -> Unit
            }
        }
        emit(AgentStreamEvent.Completed(textBuffer.toString()))
    } catch (e: Exception) {
        emit(AgentStreamEvent.Failed(e.message ?: "执行错误"))
    }
}

/**
 * 使用 Koog agent + ToolRegistry 执行当前桌面轮次。
 *
 * 依据 Koog 文档，工具注册通过 `ToolRegistry` 注入，工具/流式事件通过 `handleEvents`
 * 转为应用侧的 `AgentStreamEvent`。
 */
@Suppress("UNUSED_PARAMETER")
private suspend fun runWithKoogAgent(
    request: AgentRunRequest,
    toolRegistry: ToolRegistry,
    bridge: DesktopToolInteractionBridge,
    emitEvent: suspend (AgentStreamEvent) -> Unit,
): String {
    val agent = AIAgent
        .builder()
        .promptExecutor(buildPromptExecutor(request.profile))
        .llmModel(buildLlmModel(request.profile))
        .toolRegistry(toolRegistry)
        .prompt(buildAgentPrompt(request.profile, request.reasoningEffort))
        .maxIterations(50)
        .graphStrategy(buildStreamingSingleRunStrategy(request, emitEvent))
        .install {
            handleEvents {
                onToolCallStarting { context ->
                    emitEvent(
                        AgentStreamEvent.ToolCallStarted(
                            name = context.toolName,
                            argumentsPreview = context.toolArgs.toString().toPreview(),
                        ),
                    )
                }
                onToolCallCompleted { context ->
                    emitEvent(
                        AgentStreamEvent.ToolCallFinished(
                            name = context.toolName,
                            resultPreview = context.toolResult?.toString()?.toPreview(),
                        ),
                    )
                }
                onToolCallFailed { context ->
                    emitEvent(
                        AgentStreamEvent.Failed(
                            reason = context.message.ifBlank {
                                context.error?.message ?: "工具执行失败"
                            },
                        ),
                    )
                }
            }
        }
        .build()
    return agent.run(request.prompt, null)
}

/**
 * 构建带流式 LLM 节点的单轮策略，保留 Koog 的工具执行节点与生命周期事件。
 */
private fun buildStreamingSingleRunStrategy(
    request: AgentRunRequest,
    emitEvent: suspend (AgentStreamEvent) -> Unit,
): AIAgentGraphStrategy<String, String> = strategy("single_run_streaming") {
    val nodeCallLlm by node<String, Message.Assistant>("call_llm_streaming") { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }
            requestStreamingAssistantMessage(request, emitEvent)
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
            requestStreamingAssistantMessage(request, emitEvent)
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
 * 对 DeepSeek chat-completions 走自定义 streamer，其余 provider 继续使用 Koog 默认流式请求。
 */
private suspend fun AIAgentLLMWriteSessionCommon.requestStreamingFrames(
    request: AgentRunRequest,
): Flow<StreamFrame> = if (request.profile.isDeepSeekChatCompletionsProfile()) {
    DeepSeekChatCompletionsStreamer().stream(
        prompt = prompt,
        config = request.profile,
        reasoningEffort = request.reasoningEffort,
        tools = tools,
    )
} else {
    requestLLMStreaming()
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
): Message.Assistant {
    val response = collectAssistantMessageFromStream(
        frames = requestStreamingFrames(request),
        emitEvent = emitEvent,
    )
    rewritePrompt { currentPrompt ->
        appendAssistantMessageToPrompt(
            currentPrompt = currentPrompt,
            response = response,
            clock = clock,
        )
    }
    return response
}

/**
 * 将已经收敛完成的 assistant message 追加回 prompt，和 Koog 默认非流式语义保持一致。
 */
internal fun appendAssistantMessageToPrompt(
    currentPrompt: Prompt,
    response: Message.Assistant,
    clock: KoogClock,
): Prompt = prompt(currentPrompt, clock) {
    message(response)
}

/**
 * 将 Koog 的流式 frame 收敛为 assistant message，并同步发出 UI 所需的增量事件。
 */
internal suspend fun collectAssistantMessageFromStream(
    frames: Flow<StreamFrame>,
    emitEvent: suspend (AgentStreamEvent) -> Unit,
): Message.Assistant {
    val textParts = linkedMapOf<String, TextAccumulator>()
    val reasoningParts = linkedMapOf<String, ReasoningAccumulator>()
    val toolCalls = linkedMapOf<String, ToolCallAccumulator>()
    val orderedPartKeys = mutableListOf<String>()
    var endFrame = StreamFrame.End()

    frames.collect { frame ->
        when (frame) {
            is StreamFrame.TextDelta -> {
                val key = "text:${frame.index ?: 0}"
                val accumulator = textParts.getOrPut(key) {
                    orderedPartKeys += key
                    TextAccumulator()
                }
                accumulator.deltaText.append(frame.text)
                emitEvent(AgentStreamEvent.TextDelta(frame.text))
            }

            is StreamFrame.TextComplete -> {
                val key = "text:${frame.index ?: 0}"
                val accumulator = textParts.getOrPut(key) {
                    orderedPartKeys += key
                    TextAccumulator()
                }
                accumulator.completeText = frame.text
            }

            is StreamFrame.ReasoningDelta -> {
                val key = reasoningKey(frame.id, frame.index)
                val accumulator = reasoningParts.getOrPut(key) {
                    orderedPartKeys += key
                    ReasoningAccumulator(id = frame.id)
                }
                frame.text?.let(accumulator.deltaContent::add)
                frame.summary?.let(accumulator.deltaSummary::add)
                emitEvent(
                    AgentStreamEvent.ReasoningDelta(
                        summary = frame.summary,
                        rawText = frame.text,
                    ),
                )
            }

            is StreamFrame.ReasoningComplete -> {
                val key = reasoningKey(frame.id, frame.index)
                val accumulator = reasoningParts.getOrPut(key) {
                    orderedPartKeys += key
                    ReasoningAccumulator(id = frame.id)
                }
                accumulator.completeContent = frame.content
                accumulator.completeSummary = frame.summary
                accumulator.encrypted = frame.encrypted
                emitEvent(
                    AgentStreamEvent.ReasoningCompleted(
                        summary = frame.summary?.joinToString(separator = ""),
                        rawText = frame.content.joinToString(separator = ""),
                    ),
                )
            }

            is StreamFrame.ToolCallDelta -> {
                val key = resolveToolCallAccumulatorKey(
                    toolCalls = toolCalls,
                    id = frame.id,
                    index = frame.index,
                )
                val accumulator = toolCalls.getOrPut(key) {
                    orderedPartKeys += key
                    ToolCallAccumulator(id = frame.id, index = frame.index)
                }
                if (accumulator.id == null && frame.id != null) {
                    accumulator.id = frame.id
                }
                if (accumulator.index == null && frame.index != null) {
                    accumulator.index = frame.index
                }
                if (frame.name != null) {
                    accumulator.name = frame.name
                }
                if (frame.content != null) {
                    accumulator.deltaArgs.append(frame.content)
                }
            }

            is StreamFrame.ToolCallComplete -> {
                val key = resolveToolCallAccumulatorKey(
                    toolCalls = toolCalls,
                    id = frame.id,
                    index = frame.index,
                )
                val accumulator = toolCalls.getOrPut(key) {
                    orderedPartKeys += key
                    ToolCallAccumulator(id = frame.id, index = frame.index)
                }
                if (accumulator.id == null && frame.id != null) {
                    accumulator.id = frame.id
                }
                if (accumulator.index == null && frame.index != null) {
                    accumulator.index = frame.index
                }
                accumulator.name = frame.name
                accumulator.completeArgs = frame.content
            }

            is StreamFrame.End -> endFrame = frame
        }
    }

    val parts = orderedPartKeys.mapNotNull { key ->
        when {
            textParts.containsKey(key) -> textParts.getValue(key).toMessagePart()
            reasoningParts.containsKey(key) -> reasoningParts.getValue(key).toMessagePart()
            toolCalls.containsKey(key) -> toolCalls.getValue(key).toMessagePart()
            else -> null
        }
    }

    return Message.Assistant(
        parts = parts,
        metaInfo = endFrame.metaInfo,
        finishReason = endFrame.finishReason,
    ).requireRoutableForStreamingGraph()
}

/**
 * 验证当前 assistant message 至少能命中单轮流式策略图的一条边，避免 Koog 子图卡死。
 */
private fun Message.Assistant.requireRoutableForStreamingGraph(): Message.Assistant {
    val hasToolCalls = parts.any { it is MessagePart.Tool.Call }
    if (finishReason == "tool_calls" && !hasToolCalls) {
        error("模型返回 finishReason=tool_calls，但未提供可执行的工具调用。")
    }

    val hasText = parts.any { part ->
        part is MessagePart.Text && part.text.isNotBlank()
    }
    if (!hasToolCalls && !hasText) {
        val message = if (parts.any { it is MessagePart.Reasoning }) {
            "模型仅返回了思考内容，未返回文本或工具调用。"
        } else {
            "模型未返回文本或工具调用。"
        }
        error(message)
    }
    return this
}

/**
 * 参考 paicli 的 ReAct 循环：只要 assistant 同时携带工具调用，就继续走工具链；
 * 只有不存在工具调用且存在正文文本时，当前轮次才应结束。
 */
internal fun Message.Assistant.shouldFinishReactLoop(): Boolean =
    parts.none { it is MessagePart.Tool.Call } &&
        parts.any { part -> part is MessagePart.Text && part.text.isNotBlank() }

/**
 * 提取当前 ReAct 轮次可作为最终输出的文本；若仍需继续调用工具则返回 null。
 */
internal fun Message.Assistant.finalTextForReactLoop(): String? {
    if (!shouldFinishReactLoop()) {
        return null
    }
    return parts
        .filterIsInstance<MessagePart.Text>()
        .map { it.text }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n")
        .takeIf { it.isNotBlank() }
}

/**
 * 构建 agent 基础 prompt，只承载 provider 参数，不预写用户正文。
 */
internal fun buildAgentPrompt(
    profile: com.agent.shared.config.ConfigProfile,
    reasoningEffort: ReasoningEffort?,
): Prompt = Prompt.build(
    id = "mulehang-chat",
    params = buildPromptParams(profile, reasoningEffort),
) {}

/**
 * 文本 part 的临时收集器。
 */
private data class TextAccumulator(
    val deltaText: StringBuilder = StringBuilder(),
    var completeText: String? = null,
) {
    /**
     * 生成最终文本 part；空文本会被丢弃。
     */
    fun toMessagePart(): MessagePart.Text? {
        val text = if (deltaText.isNotEmpty()) {
            deltaText.toString()
        } else {
            completeText
        }
        return text?.takeIf { it.isNotEmpty() }?.let(MessagePart::Text)
    }
}

/**
 * 思考 part 的临时收集器。
 */
private data class ReasoningAccumulator(
    val id: String? = null,
    val deltaContent: MutableList<String> = mutableListOf(),
    val deltaSummary: MutableList<String> = mutableListOf(),
    var completeContent: List<String>? = null,
    var completeSummary: List<String>? = null,
    var encrypted: String? = null,
) {
    /**
     * 生成最终 reasoning part；没有内容时返回 null。
     */
    fun toMessagePart(): MessagePart.Reasoning? {
        val content = completeContent ?: deltaContent.takeIf { it.isNotEmpty() }
        if (content.isNullOrEmpty()) {
            return null
        }
        val summary = completeSummary ?: deltaSummary.takeIf { it.isNotEmpty() }
        return MessagePart.Reasoning(
            id = id,
            content = content,
            summary = summary,
            encrypted = encrypted,
        )
    }
}

/**
 * 工具调用 part 的临时收集器。
 */
private data class ToolCallAccumulator(
    var id: String? = null,
    var index: Int? = null,
    var name: String? = null,
    val deltaArgs: StringBuilder = StringBuilder(),
    var completeArgs: String? = null,
) {
    /**
     * 生成最终工具调用 part；名称或参数缺失时返回 null。
     */
    fun toMessagePart(): MessagePart.Tool.Call? {
        val resolvedName = name ?: return null
        val resolvedArgs = completeArgs ?: deltaArgs.toString().takeIf { it.isNotEmpty() } ?: return null
        return MessagePart.Tool.Call(
            id = id,
            tool = resolvedName,
            args = resolvedArgs,
        )
    }
}

/**
 * 解析工具调用增量对应的稳定 accumulator key，优先复用已有 index / id 对应项。
 */
private fun resolveToolCallAccumulatorKey(
    toolCalls: Map<String, ToolCallAccumulator>,
    id: String?,
    index: Int?,
): String {
    toolCalls.entries.firstOrNull { (_, accumulator) ->
        index != null && accumulator.index == index
    }?.let { return it.key }

    toolCalls.entries.firstOrNull { (_, accumulator) ->
        id != null && accumulator.id == id
    }?.let { return it.key }

    return toolCallKey(id = id, index = index)
}

/**
 * 生成 reasoning part 的稳定 key。
 */
private fun reasoningKey(id: String?, index: Int?): String = "reasoning:${id ?: index ?: 0}"

/**
 * 生成工具调用 part 的稳定 key。
 */
private fun toolCallKey(id: String?, index: Int?): String = "tool:${index ?: id ?: 0}"

/**
 * 生成适合 UI 最小展示的事件预览。
 */
private fun String.toPreview(limit: Int = 120): String = replace("\n", " ").trim().take(limit)
