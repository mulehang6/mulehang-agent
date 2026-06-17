# Desktop Tooling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为桌面聊天接入真正可执行的本地工具链，包括只读文件/搜索工具、工作区内写入工具、PowerShell 7 命令工具，以及可在当前轮次内挂起并恢复的 `ask_user` 桌面交互。

**Architecture:** 将当前 `KoogAgentGateway` 升级为基于 Koog `AIAgent + ToolRegistry` 的桌面 agent 执行链；工具权限与 UI 交互通过独立的 policy / bridge / pending state 层解耦；`ask_user` 和危险操作审批都通过时间线内嵌卡片完成，并在用户提交后恢复当前轮次执行。

**Tech Stack:** Kotlin Multiplatform、Compose Desktop、Koog 1.0.0、kotlinx.coroutines、kotlin.test、JUnit 5、PowerShell 7、ripgrep（可选）

---

## File Structure

本轮建议按职责新增或修改以下文件。

### shared/commonMain

- Modify: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentStreamEvent.kt`
  - 扩展问题请求、审批请求和恢复相关事件。
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentGateway.kt`
  - 保持接口稳定，但允许底层实现进入可挂起 agent run。
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentRunRequest.kt`
  - 增加工具运行所需上下文，例如工作区路径与 permission preset。
- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/DesktopToolPolicy.kt`
  - 定义 `Read` / `Write` / `Execute` 分类和 `PermissionPreset` 判定。
- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/DesktopToolEventModels.kt`
  - 定义 `QuestionRequest`、`ApprovalRequest` 等共享数据结构。
- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/DesktopToolInteractionBridge.kt`
  - 定义工具层与 UI 层的挂起/恢复接口。
- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/DesktopToolSet.kt`
  - 注册项目化 `ask_user` / `say_to_user` / `exit` / `glob_files` / `grep_code` / `run_powershell` 等工具。

### shared/desktopMain

- Modify: `shared/src/desktopMain/kotlin/com/agent/shared/agent/KoogAgentGateway.kt`
  - 升级为 `AIAgent + ToolRegistry` 执行入口。
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopAgentGateway.kt`
  - 如果保留 `KoogAgentGateway` 作为兼容名，则新增实现类承载新逻辑。
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopToolRegistryFactory.kt`
  - 组装桌面工具注册表。
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopFileToolSupport.kt`
  - 路径规范化、越界判断、只读/写入边界检查。
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopGlobTool.kt`
  - `glob_files` 工具实现。
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopGrepTool.kt`
  - `grep_code` 工具实现，优先 `rg`，失败回退 JVM。
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopReadWriteTools.kt`
  - `read_file` / `list_dir` / `write_file` / `edit_file`。
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopPowerShellTool.kt`
  - `run_powershell` 与 PowerShell 7 检测。
- Keep: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DeepSeekChatCompletionsStreamer.kt`
  - 若 DeepSeek 链路继续走自定义 streamer，需要适配 tools 能力或显式限制。

### composeApp/desktopMain

- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`
  - 增加 pending question / approval 状态、桥实现、恢复逻辑、权限更新后的工具上下文传递。
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt`
  - 增加问题卡片和审批卡片渲染。
- Create: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ToolInteractionCards.kt`
  - 把问题卡片与审批卡片拆成独立组件，避免 `ChatScreen.kt` 继续膨胀。

### tests

- Modify: `shared/src/commonTest/kotlin/com/agent/shared/application/SendMessageUseCaseTest.kt`
  - 校验新请求字段透传。
- Create: `shared/src/commonTest/kotlin/com/agent/shared/agent/DesktopToolPolicyTest.kt`
  - 校验 `PermissionPreset` 矩阵。
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopToolRegistryFactoryTest.kt`
  - 校验工具注册表内容。
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopFileToolSupportTest.kt`
  - 校验工作区内写入、工作区外只读、路径归一化等。
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopGlobToolTest.kt`
  - 校验 `glob_files` 预算和匹配。
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopGrepToolTest.kt`
  - 校验 `grep_code`、`partial` 和回退搜索。
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopPowerShellToolTest.kt`
  - 校验 PowerShell 7 检测和错误返回。
- Modify: `shared/src/desktopTest/kotlin/com/agent/shared/agent/KoogAgentGatewayTest.kt`
  - 改写为 agent/tool 事件级测试。
- Modify: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatWindowStateTest.kt`
  - 增加 pending question / approval / resume 测试。
