package com.agent.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.agent.capability.CapabilitySet
import com.agent.capability.HttpCapabilityAdapter
import com.agent.capability.McpCapabilityAdapter
import com.agent.capability.McpTransport
import com.agent.capability.ToolCapabilityAdapter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Serializable
private data class MockMcpToolArgs(
    @property:LLMDescription("Mock MCP payload")
    val payload: String? = null,
)

/**
 * 验证 capability set 到 Koog registry bundle 的桥接形状。
 */
class KoogToolRegistryAssemblerTest {

    @Test
    fun `should assemble tool and http capabilities into primary koog registry bundle`() = runTest {
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                ToolCapabilityAdapter.echo(id = "tool.echo"),
                HttpCapabilityAdapter.internalGet(id = "http.health", path = "/health"),
            ),
        )

        val assembled = KoogToolRegistryAssembler().assemble(capabilitySet)

        assertEquals(listOf("tool.echo", "http.health"), assembled.descriptors.map { it.id })
        assertEquals(0, assembled.mcpRegistries.size)
        assertEquals(2, assembled.primaryCapabilityIds.size)
        assertTrue(assembled.primaryRegistry.tools.isNotEmpty())
        assertEquals(
            listOf("tool.echo", "http.health"),
            assembled.primaryRegistry.tools.map { it.descriptor.name },
        )
    }

    @Test
    fun `should keep mcp capabilities as separate koog registry entries`() = runTest {
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                McpCapabilityAdapter.streamableHttp(id = "mcp.playwright", url = "http://localhost:8931/mcp"),
            ),
        )

        val assembled = KoogToolRegistryAssembler(
            createMcpRegistry = { transport: McpTransport ->
                ToolRegistry {
                    tool(MockMcpTool(transport.description))
                }
            },
        ).assemble(capabilitySet)

        assertEquals(listOf("mcp.playwright"), assembled.descriptors.map { it.id })
        assertEquals(1, assembled.mcpRegistries.size)
        assertEquals(emptyList(), assembled.primaryCapabilityIds)
        assertEquals(
            listOf("mcp:streamable-http:http://localhost:8931/mcp"),
            assembled.mcpRegistries.single().tools.map { it.descriptor.name },
        )
        assertEquals(
            listOf("mcp:streamable-http:http://localhost:8931/mcp"),
            assembled.toolRegistry.tools.map { it.descriptor.name },
        )
    }

    @Test
    fun `should pass sse mcp transport into injected registry factory`() = runTest {
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                McpCapabilityAdapter.sse(id = "mcp.browser", url = "http://localhost:8931/sse"),
            ),
        )

        val assembled = KoogToolRegistryAssembler(
            createMcpRegistry = { transport: McpTransport ->
                ToolRegistry {
                    tool(MockMcpTool(transport.description))
                }
            },
        ).assemble(capabilitySet)

        assertEquals(listOf("mcp.browser"), assembled.descriptors.map { it.id })
        assertEquals(1, assembled.mcpRegistries.size)
        assertEquals(
            listOf("mcp:sse:http://localhost:8931/sse"),
            assembled.mcpRegistries.single().tools.map { it.descriptor.name },
        )
        assertEquals(
            listOf("mcp:sse:http://localhost:8931/sse"),
            assembled.toolRegistry.tools.map { it.descriptor.name },
        )
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `should execute bridged koog tool through runtime adapter`() = runTest {
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                ToolCapabilityAdapter.echo(id = "tool.echo"),
            ),
        )

        val assembled = KoogToolRegistryAssembler().assemble(capabilitySet)
        val tool = assembled.primaryRegistry.tools.first { it.descriptor.name == "tool.echo" } as Tool<CapabilityToolArgs, String>
        val result = tool.execute(CapabilityToolArgs(payload = "hello"))

        assertEquals("tool:tool.echo", result)
    }

    @Test
    fun `should fail with capability bridge context when mcp registry creation fails`() = runTest {
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                McpCapabilityAdapter.streamableHttp(id = "mcp.playwright", url = "http://localhost:8931/mcp"),
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            KoogToolRegistryAssembler(
                createMcpRegistry = { error("connection refused") },
            ).assemble(capabilitySet)
        }

        assertContains(error.message.orEmpty(), "Failed to create MCP tool registry")
        assertContains(error.message.orEmpty(), "mcp.playwright")
        assertContains(error.message.orEmpty(), "streamable-http:http://localhost:8931/mcp")
    }

    @Test
    fun `should fail clearly when default koog assembler receives streamable http mcp transport`() = runTest {
        val capabilitySet = CapabilitySet(
            adapters = listOf(
                McpCapabilityAdapter.streamableHttp(id = "mcp.playwright", url = "http://localhost:8931/mcp"),
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            KoogToolRegistryAssembler().assemble(capabilitySet)
        }

        assertContains(error.message.orEmpty(), "Failed to create MCP tool registry")
        assertContains(error.cause?.message.orEmpty(), "Streamable HTTP MCP transport is not supported by Koog 0.8.0")
    }

    private class MockMcpTool(
        transportDescription: String,
    ) : SimpleTool<MockMcpToolArgs>(
        argsType = typeToken<MockMcpToolArgs>(),
        name = "mcp:$transportDescription",
        description = "Mock MCP tool registry entry for tests.",
    ) {
        override suspend fun execute(args: MockMcpToolArgs): String = "mcp:${args.payload.orEmpty()}"
    }
}
