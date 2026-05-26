package com.agent.shared.state

/**
 * 窗口级会话状态。
 */
data class ConversationState(
    val messages: List<ChatMessage> = emptyList(),
    val executionState: ExecutionState = ExecutionState.Idle,
    val activeProfileId: String? = null,
)