- Create: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ToolInteractionCardsTest.kt`
  - 校验卡片文案和交互态。

## Notes

- 本计划不包含 `git commit` 步骤。仓库规则明确要求只有用户显式允许时才提交。
- 计划默认按 TDD 顺序编写，但验证命令统一使用本仓库允许的 Gradle / IDEA 构建方式，不启动开发服务器。

### Task 1: 扩展共享事件与请求模型

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentRunRequest.kt`
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentStreamEvent.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/DesktopToolEventModels.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/DesktopToolPolicy.kt`
- Test: `shared/src/commonTest/kotlin/com/agent/shared/application/SendMessageUseCaseTest.kt`
- Test: `shared/src/commonTest/kotlin/com/agent/shared/agent/DesktopToolPolicyTest.kt`

- [ ] **Step 1: 写共享层失败测试，锁定新请求字段与权限矩阵**

```kotlin
package com.agent.shared.agent

import com.agent.app.ui.PermissionPreset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopToolPolicyTest {
    @Test
    fun `default should auto allow read only`() {
        assertTrue(DesktopToolPolicy.canRunRead(PermissionPreset.DEFAULT))
        assertFalse(DesktopToolPolicy.canAutoApproveWrite(PermissionPreset.DEFAULT))
        assertFalse(DesktopToolPolicy.canAutoApproveExecute(PermissionPreset.DEFAULT))
    }

    @Test
    fun `edit allow should auto approve write but not execute`() {
        assertTrue(DesktopToolPolicy.canAutoApproveWrite(PermissionPreset.EDIT_ALLOW))
        assertFalse(DesktopToolPolicy.canAutoApproveExecute(PermissionPreset.EDIT_ALLOW))
    }

    @Test
    fun `brave should auto approve execute`() {
        assertTrue(DesktopToolPolicy.canAutoApproveExecute(PermissionPreset.BRAVE))
    }

    @Test
    fun `plan should deny write and execute`() {
        assertTrue(DesktopToolPolicy.isWriteDenied(PermissionPreset.PLAN))
        assertTrue(DesktopToolPolicy.isExecuteDenied(PermissionPreset.PLAN))
    }
}
```

```kotlin
@Test
fun `should forward workspace path and permission preset in run request`() {
    val captured = mutableListOf<AgentRunRequest>()
    val gateway = object : AgentGateway {
        override fun run(request: AgentRunRequest) = flow {
            captured += request
            emit(AgentStreamEvent.Completed("ok"))
        }
    }
    val request = AgentRunRequest(
        prompt = "hello",
        profile = profile(),
        workspacePath = "D:\\\\repo",
        permissionPreset = PermissionPreset.DEFAULT,
    )

    runTest {
        SendMessageUseCase(gateway).invoke(request).collect()
    }

    assertEquals("D:\\repo", captured.single().workspacePath)
    assertEquals(PermissionPreset.DEFAULT, captured.single().permissionPreset)
}
```

- [ ] **Step 2: 运行共享层测试，确认当前实现还未支持这些字段和工具策略**

Run:

```powershell
.\gradlew.bat :shared:allTests --tests "com.agent.shared.agent.DesktopToolPolicyTest" --tests "com.agent.shared.application.SendMessageUseCaseTest"
```

Expected: FAIL，提示 `DesktopToolPolicy`、`workspacePath` 或 `permissionPreset` 尚不存在。

- [ ] **Step 3: 为 `AgentRunRequest`、`AgentStreamEvent` 和工具事件模型补最小实现**

```kotlin
data class AgentRunRequest(
    val prompt: String,
    val profile: ConfigProfile,
    val reasoningEffort: ReasoningEffort? = ReasoningEffort.MEDIUM,
    val history: List<AgentConversationHistoryMessage> = emptyList(),
    val workspacePath: String = "",
    val permissionPreset: PermissionPreset = PermissionPreset.DEFAULT,
)
```

```kotlin
sealed interface AgentStreamEvent {
    data object Started : AgentStreamEvent
    data class TextDelta(val text: String) : AgentStreamEvent
    data class ToolCallStarted(val name: String, val argumentsPreview: String? = null) : AgentStreamEvent
    data class ToolCallFinished(val name: String, val resultPreview: String? = null) : AgentStreamEvent
    data class QuestionRequested(val request: QuestionRequest) : AgentStreamEvent
    data class ApprovalRequested(val request: ApprovalRequest) : AgentStreamEvent
    data class Status(val message: String) : AgentStreamEvent
    data class Completed(val text: String) : AgentStreamEvent
    data class Failed(val reason: String) : AgentStreamEvent
}
```

```kotlin
data class QuestionRequest(
    val requestId: String,
    val toolCallId: String,
    val question: String,
    val options: List<String>,
    val allowFreeText: Boolean = true,
)

