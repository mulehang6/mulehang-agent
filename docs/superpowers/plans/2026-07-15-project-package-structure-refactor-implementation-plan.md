# Project Package Structure Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有 UI、配置格式和 Agent 执行语义的前提下，将 `shared` 与 `desktopApp` 整理为功能域优先的包结构，并拆分四个职责过宽的生产文件。

**Architecture:** 保持 `shared + desktopApp` 双模块不变。`shared` 按 `agent`、`chat`、`session`、`settings`、`tool` 组织，平台实现继续由 KMP 源码集隔离；`desktopApp` 按 `bootstrap`、`chat`、`tool`、`design`、`platform` 组织，并将状态归并、展示转换和 Composable 分离。

**Tech Stack:** Kotlin 2.2.20、Kotlin Multiplatform、Compose Multiplatform Desktop、JetBrains Koog 1.0.0、kotlinx.coroutines、kotlinx.serialization、JUnit 5、Gradle Wrapper 9.6.0、JDK 21。

**Design Reference:** `docs/superpowers/specs/2026-07-15-project-package-structure-refactor-design.md`

## Global Constraints

- 只修改根工程的 `shared`、`desktopApp`、当前主线文档及对应测试；不修改 `agent-ui-prototype1` 与 `vendor`。
- 不新增 Gradle 模块、生产依赖、配置项或兼容转发层。
- `desktopApp` 可以依赖 `shared`，`shared` 不得依赖 `desktopApp`。
- `commonMain` 不得依赖 JVM、Compose Desktop 或 Koog 具体实现。
- 保持 UI、配置格式、Agent 执行语义、工具事件顺序和错误语义不变。
- 文件迁移和内容修改优先使用 `apply_patch`；符号重命名优先使用 IDEA rename refactoring。
- 每批代码改动后运行 IDEA problems 检查，不得带着明确 error 进入下一任务。
- 不启动 Desktop 应用、Vite 开发服务器或其他长期运行服务。
- 未经用户明确授权不得创建 Git commit；每个任务以 `git diff --check` 和 `git status --short` 作为变更检查点。
- 若发现直接相关缺陷，先增加稳定复现的回归测试，再进行最小修复，并在最终说明中单列。

## Locked File Map

### shared commonMain

| Existing file | Target file/package |
| --- | --- |
| `agent/AgentConversationHistory.kt` | `agent/api/AgentConversationHistory.kt` |
| `agent/AgentGateway.kt` | `agent/api/AgentGateway.kt` |
| `agent/AgentRunRequest.kt` | `agent/api/AgentRunRequest.kt`，`ReasoningEffort` 与请求保持同包 |
| `agent/AgentStreamEvent.kt` | `agent/api/AgentStreamEvent.kt` |
| `agent/ChatRoleMapper.kt` | `agent/prompt/ChatRoleMapper.kt` |
| `agent/MulehangPromptExecutor.kt` | `agent/prompt/MulehangPromptExecutor.kt` |
| `application/SendMessageUseCase.kt` | `chat/usecase/SendMessageUseCase.kt` |
| `application/AppSessionRepository.kt` | `session/AppSessionRepository.kt` |
| `application/AppSessionSnapshot.kt` | `session/AppSessionSnapshot.kt` |
| `application/LoadAppSessionUseCase.kt` | `session/LoadAppSessionUseCase.kt` |
| `config/ConfigLayer.kt`、`ConfigProfile.kt`、`ModelLimit.kt`、`ModelProfile.kt`、`ProviderProfile.kt`、`ProviderType.kt`、`SettingsDocument.kt` | `settings/model/` 同名文件 |
| `exceptions/IllegalConfigExceptions.kt` | `settings/model/IllegalConfigExceptions.kt` |
| `config/ModelCapabilitiesResolver.kt`、`ProfileSelectionResolver.kt`、`SettingsMerger.kt` | `settings/resolver/` 同名文件 |
| `state/AppError.kt`、`ChatMessage.kt`、`ChatMessageItem.kt`、`ChatRole.kt`、`ConversationItem.kt`、`ConversationState.kt`、`ExecutionState.kt`、`ReasoningItem.kt`、`ToolEventItem.kt`、`ToolEventStatus.kt` | `chat/model/` 同名文件 |
| `state/PermissionPreset.kt` | `tool/model/PermissionPreset.kt` |
| `agent/DesktopToolEventModels.kt` | `tool/model/DesktopToolEventModels.kt` |
| `agent/DesktopToolInteractionBridge.kt` | `tool/interaction/DesktopToolInteractionBridge.kt` |
| `agent/DesktopToolPolicy.kt` | `tool/policy/DesktopToolPolicy.kt` |
| `tool/UpdatePlanPreviewParser.kt` | `tool/plan/UpdatePlanPreviewParser.kt` |

