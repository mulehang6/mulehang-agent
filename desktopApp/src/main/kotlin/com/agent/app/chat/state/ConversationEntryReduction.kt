package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.ToolEventStatus
import com.agent.shared.chat.model.conversationEntryPath
import com.agent.shared.chat.model.projectConversationEntries

/** 为树格式会话追加用户消息，并立即从新 leaf 重建线性投影。 */
internal fun appendUserConversationEntry(
    conversation: ChatConversationUiState,
    prompt: String,
    inputParts: List<UserInputPart>,
    entryId: String,
    createdAt: Long,
): ChatConversationUiState {
    if (conversation.treeFormatVersion <= 0) return conversation
    val entry = ConversationEntry.Message(
        id = entryId,
        parentId = conversation.activeEntryId,
        createdAt = createdAt,
        message = ChatMessage(ChatRole.User, prompt),
        inputParts = inputParts,
    )
    return conversation.withEntryProjection(
        entries = conversation.entries + entry,
        activeEntryId = entry.id,
        headEntryId = entry.id,
    ).copy(
        streamingAssistantEntryId = null,
        streamingReasoningEntryId = null,
    )
}

/** 把一个流式事件同步到条目图；旧线性会话保持原有 reducer 行为。 */
internal fun applyConversationEntryEvent(
    conversation: ChatConversationUiState,
    event: AgentStreamEvent,
    idFactory: () -> String,
    clock: () -> Long,
): ChatConversationUiState {
    if (conversation.treeFormatVersion <= 0) return conversation
    return when (event) {
        is AgentStreamEvent.TextDelta -> conversation.appendAssistantText(event.text, idFactory, clock)
        is AgentStreamEvent.ReasoningDelta -> conversation.appendReasoningDelta(event, idFactory, clock)
        is AgentStreamEvent.ReasoningCompleted -> conversation.completeReasoning(event, idFactory, clock)
        is AgentStreamEvent.ToolCallStarted -> conversation.appendToolCall(event, idFactory, clock)
        is AgentStreamEvent.ToolCallFinished -> conversation.appendToolResult(
            name = event.name,
            toolCallId = event.toolCallId,
            status = ToolEventStatus.Finished,
            resultPreview = event.resultPreview,
            resultDisplay = event.resultDisplay,
            errorMessage = null,
            idFactory = idFactory,
            clock = clock,
        )

        is AgentStreamEvent.ToolCallFailed -> conversation.appendToolResult(
            name = event.name,
            toolCallId = event.toolCallId,
            status = ToolEventStatus.Failed,
            resultPreview = null,
            resultDisplay = null,
            errorMessage = event.reason,
            idFactory = idFactory,
            clock = clock,
        )

        is AgentStreamEvent.ToolFileDiffPreviewed -> conversation.attachEntryDiffs(event)
        is AgentStreamEvent.Completed -> conversation.completeAssistantText(event, idFactory, clock)
        is AgentStreamEvent.Failed -> conversation.closeStreamingReasoning(clock).appendToolResult(
            name = "error",
            toolCallId = null,
            status = ToolEventStatus.Failed,
            resultPreview = null,
            resultDisplay = null,
            errorMessage = event.reason,
            idFactory = idFactory,
            clock = clock,
        )

        AgentStreamEvent.Started,
        is AgentStreamEvent.ApprovalRequested,
        is AgentStreamEvent.QuestionRequested,
        is AgentStreamEvent.Status,
            -> conversation

        is AgentStreamEvent.ToolOutputDelta -> conversation.appendToolOutput(event)
    }
}

/** 将问答交互记录为自定义展示条目。 */
internal fun appendAnswersConversationEntry(
    conversation: ChatConversationUiState,
    answers: List<com.agent.shared.tool.model.QuestionAnswer>,
    entryId: String,
    createdAt: Long,
): ChatConversationUiState {
    if (conversation.treeFormatVersion <= 0) return conversation
    val entry = ConversationEntry.Answers(
        id = entryId,
        parentId = conversation.activeEntryId,
        createdAt = createdAt,
        answers = answers,
    )
    return conversation.withAppendedEntryProjection(conversation.entries + entry, entry.id)
}

/** 从稳定 leaf 重建兼容时间线与模型 history。 */
internal fun ChatConversationUiState.withEntryProjection(
    entries: List<ConversationEntry> = this.entries,
    activeEntryId: String? = this.activeEntryId,
    headEntryId: String? = this.headEntryId,
): ChatConversationUiState {
    if (treeFormatVersion <= 0) return this
    val projection = projectConversationEntries(entries, activeEntryId)
    return copy(
        entries = entries,
        activeEntryId = activeEntryId,
        headEntryId = headEntryId,
        items = projection.timeline,
        history = projection.history,
        profileId = projection.profileId ?: profileId,
        reasoningEffort = projection.reasoningEffort
            ?.let { value -> com.agent.shared.agent.api.ReasoningEffort.entries.firstOrNull { it.name == value } }
            ?: reasoningEffort,
    )
}

/**
 * 将一个真正追加到当前路径的新条目设为活动 leaf，并仅在原活动 leaf 位于持久末端时推进 head。
 * 用户从历史位置发送消息时应直接调用 [withEntryProjection] 并显式把 head 指向新消息。
 */
