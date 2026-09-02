package com.agent.shared.agent.api

/**
 * 一次 Agent 运行固定使用的资源化上下文。
 *
 * JVM 资源加载器会在运行开始前生成该值；运行中即使用户手动重载资源，也不会修改已经启动
 * 的图，从而保证工具、指令和可用 Skill 的语义可重放。
 */
data class AgentRuntimeResources(
    val version: Long = 0L,
    val systemPromptAppendix: String = "",
    val mcpServers: List<AgentRuntimeMcpServer> = emptyList(),
)

/** 运行快照中可安全携带的 MCP 传输类型。 */
enum class AgentRuntimeMcpTransport {
    STDIO,
    SSE,
    STREAMABLE_HTTP,
}

/**
 * 由受控扩展声明、并固定到单轮运行的 MCP 服务配置。
 *
 * 环境变量值由用户的本地 settings 提供，日志和 UI 诊断不得回显它们。
 */
data class AgentRuntimeMcpServer(
    val id: String,
    val transport: AgentRuntimeMcpTransport,
    val command: List<String> = emptyList(),
    val url: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val packageId: String,
)
