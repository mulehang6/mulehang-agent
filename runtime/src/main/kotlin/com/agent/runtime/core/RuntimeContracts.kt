package com.agent.runtime.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 表示一次 runtime 会话的稳定标识。
 */
data class RuntimeSession(
    val id: String,
)

/**
 * 表示一次请求在 runtime 内流转时携带的上下文信息。
 */
data class RuntimeRequestContext(
    val sessionId: String,
    val requestId: String,
    val attributes: Map<String, String> = emptyMap(),
)

/**
 * 表示 runtime 交给能力层处理的统一请求契约。
 */
sealed interface CapabilityRequest {
    val capabilityId: String
    val payload: JsonElement?
}

/**
 * 表示默认的 capability 请求实现。
 */
data class RuntimeCapabilityRequest(
    override val capabilityId: String,
    override val payload: JsonElement? = null,
) : CapabilityRequest

/**
 * 表示 runtime 交给 agent 执行的一次统一运行请求。
 */
data class RuntimeAgentRunRequest(
    val prompt: String,
    override val capabilityId: String = "agent.run",
    override val payload: JsonElement? = null,
) : CapabilityRequest

/**
 * 表示 runtime 在处理过程中产生的统一事件契约。
 */
sealed interface RuntimeEvent {
    val message: String
    val channel: String?
    val delta: String?
    val payload: JsonElement?
}

/**
 * 表示一条通用的信息事件。
 */
data class RuntimeInfoEvent(
    override val message: String,
    override val channel: String? = null,
    override val delta: String? = null,
    override val payload: JsonElement? = null,
) : RuntimeEvent

/**
 * 表示一条可被客户端归并展示的工具调用生命周期载荷。
 */
@Serializable
data class RuntimeToolCallPayload(
    val toolCallId: String,
    val toolName: String,
    val status: String,
    val input: JsonElement? = null,
    val output: JsonElement? = null,
    val error: String? = null,
)

/**
 * 表示 runtime 失败原因的统一契约。
 */
sealed interface RuntimeFailure {
    val message: String
    val cause: Throwable?
}

/**
 * 表示默认的 runtime 错误实现。
 */
data class RuntimeError(
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeFailure

/**
 * 表示 provider 解析阶段的结构化失败。
 */
data class RuntimeProviderResolutionFailure(
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeFailure

/**
 * 表示 capability 桥接阶段的结构化失败。
 */
data class RuntimeCapabilityBridgeFailure(
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeFailure

/**
 * 表示 agent 执行阶段的结构化失败。
 */
data class RuntimeAgentExecutionFailure(
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeFailure

/**
 * 表示 runtime 请求返回值的统一契约。
 */
sealed interface RuntimeResult

/**
 * 表示请求成功完成后的标准结果。
 */
data class RuntimeSuccess(
    val events: List<RuntimeEvent> = emptyList(),
    val output: JsonElement? = null,
) : RuntimeResult

/**
 * 表示请求失败后的标准结果。
 */
data class RuntimeFailed(
    val failure: RuntimeFailure,
    val events: List<RuntimeEvent> = emptyList(),
) : RuntimeResult
