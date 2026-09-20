package com.agent.shared.agent.resource

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.fromProcess
import ai.koog.agents.mcp.metadata.McpServerInfo
import com.agent.shared.agent.api.AgentRuntimeMcpServer
import com.agent.shared.agent.api.AgentRuntimeMcpTransport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.headers
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** MCP 服务在应用级连接生命周期中的公开状态。 */
enum class McpConnectionPhase {
    CONNECTING,
    RUNNING,
    FAILED,
}

/** 不含参数或认证信息的 MCP 工具摘要。 */
data class McpToolSummary(
    val name: String,
    val description: String,
)

/** 设置页可观察的一条 MCP 连接状态。 */
data class McpServerConnectionStatus(
    val serverId: String,
    val packageId: String,
    val phase: McpConnectionPhase,
    val tools: List<McpToolSummary> = emptyList(),
    val error: String? = null,
)

/**
 * 持有应用级 MCP 连接及其原始工具注册表。
 *
 * 重载先完整构造新一代连接，再原子替换并关闭旧一代；取消或整体失败时继续保留旧运行时。
 */
class McpConnectionManager(
    private val connectionInitializationTimeoutMillis: Long = MCP_CONNECTION_INITIALIZATION_TIMEOUT_MILLIS,
) : AutoCloseable {
    private val reloadMutex = Mutex()
    private val mutableStatuses = MutableStateFlow<List<McpServerConnectionStatus>>(emptyList())
    private var generation = McpConnectionGeneration.EMPTY

    /** 当前服务连接与工具发现结果，不包含 Header 值。 */
    val statuses: StateFlow<List<McpServerConnectionStatus>> = mutableStatuses.asStateFlow()

    /**
     * 为一份资源快照建立全部启用服务；单服务失败只发布该服务失败状态，不阻止其余服务。
     */
    suspend fun reload(servers: List<AgentRuntimeMcpServer>): Boolean = reloadMutex.withLock {
        val normalizedServers = servers.toList()
        mutableStatuses.value = normalizedServers.map { server -> server.status(McpConnectionPhase.CONNECTING) }
        val newConnections = mutableListOf<ActiveMcpConnection>()
        val newStatuses = mutableListOf<McpServerConnectionStatus>()
        try {
            normalizedServers.forEach { server ->
                runCatching {
                    withTimeoutOrNull(connectionInitializationTimeoutMillis.milliseconds) {
                        connectMcpServer(server)
                    } ?: throw McpConnectionTimeoutException()
                }
                    .onSuccess { connection ->
                        newConnections += connection
                        newStatuses += connection.status()
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        newStatuses += server.status(
                            phase = McpConnectionPhase.FAILED,
                            error = mcpConnectionFailureMessage(error),
                        )
                    }
                mutableStatuses.value = newStatuses + normalizedServers
                    .drop(newStatuses.size)
                    .map { server -> server.status(McpConnectionPhase.CONNECTING) }
            }
        } catch (error: Throwable) {
            newConnections.forEach(ActiveMcpConnection::close)
            mutableStatuses.value = generation.statuses
            if (error is CancellationException) throw error
            return@withLock false
        }
        val previous = generation
        generation = McpConnectionGeneration(
            servers = normalizedServers,
            connections = newConnections.toList(),
            statuses = newStatuses.toList(),
        )
        mutableStatuses.value = generation.statuses
        previous.close()
        true
    }

    /** 返回与指定声明一致的已连接注册表；不一致时先安全重载。 */
    internal suspend fun registriesFor(
        servers: List<AgentRuntimeMcpServer>,
    ): McpRegistrySnapshot {
        if (generation.servers != servers) reload(servers)
        val current = generation
        return McpRegistrySnapshot(
            connections = current.connections.map { connection -> connection.server to connection.registry },
            diagnostics = current.statuses.mapNotNull { status ->
                status.error?.let { error -> McpToolRegistryDiagnostic(status.serverId, error) }
            },
        )
    }

    /** 应用退出时关闭所有进程、传输和自定义 HTTP Client。 */
    override fun close() {
        generation.close()
        generation = McpConnectionGeneration.EMPTY
        mutableStatuses.value = emptyList()
    }
}

/** Bridge 消费的一次稳定连接视图。 */
internal data class McpRegistrySnapshot(
    val connections: List<Pair<AgentRuntimeMcpServer, ToolRegistry>>,
    val diagnostics: List<McpToolRegistryDiagnostic>,
)

