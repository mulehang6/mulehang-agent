package com.agent.shared.agent

import kotlinx.coroutines.flow.Flow

/**
 * 对底层 agent 执行入口的最小抽象。
 */
interface AgentGateway {
    /**
     * 执行一次消息请求。
     */
    fun run(request: AgentRunRequest): Flow<AgentStreamEvent>
}
