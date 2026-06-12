# Codex Like Multi Provider Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Compose Desktop 聊天骨架上实现 codex 风格的多 provider 聊天界面，包括按 `pwd` 分组的侧栏、多会话切换、附件区、控制带、权限选择与上下文圆环。

**Architecture:** 以 `ChatWindowState` 为窗口级 UI 状态中心，新增“多会话 + composer 控件状态”两个维度，不改动 `shared` 的最小 agent 调用边界。`ChatScreen` 负责渲染 codex-like 三段式布局，并把菜单、hover、附件与发送动作绑定到 `ChatWindowState` 暴露的状态与命令。

**Tech Stack:** Kotlin 2.4, Compose Multiplatform Desktop, kotlin.test, JUnit 5, existing shared/application state

---

## File Structure

- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`
  - 扩展为多会话与 composer 控制状态中心。
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt`
  - 重写为 codex-like 三段式布局。
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/MulehangDesktopApp.kt`
  - 传入当前项目路径，初始化分组标签与默认会话。
- Modify: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatWindowStateTest.kt`
  - 先写状态层失败测试，再补实现。
- Modify: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatScreenPresentationTest.kt`
  - 补展示文案和分组/圆环/权限映射测试。

### Task 1: 定义窗口级多会话与 composer 行为

**Files:**
- Modify: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatWindowStateTest.kt`
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`

- [ ] **Step 1: 写失败测试，定义多会话、新建会话与附件行为**

```kotlin
@Test
fun `should create a new workspace conversation and switch focus to it`() = runTest(dispatcher) {
    val state = ChatWindowState(
        sendMessageUseCase = SendMessageUseCase(idleGateway()),
        snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
        projectPath = "E:\\abc\\def",
    )

    val originalConversationId = state.ui.activeConversationId
    state.createConversationForWorkspace("E:\\abc\\def")

    assertEquals("def", state.ui.workspaceGroups.single().label)
    assertEquals(2, state.ui.workspaceGroups.single().conversations.size)
    assertNotEquals(originalConversationId, state.ui.activeConversationId)
    assertEquals(emptyList(), state.ui.activeConversation.attachments)
}

