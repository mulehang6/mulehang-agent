package com.agent.runtime.server

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证最小 Ktor runtime 宿主的 HTTP 合约。
 */
class RuntimeHttpModuleTest {

    @Test
    fun `should expose health endpoint for manual probing`() = testApplication {
        application {
            runtimeHttpModule(
                service = FakeRuntimeHttpService(),
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
                ),
            ),
            body,
        )
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
            )
        }

        val client = createJsonClient()
        val response = client.post("/runtime/run") {
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
            )
        }

        val client = createJsonClient()
        val response = client.post("/runtime/run") {
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
            )
        }

        val client = createJsonClient()
        val response = client.post("/runtime/run") {
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

    private fun ApplicationTestBuilder.createJsonClient() = createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    private class FakeRuntimeHttpService(
        private val response: Result<RuntimeRunPayload> = Result.success(
            RuntimeRunPayload(
                sessionId = "session-default",
                requestId = "request-default",
            ),
        ),
    ) : RuntimeHttpService {
        override suspend fun run(request: RuntimeRunHttpRequest): Result<RuntimeRunPayload> = response
    }
}
