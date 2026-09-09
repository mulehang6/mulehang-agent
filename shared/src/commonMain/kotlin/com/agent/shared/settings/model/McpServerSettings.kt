package com.agent.shared.settings.model

import kotlinx.serialization.Serializable

/** 用户可在 settings.json 中直接声明的 MCP 连接传输。 */
@Serializable
enum class McpServerTransport {
    STDIO,
    SSE,
    STREAMABLE_HTTP,
}

/**
 * 单个直接 MCP 服务设置。
 *
 * stdio 使用 [command] 的首项作为可执行文件、其余项作为参数；SSE 与 streamable HTTP 使用 [url]。
 * [environment] 仅注入启动的 stdio 进程，展示层不得回显其值。
 */
@Serializable
data class McpServerSettings(
    val id: String,
    val transport: McpServerTransport,
    val command: List<String> = emptyList(),
    val url: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)
