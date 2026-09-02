@file:OptIn(ai.koog.agents.core.tools.annotations.InternalAgentToolsApi::class)

package com.agent.shared.agent.resource

import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.fromProcess
import com.agent.shared.agent.api.AgentRuntimeMcpServer
import com.agent.shared.agent.api.AgentRuntimeMcpTransport
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.PermissionPreset
import com.agent.shared.tool.model.ToolRisk
import com.agent.shared.tool.policy.DesktopToolPolicy
import java.util.UUID

/** MCP 连接和工具冲突在启动一轮 Agent 时产生的可见诊断。 */
data class McpToolRegistryDiagnostic(
    val serverId: String,
    val message: String,
)

/** 一轮 Agent 使用的 MCP 工具注册表与受其管理的 stdio 子进程。 */
class McpToolRegistryLease internal constructor(
    val registry: ToolRegistry,
    val diagnostics: List<McpToolRegistryDiagnostic>,
    private val processes: List<Process>,
) : AutoCloseable {
    /** 结束仅销毁本轮启动的 stdio server；HTTP client 生命周期由 Koog transport 管理。 */
    override fun close() {
        processes.forEach { process ->
            if (process.isAlive) process.destroy()
        }
    }
}

/**
 * 将快照中的受控 MCP 声明连接成 Koog ToolRegistry，并把每个远程工具置于既有审批策略之后。
 */
class McpToolRegistryBridge {
    /**
     * 连接所有服务并把其工具附加到 [baseRegistry]。内建桌面工具优先，冲突的 MCP 工具不会
     * 静默加入；连接失败也作为诊断返回，调用方可在时间线展示。
     */
    suspend fun create(
        baseRegistry: ToolRegistry,
        servers: List<AgentRuntimeMcpServer>,
        permissionPreset: PermissionPreset,
        interactionBridge: DesktopToolInteractionBridge,
    ): McpToolRegistryLease {
        if (servers.isEmpty()) {
            return McpToolRegistryLease(baseRegistry, emptyList(), emptyList())
        }
        val diagnostics = mutableListOf<McpToolRegistryDiagnostic>()
        val processes = mutableListOf<Process>()
        val mergedTools = baseRegistry.tools.toMutableList()
        val names = mergedTools.mapTo(mutableSetOf(), ToolBase<*, *>::name)
        servers.forEach { server ->
            val remoteRegistry = runCatching { connect(server, processes) }
                .getOrElse { error ->
                    diagnostics += McpToolRegistryDiagnostic(
                        serverId = server.id,
                        message = "MCP 连接失败：${error.message ?: "未知错误"}",
                    )
                    return@forEach
                }
            remoteRegistry.tools.forEach { tool ->
                if (!names.add(tool.name)) {
                    diagnostics += McpToolRegistryDiagnostic(
                        serverId = server.id,
                        message = "MCP 工具 '${tool.name}' 与已注册工具冲突，未启用。",
                    )
                    return@forEach
                }
                mergedTools += ApprovalGatedMcpTool(
                    delegate = tool.asUntypedTool(),
                    server = server,
                    permissionPreset = permissionPreset,
                    interactionBridge = interactionBridge,
                )
            }
        }
        return McpToolRegistryLease(
            registry = ToolRegistry { tools(mergedTools) },
            diagnostics = diagnostics.toList(),
            processes = processes.toList(),
        )
    }

    /** 按声明 transport 建立 Koog MCP registry。 */
    private suspend fun connect(
        server: AgentRuntimeMcpServer,
        processes: MutableList<Process>,
    ): ToolRegistry = when (server.transport) {
        AgentRuntimeMcpTransport.STDIO -> {
            val process = ProcessBuilder(server.command)
                .apply { environment().putAll(server.environment) }
                .start()
            try {
                val registry = McpToolRegistryProvider.fromProcess(process)
                processes += process
                registry
            } catch (error: Throwable) {
                process.destroyForcibly()
                throw error
            }
        }

        AgentRuntimeMcpTransport.SSE -> McpToolRegistryProvider.fromSseUrl(requireNotNull(server.url))
        AgentRuntimeMcpTransport.STREAMABLE_HTTP -> McpToolRegistryProvider.streamableHttp {
            url = requireNotNull(server.url)
            name = "mulehang-agent"
            version = "1"
        }
    }
}

/** 统一以不带泛型的安全包装调用远程工具，底层类型仍由原 descriptor 和 TypeToken 保留。 */
@Suppress("UNCHECKED_CAST")
private fun ToolBase<*, *>.asUntypedTool(): ToolBase<Any?, Any?> = this as ToolBase<Any?, Any?>

/**
 * 所有 MCP 工具视为外部危险操作，必须经过与桌面工具相同的 permission preset 和审批桥。
 */
private class ApprovalGatedMcpTool(
    private val delegate: ToolBase<Any?, Any?>,
    private val server: AgentRuntimeMcpServer,
    private val permissionPreset: PermissionPreset,
    private val interactionBridge: DesktopToolInteractionBridge,
) : ToolBase<Any?, Any?>(
    argsType = delegate.argsType,
    resultType = delegate.resultType,
    descriptor = delegate.descriptor,
    metadata = delegate.metadata,
) {
    /** 在调用远程 MCP 前执行执行型权限检查和显式审批。 */
    override suspend fun execute(args: Any?, metadata: ToolCallMetadata): Any? {
        ensureApproved()
        return delegate.executeUnsafe(args, metadata)
    }

    /** 远程工具无法安全静态分类为只读，因此默认以危险外部调用请求确认。 */
    private suspend fun ensureApproved() {
        check(!DesktopToolPolicy.isExecuteDenied(permissionPreset)) {
            "当前 permission preset=$permissionPreset，禁止调用 MCP 工具。"
        }
        val request = ApprovalRequest(
            requestId = UUID.randomUUID().toString(),
            toolName = name,
            summary = "调用 MCP 服务 ${server.packageId}/${server.id} 的工具 '$name'。",
            payloadPreview = "transport=${server.transport}",
            risk = ToolRisk.DANGEROUS,
        )
        if (
            DesktopToolPolicy.canAutoApproveExecute(permissionPreset) ||
            interactionBridge.isApprovalAutoApproved(request)
        ) {
            return
        }
        check(interactionBridge.requestApproval(request)) { "用户拒绝调用 MCP 工具 '$name'。" }
    }
}
