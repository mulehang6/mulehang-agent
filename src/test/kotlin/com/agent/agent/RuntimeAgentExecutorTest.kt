@file:Suppress("UnstableApiUsage")

package com.agent.agent

import com.agent.capability.CapabilitySet
import com.agent.capability.ToolCapabilityAdapter
import com.agent.provider.OpenAIEndpointMode
import com.agent.provider.ProviderBinding
import com.agent.provider.ProviderBindingOptions
import com.agent.provider.ProviderType
import com.agent.runtime.RuntimeAgentExecutionFailure
import com.agent.runtime.RuntimeAgentRunRequest
import com.agent.runtime.RuntimeCapabilityBridgeFailure
import com.agent.runtime.RuntimeFailed
import com.agent.runtime.RuntimeProviderResolutionFailure
import com.agent.runtime.RuntimeRequestContext
import com.agent.runtime.RuntimeSession
import com.agent.runtime.RuntimeSuccess
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 验证 runtime 到 agent.run 的结果翻译与错误分层。
 */
class RuntimeAgentExecutorTest {

    @Test
    fun `should translate successful agent run into runtime success`() = runTest {
        val executor = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            runner = { _, prompt -> "echo:$prompt" },
        )

        val result = executor.execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeSuccess>(result)
        assertEquals(JsonPrimitive("echo:hello"), result.output)
    }

    @Test
    fun `should translate provider capability and agent failures separately`() = runTest {
        val request = RuntimeAgentRunRequest(prompt = "hello")
        val session = RuntimeSession(id = "session-1")
        val context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1")
        val capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo")))

        val providerFailure = RuntimeAgentExecutor(
            assembleAgent = { _, _ -> throw IllegalArgumentException("provider failed") },
            runner = { _, _ -> "unused" },
        ).execute(session, context, request, openAiBinding(), capabilitySet)
        val capabilityFailure = RuntimeAgentExecutor(
            assembleAgent = { _, _ -> throw IllegalStateException("capability failed") },
            runner = { _, _ -> "unused" },
        ).execute(session, context, request, openAiBinding(), capabilitySet)
        val agentFailure = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            runner = { _, _ -> throw RuntimeException("agent failed") },
        ).execute(session, context, request, openAiBinding(), capabilitySet)

        assertIs<RuntimeFailed>(providerFailure)
        assertIs<RuntimeProviderResolutionFailure>(providerFailure.failure)

        assertIs<RuntimeFailed>(capabilityFailure)
        assertIs<RuntimeCapabilityBridgeFailure>(capabilityFailure.failure)

        assertIs<RuntimeFailed>(agentFailure)
        assertIs<RuntimeAgentExecutionFailure>(agentFailure.failure)
    }

    @Test
    fun `should suggest chat completions when default responses endpoint execution is unsupported`() = runTest {
        val result = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            runner = { _, _ -> throw RuntimeException("404 from /v1/responses") },
        ).execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(openAIEndpointMode = OpenAIEndpointMode.RESPONSES),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeFailed>(result)
        val failure = assertIs<RuntimeAgentExecutionFailure>(result.failure)
        assertContains(failure.message, "Responses")
        assertContains(failure.message, "chat/completions")
    }

    private fun openAiBinding(
        openAIEndpointMode: OpenAIEndpointMode = OpenAIEndpointMode.RESPONSES,
    ) = ProviderBinding(
        providerId = "provider-openai",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = "test-key",
        modelId = "openai/gpt-oss-120b:free",
        options = ProviderBindingOptions(openAIEndpointMode = openAIEndpointMode),
    )
}
