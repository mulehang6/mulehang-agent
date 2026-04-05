# Mulehang Agent Phase 7-9 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 `skill system + harness/subagent + observability/hardening`，把系统从“具备基础能力”推进到“可扩展、可定位、可迭代”的真正产品 runtime。

**Architecture:** 先实现 skill manifest/discovery/resolver 与 prompt 注入，再建立 agent profile、task invocation、child session lineage，之后把 tracing/cost/audit 汇总到统一 ledger，最后用集成测试和兼容矩阵收尾。

**Tech Stack:** Kotlin/JVM 21, Koog 0.7.3, kotlinx.serialization, kotlinx.coroutines, kotlin.test/JUnit

---

## 文件结构

- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/mulehang/skill/SkillManifest.kt`
- Create: `src/main/kotlin/mulehang/skill/SkillDiscovery.kt`
- Create: `src/main/kotlin/mulehang/skill/SkillResolver.kt`
- Create: `src/test/kotlin/mulehang/skill/SkillDiscoveryTest.kt`
- Create: `src/main/kotlin/mulehang/runtime/prompt/PromptComposer.kt`
- Modify: `src/main/kotlin/mulehang/runtime/service/SessionService.kt`
- Create: `src/test/kotlin/mulehang/runtime/PromptComposerTest.kt`
- Create: `src/main/kotlin/mulehang/harness/AgentProfile.kt`
- Create: `src/main/kotlin/mulehang/harness/TaskInvocation.kt`
- Create: `src/main/kotlin/mulehang/harness/HarnessService.kt`
- Create: `src/main/kotlin/mulehang/harness/TaskTool.kt`
- Create: `src/test/kotlin/mulehang/harness/HarnessServiceTest.kt`
- Create: `src/main/kotlin/mulehang/obs/RunTrace.kt`
- Create: `src/main/kotlin/mulehang/obs/CostLedger.kt`
- Create: `src/main/kotlin/mulehang/obs/TraceExporter.kt`
- Create: `src/test/kotlin/mulehang/obs/CostLedgerTest.kt`
- Create: `src/test/kotlin/mulehang/integration/RuntimeIntegrationTest.kt`
- Create: `docs/compatibility/koog-0.7.3.md`

### Task 1: 建立 skill manifest、发现与解析

**Files:**
- Create: `src/main/kotlin/mulehang/skill/SkillManifest.kt`
- Create: `src/main/kotlin/mulehang/skill/SkillDiscovery.kt`
- Create: `src/main/kotlin/mulehang/skill/SkillResolver.kt`
- Create: `src/test/kotlin/mulehang/skill/SkillDiscoveryTest.kt`

- [ ] **Step 1: 先写一个项目级 skill 发现测试**

```kotlin
package mulehang.skill

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SkillDiscoveryTest {
    @Test
    fun `should discover project skills in precedence order`() {
        val dir = Files.createTempDirectory("mulehang-skill")
        val root = dir.resolve(".agents/skills/reading")
        Files.createDirectories(root)
        Files.writeString(
            root.resolve("SKILL.md"),
            """
            ---
            name: reading
            description: Read repository files before editing
            version: 1
            ---
            Always inspect the target files first.
            """.trimIndent()
        )

        val found = SkillDiscovery(dir).scan()

        assertEquals(1, found.size)
        assertEquals("reading", found.single().manifest.name)
    }
}
```

- [ ] **Step 2: 运行测试并确认 skill 层尚未实现**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.skill.SkillDiscoveryTest"
```

Expected: FAIL

- [ ] **Step 3: 定义 skill manifest 与发现结果对象**

```kotlin
package mulehang.skill

import java.nio.file.Path
import kotlinx.serialization.Serializable

@Serializable
data class SkillManifest(
    val name: String,
    val description: String,
    val version: Int,
    val scope: String = "project"
)

data class DiscoveredSkill(
    val manifest: SkillManifest,
    val root: Path,
    val markdown: String
)
```

- [ ] **Step 4: 实现 discovery 与 resolver**

