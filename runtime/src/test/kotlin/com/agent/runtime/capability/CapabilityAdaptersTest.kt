package com.agent.runtime.capability

import com.agent.runtime.core.RuntimeCapabilityRequest
import com.agent.runtime.core.RuntimeInfoEvent
import com.agent.runtime.core.RuntimeSuccess
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
                    transport = McpTransport.StreamableHttp("http://localhost:8931/mcp"),
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
        val mcpAdapter = McpCapabilityAdapter.streamableHttp(
            id = "mcp.playwright",
            url = "http://localhost:8931/mcp",
        )
        val sseAdapter = McpCapabilityAdapter.sse(
            id = "mcp.browser",
            url = "http://localhost:8931/sse",
        )
        val stdioAdapter = McpCapabilityAdapter.stdio(
            id = "mcp.filesystem",
            command = listOf("npx", "-y", "@modelcontextprotocol/server-filesystem", "."),
        )
        val httpAdapter = HttpCapabilityAdapter.internalGet(
            id = "http.health",
            path = "/health",
        )

        assertEquals("echo", toolAdapter.toolName)
        assertEquals(McpTransport.StreamableHttp("http://localhost:8931/mcp"), mcpAdapter.transport)
        assertEquals(McpTransport.Sse("http://localhost:8931/sse"), sseAdapter.transport)
        assertEquals("sse:http://localhost:8931/sse", sseAdapter.transport.description)
        assertEquals(
            McpTransport.Stdio(listOf("npx", "-y", "@modelcontextprotocol/server-filesystem", ".")),
            stdioAdapter.transport,
        )
        assertEquals("/health", httpAdapter.path)
        assertNotNull(toolAdapter.toKoogDescriptor())
        assertNotNull(httpAdapter.toKoogDescriptor())
    }
}
