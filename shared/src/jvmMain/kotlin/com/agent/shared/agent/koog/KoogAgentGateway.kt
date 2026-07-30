package com.agent.shared.agent.koog

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.agent.api.AgentGateway
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.interaction.RejectingDesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.QuestionRequest
import com.agent.shared.tool.runtime.DesktopToolRegistryFactory
import kotlinx.coroutines.CancellationException
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
                } catch (cancellation: CancellationException) {
                    throw cancellation
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
