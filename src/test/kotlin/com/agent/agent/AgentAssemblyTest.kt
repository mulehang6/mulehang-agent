package com.agent.agent

import com.agent.capability.CapabilitySet
import com.agent.provider.ProviderBinding
import com.agent.provider.ProviderType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证 runtime 可基于 binding 与 capability set 组装真实 Koog agent。
 */
class AgentAssemblyTest {

    @Test
    fun `should assemble real koog ai agent for openai compatible binding`() {
        val binding = ProviderBinding(
            providerId = "provider-1",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.example.com/v1",
            apiKey = "test-key",
            modelId = "gpt-4o-mini",
        )
        val assembly = AgentAssembly()

        val assembledAgent = assembly.assemble(
            binding = binding,
            capabilitySet = CapabilitySet(adapters = emptyList()),
        )

        assertEquals(binding, assembledAgent.binding)
        assertEquals("ai.koog.agents.core.agent.GraphAIAgent", assembledAgent.agent::class.qualifiedName)
        assertEquals(
            "ai.koog.agents.core.agent.entity.AIAgentGraphStrategy",
            assembledAgent.strategy::class.qualifiedName,
        )
        assertEquals(emptyList(), assembledAgent.capabilities)
    }

    @Test
    fun `should expose real koog single run strategy`() {
        val strategy = AgentStrategyFactory.singleRun()

        assertEquals(
            "ai.koog.agents.core.agent.entity.AIAgentGraphStrategy",
            strategy::class.qualifiedName,
        )
    }

    @Test
    fun `should reject unsupported provider type until koog assembly is verified`() {
        val binding = ProviderBinding(
            providerId = "provider-2",
            providerType = ProviderType.ANTHROPIC_COMPATIBLE,
            baseUrl = "https://api.example.com",
            apiKey = "test-key",
            modelId = "claude-sonnet",
        )
        val assembly = AgentAssembly()

        val error = assertFailsWith<IllegalArgumentException> {
            assembly.assemble(
                binding = binding,
                capabilitySet = CapabilitySet(adapters = emptyList()),
            )
        }

        assertContains(error.message.orEmpty(), "OPENAI_COMPATIBLE")
    }
}
