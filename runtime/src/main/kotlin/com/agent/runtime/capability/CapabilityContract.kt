package com.agent.runtime.capability

import com.agent.runtime.core.CapabilityRequest
import com.agent.runtime.core.RuntimeResult

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
 * 表示工具或能力的风险等级。
 */
enum class ToolRiskLevel {
    LOW,
    MID,
    HIGH,
}

/**
 * 表示对外暴露的能力描述信息。
 */
data class CapabilityDescriptor(
    val id: String,
    val kind: String,
    val riskLevel: ToolRiskLevel,
)

/**
 * 表示可桥接到 Koog 的最小能力描述。
 */
data class KoogCapabilityDescriptor(
    val id: String,
    val title: String,
    val description: String,
)
