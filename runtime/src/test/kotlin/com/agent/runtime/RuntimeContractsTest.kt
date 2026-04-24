package com.agent.runtime

import com.agent.runtime.core.CapabilityRequest
import com.agent.runtime.core.RuntimeAgentExecutionFailure
import com.agent.runtime.core.RuntimeAgentRunRequest
import com.agent.runtime.core.RuntimeCapabilityBridgeFailure
import com.agent.runtime.core.RuntimeCapabilityRequest
import com.agent.runtime.core.RuntimeError
import com.agent.runtime.core.RuntimeEvent
import com.agent.runtime.core.RuntimeFailed
import com.agent.runtime.core.RuntimeFailure
import com.agent.runtime.core.RuntimeInfoEvent
import com.agent.runtime.core.RuntimeProviderResolutionFailure
import com.agent.runtime.core.RuntimeRequestContext
import com.agent.runtime.core.RuntimeResult
import com.agent.runtime.core.RuntimeSession
import com.agent.runtime.core.RuntimeSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 验证 runtime 基础契约在阶段 01 内具备稳定的最小结构。
 */
class RuntimeContractsTest {

    @Test
    fun `should expose session context request event result and failure contracts`() {
        val session = RuntimeSession(id = "session-1")
        val context = RuntimeRequestContext(
            sessionId = session.id,
            requestId = "request-1",
            attributes = mapOf("entry" to "cli"),
        )
        val request = RuntimeCapabilityRequest(capabilityId = "agent.run")
        val event = RuntimeInfoEvent(message = "started")
        val failure = RuntimeError(message = "boom")
        val success = RuntimeSuccess(events = listOf(event))
        val failed = RuntimeFailed(failure = failure)

        assertEquals("session-1", session.id)
        assertEquals("request-1", context.requestId)
        assertEquals("agent.run", request.capabilityId)
        assertEquals("started", event.message)
        assertEquals(listOf(event), success.events)
        assertEquals(failure, failed.failure)

        assertTrue(CapabilityRequest::class.java.isSealed)
        assertTrue(RuntimeEvent::class.java.isSealed)
        assertTrue(RuntimeResult::class.java.isSealed)
        assertTrue(RuntimeFailure::class.java.isSealed)
    }

    @Test
    fun `should represent agent run request through runtime contract`() {
        val request = RuntimeAgentRunRequest(prompt = "summarize this")

        assertEquals("agent.run", request.capabilityId)
        assertEquals("summarize this", request.prompt)
    }

    @Test
    fun `should keep provider capability and agent failures distinct`() {
        assertIs<RuntimeProviderResolutionFailure>(
            RuntimeProviderResolutionFailure(message = "provider failed"),
        )
        assertIs<RuntimeCapabilityBridgeFailure>(
            RuntimeCapabilityBridgeFailure(message = "capability failed"),
        )
        assertIs<RuntimeAgentExecutionFailure>(
            RuntimeAgentExecutionFailure(message = "agent failed"),
        )
    }
}
