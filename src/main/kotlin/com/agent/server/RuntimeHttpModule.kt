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

/**
 * 安装最小 runtime HTTP 宿主模块。
 */
fun Application.runtimeHttpModule(
    service: RuntimeHttpService = DefaultRuntimeHttpService(),
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
            call.respond(
                HealthHttpResponse(
                    healthy = true,
                    service = "mulehang-agent",
                ),
            )
        }

        post("/runtime/run") {
            val request = call.receive<RuntimeRunHttpRequest>()
            val response = service.run(request)
            call.respond(response.status(), response)
        }
    }
}

/**
 * 把 runtime HTTP 响应映射为最小 HTTP 状态码。
 */
private fun RuntimeRunHttpResponse.status(): HttpStatusCode {
    if (success) {
        return HttpStatusCode.OK
    }

    return when (failure?.kind) {
        "provider" -> HttpStatusCode.BadRequest
        else -> HttpStatusCode.InternalServerError
    }
}
