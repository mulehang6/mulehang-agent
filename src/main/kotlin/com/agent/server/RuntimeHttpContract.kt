package com.agent.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 表示统一的 HTTP 结果包装，使用 code/message/data 传递宿主接口的稳定语义。
 */
@Serializable
data class Result<T>(
    val code: Int,
    val message: String,
    val data: T,
) {

    companion object {

        /**
         * 构造成功结果，code 固定为 1。
         */
        fun <T> success(
            data: T,
            message: String = "success",
        ): Result<T> = Result(
            code = 1,
            message = message,
            data = data,
        )

        /**
         * 构造失败结果，code 固定为 0。
         */
        fun <T> fail(
            message: String,
            data: T,
        ): Result<T> = Result(
            code = 0,
            message = message,
            data = data,
        )
    }
}

/**
 * 表示 HTTP 宿主的最小存活检查载荷，只说明 HTTP 进程可响应，不代表 provider 或 runtime 可用。
 */
@Serializable
data class HealthPayload(
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
    val sessionId: String? = null,
    val prompt: String,
    val provider: ProviderBindingHttpRequest,
)

/**
 * 表示一次 runtime 事件载荷；失败事件通过 failureKind/failureMessage 补充结构化信息。
 */
@Serializable
data class RuntimeEventPayload(
    val message: String,
    val payload: JsonElement? = null,
    val failureKind: String? = null,// 失败时传入
    val failureMessage: String? = null,// 失败时传入
)


/**
 * 表示 `/runtime/run` 的最小结构化载荷。
 */
@Serializable
data class RuntimeRunPayload(
    val sessionId: String? = null,
    val requestId: String,
    val events: List<RuntimeEventPayload> = emptyList(),
    val output: JsonElement? = null,
)

/**
 * 返回运行结果中的结构化失败事件；成功结果返回 null。
 */
internal fun RuntimeRunPayload.failureEvent(): RuntimeEventPayload? =
    events.lastOrNull { it.failureKind != null || it.failureMessage != null }

/**
 * 表示 HTTP 宿主对 runtime 执行链暴露的最小服务契约。
 */
interface RuntimeHttpService {

    /**
     * 执行一次 HTTP runtime 请求并返回结构化结果。
     */
    suspend fun run(request: RuntimeRunHttpRequest): Result<RuntimeRunPayload>
}
