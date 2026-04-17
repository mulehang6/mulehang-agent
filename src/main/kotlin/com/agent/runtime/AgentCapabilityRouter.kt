package com.agent.runtime

import com.agent.agent.RuntimeAgentExecutor
import com.agent.capability.CapabilitySet
import com.agent.provider.ProviderBinding

/**
 * 负责把 runtime 内的 agent.run 请求转发给 RuntimeAgentExecutor。
 */
class AgentCapabilityRouter(
    private val runtimeAgentExecutor: RuntimeAgentExecutor,
    private val binding: ProviderBinding,
    private val capabilitySet: CapabilitySet,
) : RuntimeCapabilityRouter {

    /**
     * 校验请求类型后执行统一 agent 运行链。
     */
    override suspend fun route(
        context: RuntimeRequestContext,
        request: CapabilityRequest,
    ): RuntimeResult {
        require(request is RuntimeAgentRunRequest) {
            "AgentCapabilityRouter requires RuntimeAgentRunRequest."
        }

        return runtimeAgentExecutor.execute(
            session = RuntimeSession(id = context.sessionId),
            context = context,
            request = request,
            binding = binding,
            capabilitySet = capabilitySet,
        )
    }
}
