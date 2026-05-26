package com.agent.shared.agent

import com.agent.shared.config.ResolvedAgentProfile
import kotlinx.coroutines.flow.Flow

/**
 * Koog 1.0.0 接入点骨架。
 */
class KoogAgentGateway : AgentGateway {
    /**
     * 运行一次消息请求。
     */
    override fun run(prompt: String, profile: ResolvedAgentProfile): Flow<AgentStreamEvent> {
        TODO("Implement Koog 1.0.0 message execution.")
    }
}
