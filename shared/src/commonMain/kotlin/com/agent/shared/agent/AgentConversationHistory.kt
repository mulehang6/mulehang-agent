package com.agent.shared.agent

/**
 * 描述一次会话里已经沉淀的结构化历史消息。
 */
sealed interface AgentConversationHistoryMessage {
    /**
     * 用户消息历史。
     */
    data class User(
        val content: String,
    ) : AgentConversationHistoryMessage

    /**
     * 助手消息历史，由多个结构化 part 组成。
     */
    data class Assistant(
        val parts: List<AgentConversationHistoryPart> = emptyList(),
    ) : AgentConversationHistoryMessage
}

/**
 * 助手历史中的结构化片段。
 */
sealed interface AgentConversationHistoryPart {
    /**
     * 助手正文文本。
     */
    data class Text(
        val text: String,
    ) : AgentConversationHistoryPart

    /**
     * 助手思考片段。
     */
    data class Reasoning(
        val summary: String?,
        val rawText: String?,
    ) : AgentConversationHistoryPart

    /**
     * 工具调用片段。
     */
    data class ToolCall(
        val name: String,
        val argumentsPreview: String? = null,
    ) : AgentConversationHistoryPart

    /**
     * 工具结果片段。
     */
    data class ToolResult(
        val name: String,
        val resultPreview: String? = null,
    ) : AgentConversationHistoryPart
}
