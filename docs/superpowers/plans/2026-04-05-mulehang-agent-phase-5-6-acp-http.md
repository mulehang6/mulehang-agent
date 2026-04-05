# Mulehang Agent Phase 5-6 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在统一 runtime 之上交付标准 ACP server 和正式 HTTP API，使 CLI、ACP、HTTP 三个入口共享同一套 session、permission、provider、MCP 生命周期。

**Architecture:** 先补 Ktor 服务器依赖和统一 bootstrap，再实现 runtime 到 ACP 的桥接层，然后把 provider/session/permission/mcp 暴露成 HTTP + SSE 路由，最后用测试宿主验证合同稳定性与构建闭环。

**Tech Stack:** Kotlin/JVM 21, Ktor 3.1.3, Koog ACP 0.7.3, kotlinx.serialization, kotlin.test/JUnit

---

## 文件结构

- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/mulehang/app/AppBootstrap.kt`
- Create: `src/test/kotlin/mulehang/app/AppBootstrapTest.kt`
- Create: `src/main/kotlin/mulehang/acp/AcpSessionBridge.kt`
- Create: `src/main/kotlin/mulehang/acp/AcpAgentSupport.kt`
- Create: `src/test/kotlin/mulehang/acp/AcpSessionBridgeTest.kt`
- Create: `src/main/kotlin/mulehang/mcp/McpConnectionManager.kt`
- Create: `src/main/kotlin/mulehang/http/HttpServer.kt`
- Create: `src/main/kotlin/mulehang/http/routes/ProviderRoutes.kt`
- Create: `src/main/kotlin/mulehang/http/routes/SessionRoutes.kt`
- Create: `src/main/kotlin/mulehang/http/routes/PermissionRoutes.kt`
- Create: `src/main/kotlin/mulehang/http/routes/McpRoutes.kt`
- Create: `src/main/kotlin/mulehang/http/routes/SseRoutes.kt`
- Create: `src/test/kotlin/mulehang/http/HttpRoutesTest.kt`
- Modify: `src/main/kotlin/agent/AgentApp.kt`

### Task 1: 建立统一 bootstrap 与服务器依赖

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/mulehang/app/AppBootstrap.kt`
- Create: `src/test/kotlin/mulehang/app/AppBootstrapTest.kt`

- [ ] **Step 1: 先写一个 bootstrap 装配测试**

```kotlin
package mulehang.app

import kotlin.test.Test
import kotlin.test.assertNotNull

class AppBootstrapTest {
    @Test
    fun `should build shared runtime services for cli acp and http`() {
        val app = AppBootstrap.forTests()

        assertNotNull(app.sessions)
        assertNotNull(app.permissions)
        assertNotNull(app.http)
        assertNotNull(app.acp)
    }
}
```

- [ ] **Step 2: 运行测试并确认 server/bootstrap 层尚未实现**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.app.AppBootstrapTest"
```

Expected: FAIL

- [ ] **Step 3: 添加 Ktor 依赖并实现 bootstrap 容器**

`build.gradle.kts` 增加：

```kotlin
implementation("io.ktor:ktor-server-core-jvm:3.1.3")
implementation("io.ktor:ktor-server-netty-jvm:3.1.3")
implementation("io.ktor:ktor-server-content-negotiation-jvm:3.1.3")
implementation("io.ktor:ktor-server-sse-jvm:3.1.3")
implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.1.3")
testImplementation("io.ktor:ktor-server-test-host-jvm:3.1.3")
```

`AppBootstrap.kt`:

```kotlin
package mulehang.app

import java.nio.file.Files
import mulehang.acp.AcpAgentSupport
import mulehang.http.HttpServer
import mulehang.mcp.McpConnectionManager
import mulehang.permission.PermissionEngine
import mulehang.runtime.event.EventBus
import mulehang.runtime.service.SessionService
import mulehang.runtime.store.SqliteSessionStore

data class AppComponents(
    val sessions: SessionService,
    val permissions: PermissionEngine,
    val mcp: McpConnectionManager,
    val http: HttpServer,
    val acp: AcpAgentSupport
)

object AppBootstrap {
    fun forTests(): AppComponents {
        val store = SqliteSessionStore(Files.createTempFile("mulehang-app", ".db"))
        store.init()
        val bus = EventBus()
        val sessions = SessionService(store, bus)
        val permissions = PermissionEngine()
        val mcp = McpConnectionManager()
        val http = HttpServer(sessions, permissions, mcp, bus)
        val acp = AcpAgentSupport(sessions, bus, permissions)
        return AppComponents(sessions, permissions, mcp, http, acp)
    }
}
```

- [ ] **Step 4: 重新运行 bootstrap 测试**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.app.AppBootstrapTest"
```

