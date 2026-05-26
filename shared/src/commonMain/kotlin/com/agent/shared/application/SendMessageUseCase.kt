package com.agent.shared.application

import com.agent.shared.agent.AgentGateway
import com.agent.shared.config.ResolvedAgentProfile

/**
 * 发送消息用例骨架。
 */
class SendMessageUseCase(
    private val agentGateway: AgentGateway,
) {
    /**
     * 执行一次消息发送。
     */
    operator fun invoke(prompt: String, profile: ResolvedAgentProfile) =
        agentGateway.run(prompt, profile)
}
