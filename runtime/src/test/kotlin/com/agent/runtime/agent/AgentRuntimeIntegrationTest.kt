package com.agent.runtime.agent

import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.capability.ToolCapabilityAdapter
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import com.agent.runtime.core.RuntimeAgentRunRequest
import com.agent.runtime.core.RuntimeRequestContext
import com.agent.runtime.core.RuntimeRequestDispatcher
import com.agent.runtime.core.RuntimeSession
import com.agent.runtime.core.RuntimeSuccess
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
        modelId = "openai/gpt-oss-120b:free",
    )
}
