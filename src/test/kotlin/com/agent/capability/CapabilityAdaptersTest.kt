package com.agent.capability

import com.agent.runtime.RuntimeCapabilityRequest
import com.agent.runtime.RuntimeInfoEvent
import com.agent.runtime.RuntimeSuccess
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 验证三类 capability adapter 都能通过统一接口注册和调用。
 */
class CapabilityAdaptersTest {

    @Test
    fun `should register tool mcp and http adapters and invoke them uniformly`() = runTest {
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                ToolCapabilityAdapter(
                    id = "tool.echo",
                    toolName = "echo",
                    description = "Echo tool for contract tests.",
                ) {
                    RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "tool")))
                },
                McpCapabilityAdapter(
                    id = "mcp.list",
                    serverUrl = "http://localhost:8931/sse",
                ) {
                    RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "mcp")))
                },
                HttpCapabilityAdapter(
                    id = "http.internal",
                    method = "GET",
                    path = "/internal",
                    description = "Internal HTTP contract test adapter.",
                ) {
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

    @Test
    fun `should expose koog bridge metadata for tool mcp and http adapters`() = runTest {
        val toolAdapter = ToolCapabilityAdapter.echo(id = "tool.echo")
        val mcpAdapter = McpCapabilityAdapter.sse(
            id = "mcp.playwright",
            serverUrl = "http://localhost:8931/sse",
        )
        val httpAdapter = HttpCapabilityAdapter.internalGet(
            id = "http.health",
            path = "/health",
        )

        assertEquals("echo", toolAdapter.toolName)
        assertEquals("http://localhost:8931/sse", mcpAdapter.serverUrl)
        assertEquals("/health", httpAdapter.path)
        assertNotNull(toolAdapter.toKoogDescriptor())
        assertNotNull(httpAdapter.toKoogDescriptor())
    }
}
