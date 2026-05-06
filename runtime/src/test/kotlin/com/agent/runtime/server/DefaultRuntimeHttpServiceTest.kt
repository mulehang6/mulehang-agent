package com.agent.runtime.server

import com.agent.runtime.agent.AgentAssembly
import com.agent.runtime.agent.RuntimeConversationMemory
import com.agent.runtime.agent.RuntimeAgentExecutor
import com.agent.runtime.agent.buildRecordingAssembledAgent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证默认 HTTP service 能把请求落到现有 runtime 执行链。
 */
class DefaultRuntimeHttpServiceTest {

    @Test
    fun `should execute runtime request through runtime agent executor`() = runTest {
        val service = DefaultRuntimeHttpService(
            runtimeAgentExecutor = RuntimeAgentExecutor(
                assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
                runner = { _, prompt -> "done:$prompt" },
            ),
        )

        val response = service.run(validRequest())
        val data = response.data

        assertEquals(1, response.code)
        assertEquals("session-1", data.sessionId)
        assertTrue(data.requestId.isNotBlank())
        assertEquals(JsonPrimitive("done:hello"), data.output)
        assertEquals(listOf("agent.run.started", "agent.run.completed"), data.events.map { it.message })
    }

    @Test
    fun `should reject unsupported provider type before runtime execution`() = runTest {
        val service = DefaultRuntimeHttpService()

        val response = service.run(
            RuntimeRunHttpRequest(
                sessionId = "session-2",
                prompt = "hello",
                provider = ProviderBindingHttpRequest(
                    providerId = "provider-invalid",
                    providerType = "UNSUPPORTED",
                    baseUrl = "https://example.com",
                    apiKey = "test-key",
                    modelId = "custom-model",
                ),
            ),
        )
        val data = response.data

        assertEquals("session-2", data.sessionId)
        assertEquals(0, response.code)
        assertEquals("Unsupported provider type 'UNSUPPORTED'.", response.message)
        assertEquals("provider", data.events.last().failureKind)
        assertEquals("Unsupported provider type 'UNSUPPORTED'.", data.events.last().failureMessage)
    }

    @Test
    fun `should generate session id when request does not provide one`() = runTest {
        val service = DefaultRuntimeHttpService(
            runtimeAgentExecutor = RuntimeAgentExecutor(
                assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
                runner = { _, prompt -> "done:$prompt" },
            ),
        )

        val response = service.run(
            RuntimeRunHttpRequest(
                prompt = "hello",
                provider = ProviderBindingHttpRequest(
                    providerId = "provider-openai",
                    providerType = "OPENAI_COMPATIBLE",
                    baseUrl = "https://openrouter.ai/api/v1",
                    apiKey = "test-key",
                    modelId = "openai/gpt-oss-120b:free",
                ),
            ),
        )
        val data = response.data

        assertEquals(1, response.code)
        assertTrue(!data.sessionId.isNullOrBlank())
        assertTrue(data.requestId.isNotBlank())
        assertEquals(JsonPrimitive("done:hello"), data.output)
    }

    @Test
    fun `should append structured failure event when runtime execution fails`() = runTest {
        val service = DefaultRuntimeHttpService(
            runtimeAgentExecutor = RuntimeAgentExecutor(
                assembleAgent = { binding, capabilities -> AgentAssembly().assemble(binding, capabilities) },
                runner = { _, _ -> throw RuntimeException("agent failed") },
            ),
        )

        val response = service.run(validRequest())
        val data = response.data
        val failureEvent = data.events.last()

        assertEquals(0, response.code)
        assertEquals("session-1", data.sessionId)
        assertEquals("runtime.run.failed", failureEvent.message)
        assertEquals("agent", failureEvent.failureKind)
        assertEquals("agent failed", failureEvent.failureMessage)
    }

    @Test
    fun `should continue conversation across http requests with the same session id`() = runTest {
        val recording = buildRecordingAssembledAgent { prompt ->
            val previousUserMessages = prompt.messages
                .filter { it.role == ai.koog.prompt.message.Message.Role.User }
                .dropLast(1)
                .map { it.content }
            if ("hello" in previousUserMessages) "you said hello" else "no memory"
        }
        val service = DefaultRuntimeHttpService(
            runtimeAgentExecutor = RuntimeAgentExecutor(
                assembleAgent = { _, _ -> recording.assembledAgent },
                conversationMemory = RuntimeConversationMemory(),
            ),
        )

        service.run(validRequest(prompt = "hello", sessionId = "session-7"))
        val response = service.run(validRequest(prompt = "what did I say?", sessionId = "session-7"))
        val data = response.data

        assertEquals(1, response.code)
        assertEquals(JsonPrimitive("you said hello"), data.output)
    }

    private fun validRequest(
        prompt: String = "hello",
        sessionId: String = "session-1",
    ): RuntimeRunHttpRequest = RuntimeRunHttpRequest(
        sessionId = sessionId,
        prompt = prompt,
        provider = ProviderBindingHttpRequest(
            providerId = "provider-openai",
            providerType = "OPENAI_COMPATIBLE",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "openai/gpt-oss-120b:free",
        ),
    )
}
