package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.resource.AgentResourceDiagnostic
import com.agent.shared.agent.resource.AgentResourceDiagnosticSeverity

/** 将 MCP 连接和工具冲突问题留在扩展中心，而非仅短暂显示在时间线。 */
internal fun ChatWindowState.reportMcpResourceDiagnostic(event: AgentStreamEvent) {
    val failure = event as? AgentStreamEvent.ToolCallFailed ?: return
    if (!failure.name.startsWith("MCP:")) return
    val diagnostic = AgentResourceDiagnostic(
        severity = AgentResourceDiagnosticSeverity.WARNING,
        message = "${failure.name.removePrefix("MCP:")}：${failure.reason}",
    )
    if (diagnostic !in runtimeResourceDiagnostics) runtimeResourceDiagnostics += diagnostic
}