@Test
fun `should attach selected files to active conversation draft`() = runTest(dispatcher) {
    val state = ChatWindowState(
        sendMessageUseCase = SendMessageUseCase(idleGateway()),
        snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
        projectPath = "E:\\abc\\def",
    )

    state.attachFiles(listOf("D:\\tmp\\ChatScreen.kt", "D:\\tmp\\design.png"))

    assertEquals(listOf("ChatScreen.kt", "design.png"), state.ui.activeConversation.attachments.map { it.name })
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected:

```text
FAIL
Unresolved reference: ui / createConversationForWorkspace / attachFiles / projectPath
```

- [ ] **Step 3: 在 `ChatWindowState` 中实现最小多会话 UI 状态**

```kotlin
data class ChatAttachmentUiState(
    val path: String,
    val name: String,
)

data class ChatConversationUiState(
    val id: String,
    val title: String,
    val workspacePath: String,
    val items: List<ConversationItem> = emptyList(),
    val attachments: List<ChatAttachmentUiState> = emptyList(),
    val executionState: ExecutionState = ExecutionState.Idle,
    val streamingAssistantItemIndex: Int? = null,
    val streamingReasoningItemIndex: Int? = null,
)

data class WorkspaceConversationGroupUiState(
    val workspacePath: String,
    val label: String,
    val conversations: List<ChatConversationUiState>,
)
```

- [ ] **Step 4: 运行状态测试确认通过**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 2: 定义 provider / model / permission / context ring 展示规则

**Files:**
- Modify: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatScreenPresentationTest.kt`
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt`
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`

- [ ] **Step 1: 写失败测试，约束展示辅助函数**

```kotlin
@Test
fun `should map workspace path to terminal folder label`() {
    assertEquals("def", buildWorkspaceLabel("E:\\abc\\def"))
    assertEquals("repo-x", buildWorkspaceLabel("D:\\work\\repo-x"))
}

@Test
fun `should expose provider label and reasoning support from profile`() {
    assertEquals("OpenAI Compatible", providerLabel(ProviderType.OPENAI_CHAT_COMPLETIONS))
    assertEquals(true, modelSupportsReasoning("deepseek-r1"))
    assertEquals(false, modelSupportsReasoning("claude-sonnet-4"))
}

@Test
fun `should keep context usage value inside tooltip text only`() {
    assertEquals("58% remaining", buildContextTooltip(0.58f))
}
```

- [ ] **Step 2: 运行展示测试确认失败**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatScreenPresentationTest"
```

Expected:

```text
FAIL
Unresolved reference: buildWorkspaceLabel / providerLabel / modelSupportsReasoning / buildContextTooltip
```

- [ ] **Step 3: 实现最小展示辅助函数和权限枚举**

```kotlin
enum class PermissionPreset {
    AUTO,
    DEFAULT,
    EDIT_ALLOW,
    PLAN,
    BRAVE,
}

internal fun buildWorkspaceLabel(path: String): String =
    path.trimEnd('\\', '/').substringAfterLast('\\').substringAfterLast('/')

internal fun providerLabel(providerType: ProviderType): String = when (providerType) {
    ProviderType.OPENAI_RESPONSES -> "OpenAI"
    ProviderType.OPENAI_CHAT_COMPLETIONS -> "OpenAI Compatible"
    ProviderType.ANTHROPIC -> "Anthropic"
    ProviderType.GOOGLE -> "Google"
}

internal fun modelSupportsReasoning(model: String): Boolean =
    listOf("r1", "reason", "thinking").any { token -> model.contains(token, ignoreCase = true) }

internal fun buildContextTooltip(usageFraction: Float): String =
    "${(usageFraction * 100).toInt()}% remaining"
```

- [ ] **Step 4: 运行展示测试确认通过**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatScreenPresentationTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 3: 重写 ChatScreen 为 codex-like 三段式布局

**Files:**
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt`

- [ ] **Step 1: 基于现有状态实现侧栏 + 主区 + composer 布局**

```kotlin
@Composable
fun ChatScreen(state: ChatWindowState) {
    Row(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        WorkspaceSidebar(
            groups = state.ui.workspaceGroups,
            activeConversationId = state.ui.activeConversationId,
            onConversationSelected = state::selectConversation,
            onCreateConversation = state::createConversationForWorkspace,
        )
        ChatWorkspacePanel(
            ui = state.ui,
            errorMessage = state.errorMessage,
            onDraftChanged = state::updateDraft,
            onSend = state::sendDraft,
            onPermissionChanged = state::updatePermission,
            onProviderSelected = state::selectProfile,
            onAttachFiles = state::requestAttachments,
        )
    }
}
```

- [ ] **Step 2: 保留现有消息块渲染逻辑，只把它嵌入新布局**

```kotlin
@Composable
private fun ConversationTimeline(conversation: ChatConversationUiState) {
    LazyColumn {
        items(conversation.items) { item ->
            when (item) {
                is ChatMessageItem -> ChatMessageBlock(item)
                is ReasoningItem -> ReasoningBlock(item)
                is ToolEventItem -> ToolEventBlock(item)
            }
        }
    }
}
```

- [ ] **Step 3: 为空态和对话态分别渲染中央内容**

```kotlin
if (conversation.items.isEmpty()) {
    LandingState(
        workspaceLabel = state.ui.activeWorkspaceLabel,
        capabilities = defaultCapabilityCards(),
    )
} else {
    ConversationTimeline(conversation)
}
```

### Task 4: 把发送动作切到“当前活动会话”

**Files:**
- Modify: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatWindowStateTest.kt`
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`

- [ ] **Step 1: 写失败测试，确保发送只影响当前活动会话**

```kotlin
@Test
fun `should append messages only to the active conversation`() = runTest(dispatcher) {
    val state = ChatWindowState(
        sendMessageUseCase = SendMessageUseCase(streamingGateway()),
        snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
        projectPath = "E:\\abc\\def",
    )
    val firstConversationId = state.ui.activeConversationId
    state.createConversationForWorkspace("E:\\abc\\def")
    val secondConversationId = state.ui.activeConversationId

    state.updateDraft("hello")
    state.sendDraft()
    advanceUntilIdle()

    val firstConversation = state.findConversation(firstConversationId)
    val secondConversation = state.findConversation(secondConversationId)
    assertEquals(0, firstConversation.items.size)
    assertEquals(2, secondConversation.items.filterIsInstance<ChatMessageItem>().size)
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected:

```text
FAIL
current implementation still writes into a single global ConversationState
```

- [ ] **Step 3: 将现有流式拼接逻辑迁移到活动会话**

```kotlin
fun sendDraft() {
    val prompt = ui.draft.trim()
    if (prompt.isBlank()) return
    mutateActiveConversation { conversation ->
        conversation.copy(
            items = conversation.items + ChatMessageItem(ChatMessage(ChatRole.User, prompt)),
            executionState = ExecutionState.Running,
            attachments = emptyList(),
        )
    }
    scope.launch {
        sendMessageUseCase(prompt, activeProfile).collect { event ->
            applyAgentEventToActiveConversation(event)
        }
    }
}
```

- [ ] **Step 4: 运行状态测试确认通过**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 5: 验证与收尾

**Files:**
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/MulehangDesktopApp.kt`
- Verify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatScreen.kt`
- Verify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`

- [ ] **Step 1: 让 `MulehangDesktopApp` 注入当前项目路径**

```kotlin
val projectRoot = remember { DesktopProjectRootResolver.resolve(Paths.get("")) }
val windowState = remember(snapshotState.value, projectRoot) {
    ChatWindowState(
        sendMessageUseCase = SendMessageUseCase(KoogAgentGateway()),
        snapshot = snapshotState.value,
        projectPath = projectRoot.toString(),
    )
}
```

- [ ] **Step 2: 运行 composeApp 相关测试**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 运行全量构建验证**

Run:

```powershell
.\gradlew.bat build
```

Expected:

```text
BUILD SUCCESSFUL
```