internal fun ChatConversationUiState.withAppendedEntryProjection(
    entries: List<ConversationEntry>,
    activeEntryId: String,
): ChatConversationUiState = withEntryProjection(
    entries = entries,
    activeEntryId = activeEntryId,
    headEntryId = if (headEntryId == null || headEntryId == this.activeEntryId) activeEntryId else headEntryId,
)

/** 追加或更新当前流式助手正文条目。 */
private fun ChatConversationUiState.appendAssistantText(
    text: String,
    idFactory: () -> String,
    clock: () -> Long,
): ChatConversationUiState {
    val normalizedConversation = closeStreamingReasoning(clock)
    val currentId = normalizedConversation.streamingAssistantEntryId
    val current = currentId?.let { id ->
        normalizedConversation.entries.firstOrNull { it.id == id } as? ConversationEntry.Message
    }
    if (current != null) {
        val nextEntries = normalizedConversation.entries.map { entry ->
            if (entry.id == current.id) {
                current.copy(message = current.message.copy(content = current.message.content + text))
            } else {
                entry
            }
        }
        return normalizedConversation.withAppendedEntryProjection(nextEntries, current.id)
    }
    val entry = ConversationEntry.Message(
        id = idFactory(),
        parentId = normalizedConversation.activeEntryId,
        createdAt = clock(),
        message = ChatMessage(ChatRole.Assistant, text),
        inputParts = emptyList(),
    )
    return normalizedConversation.withAppendedEntryProjection(
        normalizedConversation.entries + entry,
        entry.id,
    ).copy(streamingAssistantEntryId = entry.id)
}

/** 追加或更新当前流式推理条目。 */
private fun ChatConversationUiState.appendReasoningDelta(
    event: AgentStreamEvent.ReasoningDelta,
    idFactory: () -> String,
    clock: () -> Long,
): ChatConversationUiState {
    val normalizedConversation = copy(streamingAssistantEntryId = null)
    val currentId = normalizedConversation.streamingReasoningEntryId
    val current = currentId?.let { id ->
        normalizedConversation.entries.firstOrNull { it.id == id } as? ConversationEntry.Reasoning
    }
    if (current != null) {
        val nextEntries = normalizedConversation.entries.map { entry ->
            if (entry.id == current.id) {
                current.copy(
                    summaryText = current.summaryText.orEmpty() + event.summary.orEmpty(),
                    rawText = current.rawText.orEmpty() + (event.rawText ?: event.summary).orEmpty(),
                )
            } else {
                entry
            }
        }
        return normalizedConversation.withAppendedEntryProjection(nextEntries, current.id)
    }
    val createdAt = clock()
    val entry = ConversationEntry.Reasoning(
        id = idFactory(),
        parentId = normalizedConversation.activeEntryId,
        createdAt = createdAt,
        summaryText = event.summary,
        rawText = event.rawText ?: event.summary,
        startedAtMillis = createdAt,
    )
    return normalizedConversation.withAppendedEntryProjection(
        normalizedConversation.entries + entry,
        entry.id,
    ).copy(streamingReasoningEntryId = entry.id)
}

/** 关闭仍在流式中的推理条目，保证后续正文或失败结果不会复用旧段。 */
private fun ChatConversationUiState.closeStreamingReasoning(clock: () -> Long): ChatConversationUiState {
    val currentId = streamingReasoningEntryId ?: return this
    val nextEntries = entries.map { entry ->
        val reasoning = entry as? ConversationEntry.Reasoning
        if (reasoning?.id == currentId) {
            reasoning.copy(
                isStreaming = false,
                durationMillis = (clock() - reasoning.startedAtMillis).coerceAtLeast(0L),
            )
        } else {
            entry
        }
    }
    return withEntryProjection(nextEntries, activeEntryId).copy(streamingReasoningEntryId = null)
}

/** 用完整推理内容收尾当前推理条目。 */
private fun ChatConversationUiState.completeReasoning(
    event: AgentStreamEvent.ReasoningCompleted,
    idFactory: () -> String,
    clock: () -> Long,
): ChatConversationUiState {
    val currentId = streamingReasoningEntryId
        ?: currentTurnReasoningId()
    if (currentId == null) {
        val summary = event.summary?.takeIf(String::isNotBlank)
        val rawText = event.rawText?.takeIf(String::isNotBlank) ?: summary
        if (summary == null && rawText == null) return this
        val normalizedConversation = copy(streamingAssistantEntryId = null)
        val createdAt = clock()
        val entry = ConversationEntry.Reasoning(
            id = idFactory(),
            parentId = normalizedConversation.activeEntryId,
            createdAt = createdAt,
            summaryText = summary,
            rawText = rawText,
            isStreaming = false,
            startedAtMillis = createdAt,
            durationMillis = 0L,
        )
        return normalizedConversation.withAppendedEntryProjection(
            normalizedConversation.entries + entry,
            entry.id,
        ).copy(streamingReasoningEntryId = null)
    }
    val completionTime = clock()
    val nextEntries = entries.map { entry ->
        val reasoning = entry as? ConversationEntry.Reasoning
        if (reasoning?.id == currentId) {
            reasoning.copy(
                summaryText = event.summary?.takeIf(String::isNotBlank) ?: reasoning.summaryText,
                rawText = event.rawText?.takeIf(String::isNotBlank)
                    ?: event.summary?.takeIf(String::isNotBlank)
                    ?: reasoning.rawText,
                isStreaming = false,
                durationMillis = (completionTime - reasoning.startedAtMillis).coerceAtLeast(0L),
            )
        } else {
            entry
        }
    }
    return withEntryProjection(nextEntries).copy(streamingReasoningEntryId = null)
}

