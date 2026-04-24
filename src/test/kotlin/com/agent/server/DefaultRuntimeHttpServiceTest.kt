package com.agent.server

import com.agent.agent.AgentAssembly
import com.agent.agent.RuntimeAgentExecutor
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
        assertTrue(data.sessionId?.isNotBlank() == true)
        assertTrue(data.requestId.isNotBlank())
        assertEquals(JsonPrimitive("done:hello"), data.output)
    }

    private fun validRequest(): RuntimeRunHttpRequest = RuntimeRunHttpRequest(
        sessionId = "session-1",
        prompt = "hello",
        provider = ProviderBindingHttpRequest(
            providerId = "provider-openai",
            providerType = "OPENAI_COMPATIBLE",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "openai/gpt-oss-120b:free",
        ),
    )
}