Expected: PASS

- [ ] **Step 5: 提交 bootstrap 检查点**

```powershell
git add build.gradle.kts src/main/kotlin/mulehang/app src/test/kotlin/mulehang/app/AppBootstrapTest.kt
git commit -m "feat(app): add bootstrap container"
```

### Task 2: 实现 runtime 到 ACP 的桥接层

**Files:**
- Create: `src/main/kotlin/mulehang/acp/AcpSessionBridge.kt`
- Create: `src/main/kotlin/mulehang/acp/AcpAgentSupport.kt`
- Create: `src/test/kotlin/mulehang/acp/AcpSessionBridgeTest.kt`

- [ ] **Step 1: 先写一个 ACP 顺序性测试，锁定同 session 串行执行**

```kotlin
package mulehang.acp

import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import mulehang.permission.PermissionEngine
import mulehang.runtime.event.EventBus
import mulehang.runtime.service.SessionService
import mulehang.runtime.store.SqliteSessionStore
import kotlin.test.Test
import kotlin.test.assertEquals

class AcpSessionBridgeTest {
    @Test
    fun `should serialize concurrent prompts for the same session`() = runTest {
        val store = SqliteSessionStore(Files.createTempFile("mulehang-acp", ".db"))
        store.init()
        val bus = EventBus()
        val sessions = SessionService(store, bus)
        val row = sessions.createSession("acp")
        val bridge = AcpSessionBridge(sessions, bus, PermissionEngine())

        val seen = mutableListOf<String>()
        awaitAll(
            async { bridge.prompt(row.id, "first") { seen += it.kind } },
            async { bridge.prompt(row.id, "second") { seen += it.kind } }
        )

        assertEquals(listOf("session_started", "session_finished", "session_started", "session_finished"), seen)
    }
}
```

- [ ] **Step 2: 运行测试并确认 ACP bridge 尚未实现**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.acp.AcpSessionBridgeTest"
```

Expected: FAIL

- [ ] **Step 3: 建立桥接事件对象与 `Mutex` 串行执行**

`AcpSessionBridge.kt`:

```kotlin
package mulehang.acp

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mulehang.permission.PermissionEngine
import mulehang.runtime.event.EventBus
import mulehang.runtime.service.SessionService

data class AcpBridgeEvent(
    val kind: String,
    val payload: Map<String, String> = emptyMap()
)

class AcpSessionBridge(
    private val sessions: SessionService,
    private val bus: EventBus,
    private val permissions: PermissionEngine
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun prompt(sessionId: String, input: String, emit: suspend (AcpBridgeEvent) -> Unit) {
        val lock = locks.getOrPut(sessionId) { Mutex() }
        lock.withLock {
            emit(AcpBridgeEvent("session_started", mapOf("sessionId" to sessionId)))
            val scope = CoroutineScope(currentCoroutineContext())
            val forwarder = scope.launch {
                bus.events.collect { event ->
                    emit(AcpBridgeEvent(event.type, mapOf("sessionId" to sessionId)))
                }
            }
            try {
                sessions.openTurn(sessionId, "acp")
                emit(AcpBridgeEvent("message_received", mapOf("text" to input)))
            } finally {
                forwarder.cancel()
                emit(AcpBridgeEvent("session_finished", mapOf("sessionId" to sessionId)))
            }
        }
    }
}
```

- [ ] **Step 4: 用 Koog ACP support 包装 bridge**

`AcpAgentSupport.kt`:

```kotlin
package mulehang.acp

import kotlinx.coroutines.flow.channelFlow
import mulehang.permission.PermissionEngine
import mulehang.runtime.event.EventBus
import mulehang.runtime.service.SessionService

class AcpAgentSupport(
    private val sessions: SessionService,
    private val bus: EventBus,
    private val permissions: PermissionEngine
) {
    private val bridge = AcpSessionBridge(sessions, bus, permissions)

    fun stream(sessionId: String, input: String) = channelFlow {
        bridge.prompt(sessionId, input) { send(it) }
    }
}
```

- [ ] **Step 5: 重新运行 ACP 测试并提交**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.acp.AcpSessionBridgeTest"
```

Expected: PASS

```powershell
git add src/main/kotlin/mulehang/acp src/test/kotlin/mulehang/acp
git commit -m "feat(acp): add runtime bridge"
```

### Task 3: 建立 MCP 连接管理与 HTTP 路由

