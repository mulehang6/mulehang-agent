package com.agent.runtime.cli

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * 负责为 CLI 和 runtime 之间的本地 `stdio` 协议提供统一 JSON 编解码配置。
 */
val RuntimeCliJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

/**
 * 表示 CLI 发往 runtime 的最小入站消息契约。
 */
@Serializable
sealed interface RuntimeCliInboundMessage

/**
 * 表示 CLI 触发一次 agent 运行的最小请求。
 */
@Serializable
@SerialName("run")
data class RuntimeCliRunRequest(
    val sessionId: String? = null,
    val prompt: String,
    val provider: RuntimeCliProviderBinding? = null,
) : RuntimeCliInboundMessage

/**
 * 表示 CLI 通过协议传入的 provider binding 信息。
 */
@Serializable
data class RuntimeCliProviderBinding(
    val providerId: String,
    val providerType: String,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
)

/**
 * 表示 runtime 发回 CLI 的最小出站消息契约。
 */
@Serializable
sealed interface RuntimeCliOutboundMessage

/**
 * 表示 runtime 当前状态变化的结构化消息。
 */
@Serializable
@SerialName("status")
data class RuntimeCliStatusMessage(
    val status: String,
    val sessionId: String? = null,
    val requestId: String? = null,
    val mode: String? = null,
) : RuntimeCliOutboundMessage

/**
 * 表示 runtime 事件的协议载荷。
 */
@Serializable
data class RuntimeCliEventPayload(
    val message: String,
    val channel: String? = null,
    val delta: String? = null,
    val payload: JsonElement? = null,
)

/**
 * 表示 runtime 推送给 CLI 的流式事件消息。
 */
@Serializable
@SerialName("event")
data class RuntimeCliEventMessage(
    val sessionId: String,
    val requestId: String,
    val event: RuntimeCliEventPayload,
) : RuntimeCliOutboundMessage

/**
 * 表示 runtime 成功完成一次请求后的最终结果消息。
 */
@Serializable
@SerialName("result")
data class RuntimeCliResultMessage(
    val sessionId: String,
    val requestId: String,
    val output: JsonElement? = null,
    val mode: String,
) : RuntimeCliOutboundMessage

/**
 * 表示 runtime 通过协议返回给 CLI 的结构化失败消息。
 */
@Serializable
@SerialName("failure")
data class RuntimeCliFailureMessage(
    val sessionId: String? = null,
    val requestId: String? = null,
    val kind: String,
    val message: String,
    val details: RuntimeCliFailureDetails? = null,
) : RuntimeCliOutboundMessage

/**
 * 表示 runtime 返回给 CLI 的失败诊断摘要。
 */
@Serializable
data class RuntimeCliFailureDetails(
    val source: String? = null,
    val providerId: String? = null,
    val providerType: String? = null,
    val baseUrl: String? = null,
    val modelId: String? = null,
    val apiKeyPresent: Boolean? = null,
)
