# 任务完整持久化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将桌面端的完整任务会话持久化到本机 SQLite，并移除未使用的 `paicli` Git 子模块。

**Architecture:** `shared/commonMain` 定义与 UI 无关的任务快照和仓库接口，`shared/jvmMain` 使用 JDBC 实现 SQLite 数据库、迁移和事务。`desktopApp` 将其聊天 UI 状态显式映射为快照，并在状态变化时异步保存、在启动时恢复；没有可恢复执行上下文的任务安全标记为中断。

**Tech Stack:** Kotlin Multiplatform、kotlinx-serialization-json 1.8.1、SQLite、`org.xerial:sqlite-jdbc:3.53.1.0`、JUnit 5、kotlinx-coroutines-test。

## Global Constraints

- 数据库仅运行在 JVM/Desktop：`shared` 的契约和 DTO 放 `commonMain`，JDBC 实现放 `jvmMain`。
- 数据库路径固定为 `%USERPROFILE%\\.mulehang\\tasks.db`，使用 `PRAGMA foreign_keys = ON` 和 `PRAGMA journal_mode = WAL`。
- 持久化完整消息、原始 reasoning、完整工具调用参数和结果；不将这些字段写入普通日志、网络或自动备份。
- 所有生产类、对象、数据类和函数写简短 KDoc；Kotlin 4 空格缩进、尾随逗号、无制表符。
- SQLite I/O 不在 Compose 主线程执行；运行、等待输入和等待审批均不能跨进程续跑，恢复时转为“执行已中断”。
- 不启动桌面应用或开发服务器；不提交 Git 提交，除非用户后续明确授权。
- 持久化失败使用侧栏中的静态、低干扰提示；不增加装饰性动画。

---

## 文件结构

| 文件 | 职责 |
| --- | --- |
| `shared/build.gradle.kts` | 声明 SQLite JDBC 的 JVM 依赖。 |
| `shared/src/commonMain/kotlin/com/agent/shared/chat/persistence/TaskSnapshot.kt` | 定义任务、时间线和 Agent history 的持久化 DTO 与仓库契约。 |
| `shared/src/jvmMain/kotlin/com/agent/shared/chat/persistence/SqliteTaskRepository.kt` | 创建数据库、执行迁移、事务化读写任务快照。 |
| `shared/src/jvmTest/kotlin/com/agent/shared/chat/persistence/SqliteTaskRepositoryTest.kt` | 验证初始化、迁移、完整往返和级联删除。 |
| `desktopApp/src/main/kotlin/com/agent/app/chat/persistence/ChatTaskSnapshotMapper.kt` | 在桌面聊天状态与共享持久化 DTO 之间显式映射所有密封类型。 |
| `desktopApp/src/main/kotlin/com/agent/app/chat/persistence/TaskPersistenceCoordinator.kt` | 串行化后台保存、合并流式更新并提供关闭前 flush。 |
| `desktopApp/src/main/kotlin/com/agent/app/chat/state/ChatWindowUiState.kt` | 承载一个可选的历史加载错误提示。 |
| `desktopApp/src/main/kotlin/com/agent/app/chat/state/ChatWindowState.kt` | 注入持久化协调器、恢复快照、在任务状态改变后安排保存。 |
| `desktopApp/src/main/kotlin/com/agent/app/bootstrap/MulehangDesktopApp.kt` | 创建仓库、异步加载任务并在关闭窗口前 flush。 |
| `desktopApp/src/main/kotlin/com/agent/app/chat/component/TaskSidebar.kt` | 在搜索栏下方显示静态、简短的历史加载错误。 |
| `desktopApp/src/test/kotlin/com/agent/app/chat/persistence/ChatTaskSnapshotMapperTest.kt` | 验证所有时间线与 history 变体的精确映射。 |
| `desktopApp/src/test/kotlin/com/agent/app/chat/state/ChatWindowStateTest.kt` | 验证保存触发、恢复和中断恢复。 |
| `.gitmodules` | 移除 `vendor/paicli` 注册。 |
| `vendor/paicli` | 移除 Git 子模块 gitlink。 |

