package com.agent.shared.agent

/**
 * UI 可消费的 agent 流式事件。
 */
sealed interface AgentStreamEvent {
    /**
     * 执行开始。
     */
    data object Started : AgentStreamEvent

    /**
     * 助手正文的文本增量。
     */
    data class TextDelta(val text: String) : AgentStreamEvent

    /**
     * 工具调用开始。
     */
    data class ToolCallStarted(
        val name: String,
        val argumentsPreview: String? = null,
    ) : AgentStreamEvent

    /**
     * 工具调用结束。
     */
    data class ToolCallFinished(
        val name: String,
        val resultPreview: String? = null,
    ) : AgentStreamEvent

    /**
     * 工具请求用户回答问题。
     */
    data class QuestionRequested(val request: QuestionRequest) : AgentStreamEvent

    /**
     * 工具请求用户审批危险操作。
     */
    data class ApprovalRequested(val request: ApprovalRequest) : AgentStreamEvent

    /**
     * 非正文的中间状态文本。
     */
    data class Status(val message: String) : AgentStreamEvent

    /**
     * 思考内容的流式增量。
     */
    data class ReasoningDelta(
        val summary: String?,
        val rawText: String?,
    ) : AgentStreamEvent

    /**
     * 思考内容的完整收尾事件。
     */
    data class ReasoningCompleted(
        val summary: String?,
        val rawText: String?,
    ) : AgentStreamEvent

    /**
     * 执行完成。
     */
    data class Completed(val text: String) : AgentStreamEvent

    /**
     * 执行失败。
     */
    data class Failed(val reason: String) : AgentStreamEvent
}
