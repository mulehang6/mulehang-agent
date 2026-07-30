package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationItem
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus

/**
 * 将单个 agent 流事件归并到给定会话，不读取或修改外部状态。
 */
internal fun reduceAgentEvent(
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

    is AgentStreamEvent.ToolCallFinished -> appendAssistantToolResultHistory(
        completeToolEvent(
            conversation = conversation,
            toolCallId = event.toolCallId,
            toolName = event.name,
            resultPreview = event.resultPreview,
            contextWindow = contextWindow,
        ),
        id = event.toolCallId,
        name = event.name,
        resultPreview = event.resultPreview,
    )

    is AgentStreamEvent.QuestionRequested -> conversation.copy(
        pendingQuestion = PendingQuestionUiState(
            requestId = event.request.requestId,
            question = event.request.question,
            options = event.request.options,
            allowFreeText = event.request.allowFreeText,
        ),
        pendingApproval = null,
        executionState = ExecutionState.WaitingForUserInput,
    )

    is AgentStreamEvent.ApprovalRequested -> conversation.copy(
        pendingApproval = PendingApprovalUiState(
            requestId = event.request.requestId,
            toolName = event.request.toolName,
            summary = event.request.summary,
            targetPath = event.request.targetPath,
            payloadPreview = event.request.payloadPreview,
        ),
        pendingQuestion = null,
        executionState = ExecutionState.WaitingForApproval,
    )

    is AgentStreamEvent.Status -> appendToolEvent(
        conversation = conversation,
        toolName = "status",
        status = ToolEventStatus.Status,
        preview = event.message,
        contextWindow = contextWindow,
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

/**
 * 将文本增量拼接到当前正在生成的助手消息。
 */
private fun appendAssistantDelta(
    conversation: ChatConversationUiState,
    delta: String,
    contextWindow: Int?,
): ChatConversationUiState {
    if (delta.isEmpty()) return conversation
    val normalizedConversation = closeStreamingReasoning(conversation)
    val currentIndex = normalizedConversation.streamingAssistantItemIndex
    return if (currentIndex == null) {
        val nextItems = normalizedConversation.items + ChatMessageItem(ChatMessage(ChatRole.Assistant, delta))
        normalizedConversation.copy(
            items = nextItems,
            streamingAssistantItemIndex = normalizedConversation.items.size,
            contextUsageFraction = estimateContextUsage(
                items = nextItems,
                attachmentCount = normalizedConversation.attachments.size,
                contextWindow = contextWindow,
            ),
        )
    } else {
        val existingItem = normalizedConversation.items[currentIndex] as? ChatMessageItem
            ?: return normalizedConversation
        val updatedItems = normalizedConversation.items.toMutableList()
        updatedItems[currentIndex] = existingItem.copy(
            message = existingItem.message.copy(content = existingItem.message.content + delta),
        )
        normalizedConversation.copy(
            items = updatedItems,
            contextUsageFraction = estimateContextUsage(
                items = updatedItems,
                attachmentCount = normalizedConversation.attachments.size,
                contextWindow = contextWindow,
            ),
        )
    }
}

/**
 * 将工具或状态事件追加到时间线。
 */
private fun appendToolEvent(
    conversation: ChatConversationUiState,
    toolName: String,
    status: ToolEventStatus,
    preview: String?,
    operationIntent: String? = null,
    toolCallId: String? = null,
    contextWindow: Int?,
): ChatConversationUiState {
    val normalizedConversation = closeStreamingReasoning(conversation)
    val nextItems = normalizedConversation.items + ToolEventItem(
        toolName = toolName,
        status = status,
        preview = preview,
        operationIntent = operationIntent,
        toolCallId = toolCallId,
    )
    return normalizedConversation.copy(
        items = nextItems,
        contextUsageFraction = estimateContextUsage(
            items = nextItems,
            attachmentCount = normalizedConversation.attachments.size,
            contextWindow = contextWindow,
        ),
    )
}

/**
 * 将工具输出回填到同一次调用的输入卡片；缺失输入事件时才创建仅含输出的卡片。
 */
private fun completeToolEvent(
    conversation: ChatConversationUiState,
    toolCallId: String?,
    toolName: String,
    resultPreview: String?,
    contextWindow: Int?,
): ChatConversationUiState {
    val matchedIndex = conversation.items.indexOfLast { candidate ->
        candidate is ToolEventItem && candidate.status == ToolEventStatus.Started &&
                (candidate.toolCallId == toolCallId || (toolCallId == null && candidate.toolName == toolName))
    }
    if (matchedIndex < 0) {
        return appendToolEvent(
            conversation = conversation,
            toolName = toolName,
            status = ToolEventStatus.Finished,
            preview = null,
            toolCallId = toolCallId,
            contextWindow = contextWindow,
        ).let { updated ->
            val items = updated.items.toMutableList()
            val event = items.last() as ToolEventItem
            items[items.lastIndex] = event.copy(resultPreview = resultPreview)
            updated.copy(items = items)
        }
    }
    val items = conversation.items.toMutableList()
    val started = items[matchedIndex] as ToolEventItem
    items[matchedIndex] = started.copy(
        status = ToolEventStatus.Finished,
        resultPreview = resultPreview,
    )
    return conversation.copy(items = items)
}

/**
 * 将 agent 失败原因附加到最后一个未闭合工具事件，否则追加独立失败事件。
 */
internal fun attachFailureToTimeline(
    conversation: ChatConversationUiState,
    reason: String,
    contextWindow: Int?,
): ChatConversationUiState {
    val lastStartedIndex = conversation.items.indexOfLast { item ->
        item is ToolEventItem && item.status == ToolEventStatus.Started
    }
    val hasFinishedAfterLastStarted = lastStartedIndex >= 0 && conversation.items
        .drop(lastStartedIndex + 1)
        .any { it is ToolEventItem && it.status == ToolEventStatus.Finished }
    return if (lastStartedIndex >= 0 && !hasFinishedAfterLastStarted) {
        val updatedItems = conversation.items.toMutableList()
        val started = updatedItems[lastStartedIndex] as ToolEventItem
        updatedItems[lastStartedIndex] = started.copy(
            status = ToolEventStatus.Failed,
            errorMessage = reason,
        )
        conversation.copy(items = updatedItems)
    } else {
        val nextItems = conversation.items + ToolEventItem(
            toolName = "error",
            status = ToolEventStatus.Failed,
            errorMessage = reason,
        )
        conversation.copy(
            items = nextItems,
            contextUsageFraction = estimateContextUsage(
                items = nextItems,
                attachmentCount = conversation.attachments.size,
                contextWindow = contextWindow,
            ),
        )
    }
}

/**
 * 将思考增量拼接到当前思考块。
 */
private fun appendReasoningDelta(
    conversation: ChatConversationUiState,
    summary: String?,
    rawText: String?,
    contextWindow: Int?,
): ChatConversationUiState {
    if (summary.isNullOrEmpty() && rawText.isNullOrEmpty()) return conversation
    val currentIndex = conversation.streamingReasoningItemIndex
    return if (currentIndex == null) {
        val nextItems = conversation.items + ReasoningItem(
            summaryText = summary,
            rawText = rawText ?: summary,
            expanded = true,
            isStreaming = true,
        )
        conversation.copy(
            items = nextItems,
            streamingReasoningItemIndex = conversation.items.size,
            contextUsageFraction = estimateContextUsage(
                items = nextItems,
                attachmentCount = conversation.attachments.size,
                contextWindow = contextWindow,
            ),
        )
    } else {
        val existingItem = conversation.items[currentIndex] as? ReasoningItem ?: return conversation
        val updatedItems = conversation.items.toMutableList()
        updatedItems[currentIndex] = existingItem.copy(
            summaryText = existingItem.summaryText.orEmpty().appendNullable(summary),
            rawText = existingItem.rawText.orEmpty().appendNullable(rawText ?: summary),
            expanded = true,
            isStreaming = true,
        )
        conversation.copy(items = updatedItems)
    }
}

/**
 * 收到 reasoning 完整事件后收尾当前思考块。
 */
private fun completeReasoning(
    conversation: ChatConversationUiState,
    summary: String?,
    rawText: String?,
    contextWindow: Int?,
): ChatConversationUiState {
    val currentIndex = conversation.streamingReasoningItemIndex
        ?: conversation.items.indexOfLast { item -> item is ReasoningItem }.takeIf { it >= 0 }
        ?: run {
            val nextItems = conversation.items + ReasoningItem(
                summaryText = summary,
                rawText = rawText ?: summary,
                expanded = true,
                isStreaming = false,
                durationMillis = 0L,
            )
            return conversation.copy(
                items = nextItems,
                contextUsageFraction = estimateContextUsage(
                    items = nextItems,
                    attachmentCount = conversation.attachments.size,
                    contextWindow = contextWindow,
                ),
            )
        }
    val existingItem = conversation.items[currentIndex] as? ReasoningItem ?: return conversation
    val updatedItems = conversation.items.toMutableList()
    updatedItems[currentIndex] = existingItem.copy(
        summaryText = summary ?: existingItem.summaryText,
        rawText = rawText ?: existingItem.rawText,
        expanded = true,
        isStreaming = false,
        durationMillis = (System.currentTimeMillis() - existingItem.startedAtMillis).coerceAtLeast(0L),
    )
    return conversation.copy(
        items = updatedItems,
        streamingReasoningItemIndex = null,
    )
}

/**
 * 在完成时补齐最终正文，并清理流式状态。
 */
private fun completeAssistantMessage(
    conversation: ChatConversationUiState,
    finalText: String,
    contextWindow: Int?,
): ChatConversationUiState {
    val normalizedConversation = closeStreamingReasoning(conversation)
    val currentIndex = normalizedConversation.streamingAssistantItemIndex ?: run {
        val nextItems = appendCompletedAssistantIfNeeded(normalizedConversation.items, finalText)
        return finalizeAssistantTextHistory(normalizedConversation, finalText).copy(
            items = nextItems,
            executionState = ExecutionState.Idle,
            streamingAssistantItemIndex = null,
            streamingAssistantHistoryIndex = null,
            contextUsageFraction = estimateContextUsage(
                items = nextItems,
                attachmentCount = normalizedConversation.attachments.size,
                contextWindow = contextWindow,
            ),
        )
    }
    val existingItem = normalizedConversation.items[currentIndex] as? ChatMessageItem
        ?: return normalizedConversation.copy(
            executionState = ExecutionState.Idle,
            streamingAssistantItemIndex = null,
            streamingAssistantHistoryIndex = null,
        )
    val finalizedItem = if (finalText.isNotBlank() && existingItem.message.content != finalText) {
        existingItem.copy(message = existingItem.message.copy(content = finalText))
    } else {
        existingItem
    }
    val updatedItems = normalizedConversation.items.toMutableList().apply {
        if (currentIndex == lastIndex) {
            this[currentIndex] = finalizedItem
        } else {
            removeAt(currentIndex)
            add(finalizedItem)
        }
    }
    return finalizeAssistantTextHistory(normalizedConversation, finalText).copy(
        items = updatedItems,
        executionState = ExecutionState.Idle,
        streamingAssistantItemIndex = null,
        streamingAssistantHistoryIndex = null,
        contextUsageFraction = estimateContextUsage(
            items = updatedItems,
            attachmentCount = normalizedConversation.attachments.size,
            contextWindow = contextWindow,
        ),
    )
}

/**
 * 当底层只返回完成文本时补一条助手消息。
 */
private fun appendCompletedAssistantIfNeeded(
    items: List<ConversationItem>,
    finalText: String,
): List<ConversationItem> {
    if (finalText.isBlank()) return items
    return items + ChatMessageItem(ChatMessage(ChatRole.Assistant, finalText))
}

/**
 * 在进入工具或正文阶段前关闭仍处于流式中的思考块。
 */
private fun closeStreamingReasoning(source: ChatConversationUiState): ChatConversationUiState {
    val reasoningIndex = source.streamingReasoningItemIndex ?: return source
    val reasoningItem = source.items[reasoningIndex] as? ReasoningItem ?: return source.copy(
        streamingReasoningItemIndex = null,
    )
    if (!reasoningItem.isStreaming) return source.copy(streamingReasoningItemIndex = null)
    val updatedItems = source.items.toMutableList()
    updatedItems[reasoningIndex] = reasoningItem.copy(
        isStreaming = false,
        expanded = true,
        durationMillis = (System.currentTimeMillis() - reasoningItem.startedAtMillis).coerceAtLeast(0L),
    )
    return source.copy(
        items = updatedItems,
        streamingReasoningItemIndex = null,
    )
}

/**
 * 仅在有值时追加文本片段。
 */
private fun String.appendNullable(next: String?): String = if (next.isNullOrEmpty()) this else this + next
