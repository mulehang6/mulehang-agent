package com.agent.shared.state

/**
 * 时间线中的工具调用或状态事件项。
 */
data class ToolEventItem(
    val toolName: String,
    val status: ToolEventStatus,
    val preview: String? = null,
) : ConversationItem {
    override val kind: ConversationItem.Kind = ConversationItem.Kind.ToolEvent
}
