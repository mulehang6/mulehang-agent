package com.agent.capability

import com.agent.runtime.CapabilityRequest
import com.agent.runtime.RuntimeResult

/**
 * 表示 MCP server 当前支持的连接传输方式。
 */
sealed interface McpTransport {
    val description: String

    /**
     * 表示通过本地进程 stdin/stdout 连接 MCP server。
     */
    data class Stdio(
        val command: List<String>,
    ) : McpTransport {
        init {
            require(command.isNotEmpty()) {
                "MCP stdio command must not be empty."
            }
            require(command.all { it.isNotBlank() }) {
                "MCP stdio command entries must not be blank."
            }
        }

        override val description: String = "stdio:${command.joinToString(separator = " ")}"
    }

    /**
     * 表示通过 Streamable HTTP 连接远端 MCP server。
     */
    data class StreamableHttp(
        val url: String,
    ) : McpTransport {
        init {
            require(url.isNotBlank()) {
                "MCP streamable HTTP url must not be blank."
            }
        }

        override val description: String = "streamable-http:$url"
    }
}

/**
 * 表示 MCP-backed 能力的统一适配器。
 */
class McpCapabilityAdapter(
    id: String,
    val transport: McpTransport,
    private val handler: suspend (CapabilityRequest) -> RuntimeResult,
) : CapabilityAdapter {
    override val descriptor: CapabilityDescriptor = CapabilityDescriptor(id = id, kind = "mcp")

    /**
     * 执行一次 MCP 能力调用。
     */
    override suspend fun execute(request: CapabilityRequest): RuntimeResult = handler(request)

    companion object {
        /**
         * 创建一个基于 stdio 本地进程的最小 MCP adapter。
         */
        fun stdio(
            id: String,
            command: List<String>,
        ): McpCapabilityAdapter = McpCapabilityAdapter(
            id = id,
            transport = McpTransport.Stdio(command),
        ) {
            RuntimeResultAdapter.mcp()
        }

        /**
         * 创建一个基于 Streamable HTTP 地址的最小 MCP adapter。
         */
        fun streamableHttp(
            id: String,
            url: String,
        ): McpCapabilityAdapter = McpCapabilityAdapter(
            id = id,
            transport = McpTransport.StreamableHttp(url),
        ) {
            RuntimeResultAdapter.mcp()
        }
    }
}