data class ApprovalRequest(
    val requestId: String,
    val toolName: String,
    val summary: String,
    val targetPath: String? = null,
    val payloadPreview: String? = null,
)
```

```kotlin
object DesktopToolPolicy {
    fun canRunRead(permissionPreset: PermissionPreset): Boolean = true
    fun canAutoApproveWrite(permissionPreset: PermissionPreset): Boolean =
        permissionPreset == PermissionPreset.EDIT_ALLOW || permissionPreset == PermissionPreset.BRAVE

    fun canAutoApproveExecute(permissionPreset: PermissionPreset): Boolean =
        permissionPreset == PermissionPreset.BRAVE

    fun isWriteDenied(permissionPreset: PermissionPreset): Boolean =
        permissionPreset == PermissionPreset.PLAN

    fun isExecuteDenied(permissionPreset: PermissionPreset): Boolean =
        permissionPreset == PermissionPreset.PLAN
}
```

- [ ] **Step 4: 重新运行共享层测试，确认模型扩展通过**

Run:

```powershell
.\gradlew.bat :shared:allTests --tests "com.agent.shared.agent.DesktopToolPolicyTest" --tests "com.agent.shared.application.SendMessageUseCaseTest"
```

Expected: PASS。

### Task 2: 定义桌面交互桥与挂起恢复接口

**Files:**
- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/DesktopToolInteractionBridge.kt`
- Test: `shared/src/commonTest/kotlin/com/agent/shared/agent/DesktopToolInteractionBridgeTest.kt`

- [ ] **Step 1: 写桥接口的失败测试，锁定问题请求和审批请求的 suspend 恢复语义**

```kotlin
package com.agent.shared.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopToolInteractionBridgeTest {
    @Test
    fun `question bridge should resume with submitted answer`() = runTest {
        val answer = CompletableDeferred<String>()
        val bridge = object : DesktopToolInteractionBridge {
            override suspend fun requestQuestion(request: QuestionRequest): String = answer.await()
            override suspend fun requestApproval(request: ApprovalRequest): Boolean = true
        }

        val deferred = async {
            bridge.requestQuestion(
                QuestionRequest(
                    requestId = "q1",
                    toolCallId = "call-1",
                    question = "Pick one",
                    options = listOf("A", "B"),
                ),
            )
        }

        answer.complete("B")
        assertEquals("B", deferred.await())
    }
}
```

- [ ] **Step 2: 运行桥接口测试，确认当前实现不存在**

Run:

```powershell
.\gradlew.bat :shared:allTests --tests "com.agent.shared.agent.DesktopToolInteractionBridgeTest"
```

Expected: FAIL，提示 `DesktopToolInteractionBridge` 尚不存在。

- [ ] **Step 3: 新增桥接口与最小测试通过实现**

```kotlin
interface DesktopToolInteractionBridge {
    suspend fun requestQuestion(request: QuestionRequest): String
    suspend fun requestApproval(request: ApprovalRequest): Boolean
}
```

- [ ] **Step 4: 重新运行桥接口测试**

Run:

```powershell
.\gradlew.bat :shared:allTests --tests "com.agent.shared.agent.DesktopToolInteractionBridgeTest"
```

Expected: PASS。

### Task 3: 建立桌面路径边界与工具注册表工厂

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopFileToolSupport.kt`
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopToolRegistryFactory.kt`
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopFileToolSupportTest.kt`
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopToolRegistryFactoryTest.kt`

- [ ] **Step 1: 写失败测试，锁定“只读可跨工作区，写入仅限工作区内”**

```kotlin
class DesktopFileToolSupportTest {
    @Test
    fun `read should allow file outside workspace`() {
        val support = DesktopFileToolSupport(workspacePath = "D:\\repo")
        assertTrue(support.canRead("C:\\Users\\me\\.mulehang\\settings.json"))
    }

    @Test
    fun `write should reject file outside workspace`() {
        val support = DesktopFileToolSupport(workspacePath = "D:\\repo")
        assertFalse(support.canWrite("C:\\Users\\me\\note.txt"))
    }
}
```

