package com.agent.capability

import com.agent.runtime.RuntimeCapabilityRequest
import com.agent.runtime.RuntimeInfoEvent
import com.agent.runtime.RuntimeSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证三类 capability adapter 都能通过统一接口注册和调用。
 */
class CapabilityAdaptersTest {

    @Test
    fun `should register tool mcp and http adapters and invoke them uniformly`() = runTest {
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                ToolCapabilityAdapter(id = "tool.echo") {
                    RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "tool")))
                },
                McpCapabilityAdapter(id = "mcp.list") {
                    RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "mcp")))
                },
                HttpCapabilityAdapter(id = "http.internal") {
                    RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "http")))
                },
            ),
        )

        assertEquals(
            listOf(
                CapabilityDescriptor(id = "tool.echo", kind = "tool"),
                CapabilityDescriptor(id = "mcp.list", kind = "mcp"),
                CapabilityDescriptor(id = "http.internal", kind = "http"),
            ),
            capabilitySet.descriptors(),
        )
        assertEquals(
            RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "tool"))),
            capabilitySet.execute(
                capabilityId = "tool.echo",
                request = RuntimeCapabilityRequest(capabilityId = "tool.echo"),
            ),
        )
        assertEquals(
            RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "mcp"))),
            capabilitySet.execute(
                capabilityId = "mcp.list",
                request = RuntimeCapabilityRequest(capabilityId = "mcp.list"),
            ),
        )
        assertEquals(
            RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "http"))),
            capabilitySet.execute(
                capabilityId = "http.internal",
                request = RuntimeCapabilityRequest(capabilityId = "http.internal"),
            ),
        )
    }
}
