package com.agent.capability

import com.agent.runtime.CapabilityRequest
import com.agent.runtime.RuntimeResult

/**
 * 表示 direct HTTP internal API 的统一适配器。
 */
class HttpCapabilityAdapter(
    id: String,
    val method: String,
    val path: String,
    val description: String,
    private val handler: suspend (CapabilityRequest) -> RuntimeResult,
) : CapabilityAdapter {
    override val descriptor: CapabilityDescriptor = CapabilityDescriptor(id = id, kind = "http")

    /**
     * 执行一次 HTTP 能力调用。
     */
    override suspend fun execute(request: CapabilityRequest): RuntimeResult = handler(request)

    /**
     * 返回供 Koog assembler 消费的最小 HTTP tool 描述。
     */
    fun toKoogDescriptor(): KoogCapabilityDescriptor = KoogCapabilityDescriptor(
        id = descriptor.id,
        title = "$method $path",
        description = description,
    )

    companion object {
        /**
         * 创建一个最小 HTTP GET adapter。
         */
        fun internalGet(
            id: String,
            path: String,
        ): HttpCapabilityAdapter = HttpCapabilityAdapter(
            id = id,
            method = "GET",
            path = path,
            description = "Calls internal HTTP endpoint $path for phase 03 bridging.",
        ) {
            RuntimeResultAdapter.http()
        }
    }
}
