package com.agent.runtime.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer

/**
 * 提供最小可启动的本地 runtime HTTP 宿主入口。
 */
fun main() {
    val configuration = resolveRuntimeHttpServerConfiguration()
    embeddedServer(
        factory = CIO,
        host = configuration.host,
        port = configuration.port,
        module = {
            runtimeHttpModule(
                metadata = configuration.metadata,
                auth = configuration.auth,
            )
        },
    ).start(wait = true)
}
