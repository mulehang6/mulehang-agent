package com.agent.capability

import com.agent.runtime.CapabilityRequest
import com.agent.runtime.RuntimeResult

/**
 * 表示 MCP-backed 能力的统一适配器。
 */
class McpCapabilityAdapter(
    id: String,
    val serverUrl: String,
    private val handler: suspend (CapabilityRequest) -> RuntimeResult,
) : CapabilityAdapter {
    override val descriptor: CapabilityDescriptor = CapabilityDescriptor(id = id, kind = "mcp")

    /**
     * 执行一次 MCP 能力调用。
     */
    override suspend fun execute(request: CapabilityRequest): RuntimeResult = handler(request)

    companion object {
        /**
         * 创建一个基于 SSE 地址的最小 MCP adapter。
         */
        fun sse(
            id: String,
            serverUrl: String,
        ): McpCapabilityAdapter = McpCapabilityAdapter(
            id = id,
            serverUrl = serverUrl,
        ) {
            RuntimeResultAdapter.mcp()
        }
    }
}