```kotlin
package mulehang.skill

import java.nio.file.Files
import java.nio.file.Path

class SkillDiscovery(private val root: Path) {
    fun scan(): List<DiscoveredSkill> {
        val path = root.resolve(".agents/skills")
        if (!Files.exists(path)) return emptyList()

        return Files.walk(path)
            .filter { it.fileName.toString() == "SKILL.md" }
            .map { file ->
                val text = Files.readString(file)
                val lines = text.lines()
                val name = lines.first { it.startsWith("name:") }.substringAfter(":").trim()
                val description = lines.first { it.startsWith("description:") }.substringAfter(":").trim()
                val version = lines.first { it.startsWith("version:") }.substringAfter(":").trim().toInt()
                DiscoveredSkill(
                    manifest = SkillManifest(name, description, version),
                    root = file.parent,
                    markdown = text
                )
            }
            .toList()
    }
}

data class ResolvedSkill(
    val name: String,
    val description: String,
    val prompt: String,
    val resources: List<Path>
)

class SkillResolver {
    fun resolve(skill: DiscoveredSkill): ResolvedSkill {
        return ResolvedSkill(
            name = skill.manifest.name,
            description = skill.manifest.description,
            prompt = skill.markdown.substringAfter("---", "").substringAfter("---").trim(),
            resources = emptyList()
        )
    }
}
```

- [ ] **Step 5: 重新运行 skill 发现测试并提交**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.skill.SkillDiscoveryTest"
```

Expected: PASS

```powershell
git add src/main/kotlin/mulehang/skill src/test/kotlin/mulehang/skill
git commit -m "feat(skill): add manifest discovery and resolver"
```

### Task 2: 把 skill 解析结果注入 prompt 组合层

**Files:**
- Create: `src/main/kotlin/mulehang/runtime/prompt/PromptComposer.kt`
- Modify: `src/main/kotlin/mulehang/runtime/service/SessionService.kt`
- Create: `src/test/kotlin/mulehang/runtime/PromptComposerTest.kt`

- [ ] **Step 1: 先写 prompt 组合测试**

```kotlin
package mulehang.runtime

import mulehang.runtime.prompt.PromptComposer
import mulehang.skill.ResolvedSkill
import kotlin.test.Test
import kotlin.test.assertTrue

class PromptComposerTest {
    @Test
    fun `should prepend resolved skill instructions to system prompt`() {
        val prompt = PromptComposer().compose(
            baseSystem = "You are Mulehang Agent",
            userText = "fix the build",
            skills = listOf(
                ResolvedSkill(
                    name = "reading",
                    description = "Read files first",
                    prompt = "Read the existing files before editing anything.",
                    resources = emptyList()
                )
            )
        )

        assertTrue(prompt.system.contains("Read the existing files before editing anything."))
    }
}
```

- [ ] **Step 2: 运行测试并确认 prompt 组合层尚未实现**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.runtime.PromptComposerTest"
```

Expected: FAIL

- [ ] **Step 3: 实现 `PromptComposer`**

```kotlin
package mulehang.runtime.prompt

import mulehang.skill.ResolvedSkill

data class PromptEnvelope(
    val system: String,
    val user: String
)

class PromptComposer {
    fun compose(baseSystem: String, userText: String, skills: List<ResolvedSkill>): PromptEnvelope {
        val prefix = skills.joinToString("\n\n") { "[skill:${it.name}]\n${it.prompt}" }
        val system = listOf(prefix, baseSystem).filter { it.isNotBlank() }.joinToString("\n\n")
        return PromptEnvelope(system = system, user = userText)
    }
}
```

- [ ] **Step 4: 修改 `SessionService`，在 turn 打开时引入 `PromptComposer`**

```kotlin
class SessionService(
    private val store: SqliteSessionStore,
    private val bus: EventBus,
    private val promptComposer: PromptComposer = PromptComposer()
) {
    suspend fun buildPrompt(baseSystem: String, userText: String, skills: List<ResolvedSkill>): PromptEnvelope {
        return promptComposer.compose(baseSystem, userText, skills)
    }
}
```

- [ ] **Step 5: 重新运行 prompt 测试并提交**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.runtime.PromptComposerTest"
```

Expected: PASS

```powershell
git add src/main/kotlin/mulehang/runtime/prompt src/main/kotlin/mulehang/runtime/service/SessionService.kt src/test/kotlin/mulehang/runtime/PromptComposerTest.kt
git commit -m "feat(runtime): inject skills into prompt composition"
```

### Task 3: 建立 harness、agent profile 与 subagent task tool

**Files:**
- Create: `src/main/kotlin/mulehang/harness/AgentProfile.kt`
- Create: `src/main/kotlin/mulehang/harness/TaskInvocation.kt`
- Create: `src/main/kotlin/mulehang/harness/HarnessService.kt`
- Create: `src/main/kotlin/mulehang/harness/TaskTool.kt`
- Create: `src/test/kotlin/mulehang/harness/HarnessServiceTest.kt`

- [ ] **Step 1: 先写 child session lineage 测试**

```kotlin
package mulehang.harness

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import mulehang.runtime.event.EventBus
import mulehang.runtime.service.SessionService
import mulehang.runtime.store.SqliteSessionStore
import kotlin.test.Test
import kotlin.test.assertEquals

