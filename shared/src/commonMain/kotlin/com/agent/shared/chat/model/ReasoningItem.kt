package com.agent.shared.chat.model

/**
 * 时间线中的思考块项。
 */
data class ReasoningItem(
    val summaryText: String? = null,
    val rawText: String? = null,
    val expanded: Boolean = true,
    val isStreaming: Boolean = true,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val durationMillis: Long? = null,
) : ConversationItem {
    override val kind: ConversationItem.Kind = ConversationItem.Kind.Reasoning

    /**
     * 当前应优先向用户展示的思考内容。
     */
    val displayText: String
        get() = summaryText?.takeIf { it.isNotBlank() } ?: rawText.orEmpty()
}
