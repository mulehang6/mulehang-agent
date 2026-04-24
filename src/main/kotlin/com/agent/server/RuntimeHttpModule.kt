package com.agent.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val runtimeHttpModuleLogger = LoggerFactory.getLogger("com.agent.server.RuntimeHttpModule")

/**
 * 安装最小 runtime HTTP 宿主模块。
 */
fun Application.runtimeHttpModule(
    service: RuntimeHttpService = LoggingRuntimeHttpService(DefaultRuntimeHttpService()),
) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            },
        )
    }

    routing {
        get("/health") {
            runtimeHttpModuleLogger.info("收到存活检查请求：method=GET path=/health scope=http-host-only")
            call.respond(
                Result.success(
                    HealthPayload(
                        healthy = true,
                        service = "mulehang-agent",
                    ),
                ),
            )
            runtimeHttpModuleLogger.info(
                "存活检查响应完成：method=GET path=/health status=200 healthy=true scope=http-host-only providerChecked=false runtimeChecked=false",
            )
        }

        post("/runtime/run") {
            val request = call.receive<RuntimeRunHttpRequest>()
            runtimeHttpModuleLogger.info(
                "收到接口请求：method=POST path=/runtime/run providerId={} providerType={} baseUrl={} modelId={} promptLength={}",
                request.provider.providerId,
                request.provider.providerType,
                request.provider.baseUrl,
                request.provider.modelId,
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
