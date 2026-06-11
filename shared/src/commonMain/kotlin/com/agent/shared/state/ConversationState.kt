package com.agent.shared.state

/**
 * 窗口级会话状态。
 */
data class ConversationState(
    val items: List<ConversationItem> = emptyList(),
    val executionState: ExecutionState = ExecutionState.Idle,
    val activeProfileId: String? = null,
    val streamingAssistantItemIndex: Int? = null,
)
