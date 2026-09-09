package com.agent.shared.agent.koog

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.agent.api.AgentGateway
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.agent.hook.AgentHookDispatcher
import com.agent.shared.agent.hook.AgentHookDecision
import com.agent.shared.agent.hook.AgentHookDispatchRequest
import com.agent.shared.agent.hook.AgentHookDispatchResult
import com.agent.shared.agent.hook.NoAgentHookDispatcher
import com.agent.shared.agent.hook.WindowsAgentHookDispatcher
import com.agent.shared.agent.resource.McpToolRegistryBridge
import com.agent.shared.settings.model.AgentIterationLimit
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.interaction.RejectingDesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.QuestionRequest
import com.agent.shared.tool.runtime.DesktopToolRegistryFactory
import com.agent.shared.tool.runtime.ToolApprovalAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/**
 * Koog 1.0.0 接入点，负责执行单轮消息并转换为应用事件。
 */
class KoogAgentGateway(
    private val interactionBridge: DesktopToolInteractionBridge = RejectingDesktopToolInteractionBridge,
    private val streamRunner: (suspend (request: AgentRunRequest) -> Flow<StreamFrame>)? = null,
    private val executionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val approvalAgentFactory: (AgentRunRequest) -> ToolApprovalAgent? = { request ->
        request.fasterProfile?.let(::KoogToolApprovalAgent)
    },
    private val agentRunner: suspend (
        request: AgentRunRequest,
        toolRegistry: ToolRegistry,
        bridge: DesktopToolInteractionBridge,
        emitEvent: suspend (AgentStreamEvent) -> Unit,
    ) -> String = ::runWithKoogAgent,
    private val mcpToolRegistryBridge: McpToolRegistryBridge = McpToolRegistryBridge(),
    private val hookDispatcherFactory: (AgentRunRequest) -> AgentHookDispatcher = { request ->
        if (request.hookSettings.hooks.isEmpty()) NoAgentHookDispatcher else WindowsAgentHookDispatcher(request.hookSettings)
    },
) : AgentGateway {
    private val lifecycleScope = CoroutineScope(SupervisorJob() + executionDispatcher)
    private val sessionHookDispatchers = ConcurrentHashMap<String, AgentHookDispatcher>()
    private val startedHookSessions = ConcurrentHashMap.newKeySet<String>()

    /**
     * 运行一次消息请求。
     */
    override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = if (streamRunner != null) {
        runLegacyStream(request, streamRunner)
    } else {
        channelFlow {
            send(AgentStreamEvent.Started)
            val eventQueue = Channel<AgentStreamEvent>(TOOL_EVENT_CHANNEL_CAPACITY)
            val forwardingJob = launch {
                for (event in eventQueue) {
                    send(event)
                }
            }
            val bridge = eventEmittingBridge(
                emitEvent = eventQueue::send,
                emitToolOutput = { event ->
                    runCatching {
                        runBlocking { eventQueue.send(event) }
                    }
                },
                emitFileDiffPreview = { event ->
                    runCatching {
                        runBlocking { eventQueue.send(event) }
                    }
                },
            )
            val hookDispatcher = hookDispatcherFor(request)
            val desktopRegistry = DesktopToolRegistryFactory(
                workspacePath = request.workspacePath,
                permissionPreset = request.permissionPreset,
                interactionBridge = bridge,
                isCancelled = { !isActive },
                approvalAgent = approvalAgentFactory(request) ?: com.agent.shared.tool.runtime.ManualFallbackToolApprovalAgent,
                hookDispatcher = hookDispatcher,
                sessionId = request.sessionId,
            ).create()
            launch(executionDispatcher) {
                val mcpLease = mcpToolRegistryBridge.create(
                    baseRegistry = desktopRegistry,
                    servers = request.runtimeResources.mcpServers,
                    permissionPreset = request.permissionPreset,
                    interactionBridge = bridge,
                    hookDispatcher = hookDispatcher,
                    sessionId = request.sessionId,
                    workspacePath = request.workspacePath,
                )
                try {
                    val preparedRequest = prepareRequestForHooks(request, hookDispatcher, eventQueue::send) ?: return@launch
                    mcpLease.diagnostics.forEach { diagnostic ->
                        eventQueue.send(
                            AgentStreamEvent.ToolCallFailed(
                                toolCallId = null,
                                name = "MCP:${diagnostic.serverId}",
                                reason = diagnostic.message,
                            ),
                        )
                    }
                    val result = runWithStopHooks(
                        request = preparedRequest,
                        hookDispatcher = hookDispatcher,
                        toolRegistry = mcpLease.registry,
                        bridge = bridge,
                        emitEvent = eventQueue::send,
                    )
                    eventQueue.send(AgentStreamEvent.Completed(result))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    val reason = agentFailureReason(error, request.profile.maxIterations)
                    dispatchHookSafely(
                        hookDispatcher,
                        hookRequest(
                            event = com.agent.shared.settings.model.AgentHookEvent.STOP_FAILURE,
                            request = request,
                            payload = buildJsonObject { put("error", reason) },
                        ),
                    )
                    eventQueue.send(AgentStreamEvent.Failed(reason))
                } finally {
                    mcpLease.close()
                    eventQueue.close()
                    forwardingJob.join()
                    close()
                }
            }
            awaitClose {
                eventQueue.cancel()
                forwardingJob.cancel()
            }
        }
    }

    /**
     * 在应用窗口关闭或会话被移除时结束其 Hook 生命周期。
     *
     * 该调用不阻塞 UI；SessionEnd 的 10 秒总预算由 dispatcher 自身执行。
     */
    fun endSession(
        sessionId: String,
        workspacePath: String,
    ) {
        if (sessionId.isBlank()) return
        val dispatcher = sessionHookDispatchers.remove(sessionId) ?: return
        startedHookSessions.remove(sessionId)
        lifecycleScope.launch {
            dispatchHookSafely(
                dispatcher,
                AgentHookDispatchRequest(
                    event = com.agent.shared.settings.model.AgentHookEvent.SESSION_END,
                    sessionId = sessionId,
                    workspacePath = workspacePath,
                    payload = buildJsonObject { put("reason", "session_closed") },
                ),
            )
        }
    }

    /** 每轮冻结最新规则；会话启动状态独立保存，结束事件使用最近一轮的配置。 */
    private fun hookDispatcherFor(request: AgentRunRequest): AgentHookDispatcher {
        val dispatcher = hookDispatcherFactory(request)
        if (request.sessionId.isNotBlank()) sessionHookDispatchers[request.sessionId] = dispatcher
        return dispatcher
    }

    /** 执行启动与用户提交 Hook，并把其上下文或更新后的 prompt 固定到本次请求。 */
    private suspend fun prepareRequestForHooks(
        request: AgentRunRequest,
        dispatcher: AgentHookDispatcher,
        emitEvent: suspend (AgentStreamEvent) -> Unit,
    ): AgentRunRequest? {
        var preparedRequest = request
        if (request.sessionId.isBlank() || startedHookSessions.add(request.sessionId)) {
            val startResult = dispatchHookSafely(
                dispatcher,
                hookRequest(
                    event = com.agent.shared.settings.model.AgentHookEvent.SESSION_START,
                    request = preparedRequest,
                    payload = buildJsonObject { put("source", "chat") },
                ),
            )
            if (startResult.decision == AgentHookDecision.BLOCK) {
                startedHookSessions.remove(request.sessionId)
                emitEvent(AgentStreamEvent.Failed("Hook 已阻止启动当前会话。"))
                return null
            }
            preparedRequest = preparedRequest.applyHookPrompt(startResult)
        }
        val submitResult = dispatchHookSafely(
            dispatcher,
            hookRequest(
                event = com.agent.shared.settings.model.AgentHookEvent.USER_PROMPT_SUBMIT,
                request = preparedRequest,
                payload = buildJsonObject { put("prompt", preparedRequest.prompt) },
            ),
        )
        if (submitResult.decision == AgentHookDecision.BLOCK) {
            emitEvent(AgentStreamEvent.Failed("Hook 已阻止发送当前消息。"))
            return null
        }
        return preparedRequest.applyHookPrompt(submitResult)
    }

    /** 在模型完成前运行 Stop；被阻断时把完成文本加入历史并让 agent 继续执行。 */
    private suspend fun runWithStopHooks(
        request: AgentRunRequest,
        hookDispatcher: AgentHookDispatcher,
        toolRegistry: ToolRegistry,
        bridge: DesktopToolInteractionBridge,
        emitEvent: suspend (AgentStreamEvent) -> Unit,
    ): String {
        var currentRequest = request
        repeat(STOP_RETRY_LIMIT + 1) { retryIndex ->
            val result = agentRunner(currentRequest, toolRegistry, bridge, emitEvent)
            val stopResult = dispatchHookSafely(
                hookDispatcher,
                hookRequest(
                    event = com.agent.shared.settings.model.AgentHookEvent.STOP,
                    request = currentRequest,
                    payload = buildJsonObject {
                        put("response", result)
                        put("retry_count", retryIndex)
                    },
                ),
            )
            if (stopResult.decision != AgentHookDecision.BLOCK) return result
            if (retryIndex == STOP_RETRY_LIMIT) {
                throw AgentHookStopRetryLimitException(STOP_RETRY_LIMIT)
            }
            emitEvent(AgentStreamEvent.Status("Stop Hook 要求继续，正在进行第 ${retryIndex + 1} 次补充处理。"))
            currentRequest = currentRequest.continueAfterStop(result, stopResult.additionalContext)
        }
        error("不可达的 Stop Hook 重试状态")
    }

    /** Hook 异常不能使已有会话卡死；保留 agent 主流程并把错误交给日志/命令诊断处理。 */
    private suspend fun dispatchHookSafely(
        dispatcher: AgentHookDispatcher,
        hookRequest: AgentHookDispatchRequest,
    ): AgentHookDispatchResult = try {
        dispatcher.dispatch(hookRequest)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        AgentHookDispatchResult()
    }

    /** 构建共享生命周期字段的 Hook 请求。 */
    private fun hookRequest(
        event: com.agent.shared.settings.model.AgentHookEvent,
        request: AgentRunRequest,
        payload: JsonObject,
    ): AgentHookDispatchRequest = AgentHookDispatchRequest(
        event = event,
        sessionId = request.sessionId,
        workspacePath = request.workspacePath,
        payload = payload,
    )

    /**
     * 将 ask_user / approval 桥接为 UI 可消费事件。
     */
    private fun eventEmittingBridge(
        emitEvent: suspend (AgentStreamEvent) -> Unit,
        emitToolOutput: (AgentStreamEvent.ToolOutputDelta) -> Unit,
        emitFileDiffPreview: (AgentStreamEvent.ToolFileDiffPreviewed) -> Unit,
    ): DesktopToolInteractionBridge = object : DesktopToolInteractionBridge {
        override fun isApprovalAutoApproved(request: ApprovalRequest): Boolean =
            interactionBridge.isApprovalAutoApproved(request)

        override suspend fun requestQuestion(request: QuestionRequest): String {
            emitEvent(AgentStreamEvent.QuestionRequested(request))
            return interactionBridge.requestQuestion(request)
        }

        override suspend fun requestApproval(request: ApprovalRequest): Boolean {
            if (!interactionBridge.isApprovalAutoApproved(request)) {
                emitEvent(AgentStreamEvent.ApprovalRequested(request))
            }
            return interactionBridge.requestApproval(request)
        }

        override fun onToolOutputChunk(
            toolName: String,
            text: String,
            isErrorStream: Boolean,
        ) {
            if (text.isEmpty()) return
            emitToolOutput(
                AgentStreamEvent.ToolOutputDelta(
                    name = toolName,
                    text = text,
                    stream = if (isErrorStream) {
                        AgentStreamEvent.ToolOutputStream.Stderr
                    } else {
                        AgentStreamEvent.ToolOutputStream.Stdout
                    },
                ),
            )
        }

        override fun onFileDiffPreview(toolName: String, diffs: List<com.agent.shared.tool.model.FileDiffPreview>) {
            if (diffs.isEmpty()) return
            emitFileDiffPreview(
                AgentStreamEvent.ToolFileDiffPreviewed(
                    name = toolName,
                    diffs = diffs,
                ),
            )
        }
    }

    private companion object {
        const val TOOL_EVENT_CHANNEL_CAPACITY = 64
        const val STOP_RETRY_LIMIT = 8
    }
}