```kotlin
class DesktopToolRegistryFactoryTest {
    @Test
    fun `registry should contain first batch tool names`() {
        val registry = DesktopToolRegistryFactory(
            workspacePath = "D:\\repo",
            permissionPreset = PermissionPreset.DEFAULT,
            interactionBridge = fakeBridge(),
        ).create()

        val names = registry.tools.map { it.name }.toSet()
        assertEquals(
            setOf(
                "read_file",
                "list_dir",
                "glob_files",
                "grep_code",
                "write_file",
                "edit_file",
                "run_powershell",
                "ask_user",
                "say_to_user",
                "exit",
            ),
            names,
        )
    }
}
```

- [ ] **Step 2: 运行桌面层测试，确认工具支撑层缺失**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DesktopFileToolSupportTest" --tests "com.agent.shared.agent.DesktopToolRegistryFactoryTest"
```

Expected: FAIL。

- [ ] **Step 3: 写路径支撑与注册表工厂的最小实现**

```kotlin
class DesktopFileToolSupport(
    workspacePath: String,
) {
    private val workspaceRoot = Paths.get(workspacePath).normalize().toAbsolutePath()

    fun canRead(rawPath: String): Boolean = true

    fun canWrite(rawPath: String): Boolean {
        val target = Paths.get(rawPath).normalize().toAbsolutePath()
        return target.startsWith(workspaceRoot)
    }
}
```

```kotlin
class DesktopToolRegistryFactory(
    private val workspacePath: String,
    private val permissionPreset: PermissionPreset,
    private val interactionBridge: DesktopToolInteractionBridge,
) {
    fun create(): ToolRegistry = ToolRegistry {
        tools(DesktopToolSet(workspacePath, permissionPreset, interactionBridge))
    }
}
```

- [ ] **Step 4: 重新运行桌面层测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DesktopFileToolSupportTest" --tests "com.agent.shared.agent.DesktopToolRegistryFactoryTest"
```

Expected: PASS。

### Task 4: 接入 `read_file`、`list_dir`、`glob_files`

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopReadWriteTools.kt`
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopGlobTool.kt`
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopGlobToolTest.kt`

- [ ] **Step 1: 写失败测试，锁定 `glob_files` 预算和 `read_file` 跨工作区读取**

```kotlin
class DesktopGlobToolTest {
    @Test
    fun `glob should cap results at requested budget`() = runTest {
        val tool = DesktopGlobTool()
        val result = tool.execute(
            DesktopGlobTool.Args(
                pattern = "**/*.kt",
                path = tempDir.toString(),
                maxResults = 2,
            ),
        )

        assertTrue(result.lines().count() <= 2)
    }
}
```

```kotlin
@Test
fun `read file should return content outside workspace`() = runTest {
    val toolSet = DesktopToolSet(
        workspacePath = "D:\\repo",
        permissionPreset = PermissionPreset.DEFAULT,
        interactionBridge = fakeBridge(),
    )
    val tool = toolSet.readFileTool()
    val result = tool.execute(DesktopReadFileArgs(path = tempFile.toString()))
    assertTrue(result.contains("hello"))
}
```

- [ ] **Step 2: 运行相关桌面测试，确认当前工具未实现**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DesktopGlobToolTest"
```

Expected: FAIL。

- [ ] **Step 3: 实现 `read_file` / `list_dir` / `glob_files`**

```kotlin
class DesktopGlobTool : SimpleTool<DesktopGlobTool.Args>(
    argsType = typeTokenOf<Args>(),
    name = "glob_files",
    description = "按 glob 查找文件",
) {
    @Serializable
    data class Args(
        val pattern: String,
        val path: String = ".",
        val maxResults: Int = 50,
    )

    override suspend fun doExecute(args: Args): String {
        val root = Paths.get(args.path).toAbsolutePath().normalize()
        val matcher = root.fileSystem.getPathMatcher("glob:${normalizeGlob(args.pattern)}")
        val result = mutableListOf<String>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { file ->
                if (result.size >= args.maxResults.coerceAtMost(200)) return@forEach
                if (matcher.matches(root.relativize(file))) {
                    result += file.toString()
                }
            }
        }
        return result.joinToString(separator = "\n")
    }
}
```

```kotlin
@Tool
fun read_file(path: String): String = Files.readString(Paths.get(path))

@Tool
fun list_dir(path: String): String =
    Files.list(Paths.get(path)).use { stream ->
        stream.map { it.fileName.toString() }.sorted().toList().joinToString("\n")
    }
```

