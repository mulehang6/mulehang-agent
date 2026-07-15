package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart

/**
 * 确保当前会话存在一个可追加的流式助手历史消息。
 */
internal fun ensureStreamingAssistantHistory(conversation: ChatConversationUiState): ChatConversationUiState {
    val currentIndex = conversation.streamingAssistantHistoryIndex
    if (currentIndex != null && conversation.history.getOrNull(currentIndex) is AgentConversationHistoryMessage.Assistant) {
        return conversation
    }
    val nextHistory = conversation.history + AgentConversationHistoryMessage.Assistant()
    return conversation.copy(
        history = nextHistory,
        streamingAssistantHistoryIndex = nextHistory.lastIndex,
    )
}

/**
 * 向当前流式助手历史中追加文本，并与最后一个文本 part 合并。
 */
internal fun appendAssistantTextHistory(
    conversation: ChatConversationUiState,
    delta: String,
): ChatConversationUiState {
    if (delta.isEmpty()) return conversation
    return updateAssistantHistoryParts(conversation) { parts ->
        val last = parts.lastOrNull()
        if (last is AgentConversationHistoryPart.Text) {
            parts.dropLast(1) + last.copy(text = last.text + delta)
        } else {
            parts + AgentConversationHistoryPart.Text(text = delta)
        }
    }
}

/**
 * 向当前流式助手历史中追加 reasoning，并与最后一个 reasoning part 合并。
 */
internal fun appendAssistantReasoningHistory(
    conversation: ChatConversationUiState,
    summary: String?,
    rawText: String?,
): ChatConversationUiState {
    if (summary.isNullOrEmpty() && rawText.isNullOrEmpty()) return conversation
    return updateAssistantHistoryParts(conversation) { parts ->
        val last = parts.lastOrNull()
        if (last is AgentConversationHistoryPart.Reasoning) {
            val mergedSummary = last.summary.orEmpty().appendNullable(summary).takeIf { it.isNotBlank() }
            val mergedRawText = last.rawText.orEmpty().appendNullable(rawText).takeIf { it.isNotBlank() }
            parts.dropLast(1) + last.copy(
                summary = mergedSummary,
                rawText = mergedRawText,
            )
        } else {
            parts + AgentConversationHistoryPart.Reasoning(
                summary = summary,
                rawText = rawText,
            )
        }
    }
}

/**
 * 用完整 reasoning 收尾当前 assistant history 中最后一个 reasoning part。
 */
internal fun completeAssistantReasoningHistory(
    conversation: ChatConversationUiState,
    summary: String?,
    rawText: String?,
): ChatConversationUiState {
    if (summary.isNullOrEmpty() && rawText.isNullOrEmpty()) return conversation
    return updateAssistantHistoryParts(conversation) { parts ->
        val reasoningIndex = parts.indexOfLast { part -> part is AgentConversationHistoryPart.Reasoning }
        if (reasoningIndex >= 0) {
            val last = parts[reasoningIndex] as AgentConversationHistoryPart.Reasoning
            parts.toMutableList().apply {
                this[reasoningIndex] = last.copy(
                    summary = summary ?: last.summary,
                    rawText = rawText ?: last.rawText,
                )
            }
        } else {
            parts + AgentConversationHistoryPart.Reasoning(
                summary = summary,
                rawText = rawText,
            )
        }
    }
}

/**
 * 追加 assistant tool call 历史片段。
 */
internal fun appendAssistantToolCallHistory(
    conversation: ChatConversationUiState,
    id: String?,
    name: String,
    argumentsPreview: String?,
): ChatConversationUiState = updateAssistantHistoryParts(conversation) { parts ->
    parts + AgentConversationHistoryPart.ToolCall(
        id = id,
        name = name,
        argumentsPreview = argumentsPreview,
    )
}

/**
 * 追加 assistant tool result 历史片段。
 */
internal fun appendAssistantToolResultHistory(
    conversation: ChatConversationUiState,
    id: String?,
    name: String,
    resultPreview: String?,
): ChatConversationUiState = updateAssistantHistoryParts(conversation) { parts ->
    parts + AgentConversationHistoryPart.ToolResult(
        id = id,
        name = name,
        resultPreview = resultPreview,
    )
}

/**
 * 在 assistant message 完成时用最终正文收尾文本 part，避免和流式增量重复累加。
 */
internal fun finalizeAssistantTextHistory(
    conversation: ChatConversationUiState,
    finalText: String,
): ChatConversationUiState {
    if (finalText.isBlank()) return conversation
    return updateAssistantHistoryParts(conversation) { parts ->
        val last = parts.lastOrNull()
        if (last is AgentConversationHistoryPart.Text) {
            parts.dropLast(1) + last.copy(text = finalText)
        } else {
            parts + AgentConversationHistoryPart.Text(text = finalText)
        }
    }
}

/**
 * 更新当前流式 assistant history 的 parts 列表。
 */
private fun updateAssistantHistoryParts(
    conversation: ChatConversationUiState,
    transform: (List<AgentConversationHistoryPart>) -> List<AgentConversationHistoryPart>,
): ChatConversationUiState {
    val normalizedConversation = ensureStreamingAssistantHistory(conversation)
    val historyIndex = normalizedConversation.streamingAssistantHistoryIndex ?: return normalizedConversation
    val assistant = normalizedConversation.history[historyIndex] as? AgentConversationHistoryMessage.Assistant
        ?: return normalizedConversation
    val updatedHistory = normalizedConversation.history.toMutableList()
    updatedHistory[historyIndex] = assistant.copy(parts = transform(assistant.parts))
    return normalizedConversation.copy(history = updatedHistory)
}

/**
 * 仅在有值时追加文本片段。
 */
private fun String.appendNullable(next: String?): String = if (next.isNullOrEmpty()) this else this + next
