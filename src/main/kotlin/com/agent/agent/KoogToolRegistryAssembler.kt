package com.agent.agent

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.agents.mcp.metadata.McpServerInfo
import ai.koog.serialization.typeToken
import com.agent.capability.CapabilityDescriptor
import com.agent.capability.CapabilitySet
import com.agent.capability.HttpCapabilityAdapter
import com.agent.capability.McpCapabilityAdapter
import com.agent.capability.McpTransport
import com.agent.capability.ToolCapabilityAdapter
import com.agent.runtime.RuntimeCapabilityRequest
import com.agent.runtime.RuntimeResult
import com.agent.runtime.RuntimeSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * 表示 phase 03 的 Koog registry 桥接结果。
 */
data class KoogToolingBundle(
    val toolRegistry: ToolRegistry,
    val primaryRegistry: ToolRegistry,
    val mcpRegistries: List<ToolRegistry>,
    val primaryCapabilityIds: List<String>,
    val descriptors: List<CapabilityDescriptor>,
)

/**
 * 负责把 capability set 组装成 Koog 可消费的 registry bundle。
 */
class KoogToolRegistryAssembler(
    private val createMcpRegistry: suspend (McpTransport) -> ToolRegistry = { transport ->
        when (transport) {
            is McpTransport.Stdio -> {
                val process = ProcessBuilder(transport.command).start()
                McpToolRegistryProvider.fromTransport(
                    transport = McpToolRegistryProvider.defaultStdioTransport(process),
                    serverInfo = McpServerInfo(command = transport.command.joinToString(separator = " ")),
                )
            }

            is McpTransport.StreamableHttp -> throw UnsupportedOperationException(
                "Streamable HTTP MCP transport is not supported by Koog 0.8.0. " +
                    "Inject createMcpRegistry when a compatible transport implementation is available.",
            )
        }
    },
) {

    /**
     * 组装 tool/http 主 registry 与独立的 MCP registry 标识。
     */
    suspend fun assemble(capabilitySet: CapabilitySet): KoogToolingBundle {
        val primaryIds = buildList {
            addAll(capabilitySet.toolAdapters().map { it.descriptor.id })
            addAll(capabilitySet.httpAdapters().map { it.descriptor.id })
        }
        val primaryRegistry = ToolRegistry {
            capabilitySet.toolAdapters().forEach { tool(LocalToolBridge(it)) }
            capabilitySet.httpAdapters().forEach { tool(HttpToolBridge(it)) }
        }
        val mcpRegistries = capabilitySet.mcpAdapters().map { adapter ->
            createMcpRegistrySafely(adapter)
        }
        val toolRegistry = mcpRegistries.fold(primaryRegistry) { merged, registry ->
            merged + registry
        }

        return KoogToolingBundle(
            toolRegistry = toolRegistry,
            primaryRegistry = primaryRegistry,
            mcpRegistries = mcpRegistries,
            primaryCapabilityIds = primaryIds,
            descriptors = capabilitySet.descriptors(),
        )
    }

    /**
     * 把 MCP adapter 描述安全转换成真实 Koog ToolRegistry。
     */
    private suspend fun createMcpRegistrySafely(adapter: McpCapabilityAdapter): ToolRegistry = try {
        createMcpRegistry(adapter.transport)
    } catch (error: Throwable) {
        throw IllegalStateException(
            "Failed to create MCP tool registry for '${adapter.descriptor.id}' from '${adapter.transport.description}'.",
            error,
        )
    }
}

/**
 * 表示 phase 03 里 tool/http 统一采用的最小字符串载荷。
 */
@Serializable
internal data class CapabilityToolArgs(
    @property:LLMDescription("Optional raw string payload passed to the runtime capability bridge.")
    val payload: String? = null,
)

/**
 * 把 local tool adapter 包装成 Koog 可注册的文本工具。
 */
private class LocalToolBridge(
    private val adapter: ToolCapabilityAdapter,
) : SimpleTool<CapabilityToolArgs>(
    argsType = typeToken<CapabilityToolArgs>(),
    name = adapter.descriptor.id,
    description = adapter.description,
) {

    /**
     * 把 Koog tool 调用翻译回仓库内 capability 契约。
     */
    override suspend fun execute(args: CapabilityToolArgs): String = adapter.execute(
        request = RuntimeCapabilityRequest(
            capabilityId = adapter.descriptor.id,
            payload = args.payload?.let(::JsonPrimitive),
        ),
    ).toKoogText()
}

/**
 * 把 direct HTTP adapter 包装成 Koog 可注册的文本工具。
 */
private class HttpToolBridge(
    private val adapter: HttpCapabilityAdapter,
) : SimpleTool<CapabilityToolArgs>(
    argsType = typeToken<CapabilityToolArgs>(),
    name = adapter.descriptor.id,
    description = adapter.description,
) {

    /**
     * 把 Koog tool 调用翻译回仓库内 HTTP capability 契约。
     */
    override suspend fun execute(args: CapabilityToolArgs): String = adapter.execute(
        request = RuntimeCapabilityRequest(
            capabilityId = adapter.descriptor.id,
            payload = args.payload?.let(::JsonPrimitive),
        ),
    ).toKoogText()
}

/**
 * 将 runtime 结果压平成当前阶段可供 LLM 消费的文本。
 */
private fun RuntimeResult.toKoogText(): String = when (this) {
    is RuntimeSuccess -> output?.toString() ?: events.joinToString(separator = "\n") { it.message }
    else -> toString()
}
