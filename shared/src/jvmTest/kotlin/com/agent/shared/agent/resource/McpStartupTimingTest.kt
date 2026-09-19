package com.agent.shared.agent.resource

import com.agent.shared.agent.api.AgentRuntimeMcpServer
import com.agent.shared.agent.api.AgentRuntimeMcpTransport
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.measureTime

/** 使用本机 JDK 启动最小 stdio MCP 服务，测量真实连接建立和连接复用。 */
class McpStartupTimingTest {
    /** 第二轮复用连接，不重复初始化或工具发现，也不能跳过第一次工具准备。 */
    @Test
    fun comparesColdAndConnectedPreparation() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("mcp-timing")
        val source = directory.resolve("Fixture.java")
        val requests = directory.resolve("requests.txt")
        Files.writeString(source, MCP_FIXTURE_SOURCE)
        val server = AgentRuntimeMcpServer(
            id = "local-timing", transport = AgentRuntimeMcpTransport.STDIO, packageId = "verification",
            command = listOf(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(), source.toString(), requests.toString()),
        )
        McpConnectionManager().use { manager ->
            val cold = measureTime {
                withTimeout(20_000) { assertEquals(1, manager.registriesFor(listOf(server)).connections.size) }
            }
            val warm = measureTime {
                assertEquals(1, manager.registriesFor(listOf(server)).connections.size)
            }
            val calls = Files.readAllLines(requests)
            assertEquals(1, calls.count { it.contains("initialize") && !it.contains("notifications") })
            assertEquals(1, calls.count { it.contains("tools/list") })
            val output = Path.of("build/verification")
            Files.createDirectories(output)
            Files.writeString(output.resolve("mcp-timing.txt"), "cold_ms=${cold.inWholeMilliseconds}\nconnected_ms=${warm.inWholeMilliseconds}\ninitialize_count=1\ntools_list_count=1\n")
        }
    }
}

/** 仅回答初始化与工具列表，所有输入输出均为测试数据。 */
private val MCP_FIXTURE_SOURCE = """
import java.io.*;
import java.nio.file.*;
import java.util.regex.*;
class Fixture {
    public static void main(String[] args) throws Exception {
        var input = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = input.readLine()) != null) {
            Files.writeString(Path.of(args[0]), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            var id = Pattern.compile("\"id\"\\s*:\\s*(\"[^\"]*\"|[0-9]+)").matcher(line);
            if (!id.find()) continue;
            String result = "{}";
            if (line.contains("\"initialize\"")) {
                var version = Pattern.compile("\"protocolVersion\"\\s*:\\s*\"([^\"]+)\"").matcher(line);
                version.find();
                result = "{\"protocolVersion\":\"" + version.group(1) + "\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"fixture\",\"version\":\"1\"}}";
            } else if (line.contains("tools/list")) result = "{\"tools\":[]}";
            System.out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id.group(1) + ",\"result\":" + result + "}");
            System.out.flush();
        }
    }
}
""".trimIndent()