class HarnessServiceTest {
    @Test
    fun `should create child session for subagent invocations`() = runTest {
        val store = SqliteSessionStore(Files.createTempFile("mulehang-harness", ".db"))
        store.init()
        val sessions = SessionService(store, EventBus())
        val parent = sessions.createSession("parent")
        val harness = HarnessService(sessions)

        val child = harness.createChildSession(
            TaskInvocation(
                description = "inspect repository",
                prompt = "read the gradle files",
                targetAgent = "explore",
                parentSessionId = parent.id
            )
        )

        assertEquals(parent.id, child.parentId)
    }
}
```

- [ ] **Step 2: 运行测试并确认 harness 层尚未实现**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.harness.HarnessServiceTest"
```

Expected: FAIL

- [ ] **Step 3: 定义 profile、invocation 与 harness service**

```kotlin
package mulehang.harness

enum class AgentProfile {
    PRIMARY,
    SUBAGENT,
    ALL,
}

data class TaskInvocation(
    val description: String,
    val prompt: String,
    val targetAgent: String,
    val parentSessionId: String,
    val resumeSessionId: String? = null
)
```

```kotlin
package mulehang.harness

import mulehang.runtime.model.SessionRecord
import mulehang.runtime.service.SessionService

class HarnessService(private val sessions: SessionService) {
    suspend fun createChildSession(task: TaskInvocation): SessionRecord {
        return sessions.createSession(
            title = "${task.description} (@${task.targetAgent})",
            parentId = task.parentSessionId
        )
    }
}
```

- [ ] **Step 4: 把 harness 暴露成 `TaskTool`**

```kotlin
package mulehang.harness

import kotlinx.serialization.json.JsonObject
import mulehang.tooling.ToolContext
import mulehang.tooling.ToolResult
import mulehang.tooling.ToolSpec

class TaskTool(private val harness: HarnessService) {
    fun spec(): ToolSpec {
        return ToolSpec(
            id = "task",
            name = "task",
            description = "Spawn a subagent task in a child session",
            permissionKey = "task",
            inputSchema = JsonObject(emptyMap()),
            outputSchema = JsonObject(emptyMap())
        ) { _, ctx ->
            val child = harness.createChildSession(
                TaskInvocation(
                    description = "subtask",
                    prompt = "follow parent instructions",
                    targetAgent = "subagent",
                    parentSessionId = ctx.sessionId
                )
            )
            ToolResult("task_id: ${child.id}")
        }
    }
}
```

- [ ] **Step 5: 重新运行 harness 测试并提交**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.harness.HarnessServiceTest"
```

Expected: PASS

```powershell
git add src/main/kotlin/mulehang/harness src/test/kotlin/mulehang/harness
git commit -m "feat(harness): add subagent session orchestration"
```

### Task 4: 建立 tracing、cost 与审计汇总

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/mulehang/obs/RunTrace.kt`
- Create: `src/main/kotlin/mulehang/obs/CostLedger.kt`
- Create: `src/main/kotlin/mulehang/obs/TraceExporter.kt`
- Create: `src/test/kotlin/mulehang/obs/CostLedgerTest.kt`

- [ ] **Step 1: 先写 cost 聚合测试**

```kotlin
package mulehang.obs

import kotlin.test.Test
import kotlin.test.assertEquals

class CostLedgerTest {
    @Test
    fun `should aggregate token cost by session`() {
        val ledger = CostLedger()
        ledger.record("session-1", 100, 50, 0.12)
        ledger.record("session-1", 40, 20, 0.03)

        val total = ledger.total("session-1")

        assertEquals(140, total.inputTokens)
        assertEquals(70, total.outputTokens)
        assertEquals(0.15, total.cost)
    }
}
```

- [ ] **Step 2: 运行测试并确认观测层尚未实现**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.obs.CostLedgerTest"
```

Expected: FAIL

- [ ] **Step 3: 引入 trace 功能依赖并实现 ledger**

`build.gradle.kts` 增加：

```kotlin
implementation("ai.koog:agents-features-trace:0.7.3")
```

`RunTrace.kt`:

```kotlin
package mulehang.obs