/** 只在当前活动轮次中寻找晚到的推理完成事件，避免改写上一轮的历史条目。 */
private fun ChatConversationUiState.currentTurnReasoningId(): String? {
    val activePath = conversationEntryPath(entries, activeEntryId)
    val latestUserIndex = activePath.indexOfLast { entry ->
        entry is ConversationEntry.Message && entry.message.role == ChatRole.User
    }
    if (latestUserIndex < 0) return null
    return activePath.asSequence()
        .drop(latestUserIndex + 1)
        .filterIsInstance<ConversationEntry.Reasoning>()
        .lastOrNull()
        ?.id
}

/** 追加工具调用条目，并结束此前的正文流式归属。 */
private fun ChatConversationUiState.appendToolCall(
    event: AgentStreamEvent.ToolCallStarted,
    idFactory: () -> String,
    clock: () -> Long,
): ChatConversationUiState {
    val finalizedEntries = entries.map { entry ->
        val reasoning = entry as? ConversationEntry.Reasoning
        if (reasoning != null && reasoning.id == streamingReasoningEntryId) {
            reasoning.copy(
                isStreaming = false,
                durationMillis = (clock() - reasoning.startedAtMillis).coerceAtLeast(0L),
            )
        } else {
            entry
        }
    }
    val entry = ConversationEntry.ToolCall(
        id = idFactory(),
        parentId = activeEntryId,
        createdAt = clock(),
        toolName = event.name,
        preview = event.argumentsPreview,
        operationIntent = event.operationIntent,
        toolCallId = event.toolCallId,
    )
    return withAppendedEntryProjection(finalizedEntries + entry, entry.id).copy(
        streamingAssistantEntryId = null,
        streamingReasoningEntryId = null,
    )
}

/** 追加工具结果条目。 */
private fun ChatConversationUiState.appendToolResult(
    name: String,
    toolCallId: String?,
    status: ToolEventStatus,
    resultPreview: String?,
    resultDisplay: String?,
    errorMessage: String?,
    idFactory: () -> String,
    clock: () -> Long,
): ChatConversationUiState {
    val normalizedConversation = closeStreamingReasoning(clock).copy(streamingAssistantEntryId = null)
    val entry = ConversationEntry.ToolResult(
        id = idFactory(),
        parentId = normalizedConversation.activeEntryId,
        createdAt = clock(),
        toolName = name,
        status = status,
        errorMessage = errorMessage,
        toolCallId = toolCallId,
        resultPreview = resultPreview,
        resultDisplay = resultDisplay,
    )
    return normalizedConversation.withAppendedEntryProjection(
        normalizedConversation.entries + entry,
        entry.id,
    )
}

/** 把文件差异附加到最近的同名运行中工具调用。 */
private fun ChatConversationUiState.attachEntryDiffs(
    event: AgentStreamEvent.ToolFileDiffPreviewed,
): ChatConversationUiState {
    val target = entries.asReversed().filterIsInstance<ConversationEntry.ToolCall>()
        .firstOrNull { it.toolName == event.name }
        ?: return this
    val nextEntries = entries.map { entry ->
        if (entry.id == target.id) target.copy(fileDiffs = event.diffs) else entry
    }
    return withEntryProjection(nextEntries, activeEntryId)
}

/** 将实时工具输出追加到最近的匹配调用。 */
private fun ChatConversationUiState.appendToolOutput(
    event: AgentStreamEvent.ToolOutputDelta,
): ChatConversationUiState {
    val target = entries.asReversed().filterIsInstance<ConversationEntry.ToolCall>()
        .firstOrNull { call ->
            event.toolCallId?.let { it == call.toolCallId } ?: (call.toolName == event.name)
        }
        ?: return this
    val nextEntries = entries.map { entry ->
        if (entry.id == target.id) target.copy(resultDisplay = target.resultDisplay.orEmpty() + event.text) else entry
    }
    return withEntryProjection(nextEntries, activeEntryId)
}

/** 使用完成事件补齐没有收到增量的助手正文，并清除流式归属。 */
private fun ChatConversationUiState.completeAssistantText(
    event: AgentStreamEvent.Completed,
    idFactory: () -> String,
    clock: () -> Long,
): ChatConversationUiState {
    val withText = if (streamingAssistantEntryId == null && event.text.isNotBlank()) {
        appendAssistantText(event.text, idFactory, clock)
    } else {
        this
    }
    return withText.copy(streamingAssistantEntryId = null, streamingReasoningEntryId = null)
}
