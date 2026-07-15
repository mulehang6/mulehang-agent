# Code Review Follow-up Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复空白 DeepSeek system 消息、Desktop Header 长文本布局和 Koog 旧流工具完成事件语义。

**Architecture:** 保持现有包和公开接口不变，在三个既有实现点做最小修改。DeepSeek 与 Koog 行为修复各自通过回归测试完成 RED/GREEN；Header 只同步原型已有的间距和省略行为，不引入 UI 测试依赖。

**Tech Stack:** Kotlin 2.1.21、Kotlin Multiplatform、Compose Multiplatform Desktop、Koog 1.0.0、kotlin.test、JUnit 5、Gradle Wrapper。

## Global Constraints

- 直接在当前 `main` 工作区修改，不创建 worktree。
- 保留现有 `vendor/kilocode` 子模块改动，不读取、修改、暂存或还原它。
- 不新增依赖、模块、配置项或测试基础设施。
- 写改文件使用 `functions.apply_patch`，代码修改后检查 IDEA problems。
- 未经用户明确授权不创建提交，因此本计划不执行 commit。
- 不启动 Desktop 应用或任何开发服务器。

---

### Task 1: DeepSeek 空白 system 消息

**Files:**
- Modify: `shared/src/jvmTest/kotlin/com/agent/shared/agent/provider/deepseek/DeepSeekChatCompletionsStreamerTest.kt`
- Modify: `shared/src/jvmMain/kotlin/com/agent/shared/agent/provider/deepseek/DeepSeekRequestMapper.kt:105-114`

**Interfaces:**
- Consumes: `buildDeepSeekRequest(prompt: Prompt, config: ConfigProfile, reasoningEffort: ReasoningEffort?, tools: List<ToolDescriptor>): DeepSeekChatCompletionRequest`
- Produces: 空白 `Message.System` 不进入 `DeepSeekChatCompletionRequest.messages`；其他消息映射不变。

- [ ] **Step 1: 写入失败回归测试**

在 `DeepSeekChatCompletionsStreamerTest` 中加入：

```kotlin
/**
 * 空白 system 消息不应生成缺少 content 的 DeepSeek 消息。
 */
@Test
fun `should omit blank system message from deepseek request`() {
    val prompt = Prompt(
        messages = listOf(
            Message.System(
                content = "   ",
                metaInfo = RequestMetaInfo.create(clock = KoogClock.System),
            ),
            Message.User(
                content = "question",
                metaInfo = RequestMetaInfo.create(clock = KoogClock.System),
            ),
        ),
        id = "blank-system-prompt",
    )

    val request = buildDeepSeekRequest(
        prompt = prompt,
        config = deepSeekProfile(),
        reasoningEffort = null,
    )

    assertEquals(
        listOf(DeepSeekChatMessage(role = "user", content = "question")),
        request.messages,
    )
}
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.agent.provider.deepseek.DeepSeekChatCompletionsStreamerTest.should omit blank system message from deepseek request"
```

Expected: FAIL；`request.messages` 仍多出一条 `DeepSeekChatMessage(role = "system", content = null)`。

- [ ] **Step 3: 写入最小实现**

将 `Message.System` 分支改为：

```kotlin
is Message.System -> message.textContent()
    .takeIf { it.isNotBlank() }
    ?.let { listOf(DeepSeekChatMessage(role = "system", content = it)) }
    ?: emptyList()
```

- [ ] **Step 4: 运行测试并确认 GREEN**

重复 Step 2 命令。

Expected: PASS。

---

### Task 2: Koog 旧流工具事件语义

**Files:**
- Modify: `shared/src/jvmTest/kotlin/com/agent/shared/agent/koog/KoogAgentGatewayTest.kt:267-315`
- Modify: `shared/src/jvmMain/kotlin/com/agent/shared/agent/koog/KoogAgentGateway.kt:128-146`

**Interfaces:**
- Consumes: `StreamFrame.ToolCallComplete(id, name, content, index)`，其中 `content` 是完整工具调用参数。
- Produces: 旧 `streamRunner` 兼容路径只公告 `AgentStreamEvent.ToolCallStarted`，不产生虚假的 `ToolCallFinished`。

