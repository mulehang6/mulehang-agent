package com.agent.agent

import com.agent.capability.CapabilitySet
import com.agent.capability.ToolCapabilityAdapter
import com.agent.provider.ProviderBinding
import com.agent.provider.ProviderType
import com.agent.runtime.AgentCapabilityRouter
import com.agent.runtime.RuntimeAgentRunRequest
import com.agent.runtime.RuntimeRequestContext
import com.agent.runtime.RuntimeRequestDispatcher
import com.agent.runtime.RuntimeSession
import com.agent.runtime.RuntimeSuccess
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 验证 dispatcher、router、executor 的统一 runtime 执行链。
 */
class AgentRuntimeIntegrationTest {

    @Test
    fun `should execute dispatcher router assembly and agent run through one runtime pipeline`() = runTest {
        val dispatcher = RuntimeRequestDispatcher(
            capabilityRouter = AgentCapabilityRouter(
                runtimeAgentExecutor = RuntimeAgentExecutor(
                    assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
                    runner = { _, prompt -> "done:$prompt" },
                ),
                binding = openAiBinding(),
                capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
            ),
        )

        val result = dispatcher.dispatch(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "say hello"),
        )

        assertIs<RuntimeSuccess>(result)
        assertEquals(JsonPrimitive("done:say hello"), result.output)
    }

    private fun openAiBinding() = ProviderBinding(
        providerId = "provider-openai",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = "test-key",
        modelId = "openai/gpt-4.1-mini",
    )
}
