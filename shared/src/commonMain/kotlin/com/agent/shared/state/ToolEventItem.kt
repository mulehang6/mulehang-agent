package com.agent.shared.state

/**
 * 时间线中的工具调用或状态事件项。
 *
 * [errorMessage] 仅在 [status] 为 [ToolEventStatus.Failed] 时携带失败原因，
 * 用于在工具事件卡片内就地展示错误，而非在面板顶部单独展示。
 */
data class ToolEventItem(
    val toolName: String,
    val status: ToolEventStatus,
    val preview: String? = null,
    val errorMessage: String? = null,
) : ConversationItem {
    override val kind: ConversationItem.Kind = ConversationItem.Kind.ToolEvent
}
