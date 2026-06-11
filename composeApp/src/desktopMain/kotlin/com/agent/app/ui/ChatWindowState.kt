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

            is AgentStreamEvent.Completed -> completeAssistantMessage(event.text)
            is AgentStreamEvent.Failed -> state.copy(
                executionState = ExecutionState.Failed(
                    AppError(
                        title = "Agent 执行失败",
                        message = event.reason,
                    ),
                ),
                streamingAssistantItemIndex = null,
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
        val currentIndex = state.streamingAssistantItemIndex
        return if (currentIndex == null) {
            state.copy(
                items = state.items + ChatMessageItem(ChatMessage(ChatRole.Assistant, delta)),
                streamingAssistantItemIndex = state.items.size,
            )
        } else {
            val existingItem = state.items[currentIndex] as? ChatMessageItem ?: return state
            val updatedItems = state.items.toMutableList()
            updatedItems[currentIndex] = existingItem.copy(
                message = existingItem.message.copy(content = existingItem.message.content + delta),
            )
            state.copy(items = updatedItems)
        }
    }

    /**
     * 将工具或状态事件追加到时间线。
     */
    private fun appendToolEvent(
        toolName: String,
        status: ToolEventStatus,
        preview: String?,
    ): ConversationState = state.copy(
        items = state.items + ToolEventItem(
            toolName = toolName,
            status = status,
            preview = preview,
        ),
    )

    /**
     * 在完成时补齐最终正文，并清理流式状态。
     */
    private fun completeAssistantMessage(finalText: String): ConversationState {
        val currentIndex = state.streamingAssistantItemIndex ?: return state.copy(
            items = appendCompletedAssistantIfNeeded(state.items, finalText),
            executionState = ExecutionState.Idle,
            streamingAssistantItemIndex = null,
        )
        val existingItem = state.items[currentIndex] as? ChatMessageItem ?: return state.copy(
            executionState = ExecutionState.Idle,
            streamingAssistantItemIndex = null,
        )
        val updatedItems = state.items.toMutableList()
        if (finalText.isNotBlank() && existingItem.message.content != finalText) {
            updatedItems[currentIndex] = existingItem.copy(
                message = existingItem.message.copy(content = finalText),
            )
        }
        return state.copy(
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
}
