package com.agent.runtime.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.agent.runtime.capability.CapabilityDescriptor
import com.agent.runtime.capability.CapabilitySet
import com.agent.runtime.capability.McpCapabilityAdapter
import com.agent.runtime.capability.McpTransport
import com.agent.runtime.capability.ToolCapabilityAdapter
import com.agent.runtime.provider.ProviderBinding
import com.agent.runtime.provider.ProviderType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class AgentAssemblyMcpArgs(
    @property:LLMDescription("Mock MCP payload")
    val payload: String? = null,
)

/**
 * 验证 runtime 可基于 binding 与 capability set 组装真实 Koog agent。
 */
class AgentAssemblyTest {

    @Test
    fun `should assemble real koog ai agent from resolved binding and capability registries`() = runTest {
        val binding = ProviderBinding(
            providerId = "provider-1",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "test-key",
            modelId = "openai/gpt-oss-120b:free",
        )
        val assembly = AgentAssembly(
            toolRegistryAssembler = KoogToolRegistryAssembler(
                createMcpRegistry = { transport: McpTransport ->
                    ToolRegistry {
                        tool(AssemblyMockMcpTool(transport.description))
                    }
                },
            ),
        )

        val assembledAgent = assembly.assemble(
            binding = binding,
            capabilitySet = CapabilitySet(
                adapters = listOf(
                    ToolCapabilityAdapter.echo(id = "tool.echo"),
                    McpCapabilityAdapter.streamableHttp(id = "mcp.playwright", url = "http://localhost:8931/mcp"),
                ),
            ),
        )

        assertEquals(binding, assembledAgent.binding)
        assertEquals("ai.koog.agents.core.agent.GraphAIAgent", assembledAgent.agent::class.qualifiedName)
        assertEquals(
            "ai.koog.agents.core.agent.entity.AIAgentGraphStrategy",
            assembledAgent.strategy::class.qualifiedName,
        )
        assertEquals(
            listOf(
                CapabilityDescriptor(id = "tool.echo", kind = "tool"),
                CapabilityDescriptor(id = "mcp.playwright", kind = "mcp"),
            ),
            assembledAgent.capabilities,
        )
        assertEquals(
            listOf("tool.echo", "mcp:streamable-http:http://localhost:8931/mcp"),
            assembledAgent.toolRegistry.tools.map { it.descriptor.name },
        )
    }

    @Test
    fun `should expose real koog single run strategy`() {
        val strategy = AgentStrategyFactory.singleRun()

        assertEquals(
            "ai.koog.agents.core.agent.entity.AIAgentGraphStrategy",
            strategy::class.qualifiedName,
        )
    }

    private class AssemblyMockMcpTool(
        transportDescription: String,
    ) : SimpleTool<AgentAssemblyMcpArgs>(
        argsType = typeToken<AgentAssemblyMcpArgs>(),
        name = "mcp:$transportDescription",
        description = "Mock MCP tool registry entry for AgentAssembly tests.",
    ) {
        override suspend fun execute(args: AgentAssemblyMcpArgs): String = args.payload.orEmpty()
    }
}
