# Mulehang Agent Phase 2-4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立统一 `session/runtime/tooling/permission` 产品底座，并把现有 CLI 原型升级为真正可恢复、可审计、可切换 session 的入口。

**Architecture:** 先用 SQLite + `SharedFlow` 搭出 runtime persistence 与 event bus，再把本地工具抽象成 `ToolSpec`，引入独立 `PermissionEngine`，最后让 CLI 通过 `SessionService` 驱动运行，而不是直接拼接 `OpenRouterChatClient`。

**Tech Stack:** Kotlin/JVM 21, SQLite JDBC, kotlinx.coroutines, Koog 0.7.3, kotlin.test/JUnit

---

## 文件结构

- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/mulehang/runtime/model/SessionRecord.kt`
- Create: `src/main/kotlin/mulehang/runtime/store/SqliteSessionStore.kt`
- Create: `src/test/kotlin/mulehang/runtime/SqliteSessionStoreTest.kt`
- Create: `src/main/kotlin/mulehang/runtime/event/AppEvent.kt`
- Create: `src/main/kotlin/mulehang/runtime/event/EventBus.kt`
- Create: `src/main/kotlin/mulehang/runtime/service/SessionService.kt`
- Create: `src/test/kotlin/mulehang/runtime/SessionServiceTest.kt`
- Create: `src/main/kotlin/mulehang/tooling/ToolSpec.kt`
- Create: `src/main/kotlin/mulehang/tooling/ToolCatalog.kt`
- Create: `src/main/kotlin/mulehang/tooling/KoogToolFactory.kt`
- Create: `src/test/kotlin/mulehang/tooling/ToolCatalogTest.kt`
- Create: `src/main/kotlin/mulehang/permission/PermissionRule.kt`
- Create: `src/main/kotlin/mulehang/permission/PermissionEngine.kt`
- Create: `src/test/kotlin/mulehang/permission/PermissionEngineTest.kt`
- Create: `src/main/kotlin/mulehang/cli/CliApp.kt`
- Modify: `src/main/kotlin/agent/AgentApp.kt`
- Modify: `src/main/kotlin/agent/MySimpleAgent.kt`
- Modify: `src/main/kotlin/agent/OpenRouterChatClient.kt`
- Create: `src/test/kotlin/mulehang/cli/CliAppTest.kt`

### Task 1: 建立 SQLite session 存储

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/mulehang/runtime/model/SessionRecord.kt`
- Create: `src/main/kotlin/mulehang/runtime/store/SqliteSessionStore.kt`
- Create: `src/test/kotlin/mulehang/runtime/SqliteSessionStoreTest.kt`

- [ ] **Step 1: 先写一个会失败的存储测试**

```kotlin
package mulehang.runtime

import java.nio.file.Files
import mulehang.runtime.model.SessionRecord
import mulehang.runtime.store.SqliteSessionStore
import kotlin.test.Test
import kotlin.test.assertEquals

class SqliteSessionStoreTest {
    @Test
    fun `should persist and reload a session`() {
        val db = Files.createTempFile("mulehang-session", ".db")
        val store = SqliteSessionStore(db)
        store.init()

        val row = SessionRecord(
            id = "session-1",
            title = "first run",
            status = "idle",
            parentId = null,
            createdAt = 1L,
            updatedAt = 1L
        )

        store.insertSession(row)
        val loaded = store.getSession("session-1")

        assertEquals("first run", loaded?.title)
    }
}
```

- [ ] **Step 2: 运行测试并确认缺少 JDBC 与存储层**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.runtime.SqliteSessionStoreTest"
```

Expected: FAIL

- [ ] **Step 3: 添加 SQLite 依赖并实现表结构初始化**

`build.gradle.kts` 增加：

```kotlin
implementation("org.xerial:sqlite-jdbc:3.46.1.3")
```

`SessionRecord.kt`:

```kotlin
package mulehang.runtime.model

