package com.agent.runtime.capability

import com.agent.runtime.core.CapabilityRequest
import com.agent.runtime.core.RuntimeInfoEvent
import com.agent.runtime.core.RuntimeResult
import com.agent.runtime.core.RuntimeSuccess

/**
 * 提供 capability 测试工厂方法复用的最小 runtime 结果。
 */
object RuntimeResultAdapter {

    /**
     * 返回 echo tool 的最小成功结果。
     */
    fun echo(request: CapabilityRequest): RuntimeResult = RuntimeSuccess(
        events = listOf(RuntimeInfoEvent(message = "tool:${request.capabilityId}")),
    )

    /**
     * 返回 MCP adapter 的最小成功结果。
     */
    fun mcp(): RuntimeResult = RuntimeSuccess(
        events = listOf(RuntimeInfoEvent(message = "mcp")),
    )

    /**
     * 返回 HTTP adapter 的最小成功结果。
     */
    fun http(): RuntimeResult = RuntimeSuccess(
        events = listOf(RuntimeInfoEvent(message = "http")),
    )
}