/** 一代不可变 MCP 连接集合。 */
private data class McpConnectionGeneration(
    val servers: List<AgentRuntimeMcpServer>,
    val connections: List<ActiveMcpConnection>,
    val statuses: List<McpServerConnectionStatus>,
) : AutoCloseable {
    /** 关闭此代所有连接拥有的资源。 */
    override fun close() {
        connections.forEach(ActiveMcpConnection::close)
    }

    companion object {
        /** 尚未加载 MCP 时的稳定空代。 */
        val EMPTY = McpConnectionGeneration(emptyList(), emptyList(), emptyList())
    }
}

/** 单个已建立的服务连接及其所有权资源。 */
private data class ActiveMcpConnection(
    val server: AgentRuntimeMcpServer,
    val registry: ToolRegistry,
    val process: Process? = null,
    val httpClient: HttpClient? = null,
) : AutoCloseable {
    /** 生成不暴露请求参数或 Header 值的运行状态。 */
    fun status(): McpServerConnectionStatus = server.status(
        phase = McpConnectionPhase.RUNNING,
        tools = registry.tools.map { tool ->
            McpToolSummary(tool.name, tool.descriptor.description)
        },
    )

    /** 结束 stdio 子进程并关闭远程传输使用的客户端。 */
    override fun close() {
        process?.let { child -> if (child.isAlive) child.destroy() }
        httpClient?.close()
    }
}

/** 按声明 transport 建立一个拥有明确资源所有权的连接。 */
private suspend fun connectMcpServer(server: AgentRuntimeMcpServer): ActiveMcpConnection = when (server.transport) {
    AgentRuntimeMcpTransport.STDIO -> {
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(server.command)
                .apply { environment().putAll(server.environment) }
                .start()
        }
        try {
            ActiveMcpConnection(server, McpToolRegistryProvider.fromProcess(process), process = process)
        } catch (error: Throwable) {
            process.destroyForcibly()
            throw error
        }
    }

    AgentRuntimeMcpTransport.SSE -> {
        val client = remoteMcpHttpClient(server.headers)
        try {
            val url = requireNotNull(server.url)
            ActiveMcpConnection(
                server = server,
                registry = McpToolRegistryProvider.fromTransport(
                    transport = SseClientTransport(client = client, urlString = url),
                    serverInfo = McpServerInfo(url = url),
                    name = "mulehang-agent",
                    version = "1",
                ),
                httpClient = client,
            )
        } catch (error: Throwable) {
            client.close()
            throw error
        }
    }

    AgentRuntimeMcpTransport.STREAMABLE_HTTP -> {
        val client = remoteMcpHttpClient(server.headers)
        try {
            ActiveMcpConnection(
                server = server,
                registry = McpToolRegistryProvider.streamableHttp {
                    url = requireNotNull(server.url)
                    name = "mulehang-agent"
                    version = "1"
                    httpClient = client
                },
                httpClient = client,
            )
        } catch (error: Throwable) {
            client.close()
            throw error
        }
    }
}

/** 创建为单个远程服务附加受控 Headers 的客户端。 */
internal fun remoteMcpHttpClient(configuredHeaders: Map<String, String>): HttpClient = HttpClient {
    install(SSE)
    install(HttpTimeout) {
        connectTimeoutMillis = MCP_CONNECT_TIMEOUT_MILLIS
        requestTimeoutMillis = MCP_REQUEST_TIMEOUT_MILLIS
        socketTimeoutMillis = MCP_SOCKET_TIMEOUT_MILLIS
    }
    defaultRequest {
        headers {
            configuredHeaders.forEach { (name, value) -> append(name, value) }
        }
    }
}

/** 连接准备超时，单个服务失败但不应取消整轮重载。 */
private class McpConnectionTimeoutException : IllegalStateException("MCP 连接准备超时。")

/** 生成不泄露远程地址或认证信息的连接诊断。 */
private fun mcpConnectionFailureMessage(error: Throwable): String = when (error) {
    is McpConnectionTimeoutException -> "MCP 连接准备超时。"
    else -> "MCP 连接失败：${error::class.simpleName ?: "未知错误"}。"
}

private const val MCP_CONNECTION_INITIALIZATION_TIMEOUT_MILLIS = 30_000L
private const val MCP_CONNECT_TIMEOUT_MILLIS = 10_000L
private const val MCP_REQUEST_TIMEOUT_MILLIS = 30_000L
private const val MCP_SOCKET_TIMEOUT_MILLIS = 30_000L

/** 从声明构造不含敏感内容的状态。 */
private fun AgentRuntimeMcpServer.status(
    phase: McpConnectionPhase,
    tools: List<McpToolSummary> = emptyList(),
    error: String? = null,
): McpServerConnectionStatus = McpServerConnectionStatus(
    serverId = id,
    packageId = packageId,
    phase = phase,
    tools = tools,
    error = error,
)