## Task 1: 定义跨层持久化契约与 SQLite 数据库

**Files:**

- Modify: `shared/build.gradle.kts:20-25`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/chat/persistence/TaskSnapshot.kt`
- Create: `shared/src/jvmMain/kotlin/com/agent/shared/chat/persistence/SqliteTaskRepository.kt`
- Test: `shared/src/jvmTest/kotlin/com/agent/shared/chat/persistence/SqliteTaskRepositoryTest.kt`

**Interfaces:**

- Produces `TaskRepository.loadAll(): List<PersistedTask>`、`saveAll(tasks: List<PersistedTask>)`、`delete(taskId: String)`。
- Produces `PersistedTask`、`PersistedTimelineItem` 与 `PersistedHistoryItem`，其负载是按类型标记的 JSON 字符串而不是 UI 类型。
- Consumed by `ChatTaskSnapshotMapper` 与 `TaskPersistenceCoordinator`。

- [ ] **Step 1: 写仓库往返与级联删除的失败测试。**

```kotlin
@Test
fun `should round trip raw reasoning and full tool output`() = runTest {
    repository.saveAll(listOf(taskWithRawReasoningAndToolOutput()))

    assertEquals(listOf(taskWithRawReasoningAndToolOutput()), repository.loadAll())
}

@Test
fun `should delete a task and its timeline and history rows`() = runTest {
    repository.saveAll(listOf(taskWithRawReasoningAndToolOutput()))
    repository.delete("task-1")

    assertTrue(repository.loadAll().isEmpty())
    assertEquals(0, repository.childRowCount("task-1"))
}
```

- [ ] **Step 2: 运行测试，确认它因缺少持久化类型与仓库实现而失败。**

Run: `./gradlew.bat :shared:jvmTest --tests "com.agent.shared.chat.persistence.SqliteTaskRepositoryTest"`

Expected: 编译失败，提示 `PersistedTask` 或 `SqliteTaskRepository` 未解析。

- [ ] **Step 3: 添加最小的持久化类型与接口。**

```kotlin
data class PersistedTask(
    val id: String,
    val title: String,
    val workspacePath: String,
    val reasoningEffort: String,
    val contextUsageFraction: Float,
    val executionState: String,
    val executionErrorTitle: String? = null,
    val executionErrorMessage: String? = null,
    val attachmentsJson: String,
    val timeline: List<PersistedTimelineItem>,
    val history: List<PersistedHistoryItem>,
)

interface TaskRepository {
    suspend fun loadAll(): List<PersistedTask>
    suspend fun saveAll(tasks: List<PersistedTask>)
    suspend fun delete(taskId: String)
}
```

其中两个 item 类型均包含 `sequence: Int`、`type: String`、`payloadJson: String`；构造函数及接口函数补齐 KDoc。

- [ ] **Step 4: 以 JDBC 实现最小数据库、迁移与事务写入。**

在 `jvmMain.dependencies` 添加：

```kotlin
implementation("org.xerial:sqlite-jdbc:3.53.1.0")
```

`SqliteTaskRepository` 在每个连接建立后执行：

```sql
PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;
CREATE TABLE IF NOT EXISTS schema_migration (
    version INTEGER PRIMARY KEY,
    applied_at INTEGER NOT NULL
);
```

版本 `1` 在单个事务中创建 `task`、`task_timeline_item`、`task_history_item`；子表的 `task_id` 外键指定 `ON DELETE CASCADE`。`saveAll` 用单一事务按 task id 删除再插入三个表，插入子表时按 `sequence` 排序。所有 SQL 使用 `PreparedStatement` 绑定参数；关闭 `Connection`、`Statement` 与 `ResultSet`。

- [ ] **Step 5: 运行仓库测试，确认通过。**

Run: `./gradlew.bat :shared:jvmTest --tests "com.agent.shared.chat.persistence.SqliteTaskRepositoryTest"`

Expected: PASS；测试在临时目录创建数据库，结束时不触及用户目录。

## Task 2: 显式编解码聊天状态的全部内容

**Files:**

- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/persistence/ChatTaskSnapshotMapper.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/persistence/ChatTaskSnapshotMapperTest.kt`

