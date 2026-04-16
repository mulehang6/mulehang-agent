package com.agent.capability

import com.agent.runtime.CapabilityRequest
import com.agent.runtime.RuntimeResult

/**
 * 表示 direct HTTP internal API 的统一适配器。
 */
class HttpCapabilityAdapter(
    id: String,
    private val handler: suspend (CapabilityRequest) -> RuntimeResult,
) : CapabilityAdapter {
    override val descriptor: CapabilityDescriptor = CapabilityDescriptor(id = id, kind = "http")

    /**
     * 执行一次 HTTP 能力调用。
     */
    override suspend fun execute(request: CapabilityRequest): RuntimeResult = handler(request)
}
