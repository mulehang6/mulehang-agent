package com.agent.runtime.server

import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val runtimeHttpModuleLogger = LoggerFactory.getLogger("com.agent.runtime.server.RuntimeHttpModule")
private val runtimeHttpModuleJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}
private val runtimeSseJson = Json {
    ignoreUnknownKeys = true
}

/**
 * 安装最小 runtime HTTP 宿主模块。
 */
fun Application.runtimeHttpModule(
    service: RuntimeHttpService = LoggingRuntimeHttpService(DefaultRuntimeHttpService()),
    metadata: RuntimeServerMetadata = RuntimeServerMetadata(
        service = "mulehang-agent",
        protocolVersion = "2026-05-06",
        serverVersion = "dev",
        authMode = "disabled",
    ),
    auth: RuntimeServerAuth = RuntimeServerAuth.disabledForTests(),
) {
    install(ContentNegotiation) {
        json(runtimeHttpModuleJson)
    }

    routing {
        get("/health") {
            runtimeHttpModuleLogger.info("收到存活检查请求：method=GET path=/health scope=http-host-only")
            call.respond(
                Result.success(
                    HealthPayload(
                        healthy = true,
                        service = metadata.service,
                        protocolVersion = metadata.protocolVersion,
                    ),
                ),
            )
            runtimeHttpModuleLogger.info(
                "存活检查响应完成：method=GET path=/health status=200 healthy=true scope=http-host-only providerChecked=false runtimeChecked=false",
            )
        }

        get("/meta") {
            call.respond(Result.success(metadata))
        }

        post("/runtime/run") {
            if (!auth.isAuthorized(call.request.bearerToken())) {
                call.respond(HttpStatusCode.Unauthorized, unauthorizedRunResponse())
                return@post
            }
            val request = call.receive<RuntimeRunHttpRequest>()
            val provider = request.provider
            runtimeHttpModuleLogger.info(
                "收到接口请求：method=POST path=/runtime/run providerId={} providerType={} baseUrl={} modelId={} promptLength={}",
                provider?.providerId ?: "runtime-default",
                provider?.providerType ?: "runtime-default",
                provider?.baseUrl ?: "runtime-default",
                provider?.modelId ?: "runtime-default",
                request.prompt.length,
            )
            val response = service.run(request)
            val status = response.status()
            runtimeHttpModuleLogger.info(
                "接口响应完成：method=POST path=/runtime/run status={} success={} sessionId={} requestId={} failureKind={}",
                status.value,
                response.code == 1,
                response.data.sessionId,
                response.data.requestId,
                response.data.failureEvent()?.failureKind,
            )
            call.respond(status, response)
        }

        post("/runtime/run/stream") {
            if (!auth.isAuthorized(call.request.bearerToken())) {
                call.respond(HttpStatusCode.Unauthorized, unauthorizedRunResponse())
                return@post
            }
            val request = call.receive<RuntimeRunHttpRequest>()
            call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                service.stream(request).collect { event ->
                    write("event: ${event.event}\n")
                    write("data: ${runtimeSseJson.encodeToString(RuntimeSseEvent.serializer(), event)}\n\n")
                    flush()
                }
            }
        }
    }
}

/**
 * 把 runtime HTTP 响应映射为最小 HTTP 状态码。
 */
private fun Result<RuntimeRunPayload>.status(): HttpStatusCode {
    if (code == 1) {
        return HttpStatusCode.OK
    }

    return when (data.failureEvent()?.failureKind) {
        "provider" -> HttpStatusCode.BadRequest
        else -> HttpStatusCode.InternalServerError
    }
}

/**
 * 从 Authorization 头中解析 Bearer token；非 Bearer 认证一律视为未提供。
 */
private fun io.ktor.server.request.ApplicationRequest.bearerToken(): String? =
    headers[HttpHeaders.Authorization]
        ?.removePrefix("Bearer ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * 构造未认证请求的稳定错误体。
 */
private fun unauthorizedRunResponse(): Result<RuntimeRunPayload> = Result.fail(
    message = "Unauthorized.",
    data = RuntimeRunPayload(
        requestId = "unauthorized",
        events = listOf(
            RuntimeEventPayload(
                message = "runtime.run.failed",
                failureKind = "auth",
                failureMessage = "Unauthorized.",
            ),
        ),
    ),
)