`ToolEventItem` 和 `ToolEventStatus` 必须与密封的 `ConversationItem` 同处 `chat/model`，不得为了目录对称解除 `ConversationItem` 的 `sealed` 约束。

### shared jvmMain

| Existing file | Target file/package |
| --- | --- |
| `application/DesktopAppSessionRepository.kt` | `session/DesktopAppSessionRepository.kt` |
| `state/DesktopUiStateStore.kt` | `session/persistence/DesktopUiStateStore.kt` |
| `config/DesktopEnvironmentOverrides.kt`、`DesktopPathResolver.kt`、`DesktopSettingsRepository.kt` | `settings/persistence/` 同名文件 |
| `agent/DesktopFileToolSupport.kt`、`DesktopGlobTool.kt`、`DesktopGrepTool.kt`、`DesktopPowerShellTool.kt`、`DesktopReadWriteTools.kt`、`DesktopToolRegistryFactory.kt`、`DesktopToolSet.kt` | `tool/runtime/` 同名文件 |
| `agent/DesktopPromptExecutorFactory.kt` | `agent/koog/DesktopPromptExecutorFactory.kt` |
| `agent/KoogAgentGateway.kt` | 拆为 `agent/koog/` 下五个文件 |
| `agent/DeepSeekChatCompletionsStreamer.kt` | 拆为 `agent/provider/deepseek/` 下五个文件 |

### desktopApp main

| Existing file | Target file/package |
| --- | --- |
| `Main.kt`、`MulehangDesktopApp.kt`、`DesktopProjectRootResolver.kt` | `bootstrap/` 同名文件 |
| `ui/ChatWindowState.kt` | 拆为 `chat/state/` 下六个文件 |
| `ui/ChatScreen.kt` | 拆为 `chat/component/`、`chat/presentation/`、`chat/export/` |
| `ui/DesktopToolInteractionCoordinator.kt` | `tool/interaction/DesktopToolInteractionCoordinator.kt` |
| `ui/ToolInteractionCards.kt` | `tool/component/ToolInteractionCards.kt` |
| `ui/RingUiShells.kt` | `design/RingUiShells.kt` |

---

### Task 1: 建立基线并迁移 settings 功能域

**Files:**
- Move: `shared/src/commonMain/kotlin/com/agent/shared/config/*.kt`
- Move: `shared/src/commonMain/kotlin/com/agent/shared/exceptions/IllegalConfigExceptions.kt`
- Create target packages: `shared/src/commonMain/kotlin/com/agent/shared/settings/model/`、`settings/resolver/`
- Move tests: `shared/src/commonTest/kotlin/com/agent/shared/config/*.kt`
- Modify: all production and test imports that reference `com.agent.shared.config` or `com.agent.shared.exceptions.IllegalConfigExceptions`

**Interfaces:**
- Produces: `com.agent.shared.settings.model.ConfigProfile`、`ProviderType`、`SettingsDocument`、`IllegalConfigExceptions`
- Produces: `com.agent.shared.settings.resolver.SettingsMerger`、`ProfileSelectionResolver`、`ModelCapabilitiesResolver`
- Preserves: all existing constructors, enum entries, resolver signatures and JSON serial names

- [ ] **Step 1: Read run configurations and capture the green baseline**

Use IDEA `get_run_configurations`, then run because no project run configuration covers both modules:

```powershell
.\gradlew.bat :shared:jvmTest :desktopApp:test
```

Expected: `BUILD SUCCESSFUL` and all existing tests pass before file movement.

- [ ] **Step 2: Move settings model files with unchanged declarations except package names**

Use `apply_patch` moves. Each target starts with:

```kotlin
package com.agent.shared.settings.model
```

Move `ConfigLayer.kt`, `ConfigProfile.kt`, `ModelLimit.kt`, `ModelProfile.kt`, `ProviderProfile.kt`, `ProviderType.kt`, `SettingsDocument.kt`, and `IllegalConfigExceptions.kt`. Preserve all type names, property names, annotations and bodies.