**Files:**
- Create: `src/main/kotlin/mulehang/mcp/McpConnectionManager.kt`
- Create: `src/main/kotlin/mulehang/http/HttpServer.kt`
- Create: `src/main/kotlin/mulehang/http/routes/ProviderRoutes.kt`
- Create: `src/main/kotlin/mulehang/http/routes/SessionRoutes.kt`
- Create: `src/main/kotlin/mulehang/http/routes/PermissionRoutes.kt`
- Create: `src/main/kotlin/mulehang/http/routes/McpRoutes.kt`
- Create: `src/main/kotlin/mulehang/http/routes/SseRoutes.kt`
- Create: `src/test/kotlin/mulehang/http/HttpRoutesTest.kt`

- [ ] **Step 1: 先写 HTTP 合同测试，锁定 provider/session/mcp 基础路由**

```kotlin
package mulehang.http

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import mulehang.app.AppBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpRoutesTest {
    @Test
    fun `should expose providers and mcp status routes`() = testApplication {
        val app = AppBootstrap.forTests()
        application { app.http.install(this) }

        val providers = client.get("/providers")
        val mcp = client.get("/mcp")

        assertEquals(HttpStatusCode.OK, providers.status)
        assertEquals(HttpStatusCode.OK, mcp.status)
        assertTrue(providers.bodyAsText().contains("providers"))
    }
}
```

- [ ] **Step 2: 运行测试并确认 HTTP 层尚未实现**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.http.HttpRoutesTest"
```

Expected: FAIL

- [ ] **Step 3: 实现 MCP 连接管理器**

`McpConnectionManager.kt`:

```kotlin
package mulehang.mcp

data class McpServerState(
    val name: String,
    val status: String
)

class McpConnectionManager {
    private val states = linkedMapOf<String, McpServerState>()

    fun status(): Map<String, McpServerState> = states.toMap()

    fun connect(name: String) {
        states[name] = McpServerState(name, "connected")
    }

    fun disconnect(name: String) {
        states[name] = McpServerState(name, "disconnected")
    }
}
```

- [ ] **Step 4: 实现 HTTP 服务器与 5 个基础路由模块**

`HttpServer.kt`:

```kotlin
package mulehang.http

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import mulehang.http.routes.installMcpRoutes
import mulehang.http.routes.installPermissionRoutes
import mulehang.http.routes.installProviderRoutes
import mulehang.http.routes.installSessionRoutes
import mulehang.http.routes.installSseRoutes
import mulehang.mcp.McpConnectionManager
import mulehang.permission.PermissionEngine
import mulehang.runtime.event.EventBus
import mulehang.runtime.service.SessionService

class HttpServer(
    private val sessions: SessionService,
    private val permissions: PermissionEngine,
    private val mcp: McpConnectionManager,
    private val bus: EventBus
) {
    fun install(app: Application) = with(app) {
        install(ContentNegotiation) { json() }
        install(SSE)
        routing {
            installProviderRoutes()
            installSessionRoutes(sessions)
            installPermissionRoutes(permissions)
            installMcpRoutes(mcp)
            installSseRoutes(bus)
        }
    }
}
```

`ProviderRoutes.kt`:

```kotlin
package mulehang.http.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mulehang.provider.ProviderRegistry

fun Route.installProviderRoutes() {
    get("/providers") {
        call.respond(mapOf("providers" to ProviderRegistry.providers.keys))
    }
}
```

`SessionRoutes.kt`:

```kotlin
package mulehang.http.routes

import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import mulehang.runtime.service.SessionService

fun Route.installSessionRoutes(sessions: SessionService) {
    post("/sessions") {
        val title = call.receiveText().ifBlank { "new session" }
        call.respond(sessions.createSession(title))
    }
}
```

`PermissionRoutes.kt`:

```kotlin
package mulehang.http.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mulehang.permission.PermissionEngine

fun Route.installPermissionRoutes(engine: PermissionEngine) {
    get("/permissions/health") {
        call.respond(mapOf("status" to "ok", "engine" to engine::class.simpleName))
    }
}
```

`McpRoutes.kt`:

```kotlin
package mulehang.http.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import mulehang.mcp.McpConnectionManager

fun Route.installMcpRoutes(mcp: McpConnectionManager) {
    get("/mcp") {
        call.respond(mcp.status())
    }
    post("/mcp/{name}/connect") {
        val name = call.parameters.getValue("name")
        mcp.connect(name)
        call.respond(mapOf("ok" to true))
    }
}
```

`SseRoutes.kt`:

```kotlin
package mulehang.http.routes

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.collect
import mulehang.runtime.event.EventBus

