package com.agent.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 表示 HTTP 宿主的最小健康检查响应。
 */
@Serializable
data class HealthHttpResponse(
    val healthy: Boolean,
    val service: String,
)

/**
 * 表示一次通过 HTTP 触发的 provider binding 请求体。
 */
@Serializable
data class ProviderBindingHttpRequest(
    val providerId: String,
    val providerType: String,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
)

/**
 * 表示 `/runtime/run` 的最小请求体。
 */
@Serializable
data class RuntimeRunHttpRequest(
    val prompt: String,
    val provider: ProviderBindingHttpRequest,
)

/**
 * 表示一次 runtime 事件的 HTTP 视图。
 */
@Serializable
data class RuntimeEventHttpResponse(
    val message: String,
    val payload: JsonElement? = null,
)

/**
 * 表示一次 runtime 失败的 HTTP 视图。
 */
@Serializable
data class RuntimeFailureHttpResponse(
    val kind: String,
    val message: String,
)

/**
 * 表示 `/runtime/run` 的最小结构化响应。
 */
@Serializable
data class RuntimeRunHttpResponse(
    val success: Boolean,
    val sessionId: String,
    val requestId: String,
    val events: List<RuntimeEventHttpResponse> = emptyList(),
    val output: JsonElement? = null,
    val failure: RuntimeFailureHttpResponse? = null,
)

/**
 * 表示 HTTP 宿主对 runtime 执行链暴露的最小服务契约。
 */
interface RuntimeHttpService {

    /**
     * 执行一次 HTTP runtime 请求并返回结构化结果。
     */
    suspend fun run(request: RuntimeRunHttpRequest): RuntimeRunHttpResponse
}
