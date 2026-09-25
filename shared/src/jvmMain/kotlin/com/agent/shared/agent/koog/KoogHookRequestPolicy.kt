package com.agent.shared.agent.koog

import ai.koog.agents.core.agent.exception.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.agent.hook.AgentHookDispatchResult
import com.agent.shared.settings.model.AgentIterationLimit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Stop Hook 连续要求继续超过 Junie 的默认上限时抛出，外层会把它转成可恢复的会话失败。 */
internal class AgentHookStopRetryLimitException(
    retryLimit: Int,
) : IllegalStateException("Stop Hook 已连续要求继续 $retryLimit 次，当前会话已安全停止。")

/** 将 Hook 输出中的 prompt 与附加上下文映射回结构化用户输入，附件顺序保持不变。 */
internal fun AgentRunRequest.applyHookPrompt(result: AgentHookDispatchResult): AgentRunRequest {
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
internal fun AgentRunRequest.continueAfterStop(
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
        resumeRunId = null,
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
internal fun runLegacyStream(
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
