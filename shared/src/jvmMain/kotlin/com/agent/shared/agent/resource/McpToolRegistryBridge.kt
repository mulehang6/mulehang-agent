@file:OptIn(ai.koog.agents.core.tools.annotations.InternalAgentToolsApi::class)

package com.agent.shared.agent.resource

import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.agents.core.tools.ToolRegistry
import com.agent.shared.agent.api.AgentRuntimeMcpServer
import com.agent.shared.agent.hook.AgentHookDecision
import com.agent.shared.agent.hook.AgentHookDispatchRequest
import com.agent.shared.agent.hook.AgentHookDispatcher
import com.agent.shared.agent.hook.NoAgentHookDispatcher
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.PermissionPreset
import com.agent.shared.tool.model.ToolRisk
import com.agent.shared.tool.policy.DesktopToolPolicy
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** MCP 连接和工具冲突在启动一轮 Agent 时产生的可见诊断。 */
data class McpToolRegistryDiagnostic(
    val serverId: String,
    val message: String,
)

/** 一轮 Agent 使用的 MCP 工具注册表与受其管理的 stdio 子进程。 */
class McpToolRegistryLease internal constructor(
    val registry: ToolRegistry,
    val diagnostics: List<McpToolRegistryDiagnostic>,
) : AutoCloseable {
    /** 连接由应用级管理器持有；轮次结束只释放本轮包装视图。 */
    override fun close() = Unit
}

/**
 * 将快照中的受控 MCP 声明连接成 Koog ToolRegistry，并把每个远程工具置于既有审批策略之后。
 */
class McpToolRegistryBridge(
    private val connectionManager: McpConnectionManager = McpConnectionManager(),
) {
    /**
     * 连接所有服务并把其工具附加到 [baseRegistry]。内建桌面工具优先，冲突的 MCP 工具不会
     * 静默加入；连接失败也作为诊断返回，调用方可在时间线展示。
     */
    suspend fun create(
        baseRegistry: ToolRegistry,
        servers: List<AgentRuntimeMcpServer>,
        permissionPreset: PermissionPreset,
        interactionBridge: DesktopToolInteractionBridge,
        hookDispatcher: AgentHookDispatcher = NoAgentHookDispatcher,
        sessionId: String = "",
        workspacePath: String = "",
    ): McpToolRegistryLease {
        val snapshot = connectionManager.registriesFor(servers)
        val diagnostics = snapshot.diagnostics.toMutableList()
        val mergedTools = baseRegistry.tools.toMutableList()
        val names = mergedTools.mapTo(mutableSetOf(), ToolBase<*, *>::name)
        snapshot.connections.forEach { (server, remoteRegistry) ->
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
                    hookDispatcher = hookDispatcher,
                    sessionId = sessionId,
                    workspacePath = workspacePath,
                )
            }
        }
        return McpToolRegistryLease(
            registry = ToolRegistry { tools(mergedTools) },
            diagnostics = diagnostics.toList(),
        )
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
    private val hookDispatcher: AgentHookDispatcher,
    private val sessionId: String,
    private val workspacePath: String,
) : ToolBase<Any?, Any?>(
    argsType = delegate.argsType,
    resultType = delegate.resultType,
    descriptor = delegate.descriptor,
    metadata = delegate.metadata,
) {
    /** 在调用远程 MCP 前执行执行型权限检查和显式审批。 */
    override suspend fun execute(args: Any?, metadata: ToolCallMetadata): Any? {
        when (
            hookDispatcher.dispatch(
                AgentHookDispatchRequest(
                    event = com.agent.shared.settings.model.AgentHookEvent.PRE_TOOL_USE,
                    sessionId = sessionId,
                    workspacePath = workspacePath,
                    matcherValue = name,
                    payload = buildJsonObject { args?.let { value -> put("arguments", value.toString()) } },
                ),
            ).decision
        ) {
            AgentHookDecision.BLOCK -> error("Hook 已阻止 MCP 工具 '$name'。")
            AgentHookDecision.ASK -> ensureApproved(forceManual = true)
            AgentHookDecision.ALLOW,
            AgentHookDecision.CONTINUE,
                -> ensureApproved()
        }
        return delegate.executeUnsafe(args, metadata)
    }

    /** 远程工具无法安全静态分类为只读，因此默认以危险外部调用请求确认。 */
    private suspend fun ensureApproved(forceManual: Boolean = false) {
        var requireManual = forceManual
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
        when (
            hookDispatcher.dispatch(
                AgentHookDispatchRequest(
                    event = com.agent.shared.settings.model.AgentHookEvent.PERMISSION_REQUEST,
                    sessionId = sessionId,
                    workspacePath = workspacePath,
                    matcherValue = name,
                    payload = buildJsonObject {
                        put("tool_name", name)
                        put("summary", request.summary)
                        put("server", "${server.packageId}/${server.id}")
                    },
                ),
            ).decision
        ) {
            AgentHookDecision.ALLOW -> if (!requireManual) return
            AgentHookDecision.BLOCK -> error("Hook 已拒绝 MCP 工具 '$name'。")
            AgentHookDecision.ASK -> requireManual = true
            AgentHookDecision.CONTINUE -> Unit
        }
        if (!requireManual && (
                DesktopToolPolicy.canAutoApproveExecute(permissionPreset) ||
                        interactionBridge.isApprovalAutoApproved(request)
            )
        ) {
            return
        }
        check(interactionBridge.requestApproval(request.copy(forceManual = requireManual))) { "用户拒绝调用 MCP 工具 '$name'。" }
    }
}
