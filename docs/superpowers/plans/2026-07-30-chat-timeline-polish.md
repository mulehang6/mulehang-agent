# Chat Timeline Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine the desktop chat timeline so messages, reasoning, tool activity, and pending tool interactions are compact, legible, and calm.

**Architecture:** Keep presentation rules in `desktopApp` and terminal-tool input semantics in `shared`. The reducer continues to project agent events into `ToolEventItem`; the Compose timeline renders cards from that state. Pending question and approval cards share one 180 ms fade-and-rise entrance.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform Desktop, JetBrains Koog, kotlin.test/JUnit 5.

## Global Constraints

- Keep `shared` independent from `desktopApp`.
- `run_powershell` accepts an LLM-supplied non-blank `operation_intent` alongside the raw script.
- Tool cards are collapsed by default and reveal labeled input/output only after interaction.
- User bubbles size to their contents and never exceed 80% of the timeline width.
- Never auto-toggle the embedded terminal while a tool permission request appears.

---

### Task 1: Lock presentation rules with tests

**Files:**
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/presentation/ConversationPresentationTest.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/state/AgentEventReducerTest.kt`

**Interfaces:**
- Produces: `buildReasoningHeadline`, `toolEventDetailText`, and reducer state carrying the PowerShell operation intent.

- [ ] **Step 1: Write failing presentation tests**

```kotlin
assertEquals("Thinking...", buildReasoningHeadline(ReasoningItem(isStreaming = true)))
assertEquals("无输入参数", toolEventDetailText(null))
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `.\\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.presentation.ConversationPresentationTest"`

- [ ] **Step 3: Write a failing reducer test for terminal intent**

```kotlin
val event = AgentStreamEvent.ToolCallStarted(
    name = "run_powershell",
    argumentsPreview = "Get-ChildItem",
    operationIntent = "列出当前目录内容",
)
```

- [ ] **Step 4: Run the reducer test and verify it fails**

Run: `.\\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.state.AgentEventReducerTest"`

### Task 2: Implement timeline state and compact Compose cards

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/agent/api/AgentStreamEvent.kt`
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/chat/model/ToolEventItem.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/state/AgentEventReducer.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/presentation/ConversationPresentation.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`

**Interfaces:**
- Consumes: the tests from Task 1.
- Produces: `ToolEventItem.operationIntent` and a collapsed-by-default tool-event card.

- [ ] **Step 1: Add minimal nullable operation-intent fields to events and timeline state**
- [ ] **Step 2: Project `ToolCallStarted.operationIntent` through the reducer**
- [ ] **Step 3: Render user bubbles with `wrapContentWidth` and `widthIn(max = 80% of available width)`**
- [ ] **Step 4: Render `Thinking...` with a streaming-only horizontal shimmer brush**
- [ ] **Step 5: Replace raw tool rows with clickable outlined cards; show only the tool name when collapsed and labeled details when expanded**
- [ ] **Step 6: Run the Task 1 tests and verify they pass**

### Task 3: Require and display PowerShell operation intent

**Files:**
- Modify: `shared/src/jvmMain/kotlin/com/agent/shared/tool/runtime/DesktopToolSet.kt`
- Modify: `shared/src/jvmTest/kotlin/com/agent/shared/tool/runtime/DesktopToolSetTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ToolInteractionCards.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/tool/component/ToolInteractionCardsTest.kt`

**Interfaces:**
- Consumes: `operationIntent: String?` in stream/timeline state.
- Produces: `run_powershell(script: String, operation_intent: String)` and an approval card that exposes both intent and raw command.

- [ ] **Step 1: Write a failing tool test that calls `run_powershell` with a meaningful intent**
- [ ] **Step 2: Implement the new annotated required parameter and pass it to `ApprovalRequest.summary`**
- [ ] **Step 3: Extend the approval-card model with the raw command label for PowerShell requests**
- [ ] **Step 4: Run focused shared and desktop tests and verify they pass**

### Task 4: Animate pending interactions without affecting terminal visibility

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/WorkspacePanel.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/tool/component/ToolInteractionCards.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/tool/component/ToolInteractionCardsTest.kt`

**Interfaces:**
- Consumes: `PendingQuestionUiState` and `PendingApprovalUiState`.
- Produces: a reusable `PendingInteractionEntrance` wrapper with a 180 ms fade-in/upward transition and 120 ms fade-out.

- [ ] **Step 1: Write a failing test for an exposed motion specification value**
- [ ] **Step 2: Wrap both question and approval cards in the same animated visibility entrance**
- [ ] **Step 3: Confirm no callback writes `terminalVisible` when pending interaction state changes**
- [ ] **Step 4: Run `:desktopApp:test` and `:desktopApp:compileKotlin`**
