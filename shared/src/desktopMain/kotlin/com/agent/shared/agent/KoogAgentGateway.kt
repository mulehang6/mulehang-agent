package com.agent.shared.agent

import ai.koog.prompt.Prompt
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Koog 1.0.0 接入点，负责执行单轮消息并转换为应用事件。
 */
class KoogAgentGateway(
    private val streamRunner: suspend (request: AgentRunRequest) -> Flow<StreamFrame> = { request ->
        if (request.profile.isDeepSeekChatCompletionsProfile()) {
            DeepSeekChatCompletionsStreamer().stream(request)
        } else {
            buildPromptExecutor(request.profile).executeStreaming(
                prompt = buildPrompt(request.prompt),
                model = buildLlmModel(request.profile),
            )
        }
    },
) : AgentGateway {
    /**
     * 运行一次消息请求。
     */
    override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
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
}

/**
 * 构建一次用户消息 prompt。
 */
private fun buildPrompt(prompt: String): Prompt = Prompt.build(id = "mulehang-chat") {
    user(prompt)
}

/**
 * 生成适合 UI 最小展示的事件预览。
 */
private fun String.toPreview(limit: Int = 120): String = replace("\n", " ").trim().take(limit)
