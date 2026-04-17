package com.agent.capability

import com.agent.runtime.CapabilityRequest
import com.agent.runtime.RuntimeResult

/**
 * 表示当前 runtime 可用的一组统一能力适配器。
 */
class CapabilitySet(
    adapters: List<CapabilityAdapter>,
) {
    private val adaptersById: Map<String, CapabilityAdapter> = adapters.associateBy { it.descriptor.id }

    /**
     * 返回当前集合内所有能力描述信息。
     */
    fun descriptors(): List<CapabilityDescriptor> = adaptersById.values.map { it.descriptor }

    /**
     * 返回当前集合中的 tool adapters。
     */
    fun toolAdapters(): List<ToolCapabilityAdapter> = adaptersById.values.filterIsInstance<ToolCapabilityAdapter>()

    /**
     * 返回当前集合中的 MCP adapters。
     */
    fun mcpAdapters(): List<McpCapabilityAdapter> = adaptersById.values.filterIsInstance<McpCapabilityAdapter>()

    /**
     * 返回当前集合中的 HTTP adapters。
     */
    fun httpAdapters(): List<HttpCapabilityAdapter> = adaptersById.values.filterIsInstance<HttpCapabilityAdapter>()

    /**
     * 根据能力标识执行一次统一能力调用。
     */
    suspend fun execute(
        capabilityId: String,
        request: CapabilityRequest,
    ): RuntimeResult {
        val adapter = adaptersById[capabilityId]
            ?: error("No capability adapter registered for '$capabilityId'.")
        return adapter.execute(request)
    }
}
