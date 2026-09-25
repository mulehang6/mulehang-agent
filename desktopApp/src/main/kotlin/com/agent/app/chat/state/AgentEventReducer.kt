package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ToolEventStatus

/**
 * 将单个 agent 流事件归并到给定会话，不读取或修改外部状态。
 */
internal fun reduceAgentEvent(
    conversation: ChatConversationUiState,
    event: AgentStreamEvent,
    contextWindow: Int?,
): ChatConversationUiState {
    if (event is AgentStreamEvent.Status) {
        return conversation.copy(progressMessage = event.message.takeIf(String::isNotBlank))
    }
    return reduceContentEvent(conversation.copy(progressMessage = null), event, contextWindow)
}

/** 实际内容或生命周期事件到达时撤下临时提示，再归并时间线和模型历史。 */
private fun reduceContentEvent(
    conversation: ChatConversationUiState,
    event: AgentStreamEvent,
    contextWindow: Int?,
): ChatConversationUiState = when (event) {
    AgentStreamEvent.Started -> conversation.copy(executionState = ExecutionState.Running)

    is AgentStreamEvent.TextDelta -> appendAssistantTextHistory(
        appendAssistantDelta(conversation, event.text, contextWindow),
        event.text,
    )

    is AgentStreamEvent.ToolCallStarted -> appendAssistantToolCallHistory(
        appendToolEvent(
            conversation = conversation,
            toolName = event.name,
            status = ToolEventStatus.Started,
            preview = event.argumentsPreview,
            operationIntent = event.operationIntent,
            toolCallId = event.toolCallId,
            contextWindow = contextWindow,
        ),
        id = event.toolCallId,
        name = event.name,
        argumentsPreview = event.argumentsPreview,
    )

    is AgentStreamEvent.ToolOutputDelta -> appendToolOutput(
        conversation = conversation,
        toolCallId = event.toolCallId,
        toolName = event.name,
        text = event.text,
        isErrorStream = event.stream == AgentStreamEvent.ToolOutputStream.Stderr,
        contextWindow = contextWindow,
    )

    is AgentStreamEvent.ToolCallFinished -> appendAssistantToolResultHistory(
        completeToolEvent(
            conversation = conversation,
            toolCallId = event.toolCallId,
            toolName = event.name,
            resultPreview = event.resultPreview,
            resultDisplay = event.resultDisplay,
            contextWindow = contextWindow,
        ),
        id = event.toolCallId,
        name = event.name,
        resultPreview = event.resultPreview,
    )

    is AgentStreamEvent.ToolFileDiffPreviewed -> attachFileDiffsToActiveTool(
        conversation = conversation,
        toolName = event.name,
        diffs = event.diffs,
    )

    is AgentStreamEvent.ToolCallFailed -> markToolCallFailed(
        conversation = conversation,
        toolCallId = event.toolCallId,
        toolName = event.name,
        reason = event.reason,
        contextWindow = contextWindow,
    )

    is AgentStreamEvent.ToolCallInterrupted -> markToolCallFailed(
        conversation = conversation,
        toolCallId = event.toolCallId,
        toolName = event.name,
        reason = event.reason,
        contextWindow = contextWindow,
    )

    is AgentStreamEvent.QuestionRequested -> {
        val questions = event.request.effectiveQuestions
        conversation.copy(
            pendingQuestion = PendingQuestionUiState(
                requestId = event.request.requestId,
                question = questions.firstOrNull()?.question.orEmpty(),
                options = questions.firstOrNull()?.options.orEmpty(),
                questions = questions,
                allowFreeText = event.request.allowFreeText,
            ),
            pendingApproval = null,
            executionState = ExecutionState.WaitingForUserInput,
        )
    }

    is AgentStreamEvent.ApprovalRequested -> conversation.copy(
        pendingApproval = PendingApprovalUiState(
            requestId = event.request.requestId,
            toolName = event.request.toolName,
            summary = event.request.summary,
            targetPath = event.request.targetPath,
            payloadPreview = event.request.payloadPreview,
            diff = event.request.diff,
            diffs = event.request.diffs,
        ),
        pendingQuestion = null,
        executionState = ExecutionState.WaitingForApproval,
    )

    is AgentStreamEvent.Status -> conversation
    is AgentStreamEvent.StatusSnapshotUpdated -> conversation

    is AgentStreamEvent.UsageUpdated -> conversation.copy(
        contextUsageFraction = if (event.inputTokens != null && (event.contextWindow ?: 0) > 0) {
            (event.inputTokens!!.toFloat() / event.contextWindow!!).coerceAtLeast(0f)
        } else {
            conversation.contextUsageFraction
        },
        providerContextUsageFraction = if (event.inputTokens != null && (event.contextWindow ?: 0) > 0) {
            (event.inputTokens!!.toFloat() / event.contextWindow!!).coerceAtLeast(0f)
        } else {
            conversation.providerContextUsageFraction
        },
    )

    is AgentStreamEvent.ReasoningDelta -> appendAssistantReasoningHistory(
        appendReasoningDelta(
            conversation = conversation,
            summary = event.summary,
            rawText = event.rawText,
            contextWindow = contextWindow,
        ),
        summary = event.summary,
        rawText = event.rawText,
    )

    is AgentStreamEvent.ReasoningCompleted -> completeAssistantReasoningHistory(
        completeReasoning(
            conversation = conversation,
            summary = event.summary,
            rawText = event.rawText,
            contextWindow = contextWindow,
        ),
        summary = event.summary,
        rawText = event.rawText,
    )

    is AgentStreamEvent.Completed -> completeAssistantMessage(conversation, event.text, contextWindow).copy(
        pendingQuestion = null,
        pendingApproval = null,
    )

    is AgentStreamEvent.Failed -> {
        val closedConversation = closeStreamingReasoning(conversation)
        val withToolFailure = attachFailureToTimeline(closedConversation, event.reason, contextWindow)
        withToolFailure.copy(
            executionState = ExecutionState.Failed(
                AppError(
                    title = "Agent 执行失败",
                    message = event.reason,
                ),
            ),
            streamingAssistantItemIndex = null,
            streamingReasoningItemIndex = null,
            streamingAssistantHistoryIndex = null,
            pendingQuestion = null,
            pendingApproval = null,
        )
    }
}