- [ ] **Step 3: Move resolver files and resolver tests**

Use this package in production files:

```kotlin
package com.agent.shared.settings.resolver
```

Use this package in tests:

```kotlin
package com.agent.shared.settings.resolver
```

Move `ModelCapabilitiesResolver.kt`, `ProfileSelectionResolver.kt`, `SettingsMerger.kt` and their three tests. Update `ReasoningEffort` import to the final API path from Task 2 only after Task 2; during this task keep the current symbol available through its current package so the branch remains compilable.

- [ ] **Step 4: Update project-wide settings imports and verify**

Use IDEA structured search for:

```text
com.agent.shared.config
com.agent.shared.exceptions.IllegalConfigExceptions
```

Update every production/test import to `settings.model`, `settings.resolver`, or the later `settings.persistence` target as appropriate. Do not change serialization names or settings JSON.

Run:

```powershell
.\gradlew.bat :shared:jvmTest :desktopApp:test
git diff --check
git status --short
```

Expected: tests pass; no whitespace errors; only intended structure files are changed.

### Task 2: 迁移 Agent API、聊天模型、用例与 session

**Files:**
- Move: common Agent API and prompt files from the Locked File Map
- Move: all `shared/state` files except `PermissionPreset.kt` into `chat/model`
- Move: `SendMessageUseCase.kt` into `chat/usecase`
- Move: application session files into `session`
- Move tests: Agent prompt/API, application and session tests to matching packages

**Interfaces:**
- Produces: `AgentGateway.run(request: AgentRunRequest): Flow<AgentStreamEvent>`
- Produces: `AgentRunRequest(prompt, profile, reasoningEffort, history, workspacePath, permissionPreset)` and `ReasoningEffort`
- Produces: `SendMessageUseCase.invoke(request: AgentRunRequest): Flow<AgentStreamEvent>`
- Produces: `AppSessionRepository`、`AppSessionSnapshot`、`LoadAppSessionUseCase`
- Preserves: sealed `ConversationItem` hierarchy and all `AgentStreamEvent` subclasses

- [ ] **Step 1: Move Agent contracts and prompt code**

Target package declarations:

```kotlin
package com.agent.shared.agent.api
```

for `AgentConversationHistory.kt`, `AgentGateway.kt`, `AgentRunRequest.kt`, `AgentStreamEvent.kt`; and:

```kotlin
package com.agent.shared.agent.prompt
```

for `ChatRoleMapper.kt` and `MulehangPromptExecutor.kt`. Keep `ReasoningEffort` in `AgentRunRequest.kt` so the request contract remains cohesive.

- [ ] **Step 2: Move the sealed conversation model as one atomic patch**

Move all listed chat model files together and use:

```kotlin
package com.agent.shared.chat.model
```

Do not change `sealed interface ConversationItem`, `ConversationItem.Kind`, subclass names, constructor properties or enum entries.

- [ ] **Step 3: Move the message use case and session contracts**

Use:

```kotlin
package com.agent.shared.chat.usecase
```

for `SendMessageUseCase.kt`, and:

```kotlin
package com.agent.shared.session
```

for `AppSessionRepository.kt`, `AppSessionSnapshot.kt`, `LoadAppSessionUseCase.kt`. Preserve public signatures and diagnostic text.

- [ ] **Step 4: Move corresponding common tests and repair imports**

Move:

- `ChatRoleMappingTest.kt`, `MulehangPromptExecutorTest.kt` → `agent/prompt/`
- `SendMessageUseCaseTest.kt` → `chat/usecase/`
- `LoadAppSessionUseCaseTest.kt` → `session/`

Keep behavior assertions unchanged. Update all main and test imports through IDEA structured search.

- [ ] **Step 5: Verify common model/API migration**

Run:

```powershell
.\gradlew.bat :shared:jvmTest :desktopApp:test
git diff --check
```

Expected: all tests pass and there are no files left under `shared/.../application`, `shared/.../state`, or the former common `agent` root except files scheduled for Task 3.

### Task 3: 迁移 common 工具模型、交互、策略与计划解析

**Files:**
- Move: `PermissionPreset.kt`
- Move: `DesktopToolEventModels.kt`
- Move: `DesktopToolInteractionBridge.kt`
- Move: `DesktopToolPolicy.kt`
- Move: `UpdatePlanPreviewParser.kt`
- Move tests: `DesktopToolInteractionBridgeTest.kt`、`DesktopToolPolicyTest.kt`
- Modify: imports in shared and desktop code

