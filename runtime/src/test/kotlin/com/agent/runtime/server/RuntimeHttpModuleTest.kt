package com.agent.runtime.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证最小 Ktor runtime 宿主的 HTTP 合约。
 */
class RuntimeHttpModuleTest {

    @Test
    fun `should expose health endpoint for manual probing`() = testApplication {
        application {
            runtimeHttpModule(
                service = FakeRuntimeHttpService(),
                metadata = testMetadata(),
                auth = RuntimeServerAuth.disabledForTests(),
            )
        }

        val client = createJsonClient()
        val response = client.get("/health")
        val body = response.body<Result<HealthPayload>>()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            Result.success(
                HealthPayload(
                    healthy = true,
                    service = "mulehang-agent",
                    protocolVersion = "2026-05-06",
                ),
            ),
            body,
        )
    }

    @Test
    fun `should expose runtime metadata for shared local server`() = testApplication {
        application {
            runtimeHttpModule(
                service = FakeRuntimeHttpService(),
                metadata = testMetadata(),
                auth = RuntimeServerAuth.disabledForTests(),
            )
        }

        val client = createJsonClient()
        val response = client.get("/meta")
        val body = response.body<Result<RuntimeServerMetadata>>()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(Result.success(testMetadata()), body)
    }

    @Test
    fun `should execute runtime run endpoint through runtime http service`() = testApplication {
        application {
            runtimeHttpModule(
                service = FakeRuntimeHttpService(
                    response = Result.success(
                        RuntimeRunPayload(
                            sessionId = "session-1",
                            requestId = "request-1",
                            events = listOf(RuntimeEventPayload(message = "agent.run.completed")),
                            output = JsonPrimitive("done:hello"),
                        ),
                    ),
                ),
                metadata = testMetadata(),
                auth = RuntimeServerAuth.required("secret-token"),
            )
        }

        val client = createJsonClient()
        val response = client.post("/runtime/run") {
            header(HttpHeaders.Authorization, "Bearer secret-token")
            contentType(ContentType.Application.Json)
            setBody(
                RuntimeRunHttpRequest(
                    sessionId = "session-1",
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
        }
        val body = response.body<Result<RuntimeRunPayload>>()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            Result.success(
                RuntimeRunPayload(
                    sessionId = "session-1",
                    requestId = "request-1",
                    events = listOf(RuntimeEventPayload(message = "agent.run.completed")),
                    output = JsonPrimitive("done:hello"),
                ),
            ),
            body,
        )
    }

    @Test
    fun `should reject unauthenticated runtime run request when token auth enabled`() = testApplication {
        application {
            runtimeHttpModule(
                service = FakeRuntimeHttpService(),
                metadata = testMetadata(),
                auth = RuntimeServerAuth.required("secret-token"),
            )
        }

        val client = createJsonClient()
        val response = client.post("/runtime/run") {
            contentType(ContentType.Application.Json)
            setBody(validRunRequest())
        }
        val body = response.body<Result<RuntimeRunPayload>>()

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, body.code)
        assertEquals("Unauthorized.", body.message)
    }

    @Test
    fun `should translate provider failures into bad request responses`() = testApplication {
        application {
            runtimeHttpModule(
                service = FakeRuntimeHttpService(
                    response = Result.fail(
                        message = "provider resolution failed",
                        data = RuntimeRunPayload(
                            sessionId = "session-2",
                            requestId = "request-2",
                            events = listOf(
                                RuntimeEventPayload(
                                    message = "runtime.run.failed",
                                    failureKind = "provider",
                                    failureMessage = "provider resolution failed",
                                ),
                            ),
                        ),
                    ),
                ),
                metadata = testMetadata(),
                auth = RuntimeServerAuth.required("secret-token"),
            )
        }

        val client = createJsonClient()
        val response = client.post("/runtime/run") {
            header(HttpHeaders.Authorization, "Bearer secret-token")
            contentType(ContentType.Application.Json)
            setBody(
                RuntimeRunHttpRequest(
                    sessionId = "session-2",
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
        }
        val body = response.body<Result<RuntimeRunPayload>>()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            Result.fail(
                message = "provider resolution failed",
                data = RuntimeRunPayload(
                    sessionId = "session-2",
                    requestId = "request-2",
                    events = listOf(
                        RuntimeEventPayload(
                            message = "runtime.run.failed",
                            failureKind = "provider",
                            failureMessage = "provider resolution failed",
                        ),
                    ),
                ),
            ),
            body,
        )
    }

    @Test
    fun `should return generated session id when request does not provide one`() = testApplication {
        application {
            runtimeHttpModule(
                service = FakeRuntimeHttpService(
                    response = Result.success(
                        RuntimeRunPayload(
                            sessionId = "generated-session-1",
                            requestId = "request-3",
                            events = listOf(RuntimeEventPayload(message = "agent.run.completed")),
                            output = JsonPrimitive("done:first-turn"),
                        ),
                    ),
                ),
                metadata = testMetadata(),
                auth = RuntimeServerAuth.required("secret-token"),
            )
        }

        val client = createJsonClient()
        val response = client.post("/runtime/run") {
            header(HttpHeaders.Authorization, "Bearer secret-token")
            contentType(ContentType.Application.Json)
            setBody(
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
        }
        val body = response.body<Result<RuntimeRunPayload>>()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("generated-session-1", body.data.sessionId)
        assertEquals("request-3", body.data.requestId)
        assertEquals("done:first-turn", (body.data.output as JsonPrimitive).content)
    }

    @Test
    fun `should stream runtime events through sse route`() = testApplication {
        application {
            runtimeHttpModule(
                service = FakeRuntimeHttpService(),
                metadata = testMetadata(),
                auth = RuntimeServerAuth.required("secret-token"),
            )
        }

        val client = createJsonClient()
        val response = client.post("/runtime/run/stream") {
            header(HttpHeaders.Authorization, "Bearer secret-token")
            contentType(ContentType.Application.Json)
            setBody(validRunRequest())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("text/event-stream", response.headers[HttpHeaders.ContentType]?.substringBefore(";"))
        val body = response.body<String>()
        assertTrue(body.contains("event: status"))
        assertTrue(body.contains("event: run.completed"))
        assertFalse(
            body.contains("data: {\n") || body.contains("data: {\r\n"),
            "SSE data payload must stay on one line so the CLI can JSON.parse each frame directly.",
        )
    }

    private fun ApplicationTestBuilder.createJsonClient() = createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    private fun testMetadata(): RuntimeServerMetadata = RuntimeServerMetadata(
        service = "mulehang-agent",
        protocolVersion = "2026-05-06",
        serverVersion = "test",
        authMode = "token",
    )

    private fun validRunRequest(): RuntimeRunHttpRequest = RuntimeRunHttpRequest(
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

    private class FakeRuntimeHttpService(
        private val response: Result<RuntimeRunPayload> = Result.success(
            RuntimeRunPayload(
                sessionId = "session-default",
                requestId = "request-default",
            ),
        ),
    ) : RuntimeHttpService {
        override suspend fun run(request: RuntimeRunHttpRequest): Result<RuntimeRunPayload> = response

        override fun stream(request: RuntimeRunHttpRequest) = kotlinx.coroutines.flow.flow {
            emit(
                RuntimeSseEvent(
                    event = "status",
                    sessionId = request.sessionId ?: "session-default",
                    requestId = "request-stream",
                    message = "run.started",
                ),
            )
            emit(
                RuntimeSseEvent(
                    event = "run.completed",
                    sessionId = request.sessionId ?: "session-default",
                    requestId = "request-stream",
                    output = JsonPrimitive("done:hello"),
                ),
            )
        }
    }
}
