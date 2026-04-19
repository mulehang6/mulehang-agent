package com.agent.server

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 AOP 风格日志装饰器不改变 RuntimeHttpService 的接口语义。
 */
class LoggingRuntimeHttpServiceTest {

    @Test
    fun `should delegate runtime request and preserve success response`() = runTest {
        val request = validRequest()
        val expected = RuntimeRunHttpResponse(
            success = true,
            sessionId = "session-1",
            requestId = "request-1",
            events = listOf(RuntimeEventHttpResponse(message = "agent.run.completed")),
            output = JsonPrimitive("done"),
        )
        val delegate = RecordingRuntimeHttpService(expected)
        val service = LoggingRuntimeHttpService(delegate)

        val response = service.run(request)

        assertEquals(request, delegate.receivedRequest)
        assertEquals(expected, response)
    }

    @Test
    fun `should delegate runtime request and preserve failure response`() = runTest {
        val request = validRequest()
        val expected = RuntimeRunHttpResponse(
            success = false,
            sessionId = "session-2",
            requestId = "request-2",
            failure = RuntimeFailureHttpResponse(
                kind = "agent",
                message = "agent failed",
            ),
        )
        val delegate = RecordingRuntimeHttpService(expected)
        val service = LoggingRuntimeHttpService(delegate)

        val response = service.run(request)

        assertEquals(request, delegate.receivedRequest)
        assertEquals(expected, response)
    }

    private fun validRequest(): RuntimeRunHttpRequest = RuntimeRunHttpRequest(
        prompt = "hello",
        provider = ProviderBindingHttpRequest(
            providerId = "provider-openai",
            providerType = "OPENAI_COMPATIBLE",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "nvidia/nemotron-nano-12b-v2-vl:free",
        ),
    )

    /**
     * 记录被装饰服务收到的请求，并返回固定响应。
     */
    private class RecordingRuntimeHttpService(
        private val response: RuntimeRunHttpResponse,
    ) : RuntimeHttpService {
        var receivedRequest: RuntimeRunHttpRequest? = null

        override suspend fun run(request: RuntimeRunHttpRequest): RuntimeRunHttpResponse {
            receivedRequest = request
            return response
        }
    }
}
