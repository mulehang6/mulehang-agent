package com.agent.shared.agent

import com.agent.shared.config.ResolvedAgentProfile
import kotlinx.coroutines.flow.Flow

/**
 * 对底层 agent 执行入口的最小抽象。
 */
interface AgentGateway {
    /**
     * 执行一次消息请求。
     */
    fun run(prompt: String, profile: ResolvedAgentProfile): Flow<AgentStreamEvent>
}
