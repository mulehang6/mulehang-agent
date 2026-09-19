package com.agent.app.chat.state

import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationItem
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem

/**
 * 将文本增量拼接到当前正在生成的助手消息。
 */
internal fun appendAssistantDelta(
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
 * 将思考增量拼接到当前思考块。
 */
internal fun appendReasoningDelta(
    conversation: ChatConversationUiState,
    summary: String?,
    rawText: String?,
    contextWindow: Int?,
): ChatConversationUiState {
    if (summary.isNullOrEmpty() && rawText.isNullOrEmpty()) return conversation
    val normalizedConversation = closeStreamingAssistant(conversation)
    val currentIndex = normalizedConversation.streamingReasoningItemIndex
    return if (currentIndex == null) {
        val nextItems = normalizedConversation.items + ReasoningItem(
            summaryText = summary,
            rawText = rawText ?: summary,
            expanded = true,
            isStreaming = true,
        )
        normalizedConversation.copy(
            items = nextItems,
            streamingReasoningItemIndex = normalizedConversation.items.size,
            contextUsageFraction = estimateContextUsage(
                items = nextItems,
                attachmentCount = normalizedConversation.attachments.size,
                contextWindow = contextWindow,
            ),
        )
    } else {
        val existingItem = normalizedConversation.items[currentIndex] as? ReasoningItem ?: return normalizedConversation
        val updatedItems = normalizedConversation.items.toMutableList()
        updatedItems[currentIndex] = existingItem.copy(
            summaryText = existingItem.summaryText.orEmpty().appendNullable(summary),
            rawText = existingItem.rawText.orEmpty().appendNullable(rawText ?: summary),
            expanded = true,
            isStreaming = true,
        )
        normalizedConversation.copy(items = updatedItems)
    }
}

/**
 * 结束当前流式正文段，使后续工具、思考或文本事件在时间线中保持真实顺序。
 */
internal fun closeStreamingAssistant(source: ChatConversationUiState): ChatConversationUiState =
    source.copy(streamingAssistantItemIndex = null)

/**
 * 收到 reasoning 完整事件后收尾当前思考块。
 */
internal fun completeReasoning(
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
internal fun completeAssistantMessage(
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
internal fun appendCompletedAssistantIfNeeded(
    items: List<ConversationItem>,
    finalText: String,
): List<ConversationItem> {
    if (finalText.isBlank()) return items
    return items + ChatMessageItem(ChatMessage(ChatRole.Assistant, finalText))
}

/**
 * 在进入工具或正文阶段前关闭仍处于流式中的思考块。
 */
internal fun closeStreamingReasoning(source: ChatConversationUiState): ChatConversationUiState {
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
