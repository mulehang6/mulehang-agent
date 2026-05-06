package com.agent.runtime.server

/**
 * 表示 runtime HTTP server 启动时使用的宿主配置。
 */
data class RuntimeHttpServerConfiguration(
    val host: String,
    val port: Int,
    val metadata: RuntimeServerMetadata,
    val auth: RuntimeServerAuth,
)

/**
 * 从环境变量解析 runtime HTTP server 的宿主配置。
 */
fun resolveRuntimeHttpServerConfiguration(
    environment: Map<String, String> = System.getenv(),
): RuntimeHttpServerConfiguration {
    val host = environment[RUNTIME_HOST_ENV]?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_HOST
    val port = environment[RUNTIME_PORT_ENV]?.trim()?.toIntOrNull() ?: DEFAULT_PORT
    val token = environment[RUNTIME_TOKEN_ENV]?.trim().takeUnless { it.isNullOrEmpty() }
    val authMode = if (token == null) "disabled" else "token"

    return RuntimeHttpServerConfiguration(
        host = host,
        port = port,
        metadata = RuntimeServerMetadata(
            service = "mulehang-agent",
            protocolVersion = "2026-05-06",
            serverVersion = environment[RUNTIME_SERVER_VERSION_ENV]?.trim().takeUnless { it.isNullOrEmpty() } ?: "dev",
            authMode = authMode,
        ),
        auth = if (token == null) RuntimeServerAuth.disabledForTests() else RuntimeServerAuth.required(token),
    )
}

private const val DEFAULT_HOST = "127.0.0.1"
private const val DEFAULT_PORT = 8080
private const val RUNTIME_HOST_ENV = "MULEHANG_RUNTIME_HOST"
private const val RUNTIME_PORT_ENV = "MULEHANG_RUNTIME_PORT"
private const val RUNTIME_TOKEN_ENV = "MULEHANG_RUNTIME_TOKEN"
private const val RUNTIME_SERVER_VERSION_ENV = "MULEHANG_RUNTIME_SERVER_VERSION"
