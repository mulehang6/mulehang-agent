package com.agent.capability

import com.agent.runtime.CapabilityRequest
import com.agent.runtime.RuntimeResult

/**
 * 表示 MCP-backed 能力的统一适配器。
 */
class McpCapabilityAdapter(
    id: String,
    private val handler: suspend (CapabilityRequest) -> RuntimeResult,
) : CapabilityAdapter {
    override val descriptor: CapabilityDescriptor = CapabilityDescriptor(id = id, kind = "mcp")

    /**
     * 执行一次 MCP 能力调用。
     */
    override suspend fun execute(request: CapabilityRequest): RuntimeResult = handler(request)
}