**Interfaces:**
- Produces: `QuestionRequest`、`ApprovalRequest`、`PermissionPreset`
- Produces: `DesktopToolInteractionBridge.requestQuestion/requestApproval`
- Produces: `DesktopToolPolicy.canRunRead/canAutoApproveWrite/canAutoApproveExecute/isWriteDenied/isExecuteDenied`
- Produces: existing `parseUpdatePlanPreview` signature unchanged

- [ ] **Step 1: Move model, interaction, policy and plan files**

Use exact packages:

```kotlin
package com.agent.shared.tool.model
package com.agent.shared.tool.interaction
package com.agent.shared.tool.policy
package com.agent.shared.tool.plan
```

Move the mapped files with unchanged bodies. `DesktopToolInteractionBridge` imports request models from `tool.model`; `DesktopToolPolicy` imports `PermissionPreset` from `tool.model`.

- [ ] **Step 2: Move tool common tests and update imports**

Move the two tests to matching `tool/interaction` and `tool/policy` paths. Update `AgentRunRequest`, desktop state and JVM tool imports to `tool.model.PermissionPreset`.

- [ ] **Step 3: Verify common tool migration**

Run:

```powershell
.\gradlew.bat :shared:jvmTest :desktopApp:test
```

Expected: `BUILD SUCCESSFUL`; no production import starts with `com.agent.shared.state`, `com.agent.shared.application`, `com.agent.shared.config`, or `com.agent.shared.exceptions`.

### Task 4: 迁移 JVM settings、session 与桌面工具实现

**Files:**
- Move: `shared/src/jvmMain/.../config/*.kt` → `settings/persistence/`
- Move: `DesktopAppSessionRepository.kt` → `session/`
- Move: `DesktopUiStateStore.kt` → `session/persistence/`
- Move: seven desktop tool runtime files → `tool/runtime/`
- Move: matching JVM tests to matching packages

**Interfaces:**
- Preserves: `DesktopSettingsRepository.loadResolvedProfiles()` and persistence behavior
- Preserves: `DesktopAppSessionRepository : AppSessionRepository`
- Preserves: all `DesktopToolSet` tool names and argument signatures
- Preserves: `DesktopToolRegistryFactory.create(): ToolRegistry`

- [ ] **Step 1: Move JVM settings and session persistence**

Use packages:

```kotlin
package com.agent.shared.settings.persistence
package com.agent.shared.session
package com.agent.shared.session.persistence
```

Move production files and `DesktopSettingsRepositoryTest.kt`、`DesktopUiStateStoreTest.kt`. Update imports only; do not change paths, JSON formats or environment precedence.

- [ ] **Step 2: Move desktop tool runtime as one cohesive package**

Move `DesktopFileToolSupport.kt`, `DesktopGlobTool.kt`, `DesktopGrepTool.kt`, `DesktopPowerShellTool.kt`, `DesktopReadWriteTools.kt`, `DesktopToolRegistryFactory.kt`, and `DesktopToolSet.kt` to:

```kotlin
package com.agent.shared.tool.runtime
```

Keep Koog annotations, tool names (`read_file`, `list_dir`, `glob_files`, `grep_code`, `write_file`, `edit_file`, `run_powershell`, `say_to_user`, `ask_user`, `exit`) and permission behavior unchanged.

- [ ] **Step 3: Move all desktop tool JVM tests**

Move every test beginning with `DesktopFile`、`DesktopGlob`、`DesktopGrep`、`DesktopPowerShell`、`DesktopReadOnly`、`DesktopWrite` or `DesktopToolRegistry` to `shared/src/jvmTest/kotlin/com/agent/shared/tool/runtime/`. Preserve test bodies and temporary-directory cleanup.

- [ ] **Step 4: Verify platform implementation migration**

Run:

```powershell
.\gradlew.bat :shared:jvmTest
```

Expected: all settings, session and desktop tool tests pass; IDEA problems reports no error in moved files.

### Task 5: 拆分 ChatWindowState 与桌面状态测试

