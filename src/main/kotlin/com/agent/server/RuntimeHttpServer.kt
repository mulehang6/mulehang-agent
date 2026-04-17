package com.agent.server

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

/**
 * 提供最小可启动的本地 runtime HTTP 宿主入口。
 */
fun main() {
    embeddedServer(
        factory = CIO,
        host = DEFAULT_HOST,
        port = DEFAULT_PORT,
        module = Application::runtimeHttpModule,
    ).start(wait = true)
}

private const val DEFAULT_HOST = "127.0.0.1"
private const val DEFAULT_PORT = 8080
