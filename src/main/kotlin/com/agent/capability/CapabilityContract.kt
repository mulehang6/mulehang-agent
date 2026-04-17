package com.agent.capability

import com.agent.runtime.CapabilityRequest
import com.agent.runtime.RuntimeResult

/**
 * 表示一个可被 runtime 调用的统一能力适配器。
 */
interface CapabilityAdapter {
    val descriptor: CapabilityDescriptor

    /**
     * 执行一次统一能力调用并返回标准 runtime 结果。
     */
    suspend fun execute(request: CapabilityRequest): RuntimeResult
}

/**
 * 表示对外暴露的能力描述信息。
 */
data class CapabilityDescriptor(
    val id: String,
    val kind: String,
)

/**
 * 表示可桥接到 Koog 的最小能力描述。
 */
data class KoogCapabilityDescriptor(
    val id: String,
    val title: String,
    val description: String,
)