data class SessionRecord(
    val id: String,
    val title: String,
    val status: String,
    val parentId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class MessageRecord(
    val id: String,
    val sessionId: String,
    val role: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val content: String
)
```

`SqliteSessionStore.kt`:

```kotlin
package mulehang.runtime.store

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import mulehang.runtime.model.SessionRecord

class SqliteSessionStore(path: Path) {
    private val db: Connection = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")

    fun init() {
        db.createStatement().use { st ->
            st.executeUpdate(
                """
                create table if not exists session (
                  id text primary key,
                  title text not null,
                  status text not null,
                  parent_id text null,
                  created_at integer not null,
                  updated_at integer not null
                )
                """.trimIndent()
            )
        }
    }

    fun insertSession(row: SessionRecord) {
        db.prepareStatement(
            "insert into session(id, title, status, parent_id, created_at, updated_at) values(?, ?, ?, ?, ?, ?)"
        ).use { ps ->
            ps.setString(1, row.id)
            ps.setString(2, row.title)
            ps.setString(3, row.status)
            ps.setString(4, row.parentId)
            ps.setLong(5, row.createdAt)
            ps.setLong(6, row.updatedAt)
            ps.executeUpdate()
        }
    }

    fun getSession(id: String): SessionRecord? {
        db.prepareStatement("select * from session where id = ?").use { ps ->
            ps.setString(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                return SessionRecord(
                    id = rs.getString("id"),
                    title = rs.getString("title"),
                    status = rs.getString("status"),
                    parentId = rs.getString("parent_id"),
                    createdAt = rs.getLong("created_at"),
                    updatedAt = rs.getLong("updated_at")
                )
            }
        }
    }

    fun listSessions(): List<SessionRecord> {
        db.createStatement().use { st ->
            st.executeQuery("select * from session order by created_at desc").use { rs ->
                val rows = mutableListOf<SessionRecord>()
                while (rs.next()) {
                    rows += SessionRecord(
                        id = rs.getString("id"),
                        title = rs.getString("title"),
                        status = rs.getString("status"),
                        parentId = rs.getString("parent_id"),
                        createdAt = rs.getLong("created_at"),
                        updatedAt = rs.getLong("updated_at")
                    )
                }
                return rows
            }
        }
    }
}
```

- [ ] **Step 4: 重新运行存储测试**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.runtime.SqliteSessionStoreTest"
```

Expected: PASS

- [ ] **Step 5: 提交存储层检查点**

```powershell
git add build.gradle.kts src/main/kotlin/mulehang/runtime src/test/kotlin/mulehang/runtime/SqliteSessionStoreTest.kt
git commit -m "feat(runtime): add sqlite session store"
```

### Task 2: 建立 runtime event bus 与 session service

**Files:**
- Create: `src/main/kotlin/mulehang/runtime/event/AppEvent.kt`
- Create: `src/main/kotlin/mulehang/runtime/event/EventBus.kt`
- Create: `src/main/kotlin/mulehang/runtime/service/SessionService.kt`
- Create: `src/test/kotlin/mulehang/runtime/SessionServiceTest.kt`

- [ ] **Step 1: 先写 `SessionService` 事件顺序测试**

```kotlin
package mulehang.runtime

import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mulehang.runtime.event.EventBus
import mulehang.runtime.service.SessionService
import mulehang.runtime.store.SqliteSessionStore
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SessionServiceTest {
    @Test
    fun `should publish session created and turn opened events`() = runTest {
        val store = SqliteSessionStore(Files.createTempFile("mulehang-service", ".db"))
        store.init()
        val bus = EventBus()
        val svc = SessionService(store, bus)

        val seen = mutableListOf<String>()
        val collector = async {
            bus.events.collect { seen += it.type }
        }

        val row = svc.createSession("new chat")
        svc.openTurn(row.id, "user")

        assertEquals(listOf("session.created", "turn.opened"), seen.take(2))
        collector.cancel()
    }
}
```

- [ ] **Step 2: 运行测试并确认 event bus/service 尚未实现**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.runtime.SessionServiceTest"
```

Expected: FAIL

- [ ] **Step 3: 实现事件模型与 `SharedFlow` 总线**

`AppEvent.kt`:

```kotlin
package mulehang.runtime.event

sealed interface AppEvent {
    val type: String

    data class SessionCreated(val sessionId: String, val title: String) : AppEvent {
        override val type = "session.created"
    }

    data class TurnOpened(val sessionId: String, val source: String) : AppEvent {
        override val type = "turn.opened"
    }
}
```

`EventBus.kt`:

```kotlin
package mulehang.runtime.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class EventBus {
    private val sink = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AppEvent> = sink.asSharedFlow()

    suspend fun publish(event: AppEvent) {
        sink.emit(event)
    }
}
```

- [ ] **Step 4: 实现 `SessionService`**

```kotlin
package mulehang.runtime.service

import kotlin.time.Clock
import mulehang.runtime.event.AppEvent
import mulehang.runtime.event.EventBus
import mulehang.runtime.model.SessionRecord
import mulehang.runtime.store.SqliteSessionStore

class SessionService(
    private val store: SqliteSessionStore,
    private val bus: EventBus
) {
    suspend fun createSession(title: String, parentId: String? = null): SessionRecord {
        val now = Clock.System.now().toEpochMilliseconds()
        val row = SessionRecord(
            id = "session-${now}",
            title = title,
            status = "idle",
            parentId = parentId,
            createdAt = now,
            updatedAt = now
        )
        store.insertSession(row)
        bus.publish(AppEvent.SessionCreated(row.id, row.title))
        return row
    }

    suspend fun openTurn(sessionId: String, source: String) {
        bus.publish(AppEvent.TurnOpened(sessionId, source))
    }

    fun listSessions(): List<SessionRecord> = store.listSessions()
}
```

- [ ] **Step 5: 重新运行 runtime service 测试并提交**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.runtime.SessionServiceTest"
```

Expected: PASS

```powershell
git add src/main/kotlin/mulehang/runtime/event src/main/kotlin/mulehang/runtime/service src/test/kotlin/mulehang/runtime/SessionServiceTest.kt
git commit -m "feat(runtime): add event bus and session service"
```

### Task 3: 建立统一 ToolSpec 与 Koog tool 适配层

**Files:**
- Create: `src/main/kotlin/mulehang/tooling/ToolSpec.kt`
- Create: `src/main/kotlin/mulehang/tooling/ToolCatalog.kt`
- Create: `src/main/kotlin/mulehang/tooling/KoogToolFactory.kt`
- Create: `src/test/kotlin/mulehang/tooling/ToolCatalogTest.kt`

- [ ] **Step 1: 先写工具注册测试**

```kotlin
package mulehang.tooling

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolCatalogTest {
    @Test
    fun `should expose stable tool ids and permission keys`() {
        val read = ToolSpec(
            id = "read-file",
            name = "read_file",
            description = "Read file content",
            permissionKey = "read",
            inputSchema = JsonObject(emptyMap()),
            outputSchema = JsonObject(emptyMap())
        ) { _, _ -> ToolResult("ok") }

        val catalog = ToolCatalog(listOf(read))

        assertEquals(read, catalog.get("read-file"))
        assertEquals("read", catalog.get("read-file")?.permissionKey)
    }
}
```

- [ ] **Step 2: 运行测试并确认 tooling 层还不存在**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.tooling.ToolCatalogTest"
```

Expected: FAIL

- [ ] **Step 3: 定义统一 `ToolSpec` 和 `ToolResult`**

```kotlin
package mulehang.tooling

import kotlinx.serialization.json.JsonObject

class ToolContext(
    val sessionId: String,
    val workingDirectory: String
)

data class ToolResult(
    val text: String,
    val metadata: Map<String, String> = emptyMap()
)

data class ToolSpec(
    val id: String,
    val name: String,
    val description: String,
    val permissionKey: String,
    val inputSchema: JsonObject,
    val outputSchema: JsonObject,
    val execute: suspend (JsonObject, ToolContext) -> ToolResult
)
```

- [ ] **Step 4: 建立目录与 Koog 适配器**

```kotlin
package mulehang.tooling

import ai.koog.agents.core.tools.Tool

class ToolCatalog(items: List<ToolSpec>) {
    private val tools = items.associateBy { it.id }
    fun get(id: String): ToolSpec? = tools[id]
    fun all(): List<ToolSpec> = tools.values.toList()
}

object KoogToolFactory {
    fun from(spec: ToolSpec): Tool = Tool.build {
        name = spec.name
        description = spec.description
    }
}
```

- [ ] **Step 5: 重新运行 tooling 测试并提交**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.tooling.ToolCatalogTest"
```

Expected: PASS

```powershell
git add src/main/kotlin/mulehang/tooling src/test/kotlin/mulehang/tooling
git commit -m "feat(tooling): add product tool catalog"
```

### Task 4: 建立独立 PermissionEngine

**Files:**
- Create: `src/main/kotlin/mulehang/permission/PermissionRule.kt`
- Create: `src/main/kotlin/mulehang/permission/PermissionEngine.kt`
- Create: `src/test/kotlin/mulehang/permission/PermissionEngineTest.kt`

- [ ] **Step 1: 先写 allow/ask/deny 决策测试**

```kotlin
package mulehang.permission

import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionEngineTest {
    @Test
    fun `should prefer session rule over global rule`() {
        val engine = PermissionEngine(
            global = listOf(PermissionRule("read", "*", PermissionAction.ASK)),
            session = listOf(PermissionRule("read", "README.md", PermissionAction.ALLOW))
        )

        val decision = engine.decide("read", "README.md")

        assertEquals(PermissionAction.ALLOW, decision.action)
    }
}
```

- [ ] **Step 2: 运行测试并确认 permission engine 尚未实现**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.permission.PermissionEngineTest"
```

Expected: FAIL

- [ ] **Step 3: 实现规则模型与匹配逻辑**

```kotlin
package mulehang.permission

enum class PermissionAction {
    ALLOW,
    ASK,
    DENY,
}

data class PermissionRule(
    val permission: String,
    val pattern: String,
    val action: PermissionAction
)

data class PermissionDecision(
    val action: PermissionAction,
    val matchedPattern: String
)
```

```kotlin
package mulehang.permission

class PermissionEngine(
    private val global: List<PermissionRule> = emptyList(),
    private val session: List<PermissionRule> = emptyList()
) {
    fun decide(permission: String, target: String): PermissionDecision {
        val rules = session + global
        val match = rules.firstOrNull { it.permission == permission && wildcard(it.pattern, target) }
            ?: return PermissionDecision(PermissionAction.ASK, "*")
        return PermissionDecision(match.action, match.pattern)
    }

    private fun wildcard(pattern: String, target: String): Boolean {
        if (pattern == "*") return true
        return pattern == target
    }
}
```

- [ ] **Step 4: 重新运行 permission 测试**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.permission.PermissionEngineTest"
```

Expected: PASS

- [ ] **Step 5: 提交 permission 检查点**

```powershell
git add src/main/kotlin/mulehang/permission src/test/kotlin/mulehang/permission
git commit -m "feat(permission): add permission engine"
```

### Task 5: 把 CLI 收敛到统一 SessionService

**Files:**
- Create: `src/main/kotlin/mulehang/cli/CliApp.kt`
- Modify: `src/main/kotlin/agent/AgentApp.kt`
- Modify: `src/main/kotlin/agent/MySimpleAgent.kt`
- Modify: `src/main/kotlin/agent/OpenRouterChatClient.kt`
- Create: `src/test/kotlin/mulehang/cli/CliAppTest.kt`

- [ ] **Step 1: 先写 CLI 命令分发测试**

```kotlin
package mulehang.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class CliAppTest {
    @Test
    fun `should route sessions command to list sessions`() {
        val seen = mutableListOf<String>()
        val app = CliApp(
            onChat = { seen += "chat" },
            onListSessions = { seen += "sessions" },
            onResume = { seen += "resume:$it" }
        )

        app.run(listOf("sessions"))

        assertEquals(listOf("sessions"), seen)
    }
}
```

- [ ] **Step 2: 运行测试并确认 CLI 壳层尚未存在**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.cli.CliAppTest"
```

Expected: FAIL

- [ ] **Step 3: 实现 `CliApp`，支持 `chat / sessions / resume` 三个命令**

```kotlin
package mulehang.cli

class CliApp(
    private val onChat: () -> Unit,
    private val onListSessions: () -> Unit,
    private val onResume: (String) -> Unit
) {
    fun run(args: List<String>) {
        when (args.firstOrNull() ?: "chat") {
            "chat" -> onChat()
            "sessions" -> onListSessions()
            "resume" -> onResume(args.getOrNull(1) ?: error("missing session id"))
            else -> error("unknown command: ${args.first()}")
        }
    }
}
```

- [ ] **Step 4: 修改 `AgentApp.kt`，让旧入口只负责调用新 CLI 壳层**

```kotlin
package agent

import mulehang.cli.CliApp

fun main(args: Array<String>) {
    val sessions = buildSessionService()
    val app = CliApp(
        onChat = { runAgentCli() },
        onListSessions = { sessions.listSessions().forEach { println("${it.id} ${it.title}") } },
        onResume = { id -> runAgentCli(existingSessionId = id) }
    )
    app.run(args.toList())
}
```

`MySimpleAgent.kt` 增加可恢复会话参数：

```kotlin
internal fun runAgentCli(existingSessionId: String? = null) {
    val sessionId = existingSessionId ?: kotlin.uuid.Uuid.random().toString()
    // 后续逻辑继续使用 sessionId，不再强制每次新建会话
}
```

- [ ] **Step 5: 运行 Phase 2-4 的核心测试与构建**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.runtime.SqliteSessionStoreTest" --tests "mulehang.runtime.SessionServiceTest" --tests "mulehang.tooling.ToolCatalogTest" --tests "mulehang.permission.PermissionEngineTest" --tests "mulehang.cli.CliAppTest"
.\gradlew.bat build
```

Expected: PASS

- [ ] **Step 6: 提交 Phase 2-4 收口检查点**

```powershell
git add src/main/kotlin/mulehang src/main/kotlin/agent/AgentApp.kt src/test/kotlin/mulehang
git commit -m "feat(cli): route prototype through runtime shell"
```

## 自检

- 规格覆盖：
  - persistence：Task 1
  - event bus + session service：Task 2
  - product tool catalog：Task 3
  - permission engine：Task 4
  - CLI productization：Task 5
- 占位符检查：无 `TBD`、`TODO`、`similar to`。
- 类型一致性：统一使用 `SessionRecord`、`SqliteSessionStore`、`EventBus`、`SessionService`、`ToolSpec`、`PermissionRule`、`CliApp`。