- [ ] **Step 1: 修改现有测试表达正确行为**

保留当前输入 frame，将测试名改为：

```kotlin
fun `should map tool call arguments without reporting a finished result`() = runTest {
```

并将事件断言改为：

```kotlin
assertEquals(5, events.size)
assertEquals(AgentStreamEvent.Started, events[0])
assertEquals(AgentStreamEvent.TextDelta("hel"), events[1])
assertEquals(
    AgentStreamEvent.ToolCallStarted(
        toolCallId = "call-1",
        name = "read_file",
        argumentsPreview = """{"path":"README.md"}""",
    ),
    events[2],
)
assertEquals(AgentStreamEvent.TextDelta("lo"), events[3])
assertEquals(AgentStreamEvent.Completed("hello"), events[4])
```

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```powershell
.\gradlew.bat :shared:jvmTest --tests "com.agent.shared.agent.koog.KoogAgentGatewayTest.should map tool call arguments without reporting a finished result"
```

Expected: FAIL；旧实现实际产生 6 个事件，其中包含 `ToolCallFinished`。

- [ ] **Step 3: 删除错误的完成事件**

在 `runLegacyStream` 的 `StreamFrame.ToolCallComplete` 分支保留 `ToolCallStarted` 去重逻辑，删除：

```kotlin
emit(
    AgentStreamEvent.ToolCallFinished(
        toolCallId = frame.id,
        name = frame.name,
        resultPreview = frame.content.toPreview(),
    ),
)
```

- [ ] **Step 4: 运行测试并确认 GREEN**

重复 Step 2 命令。

Expected: PASS。

---

### Task 3: Desktop Header 长文本布局

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt:70-76`

**Interfaces:**
- Consumes: 当前会话标题 `activeConversation?.title`。
- Produces: 标题与 breadcrumb 间距 12dp，标题最多一行并在受限宽度内显示省略号。

- [ ] **Step 1: 应用最小 Compose 修改**

将标题 `Text` 改为：

```kotlin
Text(
    text = activeConversation?.title ?: "No task selected",
    modifier = Modifier.padding(start = 12.dp),
    style = MaterialTheme.typography.titleSmall.copy(
        color = AppText,
        fontWeight = FontWeight.SemiBold,
    ),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)
```

- [ ] **Step 2: 检查文件问题并编译 Desktop**

使用 IDEA `get_file_problems` 检查 `ChatHeader.kt`，要求 0 error；然后运行：

```powershell
.\gradlew.bat :desktopApp:compileKotlin --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`。

---

### Task 4: 全量回归与范围审计

**Files:**
- Verify: `shared/src/jvmMain/kotlin/com/agent/shared/agent/provider/deepseek/DeepSeekRequestMapper.kt`
- Verify: `shared/src/jvmMain/kotlin/com/agent/shared/agent/koog/KoogAgentGateway.kt`
- Verify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ChatHeader.kt`
- Verify: 两个修改过的测试文件和本设计、计划文档。

**Interfaces:**
- Consumes: Tasks 1-3 的最终实现。
- Produces: 可交付、未提交的 main 工作树修改；`vendor/kilocode` 状态保持原样。

- [ ] **Step 1: IDEA problems 检查**

对三个生产文件和两个测试文件运行 IDEA `get_file_problems`，要求 0 error。

- [ ] **Step 2: 运行 shared 与 Desktop 测试**

```powershell
.\gradlew.bat :shared:jvmTest :desktopApp:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`，0 failed tests。

- [ ] **Step 3: 重新编译 Desktop**

```powershell
.\gradlew.bat :desktopApp:compileKotlin --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 4: 检查差异与工作树范围**

```powershell
git diff --check
git status --short
git diff -- shared/src desktopApp/src docs/superpowers/specs/2026-07-15-review-followup-fixes-design.md docs/superpowers/plans/2026-07-15-review-followup-fixes-implementation-plan.md
```

Expected:

- `git diff --check` 无输出；
- `vendor/kilocode` 仍保持任务开始前的既有修改状态；
- 除两个文档、三个生产文件和两个测试文件外没有本任务新增改动；
- 不创建提交。
