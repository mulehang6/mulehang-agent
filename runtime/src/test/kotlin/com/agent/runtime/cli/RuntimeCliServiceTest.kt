package com.agent.runtime.cli

import com.agent.runtime.agent.AgentAssembly
import com.agent.runtime.agent.RuntimeAgentExecutor
import com.agent.runtime.capability.CapabilitySet
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
    fun `should stream demo messages when provider binding is omitted`() = runTest {
        val service = DefaultRuntimeCliService(
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
        assertEquals(
            "runtime.cli.demo",
            messages.filterIsInstance<RuntimeCliEventMessage>().single().event.message,
        )

        val result = assertIs<RuntimeCliResultMessage>(messages.last())
        assertEquals("demo", result.mode)
        assertEquals(JsonPrimitive("echo:hello"), result.output)
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
