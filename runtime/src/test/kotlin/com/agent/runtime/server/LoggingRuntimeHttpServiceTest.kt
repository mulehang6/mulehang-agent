package com.agent.runtime.server

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
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
        val expected: Result<RuntimeRunPayload> = Result.success(
            RuntimeRunPayload(
                sessionId = "session-1",
                requestId = "request-1",
                events = listOf(RuntimeEventPayload(message = "agent.run.completed")),
                output = JsonPrimitive("done"),
            ),
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
        val expected: Result<RuntimeRunPayload> = Result.fail(
            message = "agent failed",
            data = RuntimeRunPayload(
                sessionId = "session-2",
                requestId = "request-2",
                events = listOf(
                    RuntimeEventPayload(
                        message = "runtime.run.failed",
                        failureKind = "agent",
                        failureMessage = "agent failed",
                    ),
                ),
            ),
        )
        val delegate = RecordingRuntimeHttpService(expected)
        val service = LoggingRuntimeHttpService(delegate)

        val response = service.run(request)

        assertEquals(request, delegate.receivedRequest)
        assertEquals(expected, response)
    }

    @Test
    fun `should delegate runtime stream and preserve emitted events`() = runTest {
        val request = validRequest()
        val expected = listOf(
            RuntimeSseEvent(
                event = "status",
                sessionId = "session-1",
                requestId = "request-1",
                message = "run.started",
            ),
            RuntimeSseEvent(
                event = "run.completed",
                sessionId = "session-1",
                requestId = "request-1",
                output = JsonPrimitive("done"),
            ),
        )
        val delegate = RecordingRuntimeHttpService(
            response = Result.success(RuntimeRunPayload(requestId = "request-1")),
            streamResponse = expected,
        )
        val service = LoggingRuntimeHttpService(delegate)

        val response = service.stream(request).toList()

        assertEquals(request, delegate.receivedStreamRequest)
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
        private val response: Result<RuntimeRunPayload>,
        private val streamResponse: List<RuntimeSseEvent> = emptyList(),
    ) : RuntimeHttpService {
        var receivedRequest: RuntimeRunHttpRequest? = null
        var receivedStreamRequest: RuntimeRunHttpRequest? = null

        override suspend fun run(request: RuntimeRunHttpRequest): Result<RuntimeRunPayload> {
            receivedRequest = request
            return response
        }

        override fun stream(request: RuntimeRunHttpRequest) = flow {
            receivedStreamRequest = request
            streamResponse.forEach { emit(it) }
        }
    }
}
