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
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.prompt.buildLlmModel
import com.agent.shared.agent.prompt.isDeepSeekChatCompletionsProfile
import com.agent.shared.agent.provider.deepseek.DeepSeekChatCompletionsStreamer
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import kotlinx.coroutines.flow.Flow

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
                            toolCallId = context.toolCallId,
                            name = context.toolName,
                            argumentsPreview = context.toolArgs.toString().toPreview(),
                        ),
                    )
                }
                onToolCallCompleted { context ->
                    emitEvent(
                        AgentStreamEvent.ToolCallFinished(
                            toolCallId = context.toolCallId,
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
                buildConversationMessages(request.history, message).forEach(::message)
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