**Interfaces:**

- Consumes `ChatConversationUiState`、`ConversationItem`、`AgentConversationHistoryMessage`、`PersistedTask`。
- Produces `ChatTaskSnapshotMapper.toPersistedTask(conversation)` 和 `toConversation(task)`。
- `toConversation` 负责将不可恢复执行状态转换为 `ExecutionState.Failed(AppError("执行已中断", ...))` 并清除 pending question/approval 与 streaming 索引。

- [ ] **Step 1: 写覆盖全部密封变体的失败测试。**

```kotlin
@Test
fun `should preserve every timeline and history variant`() {
    val source = conversationWith(
        ChatMessageItem(ChatMessage(ChatRole.User, "secret prompt")),
        ReasoningItem(summaryText = "summary", rawText = "raw reasoning", isStreaming = false),
        ToolEventItem(
            toolName = "run_powershell",
            status = ToolEventStatus.Finished,
            preview = "arguments",
            resultPreview = "short result",
            resultDisplay = "complete result",
        ),
    )

    assertEquals(source, ChatTaskSnapshotMapper.toConversation(ChatTaskSnapshotMapper.toPersistedTask(source)))
}
```

另加一例断言 `Running`、`WaitingForUserInput` 和 `WaitingForApproval` 恢复为失败态，且不保留 pending interaction。

- [ ] **Step 2: 运行 mapper 测试，确认它因 mapper 不存在而失败。**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.persistence.ChatTaskSnapshotMapperTest"`

Expected: 编译失败，提示 `ChatTaskSnapshotMapper` 未解析。

- [ ] **Step 3: 实现类型标记的 JSON 编解码。**

使用 `kotlinx.serialization.json.Json`，在 mapper 私有的 `@Serializable` payload DTO 中保存：

- 时间线的 `ChatMessageItem`、`ReasoningItem`、`ToolEventItem` 的所有字段；
- history 的 `User`、`Assistant` 和其 `Text`、`Reasoning`、`ToolCall`、`ToolResult` parts；
- 附件路径和展示名称。

```kotlin
internal object ChatTaskSnapshotMapper {
    fun toPersistedTask(source: ChatConversationUiState): PersistedTask = PersistedTask(
        id = source.id,
        title = source.title,
        workspacePath = source.workspacePath,
        reasoningEffort = source.reasoningEffort.name,
        contextUsageFraction = source.contextUsageFraction,
        executionState = source.executionState.persistenceType(),
        executionErrorTitle = (source.executionState as? ExecutionState.Failed)?.error?.title,
        executionErrorMessage = (source.executionState as? ExecutionState.Failed)?.error?.message,
        attachmentsJson = JSON.encodeToString(source.attachments.map(AttachmentPayload::from)),
        timeline = source.items.mapIndexed(::encodeTimelineItem),
        history = source.history.mapIndexed(::encodeHistoryItem),
    )

    fun toConversation(source: PersistedTask): ChatConversationUiState = ChatConversationUiState(
        id = source.id,
        title = source.title,
        workspacePath = source.workspacePath,
        items = source.timeline.sortedBy(PersistedTimelineItem::sequence).map(::decodeTimelineItem),
        attachments = JSON.decodeFromString(source.attachmentsJson).map(AttachmentPayload::toUiState),
        history = source.history.sortedBy(PersistedHistoryItem::sequence).map(::decodeHistoryItem),
        reasoningEffort = ReasoningEffort.valueOf(source.reasoningEffort),
        executionState = source.toRecoveredExecutionState(),
        streamingAssistantItemIndex = null,
        streamingReasoningItemIndex = null,
        streamingAssistantHistoryIndex = null,
        contextUsageFraction = source.contextUsageFraction,
        pendingQuestion = null,
        pendingApproval = null,
    )
}
```

编码前后不依赖 `ConversationItem` 或 Agent history 的 `@Serializable` 注解。未知 `type`、损坏 JSON 或非法 enum 必须返回可描述的加载失败，而不能静默丢弃内容。

