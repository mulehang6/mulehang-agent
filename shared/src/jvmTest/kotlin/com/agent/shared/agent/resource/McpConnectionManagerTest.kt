package com.agent.shared.agent.resource

import ai.koog.agents.core.tools.ToolRegistry
import com.agent.shared.agent.api.AgentRuntimeMcpServer
import com.agent.shared.agent.api.AgentRuntimeMcpTransport
import com.agent.shared.tool.interaction.RejectingDesktopToolInteractionBridge
import com.agent.shared.tool.model.PermissionPreset
import com.sun.net.httpserver.HttpServer
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/** 应用级 MCP 连接管理器的隔离和 Header 注入测试。 */
class McpConnectionManagerTest {
    /** 自定义 Header 应进入远程请求，且客户端关闭由管理器所有权负责。 */
    @Test
    fun `should inject configured headers into remote requests`() = runBlocking {
        val observed = AtomicReference<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/mcp") { exchange ->
                observed.set(exchange.requestHeaders.getFirst("Authorization"))
                val response = "ok".toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            start()
        }
        val client = remoteMcpHttpClient(mapOf("Authorization" to "Bearer placeholder"))
        try {
            val body: String = client.get("http://127.0.0.1:${server.address.port}/mcp").body()
            assertEquals("ok", body)
            assertEquals("Bearer placeholder", observed.get())
        } finally {
            client.close()
            server.stop(0)
        }
    }

    /** 单个服务连接失败不会终止同批其他服务的状态发布。 */
    @Test
    fun `should isolate connection failures by server`() = runBlocking {
        McpConnectionManager().use { manager ->
            assertTrue(
                manager.reload(
                    listOf(
                        invalidStdio("first"),
                        invalidStdio("second"),
                    ),
                ),
            )
            assertEquals(listOf("first", "second"), manager.statuses.value.map(McpServerConnectionStatus::serverId))
            assertTrue(manager.statuses.value.all { it.phase == McpConnectionPhase.FAILED })
        }
    }

    /** 远程 MCP 初始化不返回响应时，重载必须在有界时间内发布失败状态。 */
    @Test
    fun `should bound remote MCP initialization`() = runBlocking {
        val started = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/sse") { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                started.countDown()
                try {
                    Thread.sleep(5_000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    exchange.close()
                }
            }
            start()
        }
        try {
            McpConnectionManager(connectionInitializationTimeoutMillis = 100).use { manager ->
                assertTrue(
                    withTimeout(2.seconds) {
                        manager.reload(
                            listOf(
                                AgentRuntimeMcpServer(
                                    id = "slow",
                                    transport = AgentRuntimeMcpTransport.SSE,
                                    url = "http://127.0.0.1:${server.address.port}/sse",
                                    packageId = "test",
                                ),
                            ),
                        )
                    },
                )
                assertTrue(started.await(1, TimeUnit.SECONDS))
                assertEquals(McpConnectionPhase.FAILED, manager.statuses.value.single().phase)
                assertTrue(manager.statuses.value.single().error.orEmpty().contains("超时"))
            }
        } finally {
            server.stop(0)
        }
    }

    /** MCP 工具桥收到空服务列表时必须先让连接管理器释放旧一代连接。 */
    @Test
    fun `should close previous generation when bridge receives no servers`() = runBlocking {
        McpConnectionManager().use { manager ->
            manager.reload(listOf(invalidStdio("old")))
            McpToolRegistryBridge(manager).create(
                baseRegistry = ToolRegistry.EMPTY,
                servers = emptyList(),
                permissionPreset = PermissionPreset.DEFAULT,
                interactionBridge = RejectingDesktopToolInteractionBridge,
            )

            assertTrue(manager.statuses.value.isEmpty())
        }
    }

    /** 构造必定无法启动的 stdio 声明。 */
    private fun invalidStdio(id: String) = AgentRuntimeMcpServer(
        id = id,
        transport = AgentRuntimeMcpTransport.STDIO,
        command = emptyList(),
        packageId = "settings",
    )
}