/** Stop Hook 连续要求继续超过 Junie 的默认上限时抛出，外层会把它转成可恢复的会话失败。 */
private class AgentHookStopRetryLimitException(
    retryLimit: Int,
) : IllegalStateException("Stop Hook 已连续要求继续 $retryLimit 次，当前会话已安全停止。")

/** 将 Hook 输出中的 prompt 与附加上下文映射回结构化用户输入，附件顺序保持不变。 */
private fun AgentRunRequest.applyHookPrompt(result: AgentHookDispatchResult): AgentRunRequest {
    val updatedPrompt = result.updatedInput?.get("prompt")?.jsonPrimitive?.contentOrNull ?: prompt
    val additionalContext = result.additionalContext?.trim().orEmpty()
    val effectivePrompt = if (additionalContext.isBlank()) {
        updatedPrompt
    } else {
        "$updatedPrompt\n\n[Hook context]\n$additionalContext"
    }
    if (effectivePrompt == prompt) return this
    var replacedText = false
    val effectiveParts = inputParts.map { part ->
        if (!replacedText && part is UserInputPart.Text) {
            replacedText = true
            UserInputPart.Text(effectivePrompt)
        } else {
            part
        }
    }.let { parts ->
        if (replacedText) parts else listOf(UserInputPart.Text(effectivePrompt)) + parts
    }
    return copy(prompt = effectivePrompt, inputParts = effectiveParts)
}

