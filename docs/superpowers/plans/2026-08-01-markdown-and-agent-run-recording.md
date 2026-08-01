# Markdown 渲染与 Agent 回合记录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将模型的正常 Markdown 回复渲染为结构化 Compose 内容，并在每个 Agent 回合结束后写出完整、可关联的诊断记录。

**Architecture:** `say_to_user` 已移除，正常可见回复统一通过 `ChatMessageItem`。桌面端以 CommonMark AST 替换正则式片段渲染；共享 Agent 层以 `AgentGateway` 装饰器汇总流事件，只在 `Completed` 或 `Failed` 时将单个回合记录交给 JVM 文件记录器。

**Tech Stack:** Kotlin Multiplatform、Compose Desktop、CommonMark Markdown renderer、kotlinx.serialization、Kotlin Flow、Logback。

## Global Constraints

- 不记录 API key；诊断记录不逐 token 写入。
- 日志以独立 JSON Lines 文件滚动保存，带每轮唯一 runId。
- 不修改用户消息、工具审批或 provider 协议。
- 新行为必须先有失败测试，再写最小实现。

---

### Task 1: Markdown 正文渲染

**Files:**
- Modify: `desktopApp/build.gradle.kts`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`
- Test: `desktopApp/src/test/kotlin/com/agent/app/chat/presentation/ConversationPresentationTest.kt`

- [ ] **Step 1: Write a failing Markdown capability test**

Assert a representative document containing an ATX heading, nested list, block quote, fenced code block and table is parsed into structured blocks rather than one plain paragraph.

- [ ] **Step 2: Run the presentation test**

Run: `.\\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.presentation.ConversationPresentationTest"`

- [ ] **Step 3: Add the parser/renderer dependency and replace the regex renderer**

Use a CommonMark AST renderer in `AssistantMessageBlock`; preserve the application colour, typography and code-surface styling. Do not render untrusted HTML or execute links.

- [ ] **Step 4: Re-run the presentation test**

Run: `.\\gradlew.bat :desktopApp:test --tests "com.agent.app.chat.presentation.ConversationPresentationTest"`

### Task 2: Agent 回合记录装饰器

**Files:**
- Create: `shared/src/jvmMain/kotlin/com/agent/shared/agent/recording/RecordingAgentGateway.kt`
- Create: `shared/src/jvmTest/kotlin/com/agent/shared/agent/recording/RecordingAgentGatewayTest.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/bootstrap/MulehangDesktopApp.kt`
- Modify: `desktopApp/src/main/resources/logback.xml`

- [ ] **Step 1: Write a failing decorator test**

Provide a fake `AgentGateway` that emits text/reasoning/tool events then `Completed`; assert one record is emitted only after completion and contains final text, completed reasoning, tool input/output and run metadata.

- [ ] **Step 2: Run the JVM test**

Run: `.\\gradlew.bat :shared:jvmTest --tests "com.agent.shared.agent.recording.RecordingAgentGatewayTest"`

- [ ] **Step 3: Implement the Flow-decorating recorder and JSONL sink**

Buffer event data in the decorator, write once at terminal events, and register it around `KoogAgentGateway` in the desktop composition root.

- [ ] **Step 4: Re-run the JVM test and the desktop test suite**

Run: `.\\gradlew.bat :shared:jvmTest :desktopApp:test`
