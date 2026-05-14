package com.agent.runtime.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 表示 runtime 通过 SSE 发给 client 的最小事件载荷。
 */
@Serializable
data class RuntimeSseEvent(
    val event: String,
    val sessionId: String,
    val requestId: String,
    val channel: String? = null,
    val message: String? = null,
    val delta: String? = null,
    val output: JsonElement? = null,
    val failureKind: String? = null,
    val providerLabel: String? = null,
    val modelLabel: String? = null,
    val reasoningEffort: String? = null,
)
