package com.agent.shared.state

/**
 * 会话时间线中的统一项模型。
 */
sealed interface ConversationItem {
    /**
     * 当前项的展示类别。
     */
    val kind: Kind

    /**
     * 时间线项类型。
     */
    enum class Kind {
        /**
         * 标准聊天消息。
         */
        ChatMessage,

        /**
         * 工具调用或中间状态事件。
         */
        ToolEvent,
    }
}