- [ ] **Step 4: 重新运行 `glob` 和相关桌面测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DesktopGlobToolTest"
```

Expected: PASS。

### Task 5: 接入 `grep_code`，优先 `rg`，失败回退 JVM 搜索

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopGrepTool.kt`
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopGrepToolTest.kt`

- [ ] **Step 1: 写失败测试，锁定 `partial`、`max_chars` 和 `context_lines` 行为**

```kotlin
class DesktopGrepToolTest {
    @Test
    fun `grep should mark partial when output exceeds max results`() = runTest {
        val tool = DesktopGrepTool()
        val result = tool.execute(
            DesktopGrepTool.Args(
                pattern = "targetSymbol",
                path = fixtureDir.toString(),
                maxResults = 1,
            ),
        )

        assertTrue(result.contains("partial"))
    }

    @Test
    fun `grep should include context lines when requested`() = runTest {
        val tool = DesktopGrepTool()
        val result = tool.execute(
            DesktopGrepTool.Args(
                pattern = "targetSymbol",
                path = fixtureDir.toString(),
                contextLines = 1,
            ),
        )

        assertTrue(result.contains("line"))
    }
}
```

- [ ] **Step 2: 运行 grep 测试，确认工具缺失**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DesktopGrepToolTest"
```

Expected: FAIL。

- [ ] **Step 3: 实现 `grep_code`，包含 `rg` 优先与 JVM 回退**

```kotlin
class DesktopGrepTool : SimpleTool<DesktopGrepTool.Args>(
    argsType = typeTokenOf<Args>(),
    name = "grep_code",
    description = "按关键字或正则搜索代码",
) {
    @Serializable
    data class Args(
        val pattern: String,
        val path: String = ".",
        val glob: String? = null,
        val regex: Boolean = false,
        val caseSensitive: Boolean = true,
        val contextLines: Int = 0,
        val maxResults: Int = 50,
        val headLimit: Int = 20,
        val maxChars: Int = 24_000,
    )

    override suspend fun doExecute(args: Args): String {
        return runRipgrep(args) ?: runJvmFallback(args)
    }
}
```

```kotlin
private fun runRipgrep(args: Args): String? {
    val available = runCatching {
        ProcessBuilder("rg", "--version").start().waitFor() == 0
    }.getOrDefault(false)
    if (!available) return null
    // 这里按 paicli 风格拼接 --json / --glob / -C 参数，超过预算时返回 partial
    return executeRipgrepAndTrim(args)
}
```

- [ ] **Step 4: 重新运行 grep 测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DesktopGrepToolTest"
```

Expected: PASS。

### Task 6: 接入 `write_file`、`edit_file` 及工作区内写入边界

**Files:**
- Modify: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopReadWriteTools.kt`
- Modify: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopFileToolSupportTest.kt`
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopWriteToolsTest.kt`

- [ ] **Step 1: 写失败测试，锁定工作区外写入拒绝和 `EDIT_ALLOW` 自动放行**

```kotlin
class DesktopWriteToolsTest {
    @Test
    fun `write file should reject workspace external path`() = runTest {
        val toolSet = DesktopToolSet(
            workspacePath = workspace.toString(),
            permissionPreset = PermissionPreset.BRAVE,
            interactionBridge = fakeBridge(),
        )

        val error = assertFailsWith<IllegalStateException> {
            toolSet.writeFileTool().execute(
                DesktopWriteFileArgs(
                    path = externalFile.toString(),
                    content = "x",
                ),
            )
        }

        assertTrue(error.message!!.contains("工作区"))
    }
}
```

- [ ] **Step 2: 运行写入工具测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DesktopWriteToolsTest"
```

Expected: FAIL。

- [ ] **Step 3: 实现写入工具与边界校验**

```kotlin
@Tool
fun write_file(path: String, content: String): String {
    require(fileSupport.canWrite(path)) { "只允许修改当前工作区内文件" }
    Files.createDirectories(Paths.get(path).parent)
    Files.writeString(Paths.get(path), content)
    return "ok"
}

@Tool
fun edit_file(path: String, oldText: String, newText: String): String {
    require(fileSupport.canWrite(path)) { "只允许修改当前工作区内文件" }
    val file = Paths.get(path)
    val current = Files.readString(file)
    require(current.contains(oldText)) { "目标文本不存在，无法执行定点替换" }
    Files.writeString(file, current.replaceFirst(oldText, newText))
    return "ok"
}
```

- [ ] **Step 4: 重新运行写入工具测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DesktopWriteToolsTest"
```

Expected: PASS。

### Task 7: 接入 `run_powershell`，只支持 PowerShell 7

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopPowerShellTool.kt`
- Create: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DesktopPowerShellToolTest.kt`

- [ ] **Step 1: 写失败测试，锁定“非 PowerShell 7 直接报不支持”**

