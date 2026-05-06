package com.agent.runtime.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.agent.runtime.capability.CapabilityDescriptor
import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.provider.ProviderBinding

/**
 * 表示当前阶段装配出的真实 Koog agent 及其仓库侧元数据。
 */
data class AssembledAgent(
    val binding: ProviderBinding,
    val promptExecutor: PromptExecutor,
    val llmModel: LLModel,
    val strategy: AIAgentGraphStrategy<String, String>,
    val agent: AIAgent<String, String>,
    val toolRegistry: ToolRegistry,
    val capabilities: List<CapabilityDescriptor>,
)

/**
 * 负责把 binding 与 capability set 组装成真实 Koog agent。
 */
class AgentAssembly(
    private val executorResolver: KoogExecutorResolver = KoogExecutorResolver(),
    private val toolRegistryAssembler: KoogToolRegistryAssembler = KoogToolRegistryAssembler(),
) {

    /**
     * 基于运行时 binding 和能力集合返回真实 Koog agent 装配结果。
     */
    suspend fun assemble(
        binding: ProviderBinding,
        capabilitySet: CapabilitySet,
    ): AssembledAgent {
        val resolvedBinding = executorResolver.resolve(binding)
        val tooling = toolRegistryAssembler.assemble(capabilitySet)
        val strategy = AgentStrategyFactory.singleRun()
        val agent = AIAgent(
            promptExecutor = resolvedBinding.promptExecutor,
            strategy = strategy,
            llmModel = resolvedBinding.llmModel,
            systemPrompt = "You are a helpful assistant.",
            toolRegistry = tooling.toolRegistry,
        )

        return AssembledAgent(
            binding = binding,
            promptExecutor = resolvedBinding.promptExecutor,
            llmModel = resolvedBinding.llmModel,
            strategy = strategy,
            agent = agent,
            toolRegistry = tooling.toolRegistry,
            capabilities = tooling.descriptors,
        )
    }
}
