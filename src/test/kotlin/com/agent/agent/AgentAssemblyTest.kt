package com.agent.agent

import com.agent.capability.CapabilityAdapter
import com.agent.capability.CapabilityDescriptor
import com.agent.capability.CapabilitySet
import com.agent.provider.ProviderBinding
import com.agent.provider.ProviderType
import com.agent.runtime.CapabilityRequest
import com.agent.runtime.RuntimeInfoEvent
import com.agent.runtime.RuntimeResult
import com.agent.runtime.RuntimeSuccess
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 runtime 可基于 binding 与 capability set 组装最小 agent。
 */
class AgentAssemblyTest {

    @Test
    fun `should assemble minimal agent from binding and capability set`() {
        val binding = ProviderBinding(
            providerId = "provider-1",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.example.com",
            apiKey = "test-key",
            modelId = "gpt-4o-mini",
        )
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                FakeCapabilityAdapter(
                    descriptor = CapabilityDescriptor(id = "tool.echo", kind = "tool"),
                    result = RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "tool"))),
                ),
            ),
        )
        val assembly = AgentAssembly()

        val assembledAgent = assembly.assemble(
            binding = binding,
            capabilitySet = capabilitySet,
        )

        assertEquals(binding, assembledAgent.binding)
        assertEquals("single-run", assembledAgent.strategy)
        assertEquals(listOf(CapabilityDescriptor(id = "tool.echo", kind = "tool")), assembledAgent.capabilities)
    }

    /**
     * 用于组装测试的能力替身。
     */
    private class FakeCapabilityAdapter(
        override val descriptor: CapabilityDescriptor,
        private val result: RuntimeResult,
    ) : CapabilityAdapter {
        override suspend fun execute(request: CapabilityRequest): RuntimeResult = result
    }
}