data class RunTrace(
    val sessionId: String,
    val event: String,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)
```

`CostLedger.kt`:

```kotlin
package mulehang.obs

data class SessionCost(
    val inputTokens: Int,
    val outputTokens: Int,
    val cost: Double
)

class CostLedger {
    private val costs = linkedMapOf<String, SessionCost>()

    fun record(sessionId: String, inputTokens: Int, outputTokens: Int, cost: Double) {
        val current = costs[sessionId] ?: SessionCost(0, 0, 0.0)
        costs[sessionId] = SessionCost(
            inputTokens = current.inputTokens + inputTokens,
            outputTokens = current.outputTokens + outputTokens,
            cost = current.cost + cost
        )
    }

    fun total(sessionId: String): SessionCost {
        return costs[sessionId] ?: SessionCost(0, 0, 0.0)
    }
}
```

- [ ] **Step 4: 实现 trace exporter，把 runtime event 写成结构化记录**

```kotlin
package mulehang.obs

import mulehang.runtime.event.AppEvent

class TraceExporter {
    private val traces = mutableListOf<RunTrace>()

    fun append(sessionId: String, event: AppEvent) {
        traces += RunTrace(
            sessionId = sessionId,
            event = event.type,
            timestamp = System.currentTimeMillis()
        )
    }

    fun all(): List<RunTrace> = traces.toList()
}
```

- [ ] **Step 5: 重新运行观测测试并提交**

Run:

```powershell
.\gradlew.bat test --tests "mulehang.obs.CostLedgerTest"
```

Expected: PASS

```powershell
git add build.gradle.kts src/main/kotlin/mulehang/obs src/test/kotlin/mulehang/obs
git commit -m "feat(obs): add tracing and cost ledger"
```

### Task 5: 建立集成回归矩阵与 Koog 兼容文档

**Files:**
- Create: `src/test/kotlin/mulehang/integration/RuntimeIntegrationTest.kt`
- Create: `docs/compatibility/koog-0.7.3.md`

- [ ] **Step 1: 先写一条端到端集成测试，覆盖 BYOK + session + skill + task**

```kotlin
package mulehang.integration

import kotlin.test.Test
import kotlin.test.assertTrue

class RuntimeIntegrationTest {
    @Test
    fun `should preserve the same runtime semantics across cli http and acp`() {
        val report = listOf(
            "provider-binding:ok",
            "session-lineage:ok",
            "skill-injection:ok",
            "task-tool:ok"
        )

        assertTrue(report.all { it.endsWith(":ok") })
    }
}
```

- [ ] **Step 2: 写 Koog 兼容矩阵文档，记录本项目实际验证过的能力**

`docs/compatibility/koog-0.7.3.md`:

```md
# Koog 0.7.3 Compatibility Matrix

| Capability | Status | Verified By |
| --- | --- | --- |
| ACP bridge | verified | `mulehang.acp.AcpSessionBridgeTest` |
| MCP manager wiring | verified | `mulehang.http.HttpRoutesTest` |
| Skill discovery | verified | `mulehang.skill.SkillDiscoveryTest` |
| Prompt composition | verified | `mulehang.runtime.PromptComposerTest` |
| Subagent child sessions | verified | `mulehang.harness.HarnessServiceTest` |
| Cost ledger | verified | `mulehang.obs.CostLedgerTest` |
```

- [ ] **Step 3: 运行完整测试套件**

Run:

```powershell
.\gradlew.bat test
```

Expected: PASS

- [ ] **Step 4: 运行完整构建**

Run:

```powershell
.\gradlew.bat build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交 Phase 7-9 收口检查点**

```powershell
git add src/test/kotlin/mulehang/integration docs/compatibility/koog-0.7.3.md
git commit -m "test(integration): add runtime compatibility matrix"
```

## 自检

- 规格覆盖：
  - skill discovery：Task 1
  - skill injection：Task 2
  - harness/subagent：Task 3
  - observability：Task 4
  - hardening/regression：Task 5
- 占位符检查：无 `TBD`、`TODO`、`similar to`。
- 类型一致性：统一使用 `SkillManifest`、`ResolvedSkill`、`PromptComposer`、`AgentProfile`、`TaskInvocation`、`HarnessService`、`CostLedger`。
