package com.agent.shared.agent

/**
 * UI 可消费的 agent 事件骨架。
 */
sealed interface AgentStreamEvent {
    /**
     * 执行开始。
     */
    data object Started : AgentStreamEvent

    /**
     * 增量输出。
     */
    data class Delta(val text: String) : AgentStreamEvent

    /**
     * 执行完成。
     */
    data class Completed(val text: String) : AgentStreamEvent

    /**
     * 执行失败。
     */
    data class Failed(val reason: String) : AgentStreamEvent
}
