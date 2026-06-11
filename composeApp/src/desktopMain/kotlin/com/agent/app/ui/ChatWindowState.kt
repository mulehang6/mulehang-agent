package com.agent.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.state.AppError
import com.agent.shared.state.ChatMessage
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ConversationState
import com.agent.shared.state.ConversationItem
import com.agent.shared.state.ExecutionState
import com.agent.shared.state.ReasoningItem
import com.agent.shared.state.ToolEventItem
import com.agent.shared.state.ToolEventStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 窗口级状态持有者骨架。
 */
class ChatWindowState(
    private val sendMessageUseCase: SendMessageUseCase,
    private val snapshot: AppSessionSnapshot,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 当前窗口状态。
     */
    var state by mutableStateOf(
        ConversationState(activeProfileId = snapshot.activeProfile?.id),
    )
        private set

    /**
     * 当前失败状态对应的 UI 可见错误文本。
     */
    val errorMessage: String?
        get() = (state.executionState as? ExecutionState.Failed)?.error?.let { error ->
            "${error.title}: ${error.message}"
        }

    /**
     * 发送消息并根据 agent 事件更新窗口状态。
     */
    fun send(message: String) {
        val prompt = message.trim()
        if (prompt.isBlank()) return

        val activeProfile = snapshot.activeProfile
        if (activeProfile == null) {
            state = state.copy(
                executionState = ExecutionState.Failed(
                    AppError(
                        title = "缺少可用配置",
                        message = "请先在 settings.json 中配置并启用至少一个 profile。",
                    ),
                ),
            )
            return
        }

        state = state.copy(
            items = state.items + ChatMessageItem(ChatMessage(ChatRole.User, prompt)),
            executionState = ExecutionState.Running,
            streamingAssistantItemIndex = null,
            streamingReasoningItemIndex = null,
        )

        scope.launch {
            try {
                sendMessageUseCase(prompt, activeProfile).collect { event ->
                    applyAgentEvent(event)
                }
            } catch (e: Exception) {
                state = state.copy(
                    executionState = ExecutionState.Failed(
                        AppError(
                            title = "发送失败",
                            message = e.message ?: "执行过程中发生未知错误。",
                        ),
                    ),
                )
            }
        }
    }

    /**
     * 将 agent 事件折叠为 UI 会话状态。
     */
    private fun applyAgentEvent(event: AgentStreamEvent) {
        state = when (event) {
            AgentStreamEvent.Started -> state.copy(executionState = ExecutionState.Running)
            is AgentStreamEvent.TextDelta -> appendAssistantDelta(event.text)
            is AgentStreamEvent.ToolCallStarted -> appendToolEvent(
                toolName = event.name,
                status = ToolEventStatus.Started,
                preview = event.argumentsPreview,
            )

            is AgentStreamEvent.ToolCallFinished -> appendToolEvent(
                toolName = event.name,
                status = ToolEventStatus.Finished,
                preview = event.resultPreview,
            )

            is AgentStreamEvent.Status -> appendToolEvent(
                toolName = "status",
                status = ToolEventStatus.Status,
                preview = event.message,
            )

            is AgentStreamEvent.ReasoningDelta -> appendReasoningDelta(
                summary = event.summary,
                rawText = event.rawText,
            )

            is AgentStreamEvent.ReasoningCompleted -> completeReasoning(
                summary = event.summary,
                rawText = event.rawText,
            )

            is AgentStreamEvent.Completed -> completeAssistantMessage(event.text)
            is AgentStreamEvent.Failed -> state.copy(
                executionState = ExecutionState.Failed(
                    AppError(
                        title = "Agent 执行失败",
                        message = event.reason,
                    ),
                ),
                streamingAssistantItemIndex = null,
                streamingReasoningItemIndex = null,
                items = closeStreamingReasoning(state).items,
            )
        }
    }

    /**
     * 将文本增量拼接到当前正在生成的助手消息。
     */
    private fun appendAssistantDelta(delta: String): ConversationState {
        if (delta.isEmpty()) {
            return state
        }
        val normalizedState = closeStreamingReasoning(state)
        val currentIndex = normalizedState.streamingAssistantItemIndex
        return if (currentIndex == null) {
            normalizedState.copy(
                items = normalizedState.items + ChatMessageItem(ChatMessage(ChatRole.Assistant, delta)),
                streamingAssistantItemIndex = normalizedState.items.size,
            )
        } else {
            val existingItem = normalizedState.items[currentIndex] as? ChatMessageItem ?: return normalizedState
            val updatedItems = normalizedState.items.toMutableList()
            updatedItems[currentIndex] = existingItem.copy(
                message = existingItem.message.copy(content = existingItem.message.content + delta),
            )
            normalizedState.copy(items = updatedItems)
        }
    }

    /**
     * 将工具或状态事件追加到时间线。
     */
    private fun appendToolEvent(
        toolName: String,
        status: ToolEventStatus,
        preview: String?,
    ): ConversationState {
        val normalizedState = closeStreamingReasoning(state)
        return normalizedState.copy(
            items = normalizedState.items + ToolEventItem(
            toolName = toolName,
            status = status,
            preview = preview,
        ),
        )
    }

    /**
     * 将思考增量拼接到当前思考块。
     */
    private fun appendReasoningDelta(
        summary: String?,
        rawText: String?,
    ): ConversationState {
        if (summary.isNullOrEmpty() && rawText.isNullOrEmpty()) {
            return state
        }
        val currentIndex = state.streamingReasoningItemIndex
        return if (currentIndex == null) {
            state.copy(
                items = state.items + ReasoningItem(
                    summaryText = summary,
                    rawText = rawText ?: summary,
                    expanded = true,
                    isStreaming = true,
                ),
                streamingReasoningItemIndex = state.items.size,
            )
        } else {
            val existingItem = state.items[currentIndex] as? ReasoningItem ?: return state
            val updatedItems = state.items.toMutableList()
            updatedItems[currentIndex] = existingItem.copy(
                summaryText = existingItem.summaryText.orEmpty().appendNullable(summary),
                rawText = existingItem.rawText.orEmpty().appendNullable(rawText ?: summary),
                expanded = true,
                isStreaming = true,
            )
            state.copy(items = updatedItems)
        }
    }

    /**
     * 收到 reasoning 完整事件后收尾当前思考块。
     */
    private fun completeReasoning(
        summary: String?,
        rawText: String?,
    ): ConversationState {
        val currentIndex = state.streamingReasoningItemIndex ?: return state.copy(
            items = state.items + ReasoningItem(
                summaryText = summary,
                rawText = rawText ?: summary,
                expanded = true,
                isStreaming = false,
            ),
        )
        val existingItem = state.items[currentIndex] as? ReasoningItem ?: return state
        val updatedItems = state.items.toMutableList()
        updatedItems[currentIndex] = existingItem.copy(
            summaryText = summary ?: existingItem.summaryText,
            rawText = rawText ?: existingItem.rawText,
            expanded = true,
            isStreaming = false,
        )
        return state.copy(
            items = updatedItems,
            streamingReasoningItemIndex = null,
        )
    }

    /**
     * 在完成时补齐最终正文，并清理流式状态。
     */
    private fun completeAssistantMessage(finalText: String): ConversationState {
        val normalizedState = closeStreamingReasoning(state)
        val currentIndex = normalizedState.streamingAssistantItemIndex ?: return normalizedState.copy(
            items = appendCompletedAssistantIfNeeded(normalizedState.items, finalText),
            executionState = ExecutionState.Idle,
            streamingAssistantItemIndex = null,
        )
        val existingItem = normalizedState.items[currentIndex] as? ChatMessageItem ?: return normalizedState.copy(
            executionState = ExecutionState.Idle,
            streamingAssistantItemIndex = null,
        )
        val updatedItems = normalizedState.items.toMutableList()
        if (finalText.isNotBlank() && existingItem.message.content != finalText) {
            updatedItems[currentIndex] = existingItem.copy(
                message = existingItem.message.copy(content = finalText),
            )
        }
        return normalizedState.copy(
            items = updatedItems,
            executionState = ExecutionState.Idle,
            streamingAssistantItemIndex = null,
        )
    }

    /**
     * 当底层只返回完成文本时补一条助手消息。
     */
    private fun appendCompletedAssistantIfNeeded(
        items: List<ConversationItem>,
        finalText: String,
    ): List<ConversationItem> {
        if (finalText.isBlank()) {
            return items
        }
        return items + ChatMessageItem(ChatMessage(ChatRole.Assistant, finalText))
    }

    /**
     * 在进入工具或正文阶段前关闭仍处于流式中的思考块。
     */
    private fun closeStreamingReasoning(source: ConversationState): ConversationState {
        val reasoningIndex = source.streamingReasoningItemIndex ?: return source
        val reasoningItem = source.items[reasoningIndex] as? ReasoningItem ?: return source.copy(
            streamingReasoningItemIndex = null,
        )
        if (!reasoningItem.isStreaming) {
            return source.copy(streamingReasoningItemIndex = null)
        }
        val updatedItems = source.items.toMutableList()
        updatedItems[reasoningIndex] = reasoningItem.copy(isStreaming = false, expanded = true)
        return source.copy(
            items = updatedItems,
            streamingReasoningItemIndex = null,
        )
    }
}

/**
 * 仅在有值时追加文本片段。
 */
private fun String.appendNullable(next: String?): String = if (next.isNullOrEmpty()) this else this + next