**Files:**
- Move/Modify: `desktopApp/src/main/kotlin/com/agent/app/ui/ChatWindowState.kt`
- Create: `desktopApp/src/main/kotlin/com/agent/app/chat/state/ChatWindowUiState.kt`
- Create: `.../AgentEventReducer.kt`
- Create: `.../ConversationHistoryReducer.kt`
- Create: `.../ConversationFactory.kt`
- Create: `.../ContextUsageEstimator.kt`
- Create/Modify tests under `desktopApp/src/test/kotlin/com/agent/app/chat/state/`

**Interfaces:**
- Produces: `class ChatWindowState` with its current public operations unchanged
- Produces: `internal fun reduceAgentEvent(conversation: ChatConversationUiState, event: AgentStreamEvent, contextWindow: Int?): ChatConversationUiState`
- Produces: existing history mutation helpers with explicit `ChatConversationUiState` input/output
- Produces: `resolveContextWindow` and `estimateContextUsage` as deterministic functions

- [ ] **Step 1: Move UI state models without changing properties**

Move `ChatAttachmentUiState`, `PendingQuestionUiState`, `PendingApprovalUiState`, `ChatConversationUiState`, `WorkspaceConversationGroupUiState`, `ChatTaskGroup`, `ChatTaskListItemUiState`, `ChatTaskSectionUiState`, and `ChatWindowUiState` into `ChatWindowUiState.kt` with:

```kotlin
package com.agent.app.chat.state
```

Move `toConversationState` with `ChatConversationUiState`. Update package names in existing tests; run `:desktopApp:test` before further extraction.

- [ ] **Step 2: Add reducer-focused regression tests before extraction**

Move existing event behavior cases from `ChatWindowStateTest.kt` into `AgentEventReducerTest.kt`. Cover exactly:

```kotlin
reduceAgentEvent(conversation, AgentStreamEvent.TextDelta("delta"), contextWindow)
reduceAgentEvent(conversation, AgentStreamEvent.ReasoningDelta(summary, rawText), contextWindow)
reduceAgentEvent(conversation, AgentStreamEvent.ToolCallStarted(id, name, preview), contextWindow)
reduceAgentEvent(conversation, AgentStreamEvent.Completed(text), contextWindow)
reduceAgentEvent(conversation, AgentStreamEvent.Failed(reason), contextWindow)
```

Assertions must preserve message ordering, no duplicate completion text, reasoning closure, tool failure placement and execution state.

- [ ] **Step 3: Extract the pure agent event reducer**

Create this entry point:

```kotlin
internal fun reduceAgentEvent(
    conversation: ChatConversationUiState,
    event: AgentStreamEvent,
    contextWindow: Int?,
): ChatConversationUiState
```

Move the existing append/complete/failure functions into the reducer file and replace implicit `activeContextWindow()` reads with the explicit `contextWindow` parameter. `ChatWindowState` remains responsible for pending question/approval ownership IDs and calls the reducer inside `mutateConversation`.

- [ ] **Step 4: Extract history, factory and context functions**

Move history-only functions into `ConversationHistoryReducer.kt`; move `initialUiState`, `newConversation`, empty-conversation detection and title construction into `ConversationFactory.kt`; move `resolveContextWindow`, `estimateContextUsage` and `estimateTokens` into `ContextUsageEstimator.kt`. Preserve formulas and default values exactly.

- [ ] **Step 5: Reduce ChatWindowState to orchestration and verify**

`ChatWindowState.kt` retains constructor dependencies, `ui`, public user actions, coroutine jobs, active-profile selection, pending interaction ownership and calls to extracted pure functions.

Run:

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.state.*"
.\gradlew.bat :desktopApp:test
```

Expected: all state tests pass, including cancellation, task switching, completion deduplication and pending interaction cases.

### Task 6: 拆分 DeepSeek Provider 实现

**Files:**
- Move/Modify: `DeepSeekChatCompletionsStreamer.kt`
- Create: `DeepSeekProtocolModels.kt`
- Create: `DeepSeekRequestMapper.kt`
- Create: `DeepSeekResponseDecoder.kt`
- Create: `DeepSeekSseClient.kt`
- Move/Modify: `DeepSeekChatCompletionsStreamerTest.kt`

**Interfaces:**
- Produces: `DeepSeekChatCompletionsStreamer.stream(...)` overloads unchanged
- Produces: `internal fun buildDeepSeekRequest(request: AgentRunRequest): DeepSeekChatCompletionRequest`
- Produces: prompt overload of `buildDeepSeekRequest`
- Produces: `DeepSeekResponseDecoder.decode(raw: String): DeepSeekChatCompletionChunk`
- Preserves: injected `chunkRunner` seam used by tests

- [ ] **Step 1: Move the existing DeepSeek test package and verify it remains green**

Move the test to `com.agent.shared.agent.provider.deepseek`, update imports, and run:

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.agent.provider.deepseek.DeepSeekChatCompletionsStreamerTest"
```

