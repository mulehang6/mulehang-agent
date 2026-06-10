package com.agent.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.state.AppError
import com.agent.shared.state.ChatMessage
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ConversationState
import com.agent.shared.state.ExecutionState
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
            messages = state.messages + ChatMessage(ChatRole.User, prompt),
            executionState = ExecutionState.Running,
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
            is AgentStreamEvent.Delta -> state.copy(
                messages = state.messages + ChatMessage(ChatRole.Assistant, event.text),
            )

            is AgentStreamEvent.Completed -> state.copy(executionState = ExecutionState.Idle)
            is AgentStreamEvent.Failed -> state.copy(
                executionState = ExecutionState.Failed(
                    AppError(
                        title = "Agent 执行失败",
                        message = event.reason,
                    ),
                ),
            )
        }
    }
}