/** 将被 Stop Hook 拦截的完成结果变成下一轮可见历史，并附加其继续说明。 */
private fun AgentRunRequest.continueAfterStop(
    previousResult: String,
    additionalContext: String?,
): AgentRunRequest {
    val continuationPrompt = buildString {
        append("上一轮已经给出结果，但 Stop Hook 要求继续处理。请检查并补充尚未完成的部分。")
        additionalContext?.trim()?.takeIf(String::isNotBlank)?.let { context ->
            append("\n\n[Hook context]\n")
            append(context)
        }
    }
    return copy(
        prompt = continuationPrompt,
        history = history + AgentConversationHistoryMessage.User(prompt, inputParts) +
            AgentConversationHistoryMessage.Assistant(listOf(AgentConversationHistoryPart.Text(previousResult))),
        inputParts = listOf(UserInputPart.Text(continuationPrompt)),
    )
}

/**
 * 将 Koog 的循环上限异常变为可行动的会话提示；其他异常保留原始原因以利于诊断。
 */
internal fun agentFailureReason(error: Exception, maxIterations: Int): String = when (error) {
    is AIAgentMaxNumberOfIterationsReachedException -> {
        if (maxIterations == AgentIterationLimit.KOOG_MAXIMUM) {
            "Agent 已达到运行时允许的最大迭代次数；当前会话仍可继续，请缩小任务范围后重试。"
        } else {
            "Agent 已达到本次配置的最大迭代次数（$maxIterations）；当前会话仍可继续，请提高上限或缩小任务范围后重试。"
        }
    }

    else -> error.message ?: "执行错误"
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
                                toolCallId = frame.id,
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
                                toolCallId = frame.id,
                                name = frame.name,
                                argumentsPreview = frame.content.toPreview(),
                            ),
                        )
                    }
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
    } catch (error: Exception) {
        emit(AgentStreamEvent.Failed(agentFailureReason(error, request.profile.maxIterations)))
    }
}