- [ ] **Step 4: 运行 mapper 测试，确认通过。**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.persistence.ChatTaskSnapshotMapperTest"`

Expected: PASS；原始 reasoning、工具参数和完整结果逐字段相等。

## Task 3: 将状态变更异步保存，并在启动和关闭时接入

**Files:**

- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/persistence/TaskPersistenceCoordinator.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/state/ChatWindowUiState.kt:130-136`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/state/ChatWindowState.kt:33-586`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/MulehangDesktopApp.kt:38-107`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/Main.kt:19-47`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/TaskSidebar.kt:130-156`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/state/ChatWindowStateTest.kt:52-1546`

**Interfaces:**

- `TaskPersistenceCoordinator.schedule(tasks: List<ChatConversationUiState>)` 延迟 300ms 合并流式变化；`flush(tasks)` 立即且串行提交；`load()` 在 `Dispatchers.IO` 调用仓库并映射结果。
- `ChatWindowState.restoreTasks(tasks: List<ChatConversationUiState>)` 仅在有已保存任务时替换初始占位任务；`flushPersistence(onFlushed: () -> Unit)` 用于关闭窗口。
- `ChatWindowUiState.persistenceErrorMessage: String?` 由加载失败设置，仅在 `TaskSidebar` 显示。

- [ ] **Step 1: 写状态层保存、恢复和中断恢复的失败测试。**

```kotlin
@Test
fun `should schedule persistence after renaming and deleting a task`() = runTest(dispatcher) {
    val coordinator = recordingCoordinator()
    val state = stateWith(coordinator = coordinator)

    state.renameConversation(state.ui.activeTaskId, "保存后的名称")
    state.deleteConversation(state.ui.activeTaskId)
    advanceUntilIdle()

    assertEquals(listOf("save", "delete"), coordinator.operations)
}

@Test
fun `should restore an in progress task as interrupted`() = runTest(dispatcher) {
    val state = stateWith(coordinator = recordingCoordinator())

    state.restoreTasks(listOf(runningConversation()))

    assertEquals("执行已中断", (state.ui.activeConversation.executionState as ExecutionState.Failed).error.title)
}
```

- [ ] **Step 2: 运行状态层测试，确认新增用例失败。**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.state.ChatWindowStateTest"`

Expected: 编译失败，提示 coordinator 注入或 `restoreTasks` 不存在。

- [ ] **Step 3: 实现后台协调器并将状态变更接入。**

协调器维护一个串行写入 `Mutex`，并只保留最新待写 snapshot。正常保存使用 `delay(300)`，`Completed`、`Failed`、取消、删除和关闭前 flush 调用立即保存。每个数据库调用包在 `withContext(Dispatchers.IO)` 中；失败回传为错误文本，不记录包含敏感负载的异常上下文。

```kotlin
class TaskPersistenceCoordinator(
    private val repository: TaskRepository,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
) {
    private val writeMutex = Mutex()
    private var scheduledWrite: Job? = null

    fun schedule(tasks: List<ChatConversationUiState>) {
        scheduledWrite?.cancel()
        scheduledWrite = scope.launch {
            delay(300)
            persist(tasks)
        }
    }

    fun flush(tasks: List<ChatConversationUiState>, onFlushed: () -> Unit) {
        scope.launch {
            scheduledWrite?.cancel()
            persist(tasks)
            onFlushed()
        }
    }

    private suspend fun persist(tasks: List<ChatConversationUiState>) = writeMutex.withLock {
        runCatching {
            withContext(Dispatchers.IO) {
                repository.saveAll(tasks.map(ChatTaskSnapshotMapper::toPersistedTask))
            }
        }.onFailure { reportError("任务保存失败") }
    }
}
```

在 `ChatWindowState` 中，对以下会改变持久化任务内容的路径调用 `schedule` 或 `flush`：`renameConversation`、`deleteConversation`、`createConversationForWorkspace`、附件增删、推理档位变更、`sendDraft`、取消、问题与审批回答、`applyAgentEvent`、流异常处理。仅选中任务、更新草稿或选择 profile 不触发任务快照写入。

