package com.agent.shared.agent.koog

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.agent.api.AgentGateway
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.resource.McpToolRegistryBridge
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

/**
 * Koog 1.0.0 接入点，负责执行单轮消息并转换为应用事件。
 */
class KoogAgentGateway(
    private val interactionBridge: DesktopToolInteractionBridge = RejectingDesktopToolInteractionBridge,
    private val streamRunner: (suspend (request: AgentRunRequest) -> Flow<StreamFrame>)? = null,
    private val executionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val approvalAgentFactory: (AgentRunRequest) -> ToolApprovalAgent? = { request ->
        request.approvalProfile?.let(::KoogToolApprovalAgent)
    },
    private val agentRunner: suspend (
        request: AgentRunRequest,
        toolRegistry: ToolRegistry,
        bridge: DesktopToolInteractionBridge,
        emitEvent: suspend (AgentStreamEvent) -> Unit,
    ) -> String = ::runWithKoogAgent,
    private val mcpToolRegistryBridge: McpToolRegistryBridge = McpToolRegistryBridge(),
) : AgentGateway {
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
            val desktopRegistry = DesktopToolRegistryFactory(
                workspacePath = request.workspacePath,
                permissionPreset = request.permissionPreset,
                interactionBridge = bridge,
                isCancelled = { !isActive },
                approvalAgent = approvalAgentFactory(request) ?: com.agent.shared.tool.runtime.ManualFallbackToolApprovalAgent,
            ).create()
            launch(executionDispatcher) {
                val mcpLease = mcpToolRegistryBridge.create(
                    baseRegistry = desktopRegistry,
                    servers = request.runtimeResources.mcpServers,
                    permissionPreset = request.permissionPreset,
                    interactionBridge = bridge,
                )
                try {
                    mcpLease.diagnostics.forEach { diagnostic ->
                        eventQueue.send(
                            AgentStreamEvent.ToolCallFailed(
                                toolCallId = null,
                                name = "MCP:${diagnostic.serverId}",
                                reason = diagnostic.message,
                            ),
                        )
                    }
                    val result = agentRunner(
                        request,
                        mcpLease.registry,
                        bridge,
                        eventQueue::send,
                    )
                    eventQueue.send(AgentStreamEvent.Completed(result))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    eventQueue.send(AgentStreamEvent.Failed(e.message ?: "执行错误"))
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
    } catch (e: Exception) {
        emit(AgentStreamEvent.Failed(e.message ?: "执行错误"))
    }
}
