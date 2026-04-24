package com.agent.runtime.capability

import com.agent.runtime.core.CapabilityRequest
import com.agent.runtime.core.RuntimeResult

/**
 * 表示 local/custom tool 的统一能力适配器。
 */
class ToolCapabilityAdapter(
    id: String,
    val toolName: String,
    val description: String,
    private val handler: suspend (CapabilityRequest) -> RuntimeResult,
) : CapabilityAdapter {
    override val descriptor: CapabilityDescriptor = CapabilityDescriptor(id = id, kind = "tool")

    /**
     * 执行一次 tool 能力调用。
     */
    override suspend fun execute(request: CapabilityRequest): RuntimeResult = handler(request)

    /**
     * 返回供 Koog assembler 消费的最小工具描述。
     */
    fun toKoogDescriptor(): KoogCapabilityDescriptor = KoogCapabilityDescriptor(
        id = descriptor.id,
        title = toolName,
        description = description,
    )

    companion object {
        /**
         * 创建一个用于测试和最小集成的 echo tool adapter。
         */
        fun echo(id: String): ToolCapabilityAdapter = ToolCapabilityAdapter(
            id = id,
            toolName = "echo",
            description = "Echoes the request payload for phase 03 bridging.",
        ) { request ->
            RuntimeResultAdapter.echo(request)
        }
    }
}