Expected: PASS before implementation is split.

- [ ] **Step 2: Extract protocol DTOs and decoder**

Move every `DeepSeek*` serializable request/response data class to `DeepSeekProtocolModels.kt`. Create:

```kotlin
internal object DeepSeekResponseDecoder {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun decode(raw: String): DeepSeekChatCompletionChunk =
        json.decodeFromString(DeepSeekChatCompletionChunk.serializer(), raw)
}
```

Use the same configured `Json` instance/options currently used by the companion decoder; do not change serial names or nullable defaults.

- [ ] **Step 3: Extract request mapping**

Move history mapping, Prompt mapping, tool schema mapping and request diagnostics to `DeepSeekRequestMapper.kt` with top-level internal functions:

```kotlin
internal fun buildDeepSeekRequest(request: AgentRunRequest): DeepSeekChatCompletionRequest

internal fun buildDeepSeekRequest(
    prompt: Prompt,
    config: ConfigProfile,
    reasoningEffort: ReasoningEffort?,
    tools: List<ToolDescriptor> = emptyList(),
): DeepSeekChatCompletionRequest
```

Update tests from `streamer.buildRequest(...)` to the top-level functions while preserving expected payload assertions.

- [ ] **Step 4: Extract the SSE client and leave Streamer as orchestration**

Move HTTP client creation and SSE collection to `DeepSeekSseClient.kt`. `DeepSeekChatCompletionsStreamer` keeps `emitAllFrames`, its public/internal stream overloads, logging, the injected `chunkRunner`, and calls `buildDeepSeekRequest`.

- [ ] **Step 5: Verify DeepSeek behavior**

Run:

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.agent.provider.deepseek.*"
.\gradlew.bat :shared:jvmTest
```

Expected: reasoning deltas, text deltas, tool calls, usage metadata, request diagnostics and `[DONE]` filtering retain existing results.

### Task 7: 拆分 Koog Gateway、Prompt 映射与流累积

**Files:**
- Move/Modify: `KoogAgentGateway.kt`
- Move: `DesktopPromptExecutorFactory.kt`
- Create: `KoogStreamingStrategy.kt`
- Create: `KoogPromptMapper.kt`
- Create: `KoogAssistantMessageMapper.kt`
- Create: `KoogStreamAccumulators.kt`
- Move/Modify: `KoogAgentGatewayTest.kt`、`KoogPromptTest.kt`、`DesktopKoogHttpClientFactoryProviderTest.kt`

**Interfaces:**
- Preserves: `KoogAgentGateway : AgentGateway`
- Produces: existing `buildAgentPrompt` and `buildConversationMessages`
- Produces: existing `appendAssistantMessageToPrompt` and assistant completion helpers
- Produces: existing `collectAssistantMessageFromStream`
- Preserves: DeepSeek routing only for DeepSeek chat-completions profiles

- [ ] **Step 1: Move Koog files/tests into the final package**

Use:

```kotlin
package com.agent.shared.agent.koog
```

Move `KoogAgentGateway.kt`, `DesktopPromptExecutorFactory.kt`, and the three Koog tests. Update imports for `agent.api`, `agent.prompt`, `settings.model`, `tool.interaction`, `tool.runtime`, and `agent.provider.deepseek`.

- [ ] **Step 2: Extract prompt and history mapping**

Move `buildAgentPrompt`, `buildConversationMessages`, `AgentConversationHistoryMessage.toKoogMessages`, assistant-history conversion and historical tool-call matching to `KoogPromptMapper.kt`. Preserve message order, missing tool-result insertion and timestamps.

- [ ] **Step 3: Extract stream accumulation**

Move `collectAssistantMessageFromStream`, `TextAccumulator`, `ReasoningAccumulator`, `ToolCallAccumulator`, key helpers and preview normalization to `KoogStreamAccumulators.kt`. Keep the existing signature:

```kotlin
internal suspend fun collectAssistantMessageFromStream(
    frames: Flow<StreamFrame>,
    emitEvent: suspend (AgentStreamEvent) -> Unit,
): Message.Assistant
```

- [ ] **Step 4: Extract assistant mapping and graph strategy**

Move assistant prompt append/completion helpers to `KoogAssistantMessageMapper.kt`. Move `runWithKoogAgent`, `buildStreamingSingleRunStrategy`, streaming request extensions and tool graph wiring to `KoogStreamingStrategy.kt`.

`KoogAgentGateway.kt` retains constructor dependencies, `run`, event-emitting bridge selection, provider routing and legacy-stream orchestration.

- [ ] **Step 5: Verify Koog mapping, streaming and gateway behavior**

Run:

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.agent.koog.*"
.\gradlew.bat :shared:jvmTest
```