- [ ] **Step 4: 实现启动加载、关闭 flush 和静态失败提示。**

`MulehangDesktopApp` 创建 `SqliteTaskRepository(userHome.resolve(".mulehang/tasks.db"))` 与 coordinator，并在 `LaunchedEffect` 中加载任务。加载成功调用 `restoreTasks`；失败调用 `setPersistenceError("历史任务未加载")` 并保留可用的新任务。

```kotlin
LaunchedEffect(Unit) {
    runCatching { coordinator.load() }
        .onSuccess(windowState::restoreTasks)
        .onFailure { windowState.setPersistenceError("历史任务未加载") }
}

Window(onCloseRequest = {
    chatWindowState.flushPersistence(::exitApplication)
}, state = windowState) {
    MulehangDesktopApp(
        initialProjectRoot = initialProjectRoot,
        desktopWindowState = windowState,
        windowChromeMode = windowChromeMode,
        onCloseRequest = ::exitApplication,
    )
}
```

将窗口关闭回调改为先调用 `windowState.flushPersistence`，完成后再执行原 `onCloseRequest`。`TaskSidebar` 在搜索栏与“新建任务”按钮之间显示一行 `persistenceErrorMessage`，使用已有 `AppMuted`/`AppDanger` 色板、无动画、可复制但不暴露异常堆栈。

- [ ] **Step 5: 运行状态测试与编译，确认通过。**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.state.ChatWindowStateTest"`

Expected: PASS；流式事件被合并写入，终态和关闭前写入立即完成。

Run: `./gradlew.bat :desktopApp:compileKotlin`

Expected: BUILD SUCCESSFUL。

## Task 4: 移除未使用的 `paicli` 子模块并完成回归验证

**Files:**

- Modify: `.gitmodules:1-3`
- Delete: `vendor/paicli` gitlink

**Interfaces:**

- 不产生运行时接口。
- 保留 `vendor/kilocode` 与 `vendor/liquid-glass` 的子模块注册。

- [ ] **Step 1: 在移除前确认没有主工程引用。**

Run: `rg -n --hidden --glob '!vendor/paicli/**' "vendor/paicli|paicli" .`

Expected: 仅 `.gitmodules` 的 `vendor/paicli` 注册项；没有构建、代码或文档引用。

- [ ] **Step 2: 移除 gitlink 和模块注册。**

Run: `git rm -f vendor/paicli`

随后从 `.gitmodules` 删除完整的 `[submodule "vendor/paicli"]` 段，保留 Kilo 与 liquid-glass 段不变。若 `git rm` 已自动修改 `.gitmodules`，只校验结果，不重复编辑。

- [ ] **Step 3: 验证工作树和子模块配置。**

Run: `git submodule status`

Expected: 输出只包含 `vendor/kilocode` 与 `vendor/liquid-glass`，不包含 `vendor/paicli`。

Run: `git diff --check`

Expected: 无空白错误。

- [ ] **Step 4: 运行最终最小回归验证。**

Run: `./gradlew.bat :shared:jvmTest :desktopApp:test :desktopApp:compileKotlin`

Expected: BUILD SUCCESSFUL，所有相关测试通过。

Run: `git status --short`

Expected: 仅本计划列出的源码、测试、Gradle 配置、规格/计划文档、`.gitmodules` 和 `vendor/paicli` gitlink 变更。

## 自检

- 规格覆盖：任务元数据、完整时间线、完整 history、原始 reasoning、完整工具负载、SQLite 迁移、外键、WAL、后台 I/O、失败恢复、启动恢复、关闭 flush、无日志泄漏、`paicli` 移除和验证均映射到 Task 1–4。
- 占位检查：计划不含待定项；每个实现任务给出目标路径、接口、测试、失败验证、最小实现与通过验证。
- 类型一致性：Task 1 定义 `PersistedTask`/`TaskRepository`；Task 2 的 mapper 消费和产生这些类型；Task 3 的 coordinator 使用 mapper 与 repository；Task 4 不依赖运行时类型。