fun Route.installSseRoutes(bus: EventBus) {
    get("/events") {
        call.respondText("use /events/stream")
    }
    sse("/events/stream") {
        bus.events.collect { event ->
            send(ServerSentEvent(event.type, event.type))
        }
    }
}
```

- [ ] **Step 5: 重新运行 HTTP 合同测试并提交**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.http.HttpRoutesTest"
```

Expected: PASS

```powershell
git add src/main/kotlin/mulehang/http src/main/kotlin/mulehang/mcp src/test/kotlin/mulehang/http
git commit -m "feat(http): expose provider session permission and mcp routes"
```

### Task 4: 用统一 bootstrap 暴露 `serve-http` 和 `serve-acp` 入口

**Files:**
- Modify: `src/main/kotlin/agent/AgentApp.kt`
- Modify: `src/main/kotlin/mulehang/app/AppBootstrap.kt`

- [ ] **Step 1: 先写 `AgentApp` 命令分发测试，锁定新增入口**

```kotlin
package agent

import mulehang.cli.CliApp
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentAppCommandTest {
    @Test
    fun `should support serve-http and serve-acp commands`() {
        val seen = mutableListOf<String>()
        val app = CliApp(
            onChat = { seen += "chat" },
            onListSessions = { seen += "sessions" },
            onResume = { seen += "resume:$it" },
            onServeHttp = { seen += "serve-http" },
            onServeAcp = { seen += "serve-acp" }
        )

        app.run(listOf("serve-http"))
        app.run(listOf("serve-acp"))

        assertEquals(listOf("serve-http", "serve-acp"), seen)
    }
}
```

- [ ] **Step 2: 修改 `CliApp` 与 `AgentApp.kt`，加入 `serve-http` 和 `serve-acp` 分支**

```kotlin
class CliApp(
    private val onChat: () -> Unit,
    private val onListSessions: () -> Unit,
    private val onResume: (String) -> Unit,
    private val onServeHttp: () -> Unit,
    private val onServeAcp: () -> Unit
) {
    fun run(args: List<String>) {
        when (args.firstOrNull() ?: "chat") {
            "chat" -> onChat()
            "sessions" -> onListSessions()
            "resume" -> onResume(args.getOrNull(1) ?: error("missing session id"))
            "serve-http" -> onServeHttp()
            "serve-acp" -> onServeAcp()
            else -> error("unknown command: ${args.first()}")
        }
    }
}
```

```kotlin
fun main(args: Array<String>) {
    val app = AppBootstrap.forTests()
    val cli = CliApp(
        onChat = { runAgentCli() },
        onListSessions = { app.sessions.listSessions().forEach { println("${it.id} ${it.title}") } },
        onResume = { id -> runAgentCli(sessionId = id) },
        onServeHttp = { app.http.start() },
        onServeAcp = { app.acp.start() }
    )
    cli.run(args.toList())
}
```

- [ ] **Step 3: 为 `HttpServer` 和 `AcpAgentSupport` 增加显式 `start()` 方法**

```kotlin
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class HttpServer(...) {
    fun start() {
        embeddedServer(Netty, port = 8787) {
            install(this)
        }.start(wait = true)
    }
}

class AcpAgentSupport(...) {
    fun start() {
        runBlocking {
            while (true) {
                val line = readlnOrNull() ?: break
                stream("stdio-session", line).toList().forEach { println("${it.kind}:${it.payload}") }
            }
        }
    }
}
```

- [ ] **Step 4: 运行 Phase 5-6 测试与构建**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.app.AppBootstrapTest" --tests "mulehang.acp.AcpSessionBridgeTest" --tests "mulehang.http.HttpRoutesTest"
.\gradlew.bat build
```

Expected: PASS

- [ ] **Step 5: 提交 Phase 5-6 收口检查点**

```powershell
git add src/main/kotlin/mulehang/app src/main/kotlin/mulehang/acp src/main/kotlin/mulehang/http src/main/kotlin/mulehang/mcp src/main/kotlin/agent/AgentApp.kt
git commit -m "feat(server): add acp and http entrypoints"
```

## 自检

- 规格覆盖：
  - bootstrap：Task 1
  - ACP bridge：Task 2
  - HTTP + MCP routes：Task 3
  - 启动入口：Task 4
- 占位符检查：无 `TBD`、`TODO`、`similar to`。
- 类型一致性：统一使用 `AppBootstrap`、`AcpSessionBridge`、`AcpAgentSupport`、`McpConnectionManager`、`HttpServer`。