Expected: prompt/history mapping, stream accumulation, tool-loop completion, event order and gateway failures match existing assertions.

### Task 8: 拆分桌面 presentation、自动滚动和会话导出

**Files:**
- Modify: existing `ChatScreen.kt`
- Create: `chat/presentation/ConversationPresentation.kt`
- Create: `chat/presentation/ComposerPresentation.kt`
- Create: `chat/presentation/TaskPresentation.kt`
- Create: `chat/presentation/AutoScrollPolicy.kt`
- Create: `chat/export/ConversationMarkdownExporter.kt`
- Split/Move: `ChatScreenPresentationTest.kt`

**Interfaces:**
- Preserves: all current `internal build*` presentation functions used by tests
- Produces: `buildConversationMarkdown(conversation)` and `writeConversationMarkdown(target, markdown)` under `chat.export`
- Preserves: auto-scroll thresholds and context usage formatting

- [ ] **Step 1: Move presentation tests to final packages before production extraction**

Split the existing test by behavior:

- answer/tool/reasoning labels → `ConversationPresentationTest.kt`
- model/profile/action/context labels → `ComposerPresentationTest.kt`
- scroll follow/anchor behavior → `AutoScrollPolicyTest.kt`
- Markdown output and filename behavior → `ConversationMarkdownExporterTest.kt`

Keep assertions unchanged; update imports only.

- [ ] **Step 2: Extract pure conversation and composer presentation functions**

Move answer title/paragraphs/status, tool headline/kind/details, chat/reasoning text, provider labels, model variants, profile grouping, context labels and primary action visual functions into the matching presentation files. Functions returning Compose `Color` stay with the component that renders them; pure presentation files must not import Compose UI types.

- [ ] **Step 3: Extract task and auto-scroll policy**

Move workspace labels and task-creation workspace resolution into `TaskPresentation.kt`. Move `shouldAutoScrollToLatest`, `nextAutoScrollFollowState`, `timelineAutoScrollAnchorIndex` and content-size helpers into `AutoScrollPolicy.kt` without changing thresholds. Move `shouldSubmitComposerKey` into `ComposerPresentation.kt`.

- [ ] **Step 4: Extract Markdown export**

Move Markdown generation, file writing and filename sanitization to `chat/export/ConversationMarkdownExporter.kt`. Preserve encoding, section order, tool/reasoning representation and filename normalization.

- [ ] **Step 5: Verify presentation and export behavior**

Run:

```powershell
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.presentation.*"
.\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.export.*"
```

Expected: all moved presentation/export tests pass without Compose runtime or provider network access.

### Task 9: 拆分 Compose 组件并迁移 bootstrap、design、tool 与 platform

**Files:**
- Create/Move: `bootstrap/Main.kt`、`MulehangDesktopApp.kt`、`DesktopProjectRootResolver.kt`
- Create: `chat/component/ChatScreen.kt`、`ChatHeader.kt`、`TaskSidebar.kt`、`WorkspacePanel.kt`、`ConversationTimeline.kt`、`ComposerPanel.kt`、`PlanCard.kt`、`AuxiliaryPanels.kt`
- Move: `design/RingUiShells.kt`
- Move: `tool/interaction/DesktopToolInteractionCoordinator.kt`
- Move: `tool/component/ToolInteractionCards.kt`
- Create: platform helpers for file chooser and clipboard actions extracted from `ChatScreen.kt`
- Move/Modify corresponding desktop tests

**Interfaces:**
- Preserves: `fun main()` and `@Composable fun MulehangDesktopApp()`
- Preserves: `@Composable fun ChatScreen(state: ChatWindowState)`
- Preserves: `QuestionCard`、`ApprovalCard` and `DesktopToolInteractionCoordinator` public behavior
- Preserves: all callbacks from components to `ChatWindowState`

