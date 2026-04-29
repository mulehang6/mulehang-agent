package com.agent.runtime.cli

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 runtime CLI 协议可以稳定表达最小 `stdio` 往返消息。
 */
class RuntimeCliProtocolTest {

    @Test
    fun `should encode run request with stable type discriminator`() {
        val message: RuntimeCliInboundMessage = RuntimeCliRunRequest(
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

        val encoded = RuntimeCliJson.encodeToString(RuntimeCliInboundMessage.serializer(), message)
        val decoded = RuntimeCliJson.decodeFromString(RuntimeCliInboundMessage.serializer(), encoded)

        assertTrue(encoded.contains("\"type\":\"run\""))
        assertEquals(message, decoded)
    }

    @Test
    fun `should round trip status event result and failure outbound messages`() {
        val messages = listOf<RuntimeCliOutboundMessage>(
            RuntimeCliStatusMessage(
                status = "session.started",
                sessionId = "session-1",
                requestId = "request-1",
                mode = "agent",
            ),
            RuntimeCliEventMessage(
                sessionId = "session-1",
                requestId = "request-1",
                event = RuntimeCliEventPayload(
                    message = "agent.run.started",
                    payload = JsonPrimitive("request-1"),
                ),
            ),
            RuntimeCliResultMessage(
                sessionId = "session-1",
                requestId = "request-1",
                output = JsonPrimitive("done:hello"),
                mode = "agent",
            ),
            RuntimeCliFailureMessage(
                sessionId = "session-1",
                requestId = "request-1",
                kind = "agent",
                message = "agent failed",
                details = RuntimeCliFailureDetails(
                    source = "runtime-default",
                    providerType = "OPENAI_COMPATIBLE",
                    baseUrl = "https://openrouter.ai/api/v1",
                    modelId = "openai/gpt-oss-120b:free",
                    apiKeyPresent = true,
                ),
            ),
        )

        messages.forEach { message ->
            val encoded = RuntimeCliJson.encodeToString(RuntimeCliOutboundMessage.serializer(), message)
            val decoded = RuntimeCliJson.decodeFromString(RuntimeCliOutboundMessage.serializer(), encoded)

            assertTrue(encoded.contains("\"type\""))
            assertEquals(message, decoded)
        }
    }

    @Test
    fun `should encode thinking delta as first class streaming event fields`() {
        val inboundJson = """
            {
              "type": "event",
              "sessionId": "session-1",
              "requestId": "request-1",
              "event": {
                "message": "agent.reasoning.delta",
                "channel": "thinking",
                "delta": "I need to inspect the available context first."
              }
            }
        """.trimIndent()

        val decoded = RuntimeCliJson.decodeFromString(RuntimeCliOutboundMessage.serializer(), inboundJson)
        val encoded = RuntimeCliJson.encodeToString(RuntimeCliOutboundMessage.serializer(), decoded)

        assertTrue(encoded.contains("\"message\":\"agent.reasoning.delta\""))
        assertTrue(encoded.contains("\"channel\":\"thinking\""))
        assertTrue(encoded.contains("\"delta\":\"I need to inspect the available context first.\""))
    }
}
