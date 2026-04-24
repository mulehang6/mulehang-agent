package com.agent.runtime.cli

import com.agent.runtime.agent.AgentAssembly
import com.agent.runtime.agent.RuntimeAgentExecutor
import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.provider.ProviderBinding
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 验证 runtime CLI service 会把一次运行请求翻译成稳定的流式消息序列。
 */
class RuntimeCliServiceTest {

    @Test
    fun `should resolve default provider binding when request omits provider`() = runTest {
        val service = DefaultRuntimeCliService(
            defaultBindingResolver = {
                CliProviderResolution(
                    binding = ProviderBinding(
                        providerId = "provider-openai",
                        providerType = com.agent.runtime.provider.ProviderType.OPENAI_COMPATIBLE,
                        baseUrl = "https://openrouter.ai/api/v1",
                        apiKey = "test-key",
                        modelId = "openai/gpt-oss-120b:free",
                    ),
                    details = RuntimeCliFailureDetails(
                        source = "runtime-default",
                        providerType = "OPENAI_COMPATIBLE",
                        baseUrl = "https://openrouter.ai/api/v1",
                        modelId = "openai/gpt-oss-120b:free",
                        apiKeyPresent = true,
                    ),
                )
            },
            runtimeAgentExecutor = RuntimeAgentExecutor(
                assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
                runner = { _, prompt -> "done:$prompt" },
            ),
            sessionIdFactory = { "session-1" },
            requestIdFactory = { "request-1" },
        )

        val messages = service.stream(
            RuntimeCliRunRequest(prompt = "hello"),
        ).toList()

        assertEquals(
            listOf("session.started", "run.started"),
            messages.filterIsInstance<RuntimeCliStatusMessage>().map { it.status },
        )
        val result = assertIs<RuntimeCliResultMessage>(messages.last())
        assertEquals("agent", result.mode)
        assertEquals(JsonPrimitive("done:hello"), result.output)
    }

    @Test
    fun `should emit provider failure when default provider binding is unavailable`() = runTest {
        val service = DefaultRuntimeCliService(
            defaultBindingResolver = {
                CliProviderResolution(
                    binding = null,
                    details = RuntimeCliFailureDetails(
                        source = "runtime-default",
                        apiKeyPresent = false,
                    ),
                )
            },
            sessionIdFactory = { "session-1" },
            requestIdFactory = { "request-1" },
        )

        val messages = service.stream(
            RuntimeCliRunRequest(prompt = "hello"),
        ).toList()

        assertEquals(
            listOf("session.started"),
            messages.filterIsInstance<RuntimeCliStatusMessage>().map { it.status },
        )
        val failure = assertIs<RuntimeCliFailureMessage>(messages.last())
        assertEquals("provider", failure.kind)
        assertEquals("Missing runtime provider configuration for CLI request.", failure.message)
        assertEquals("runtime-default", failure.details?.source)
        assertEquals(false, failure.details?.apiKeyPresent)
    }

    @Test
    fun `should stream runtime events and result when agent run succeeds`() = runTest {
        val service = DefaultRuntimeCliService(
            runtimeAgentExecutor = RuntimeAgentExecutor(
                assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
                runner = { _, prompt -> "done:$prompt" },
            ),
            capabilitySetFactory = { CapabilitySet(adapters = emptyList()) },
            sessionIdFactory = { "session-1" },
            requestIdFactory = { "request-1" },
        )

        val messages = service.stream(validRequest()).toList()

        assertEquals(
            listOf("session.started", "run.started"),
            messages.filterIsInstance<RuntimeCliStatusMessage>().map { it.status },
        )
        assertEquals(
            listOf("agent.run.started", "agent.run.completed"),
            messages.filterIsInstance<RuntimeCliEventMessage>().map { it.event.message },
        )

        val result = assertIs<RuntimeCliResultMessage>(messages.last())
        assertEquals("agent", result.mode)
        assertEquals(JsonPrimitive("done:hello"), result.output)
    }

    @Test
    fun `should emit structured failure when agent run fails`() = runTest {
        val service = DefaultRuntimeCliService(
            runtimeAgentExecutor = RuntimeAgentExecutor(
                assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
                runner = { _, _ -> throw IllegalStateException("capability bridge failed") },
            ),
            capabilitySetFactory = { CapabilitySet(adapters = emptyList()) },
            sessionIdFactory = { "session-1" },
            requestIdFactory = { "request-1" },
        )

        val messages = service.stream(validRequest()).toList()

        val failure = assertIs<RuntimeCliFailureMessage>(messages.last())
        assertEquals("capability", failure.kind)
        assertEquals("capability bridge failed", failure.message)
        assertEquals("cli-request", failure.details?.source)
        assertEquals("OPENAI_COMPATIBLE", failure.details?.providerType)
        assertEquals("https://openrouter.ai/api/v1", failure.details?.baseUrl)
        assertEquals("openai/gpt-oss-120b:free", failure.details?.modelId)
        assertEquals(true, failure.details?.apiKeyPresent)
    }

    /**
     * 构造一条可直接进入现有 runtime agent 执行链的最小请求。
     */
    private fun validRequest(): RuntimeCliRunRequest = RuntimeCliRunRequest(
        sessionId = "session-1",
        prompt = "hello",
        provider = RuntimeCliProviderBinding(
            providerId = "provider-openai",
            providerType = "OPENAI_COMPATIBLE",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "openai/gpt-oss-120b:free",
        ),
    )
}
