package com.agent.server

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

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            HealthHttpResponse(
                healthy = true,
                service = "mulehang-agent",
            ),
            response.body(),
        )
    }

    @Test
    fun `should execute runtime run endpoint through runtime http service`() = testApplication {
        application {
            runtimeHttpModule(
                service = FakeRuntimeHttpService(
                    response = RuntimeRunHttpResponse(
                        success = true,
                        sessionId = "session-1",
                        requestId = "request-1",
                        events = listOf(RuntimeEventHttpResponse(message = "agent.run.completed")),
                        output = JsonPrimitive("done:hello"),
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

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            RuntimeRunHttpResponse(
                success = true,
                sessionId = "session-1",
                requestId = "request-1",
                events = listOf(RuntimeEventHttpResponse(message = "agent.run.completed")),
                output = JsonPrimitive("done:hello"),
            ),
            response.body(),
        )
    }

    @Test
    fun `should translate provider failures into bad request responses`() = testApplication {
        application {
            runtimeHttpModule(
                service = FakeRuntimeHttpService(
                    response = RuntimeRunHttpResponse(
                        success = false,
                        sessionId = "session-2",
                        requestId = "request-2",
                        failure = RuntimeFailureHttpResponse(
                            kind = "provider",
                            message = "provider resolution failed",
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

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            RuntimeRunHttpResponse(
                success = false,
                sessionId = "session-2",
                requestId = "request-2",
                failure = RuntimeFailureHttpResponse(
                    kind = "provider",
                    message = "provider resolution failed",
                ),
            ),
            response.body(),
        )
    }

    private fun ApplicationTestBuilder.createJsonClient() = createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    private class FakeRuntimeHttpService(
        private val response: RuntimeRunHttpResponse = RuntimeRunHttpResponse(
            success = true,
            sessionId = "session-default",
            requestId = "request-default",
        ),
    ) : RuntimeHttpService {
        override suspend fun run(request: RuntimeRunHttpRequest): RuntimeRunHttpResponse = response
    }
}
