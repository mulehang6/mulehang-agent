package com.agent.capability

import com.agent.runtime.CapabilityRequest
import com.agent.runtime.RuntimeResult

/**
 * 表示 local/custom tool 的统一能力适配器。
 */
class ToolCapabilityAdapter(
    id: String,
    private val handler: suspend (CapabilityRequest) -> RuntimeResult,
) : CapabilityAdapter {
    override val descriptor: CapabilityDescriptor = CapabilityDescriptor(id = id, kind = "tool")

    /**
     * 执行一次 tool 能力调用。
     */
    override suspend fun execute(request: CapabilityRequest): RuntimeResult = handler(request)
}
