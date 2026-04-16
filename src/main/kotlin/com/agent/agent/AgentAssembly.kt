package com.agent.agent

import com.agent.capability.CapabilityDescriptor
import com.agent.capability.CapabilitySet
import com.agent.provider.ProviderBinding

/**
 * 表示仓库侧最小 agent 装配结果。
 */
data class AssembledAgent(
    val binding: ProviderBinding,
    val strategy: String,
    val capabilities: List<CapabilityDescriptor>,
)

/**
 * 负责把 binding 与 capability set 组装成最小 agent 描述。
 */
class AgentAssembly {

    /**
     * 基于运行时 binding 和能力集合返回最小装配结果。
     */
    fun assemble(
        binding: ProviderBinding,
        capabilitySet: CapabilitySet,
    ): AssembledAgent {
        return AssembledAgent(
            binding = binding,
            strategy = AgentStrategyFactory.singleRun(),
            capabilities = capabilitySet.descriptors(),
        )
    }
}
