package com.agent.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import com.agent.capability.CapabilityDescriptor
import com.agent.capability.CapabilitySet
import com.agent.provider.ProviderBinding
import com.agent.provider.ProviderType

/**
 * 表示当前阶段装配出的真实 Koog agent 及其仓库侧元数据。
 */
data class AssembledAgent(
    val binding: ProviderBinding,
    val strategy: AIAgentGraphStrategy<String, String>,
    val agent: AIAgent<String, String>,
    val capabilities: List<CapabilityDescriptor>,
)

/**
 * 负责把 binding 与 capability set 组装成真实 Koog agent。
 */
class AgentAssembly {

    /**
     * 基于运行时 binding 和能力集合返回真实 Koog agent 装配结果。
     */
    fun assemble(
        binding: ProviderBinding,
        capabilitySet: CapabilitySet,
    ): AssembledAgent {
        require(binding.providerType == ProviderType.OPENAI_COMPATIBLE) {
            "Real Koog assembly currently supports only ${ProviderType.OPENAI_COMPATIBLE} bindings."
        }

        val strategy = AgentStrategyFactory.singleRun()
        val agent = AIAgent(
            promptExecutor = simpleOpenAIExecutor(binding.apiKey),
            strategy = strategy,
            llmModel = resolveOpenAiModel(binding),
            systemPrompt = "You are a helpful assistant.",
            toolRegistry = ToolRegistry { },
        )

        return AssembledAgent(
            binding = binding,
            strategy = strategy,
            agent = agent,
            capabilities = capabilitySet.descriptors(),
        )
    }

    /**
     * 把当前 binding 的模型标识映射到已验证的 OpenAI 模型常量。
     */
    private fun resolveOpenAiModel(binding: ProviderBinding) = when (binding.modelId) {
        "gpt-4o-mini" -> OpenAIModels.Chat.GPT4oMini
        "gpt-4o" -> OpenAIModels.Chat.GPT4o
        else -> throw IllegalArgumentException("Unsupported OpenAI-compatible model id '${binding.modelId}'.")
    }
}