```kotlin
class DesktopPowerShellToolTest {
    @Test
    fun `should fail clearly when pwsh is unavailable or not version 7`() = runTest {
        val tool = DesktopPowerShellTool(
            shellVersionProbe = { "PowerShell 5.1.22621.2506" },
            commandRunner = { error("should not run") },
        )

        val result = tool.execute(
            DesktopPowerShellTool.Args(script = "Get-Location"),
        )

        assertTrue(result.contains("仅支持 PowerShell 7"))
    }
}
```

- [ ] **Step 2: 运行 PowerShell 工具测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DesktopPowerShellToolTest"
```

Expected: FAIL。

- [ ] **Step 3: 实现 `run_powershell` 与版本检测**

```kotlin
class DesktopPowerShellTool(
    private val shellVersionProbe: () -> String = {
        ProcessBuilder("pwsh", "-NoLogo", "-Command", "$PSVersionTable.PSVersion.ToString()")
            .start()
            .inputReader()
            .readText()
            .trim()
    },
    private val commandRunner: suspend (Args) -> PowerShellExecutionResult = ::runPowerShell,
) : SimpleTool<DesktopPowerShellTool.Args>(
    argsType = typeTokenOf<Args>(),
    name = "run_powershell",
    description = "执行 PowerShell 7 脚本",
) {
    @Serializable
    data class Args(val script: String, val workingDirectory: String? = null)

    override suspend fun doExecute(args: Args): String {
        val version = shellVersionProbe()
        if (!version.startsWith("7.")) {
            return "当前工具仅支持 PowerShell 7，请先升级后再使用。检测到版本: $version"
        }
        return commandRunner(args).toDisplayString()
    }
}
```

- [ ] **Step 4: 重新运行 PowerShell 工具测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DesktopPowerShellToolTest"
```

Expected: PASS。

### Task 8: 把桌面交互工具与审批逻辑接入 agent gateway

**Files:**
- Modify: `shared/src/desktopMain/kotlin/com/agent/shared/agent/KoogAgentGateway.kt`
- Create: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopToolSet.kt`
- Modify: `shared/src/desktopTest/kotlin/com/agent/shared/agent/KoogAgentGatewayTest.kt`

- [ ] **Step 1: 写失败测试，锁定 `ask_user` 会发出问题事件并等待恢复**

```kotlin
@Test
fun `gateway should emit question requested when ask user tool blocks`() = runTest {
    val bridge = object : DesktopToolInteractionBridge {
        override suspend fun requestQuestion(request: QuestionRequest): String = "Use option A"
        override suspend fun requestApproval(request: ApprovalRequest): Boolean = true
    }

    val gateway = KoogAgentGateway(
        interactionBridge = bridge,
        agentRunner = fakeAskUserAgentRunner(),
    )

    val events = gateway.run(
        AgentRunRequest(
            prompt = "decide",
            profile = openAiProfile(),
            workspacePath = "D:\\repo",
            permissionPreset = PermissionPreset.DEFAULT,
        ),
    ).toList()

    assertTrue(events.any { it is AgentStreamEvent.QuestionRequested })
    assertTrue(events.last() is AgentStreamEvent.Completed)
}
```

- [ ] **Step 2: 运行 gateway 测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.KoogAgentGatewayTest"
```

Expected: FAIL。

- [ ] **Step 3: 升级 gateway，实现问题请求、审批请求和工具执行**

```kotlin
class KoogAgentGateway(
    private val interactionBridge: DesktopToolInteractionBridge,
    private val registryFactory: (AgentRunRequest) -> ToolRegistry = { request ->
        DesktopToolRegistryFactory(
            workspacePath = request.workspacePath,
            permissionPreset = request.permissionPreset,
            interactionBridge = interactionBridge,
        ).create()
    },
) : AgentGateway {
    override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
        emit(AgentStreamEvent.Started)
        val agent = buildDesktopAgent(request, registryFactory(request), interactionBridge)
        agent.run(request.prompt, null)
        emit(AgentStreamEvent.Completed(""))
    }
}
```

```kotlin
class DesktopToolSet(
    private val workspacePath: String,
    private val permissionPreset: PermissionPreset,
    private val interactionBridge: DesktopToolInteractionBridge,
) : ToolSet
```

