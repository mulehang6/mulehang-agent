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

        assertTrue(response.success)
        assertTrue(response.sessionId.isNotBlank())
        assertTrue(response.requestId.isNotBlank())
        assertEquals(JsonPrimitive("done:hello"), response.output)
        assertEquals(listOf("agent.run.started", "agent.run.completed"), response.events.map { it.message })
    }

    @Test
    fun `should reject unsupported provider type before runtime execution`() = runTest {
        val service = DefaultRuntimeHttpService()

        val response = service.run(
            RuntimeRunHttpRequest(
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

        assertEquals(false, response.success)
        assertEquals("provider", response.failure?.kind)
        assertEquals("Unsupported provider type 'UNSUPPORTED'.", response.failure?.message)
    }

    private fun validRequest(): RuntimeRunHttpRequest = RuntimeRunHttpRequest(
        prompt = "hello",
        provider = ProviderBindingHttpRequest(
            providerId = "provider-openai",
            providerType = "OPENAI_COMPATIBLE",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "openai/gpt-4.1-mini",
        ),
    )
}
