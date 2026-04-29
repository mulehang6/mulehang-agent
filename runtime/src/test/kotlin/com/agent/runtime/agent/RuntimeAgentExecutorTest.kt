@file:Suppress("UnstableApiUsage")

package com.agent.runtime.agent

import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.capability.ToolCapabilityAdapter
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import com.agent.runtime.core.RuntimeAgentExecutionFailure
import com.agent.runtime.core.RuntimeAgentRunRequest
import com.agent.runtime.core.RuntimeCapabilityBridgeFailure
import com.agent.runtime.core.RuntimeFailed
import com.agent.runtime.core.RuntimeProviderResolutionFailure
import com.agent.runtime.core.RuntimeRequestContext
import com.agent.runtime.core.RuntimeSession
import com.agent.runtime.core.RuntimeSuccess
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flowOf
import ai.koog.prompt.streaming.StreamFrame
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
            binding = openAiBinding(providerType = ProviderType.OPENAI_RESPONSES),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeFailed>(result)
        val failure = assertIs<RuntimeAgentExecutionFailure>(result.failure)
        assertContains(failure.message, "Responses")
        assertContains(failure.message, "chat/completions")
    }

    @Test
    fun `should translate streaming text and reasoning frames into runtime events`() = runTest {
        val result = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            streamRunner = { _, _ ->
                flowOf(
                    StreamFrame.ReasoningDelta(text = "thinking "),
                    StreamFrame.TextDelta("hello "),
                    StreamFrame.TextDelta("world"),
                )
            },
        ).execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(),
            capabilitySet = CapabilitySet(adapters = listOf(ToolCapabilityAdapter.echo(id = "tool.echo"))),
        )

        assertIs<RuntimeSuccess>(result)
        assertEquals(JsonPrimitive("hello world"), result.output)
        assertEquals(
            listOf("status", "thinking", "text", "text", "status"),
            result.events.map { it.channel ?: "status" },
        )
        assertEquals("thinking ", result.events[1].delta)
        assertEquals("hello ", result.events[2].delta)
        assertEquals("world", result.events[3].delta)
    }

    @Test
    fun `should use DeepSeek thinking compatibility stream when chat completions reasoning is hidden from Koog`() = runTest {
        val result = RuntimeAgentExecutor(
            assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
            compatibilityStreamRunner = object : CompatibilityStreamRunner {
                override fun supports(binding: ProviderBinding, hasTools: Boolean): Boolean = true

                override fun stream(binding: ProviderBinding, userPrompt: String) = flowOf(
                    StreamFrame.ReasoningDelta(text = "inspect "),
                    StreamFrame.TextDelta("answer"),
                )
            },
        ).execute(
            session = RuntimeSession(id = "session-1"),
            context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1"),
            request = RuntimeAgentRunRequest(prompt = "hello"),
            binding = openAiBinding(
                baseUrl = "https://api.deepseek.com",
                modelId = "deepseek-v4-flash",
                enableThinking = true,
            ),
            capabilitySet = CapabilitySet(adapters = emptyList()),
        )

        assertIs<RuntimeSuccess>(result)
        assertEquals(JsonPrimitive("answer"), result.output)
        assertEquals(
            listOf("status", "thinking", "text", "status"),
            result.events.map { it.channel ?: "status" },
        )
        assertEquals("inspect ", result.events[1].delta)
        assertEquals("answer", result.events[2].delta)
    }

    @Test
    fun `should inject deepseek thinking payload into prompt params when enabled`() {
        val prompt = buildRuntimePrompt(
            userPrompt = "hello",
            binding = openAiBinding(
                baseUrl = "https://api.deepseek.com",
                modelId = "deepseek-v4-flash",
                enableThinking = true,
            ),
        )

        val params = assertIs<OpenAIChatParams>(prompt.params)
        assertEquals("{\"type\":\"enabled\"}", params.additionalProperties?.get("thinking")?.toString())
    }

    private fun openAiBinding(
        providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl: String = "https://openrouter.ai/api/v1",
        modelId: String = "openai/gpt-oss-120b:free",
        enableThinking: Boolean = false,
    ) = ProviderBinding(
        providerId = "provider-openai",
        providerType = providerType,
        baseUrl = baseUrl,
        apiKey = "test-key",
        modelId = modelId,
        enableThinking = enableThinking,
    )
}