- [ ] **Step 4: 重新运行 gateway 测试**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.KoogAgentGatewayTest"
```

Expected: PASS。

### Task 9: 在 `ChatWindowState` 中引入 pending question / approval 状态

**Files:**
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`
- Modify: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatWindowStateTest.kt`

- [ ] **Step 1: 写失败测试，锁定问题卡片与恢复同一轮次**

```kotlin
@Test
fun `should keep same turn running while waiting for ask user response`() = runTest(dispatcher) {
    val bridge = RecordingBridge()
    val gateway = fakeQuestionGateway(bridge)
    val state = ChatWindowState(
        sendMessageUseCase = SendMessageUseCase(gateway),
        snapshot = snapshot(),
        projectPath = "D:\\repo",
    )

    state.send("start")
    advanceUntilIdle()

    assertEquals(ExecutionState.WaitingForUserInput, state.ui.activeConversation.executionState)
    assertEquals("Pick one", state.ui.activeConversation.pendingQuestion?.question)

    state.answerPendingQuestion("Option A")
    advanceUntilIdle()

    assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
}
```

- [ ] **Step 2: 运行窗口状态测试**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected: FAIL。

- [ ] **Step 3: 为 `ChatWindowState` 增加 pending 状态和桥接提交入口**

```kotlin
data class PendingQuestionUiState(
    val requestId: String,
    val question: String,
    val options: List<String>,
    val allowFreeText: Boolean = true,
    val answeredText: String? = null,
)
```

```kotlin
fun answerPendingQuestion(answer: String) {
    val pending = ui.activeConversation.pendingQuestion ?: return
    completeQuestion(pending.requestId, answer)
}
```

```kotlin
private fun applyAgentEvent(conversationId: String, event: AgentStreamEvent) {
    when (event) {
        is AgentStreamEvent.QuestionRequested -> mutateConversation(conversationId) { conversation ->
            conversation.copy(
                pendingQuestion = PendingQuestionUiState(
                    requestId = event.request.requestId,
                    question = event.request.question,
                    options = event.request.options,
                ),
                executionState = ExecutionState.WaitingForUserInput,
            )
        }
        else -> { /* 维持现有逻辑 */ }
    }
}
```

- [ ] **Step 4: 重新运行窗口状态测试**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected: PASS。

### Task 10: 在聊天界面渲染问题卡片与审批卡片

**Files:**
- Create: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ToolInteractionCards.kt`
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt`
- Create: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ToolInteractionCardsTest.kt`

- [ ] **Step 1: 写失败测试，锁定卡片文案和控件出现条件**

```kotlin
class ToolInteractionCardsTest {
    @Test
    fun `question card should show options and input field`() {
        val pending = PendingQuestionUiState(
            requestId = "q1",
            question = "Pick one",
            options = listOf("A", "B", "C"),
        )

        val model = buildQuestionCardModel(pending)
        assertEquals("Pick one", model.title)
        assertEquals(3, model.options.size)
        assertTrue(model.allowFreeText)
    }
}
```

- [ ] **Step 2: 运行 UI 卡片测试**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ToolInteractionCardsTest"
```

Expected: FAIL。

- [ ] **Step 3: 抽出卡片组件并接到 `ChatScreen`**

```kotlin
@Composable
fun QuestionCard(
    pending: PendingQuestionUiState,
    onOptionClick: (String) -> Unit,
    onSubmitText: (String) -> Unit,
) {
    Column {
        Text(pending.question)
        pending.options.forEach { option ->
            Button(onClick = { onOptionClick(option) }) {
                Text(option)
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("自定义回答") },
        )
        Button(onClick = { onSubmitText(draft.trim()) }, enabled = draft.isNotBlank()) {
            Text("提交")
        }
    }
}
```

```kotlin
state.ui.activeConversation.pendingQuestion?.let { pending ->
    QuestionCard(
        pending = pending,
        onOptionClick = state::answerPendingQuestion,
        onSubmitText = state::answerPendingQuestion,
    )
}
```

- [ ] **Step 4: 重新运行 UI 卡片测试**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ToolInteractionCardsTest"
```

Expected: PASS。

### Task 11: 把审批矩阵接入 `ChatWindowState` 与工具执行

**Files:**
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`
- Modify: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DesktopToolSet.kt`
- Modify: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatWindowStateTest.kt`

- [ ] **Step 1: 写失败测试，锁定 `DEFAULT` 询问、`EDIT_ALLOW` 写入自动、`BRAVE` 执行自动、`PLAN` 拒绝**

```kotlin
@Test
fun `default should request approval before write tool completes`() = runTest(dispatcher) {
    val state = stateWithPreset(PermissionPreset.DEFAULT)
    state.send("modify file")
    advanceUntilIdle()
    assertEquals(ExecutionState.WaitingForApproval, state.ui.activeConversation.executionState)
}