- [ ] **Step 1: Move bootstrap, design and tool files**

Use exact packages:

```kotlin
package com.agent.app.bootstrap
package com.agent.app.design
package com.agent.app.tool.interaction
package com.agent.app.tool.component
```

Move corresponding tests: bootstrap tests to `com.agent.app.bootstrap`, tool card tests to `com.agent.app.tool.component`. Update the Gradle application main class if its fully qualified name changes from the current package.

- [ ] **Step 2: Extract desktop platform actions**

Move file/directory chooser calls and clipboard writes out of `ChatScreen.kt` into `com.agent.app.platform`. Expose focused functions with their current return semantics:

```kotlin
internal fun pickFiles(): List<String>
internal fun pickWorkspaceDirectory(): String?
internal fun copyTextToClipboard(text: String)
```

Do not add abstraction interfaces because only one desktop implementation exists.

- [ ] **Step 3: Split ChatScreen by visible region**

Move existing Composable bodies without visual changes:

- root layout → `ChatScreen.kt`
- top header → `ChatHeader.kt`
- task list → `TaskSidebar.kt`
- workspace/empty state → `WorkspacePanel.kt`
- conversation messages/reasoning/tool rows → `ConversationTimeline.kt`
- composer/footer → `ComposerPanel.kt`
- plan model/parser/card → `PlanCard.kt`
- terminal/history/tool rail → `AuxiliaryPanels.kt`

Keep dimensions, colors, spacing, labels, animation behavior, callback order and remembered keys unchanged.

- [ ] **Step 4: Update all desktop imports and run focused tests**

Run:

```powershell
.\gradlew.bat :desktopApp:test
.\gradlew.bat :desktopApp:compileKotlin
```

Expected: all desktop tests pass and production Desktop code compiles; no application window is started.

### Task 10: 清理旧包、同步文档并完成全量验证

**Files:**
- Delete: empty old package directories under shared and desktop source/test roots
- Modify if required: `README.md`、`AGENTS.md`、current spec/plan references
- Check: all moved production/test files

**Interfaces:**
- Consumes: final packages and interfaces produced by Tasks 1–9
- Produces: a clean source tree with no stale current-mainline references

- [ ] **Step 1: Search for stale packages and paths**

Use IDEA regex search for:

```regex
com\.agent\.shared\.(state|application|config|exceptions)(\.|$)|com\.agent\.app\.ui(\.|$)
```

Expected: zero production/test imports or package declarations. Historical documentation matches may remain only when they clearly describe historical state; current mainline documentation must be updated.

- [ ] **Step 2: Inspect all changed files with IDEA problems**

Run IDEA `get_file_problems` with warnings included for every changed Kotlin file. Fix all errors and only warnings introduced by this refactor. Do not reformat unrelated code.

- [ ] **Step 3: Run authoritative test and compile verification**

Run:

```powershell
.\gradlew.bat :shared:jvmTest :desktopApp:test
.\gradlew.bat :desktopApp:compileKotlin
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run full build if focused verification is green**

Run:

```powershell
.\gradlew.bat build
```

Expected: `BUILD SUCCESSFUL`; no Desktop app or development server starts.

- [ ] **Step 5: Final diff and scope audit**

Run:

```powershell
git diff --check
git status --short
git diff --stat
```

Confirm:

1. Only `shared`、`desktopApp`、design/plan/current-mainline docs changed.
2. No new dependency, module, configuration key, generated output, log, secret or vendor change appears.
3. Any direct bug fix has a regression test and is listed separately in the final handoff.
4. No commit is created unless the user has explicitly authorized it.

## Self-Review

- Spec coverage: Tasks 1–4 cover the shared package migration; Tasks 5–9 cover the four large-file splits and desktop package migration; Task 10 covers dependency cleanup, documentation and all acceptance checks.
- Placeholder scan: the plan contains no deferred implementation marker; every task names exact files, target packages, interfaces, commands and expected outcomes.
- Type consistency: `ReasoningEffort` remains with `AgentRunRequest`; `ToolEventItem` remains with sealed `ConversationItem`; all reducers consume and return `ChatConversationUiState`; DeepSeek and Koog extracted function names match their current callers/tests.
- Scope check: tasks are ordered by dependency direction and each ends in an independently testable state; no new Gradle module or external subsystem is introduced.
