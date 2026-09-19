package com.agent.app.chat.state

import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus

/**
 * 将工具或状态事件追加到时间线。
 */
internal fun appendToolEvent(
    conversation: ChatConversationUiState,
    toolName: String,
    status: ToolEventStatus,
    preview: String?,
    operationIntent: String? = null,
    toolCallId: String? = null,
    contextWindow: Int?,
): ChatConversationUiState {
    val normalizedConversation = closeStreamingAssistant(closeStreamingReasoning(conversation))
    val nextItems = normalizedConversation.items + ToolEventItem(
        toolName = toolName,
        status = status,
        preview = preview,
        operationIntent = operationIntent,
        toolCallId = toolCallId,
    )
    val nextConversation = normalizedConversation.copy(items = nextItems)
    return nextConversation.copy(
        contextUsageFraction = estimateContextUsage(
            items = nextConversation.items,
            attachmentCount = nextConversation.attachments.size,
            contextWindow = contextWindow,
        ),
    )
}

/**
 * 将工具输出回填到同一次调用的输入卡片；缺失输入事件时才创建仅含输出的卡片。
 */
internal fun completeToolEvent(
    conversation: ChatConversationUiState,
    toolCallId: String?,
    toolName: String,
    resultPreview: String?,
    resultDisplay: String?,
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
            items[items.lastIndex] = event.copy(
                resultPreview = resultPreview,
                resultDisplay = resultDisplay,
            )
            updated.copy(items = items)
        }
    }
    val items = conversation.items.toMutableList()
    val started = items[matchedIndex] as ToolEventItem
    items[matchedIndex] = started.copy(
        status = ToolEventStatus.Finished,
        resultPreview = resultPreview,
        resultDisplay = resultDisplay,
    )
    return conversation.copy(items = items)
}

/**
 * 将文件工具刚生成的 Diff 附加到最近开始的同名调用。
 *
 * Koog 按调用开始、工具执行、调用完成的顺序派发事件；找不到对应调用时忽略预览，避免把
 * 已过期 Diff 误挂到其他工具卡片。
 */
internal fun attachFileDiffsToActiveTool(
    conversation: ChatConversationUiState,
    toolName: String,
    diffs: List<com.agent.shared.tool.model.FileDiffPreview>,
): ChatConversationUiState {
    val matchedIndex = conversation.items.indexOfLast { candidate ->
        candidate is ToolEventItem && candidate.status == ToolEventStatus.Started && candidate.toolName == toolName
    }
    if (matchedIndex < 0) return conversation
    val updatedItems = conversation.items.toMutableList()
    val activeTool = updatedItems[matchedIndex] as ToolEventItem
    updatedItems[matchedIndex] = activeTool.copy(fileDiffs = diffs)
    return conversation.copy(items = updatedItems)
}

/**
 * 将指定工具调用标记为失败；不改动会话执行状态，agent 运行继续。
 *
 * 按 toolCallId 匹配进行中的工具卡片，缺失 id 时回退到同名匹配；找不到
 * 进行中卡片时追加一条独立失败卡片。
 */
internal fun markToolCallFailed(
    conversation: ChatConversationUiState,
    toolCallId: String?,
    toolName: String,
    reason: String,
    contextWindow: Int?,
): ChatConversationUiState {
    val matchedIndex = conversation.items.indexOfLast { candidate ->
        candidate is ToolEventItem && candidate.status == ToolEventStatus.Started &&
                (candidate.toolCallId == toolCallId || (toolCallId == null && candidate.toolName == toolName))
    }
    if (matchedIndex < 0) {
        val nextItems = conversation.items + ToolEventItem(
            toolName = toolName,
            status = ToolEventStatus.Failed,
            errorMessage = reason,
            toolCallId = toolCallId,
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
    val items = conversation.items.toMutableList()
    val started = items[matchedIndex] as ToolEventItem
    items[matchedIndex] = started.copy(
        status = ToolEventStatus.Failed,
        errorMessage = reason,
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
 * 将进行中工具的输出增量追加到对应卡片，而不提前结束该工具调用。
 */
internal fun appendToolOutput(
    conversation: ChatConversationUiState,
    toolCallId: String?,
    toolName: String,
    text: String,
    isErrorStream: Boolean,
    contextWindow: Int?,
): ChatConversationUiState {
    if (text.isEmpty()) return conversation
    val matchedIndex = conversation.items.indexOfLast { candidate ->
        candidate is ToolEventItem && candidate.status == ToolEventStatus.Started &&
                (candidate.toolCallId == toolCallId || (toolCallId == null && candidate.toolName == toolName))
    }
    val displayText = if (isErrorStream) "stderr: $text" else text
    if (matchedIndex < 0) {
        return appendToolEvent(
            conversation = conversation,
            toolName = toolName,
            status = ToolEventStatus.Started,
            preview = null,
            toolCallId = toolCallId,
            contextWindow = contextWindow,
        ).let { updated ->
            val items = updated.items.toMutableList()
            val event = items.last() as ToolEventItem
            items[items.lastIndex] = event.copy(resultDisplay = displayText)
            updated.copy(items = items)
        }
    }
    val items = conversation.items.toMutableList()
    val started = items[matchedIndex] as ToolEventItem
    items[matchedIndex] = started.copy(
        resultDisplay = started.resultDisplay.orEmpty() + displayText,
    )
    return conversation.copy(items = items)
}