@Test
fun `plan should fail fast on execute tool`() = runTest(dispatcher) {
    val state = stateWithPreset(PermissionPreset.PLAN)
    state.send("run command")
    advanceUntilIdle()
    assertTrue(state.errorMessage!!.contains("PLAN"))
}
```

- [ ] **Step 2: 运行窗口状态测试**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected: FAIL。

- [ ] **Step 3: 在 `ChatWindowState` 和工具执行路径中落地审批矩阵**

```kotlin
private fun shouldAutoApproveWrite(): Boolean =
    DesktopToolPolicy.canAutoApproveWrite(ui.permissionPreset)

private fun shouldAutoApproveExecute(): Boolean =
    DesktopToolPolicy.canAutoApproveExecute(ui.permissionPreset)
```

```kotlin
if (DesktopToolPolicy.isExecuteDenied(permissionPreset)) {
    return "当前 permission preset=PLAN，禁止执行 PowerShell。"
}
if (!DesktopToolPolicy.canAutoApproveExecute(permissionPreset)) {
    val approved = interactionBridge.requestApproval(
        ApprovalRequest(
            requestId = approvalId,
            toolName = "run_powershell",
            summary = "执行 PowerShell 脚本",
            payloadPreview = args.script,
        ),
    )
    if (!approved) return "用户拒绝执行。"
}
```

- [ ] **Step 4: 重新运行窗口状态测试**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected: PASS。

### Task 12: 最终校验与回归验证

**Files:**
- Check: `shared/src/commonMain/kotlin/com/agent/shared/agent/*.kt`
- Check: `shared/src/desktopMain/kotlin/com/agent/shared/agent/*.kt`
- Check: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/*.kt`
- Check: `shared/src/commonTest/kotlin/com/agent/shared/**/*.kt`
- Check: `shared/src/desktopTest/kotlin/com/agent/shared/**/*.kt`
- Check: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/*.kt`

- [ ] **Step 1: 对修改过的 UI 与 agent 文件跑 IDEA 问题检查**

Run: 使用 IDEA MCP `get_file_problems` 检查以下文件：

```text
shared/src/commonMain/kotlin/com/agent/shared/agent/AgentRunRequest.kt
shared/src/commonMain/kotlin/com/agent/shared/agent/AgentStreamEvent.kt
shared/src/desktopMain/kotlin/com/agent/shared/agent/KoogAgentGateway.kt
composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt
composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt
composeApp/src/desktopMain/kotlin/com/agent/app/ui/ToolInteractionCards.kt
```

Expected: 无 error。

- [ ] **Step 2: 跑共享层与桌面层测试**

Run:

```powershell
.\gradlew.bat :shared:allTests
.\gradlew.bat :composeApp:desktopTest
```

Expected: PASS。

- [ ] **Step 3: 跑全量构建**

Run:

```powershell
.\gradlew.bat build
```

Expected: PASS。

- [ ] **Step 4: 记录最终验证结果**

最终汇报至少包含：

```text
1. 是否已升级到 AIAgent + ToolRegistry
2. 首批只读 / 写入 / 执行工具是否全部接入
3. ask_user 是否已支持候选项 + 自由输入 + 当前轮次恢复
4. 写入是否仍限制在当前工作区内
5. 只读工具是否支持工作区外读取
6. PowerShell 7 检测与不支持提示是否生效
7. shared tests / desktopTest / build 是否通过
```

## Self-Review

- Spec coverage:
  - AIAgent + ToolRegistry 架构：Task 1, Task 2, Task 3, Task 8
  - 只读工具与 `paicli` 风格搜索链：Task 4, Task 5
  - 工作区内写入边界：Task 3, Task 6
  - PowerShell 7 工具：Task 7
  - `ask_user` 内嵌卡片与当前轮次恢复：Task 8, Task 9, Task 10
  - 权限矩阵：Task 1, Task 11
  - 最终验证：Task 12
- Placeholder scan:
  - 没有 `TODO`、`TBD`、`implement later`
  - 每个任务都提供了明确文件路径、测试入口和验证命令
- Type consistency:
  - `QuestionRequest` / `ApprovalRequest` / `DesktopToolInteractionBridge` / `DesktopToolPolicy` 在任务间命名保持一致
  - `workspacePath` 与 `permissionPreset` 统一从 `AgentRunRequest` 向下透传
  - `run_powershell` 明确使用 `PermissionPreset` 的执行矩阵，不与写入矩阵混用
