# 批量 Answers 与 Composer 流光 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次展示并提交多个 `ask_user` 问题，完成后以可展开的 `Answers` 时间线项呈现问答，同时把 Composer 改为双层深色表面并在任务运行时显示蓝色流光边框。

**Architecture:** 共享层将问题请求标准化为 `QuestionPrompt` 列表，并由一个 `QuestionAnswer` 列表完成一次恢复。桌面状态把完成的问答写入新的时间线项；Compose 以问卷卡收集本地选择状态，并以独立 Answers 行展示结果。Composer 在不改变布局的前提下叠加独立的 Canvas 描边层，仅在 `ExecutionState.Running` 时推进蓝色扫光。

**Tech Stack:** Kotlin Multiplatform、JetBrains Koog `ToolSet`、Compose Multiplatform Desktop、kotlin.test、JUnit 5。

## Global Constraints

- 保持 `desktopApp` 依赖 `shared` 的单向边界。
- 每题候选项在共享层去空白、去重并限制为 5 项；自由输入行不计入此上限。
- 问卷必须全部回答后才允许一次提交；工具恢复只能发生一次。
- `ask_user` 不得显示为工具名称或混入工具展示组。
- Composer 仅在 `ExecutionState.Running` 时显示流动蓝色描边；等待用户输入、等待审批和空闲态使用静态描边。
- 不启动开发服务器；只运行 Gradle 测试、编译与静态检查。
- 未获得明确授权前不执行 git 提交。

---

### Task 1: 标准化批量提问请求与工具输入

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/tool/model/DesktopToolEventModels.kt`
- Modify: `shared/src/jvmMain/kotlin/com/agent/shared/tool/runtime/DesktopToolSet.kt`
- Create: `shared/src/commonTest/kotlin/com/agent/shared/tool/model/QuestionRequestTest.kt`
- Modify: `shared/src/jvmTest/kotlin/com/agent/shared/tool/runtime/DesktopToolRegistryFactoryTest.kt`

**Interfaces:**
- Consumes: Koog 反射工具参数与 `DesktopToolInteractionBridge.requestQuestion(request): String`。
- Produces: `@Serializable QuestionPrompt(question: String, options: List<String>)`、`QuestionAnswer(question: String, answer: String)`、`QuestionRequest.questions: List<QuestionPrompt>` 与 `normalizeQuestionPrompts(raw: List<QuestionPrompt>): List<QuestionPrompt>`。
- Produces: `DesktopToolSet.ask_user(question: String = "", options: List<String> = emptyList(), questions_json: String = ""): String`；`questions_json` 是 `[{"question":"…","options":["…"]}]`，非空时优先使用它，否则回退为既有单题参数。

- [ ] **Step 1: 写失败的共享层规整测试**

```kotlin
@Test
fun `normalizes prompts and caps choices at five`() {
    val prompts = normalizeQuestionPrompts(
        listOf(
            QuestionPrompt(" 目标 ", listOf("UI", "", "UI", "Bug", "Feature", "Review", "Extra")),
        QuestionPrompt("   ", listOf("ignored")),
        QuestionPrompt("语言", emptyList()),
    ),
)

    assertEquals(
        listOf(
            QuestionPrompt("目标", listOf("UI", "Bug", "Feature", "Review", "Extra")),
            QuestionPrompt("语言", emptyList()),
        ),
        prompts,
    )
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `./gradlew.bat :shared:jvmTest --tests "com.agent.shared.tool.model.QuestionRequestTest"`

Expected: FAIL，提示 `QuestionPrompt` 或 `normalizeQuestionPrompts` 尚不存在。

- [ ] **Step 3: 实现请求模型和类型安全的 JSON 解析**

```kotlin
@Serializable
data class QuestionPrompt(
    val question: String,
    val options: List<String> = emptyList(),
)

data class QuestionAnswer(
    val question: String,
    val answer: String,
)

internal fun normalizeQuestionPrompts(raw: List<QuestionPrompt>): List<QuestionPrompt> = raw
    .map { prompt ->
        prompt.copy(
            question = prompt.question.trim(),
            options = prompt.options.map(String::trim).filter(String::isNotEmpty).distinct().take(5),
        )
    }
    .filter { it.question.isNotEmpty() }
```

将 `QuestionRequest` 改为携带规整后的 `questions`，并让 `ask_user` 使用 `kotlinx.serialization` 解码非空的 `questions_json`；未传该字段时，将既有 `question` 与 `options` 规整为一题。解析失败或规整后为空时抛出带有参数格式说明的 `IllegalArgumentException`，防止 Agent 进入没有可显示题目的等待态。更新工具注册表测试，断言 schema 同时说明 `questions_json` 的 JSON 样例和旧参数的兼容回退规则。

- [ ] **Step 4: 运行共享层测试并确认通过**

Run: `./gradlew.bat :shared:jvmTest --tests "com.agent.shared.tool.model.QuestionRequestTest" --tests "com.agent.shared.tool.runtime.DesktopToolRegistryFactoryTest"`

Expected: PASS。

- [ ] **Step 5: 保持变更未提交**

根据仓库规则不创建 git 提交；仅保留已验证的工作区变更。

### Task 2: 让桌面状态一次恢复并记录 Answers 时间线项

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/chat/model/ConversationItem.kt`
- Create: `shared/src/commonMain/kotlin/com/agent/shared/chat/model/AnsweredQuestionsItem.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/state/ChatWindowUiState.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/state/AgentEventReducer.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/state/ChatWindowState.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/state/ContextUsageEstimator.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/export/ConversationMarkdownExporter.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/state/ChatWindowStateTest.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/state/AgentEventReducerTest.kt`

**Interfaces:**
- Consumes: Task 1 的 `QuestionRequest.questions`、`QuestionAnswer` 与 `normalizeQuestionPrompts`。
- Produces: `PendingQuestionUiState.questions: List<QuestionPrompt>`、`ChatWindowState.answerPendingQuestions(answers: List<QuestionAnswer>)`。
- Produces: `AnsweredQuestionsItem(answers: List<QuestionAnswer>) : ConversationItem`，并新增 `ConversationItem.Kind.Answers`。

- [ ] **Step 1: 写失败的同轮批量恢复测试**

```kotlin
@Test
fun `should resume once and append answers after every batch question is answered`() = runTest(dispatcher) {
    val coordinator = DesktopToolInteractionCoordinator()
    val request = QuestionRequest(
        requestId = "q1",
        toolCallId = "call-1",
        questions = listOf(
            QuestionPrompt("目标", listOf("UI", "Bug")),
            QuestionPrompt("语言", listOf("中文", "English")),
        ),
    )
    val state = stateWaitingFor(request, coordinator)

    state.answerPendingQuestions(
        listOf(QuestionAnswer("目标", "UI"), QuestionAnswer("语言", "中文")),
    )
    advanceUntilIdle()

    assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
    assertEquals(
        listOf("UI", "中文"),
        (state.ui.activeConversation.items.last() as AnsweredQuestionsItem).answers.map(QuestionAnswer::answer),
    )
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.state.ChatWindowStateTest"`

Expected: FAIL，提示 `AnsweredQuestionsItem` 或 `answerPendingQuestions` 尚不存在。

- [ ] **Step 3: 仅实现一次性回答状态流**

在 `AgentEventReducer` 中把 `QuestionRequested` 转换为包含 `questions` 的待回答 UI 状态。在 `answerPendingQuestions` 中验证答案数、问题顺序与非空答案完全匹配当前请求；校验成功后将 `AnsweredQuestionsItem` 追加到请求所属会话、清除挂起状态、调用 `submitQuestion` 一次，并恢复为 `ExecutionState.Running`。把供 Agent 读取的文本格式化为稳定的逐题文本：`Question: …\nAnswer: …`。

在 `ContextUsageEstimator` 与 Markdown 导出器中显式处理 `AnsweredQuestionsItem`，使上下文与导出都保留问答。更新所有因 sealed 类型新增分支而不完整的 `when`。

- [ ] **Step 4: 运行状态与导出相关测试并确认通过**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.state.ChatWindowStateTest" --tests "com.agent.app.chat.state.AgentEventReducerTest" --tests "com.agent.app.chat.export.ConversationMarkdownExporterTest"`

Expected: PASS。

- [ ] **Step 5: 保持变更未提交**

根据仓库规则不创建 git 提交；仅保留已验证的工作区变更。

### Task 3: 构建问卷卡与可展开的 Answers 摘要

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/tool/component/ToolInteractionCards.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ComposerPanel.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ConversationTimeline.kt`
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/presentation/ConversationPresentation.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/tool/component/ToolInteractionCardsTest.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/presentation/ConversationPresentationTest.kt`

**Interfaces:**
- Consumes: `PendingQuestionUiState.questions`、`ChatWindowState.answerPendingQuestions`、`AnsweredQuestionsItem`。
- Produces: `QuestionnaireCardModel(questions: List<QuestionPrompt>, submitEnabled: Boolean)`、`answersSummaryModel(item: AnsweredQuestionsItem)`。
- Produces: `QuestionCard` 的唯一完成回调 `onSubmitAnswers: (List<QuestionAnswer>) -> Unit`。

- [ ] **Step 1: 写失败的问卷模型与摘要模型测试**

```kotlin
@Test
fun `questionnaire limits built in choices and requires every answer`() {
    val model = buildQuestionnaireCardModel(
        questions = listOf(
            QuestionPrompt("目标", listOf("UI", "Bug", "Feature", "Review", "Other", "Ignored")),
            QuestionPrompt("语言", listOf("中文")),
        ),
        answers = listOf("UI", ""),
    )

    assertEquals(5, model.questions.first().options.size)
    assertEquals("Other…", model.questions.first().freeTextLabel)
    assertEquals(false, model.submitEnabled)
}

@Test
fun `answers summary keeps questions and answers in order`() {
    val summary = answersSummaryModel(
        AnsweredQuestionsItem(listOf(QuestionAnswer("目标", "UI"), QuestionAnswer("语言", "中文"))),
    )

    assertEquals("Answers", summary.label)
    assertEquals(listOf("目标", "语言"), summary.entries.map { it.question })
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.tool.component.ToolInteractionCardsTest" --tests "com.agent.app.chat.component.ChatScreenPresentationTest" --tests "com.agent.app.chat.presentation.ConversationPresentationTest"`

Expected: FAIL，提示问卷和 Answers 摘要模型尚不存在。

- [ ] **Step 3: 实现纵向问卷与摘要时间线行**

将 `QuestionCard` 改为按问题顺序显示：题号和题目、至多五个独立的一行选项、最后一行 `Other…`。只有点击该行才显示对应的单行或多行输入控件；选择候选项会替换该题的自由输入草稿。每个问题的答案保存在以题目序号索引的本地 Compose 状态中，所有题目有效时才启用“提交回答”。

在 `ConversationTimeline` 增加带对话图标和 `Answers` 文案的紧凑行，默认收起；点击后用 `expandVertically + fadeIn` 展示逐题问题与答案。不要为它复用工具组，且不能渲染 `ask_user` 工具名。保持现有外层 `PendingInteractionCards` 入场动画。

- [ ] **Step 4: 运行 UI 展示测试并确认通过**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.tool.component.ToolInteractionCardsTest" --tests "com.agent.app.chat.component.ChatScreenPresentationTest" --tests "com.agent.app.chat.presentation.ConversationPresentationTest"`

Expected: PASS。

- [ ] **Step 5: 保持变更未提交**

根据仓库规则不创建 git 提交；仅保留已验证的工作区变更。

### Task 4: 改造 Composer 并增加运行态蓝色流光边框

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/agent/app/chat/component/ComposerPanel.kt`
- Modify: `desktopApp/src/test/kotlin/com/agent/app/chat/component/ChatScreenPresentationTest.kt`

**Interfaces:**
- Consumes: `ExecutionState` 与现有 Composer 内容、菜单和发送/停止逻辑。
- Produces: `shouldAnimateComposerBorder(executionState: ExecutionState): Boolean`、`COMPOSER_BORDER_FLOW_DURATION_MILLIS = 1_600`。

- [ ] **Step 1: 写失败的运行态边框规则测试**

```kotlin
@Test
fun `composer border flows only while a task is running`() {
    assertEquals(true, shouldAnimateComposerBorder(ExecutionState.Running))
    assertEquals(false, shouldAnimateComposerBorder(ExecutionState.WaitingForUserInput))
    assertEquals(false, shouldAnimateComposerBorder(ExecutionState.Idle))
    assertEquals(1_600, COMPOSER_BORDER_FLOW_DURATION_MILLIS)
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: FAIL，提示边框状态函数和动效常量尚不存在。

- [ ] **Step 3: 实现双层 Composer 和独立描边绘制层**

保留现有 Composer 的附件、输入、菜单和发送按钮逻辑；调整表面层级为外层圆角描边容器、内层深色编辑区、独立底栏。新增仅覆盖描边的 Canvas 层：静态态绘制细蓝色描边，运行态以 `rememberInfiniteTransition` 驱动一个旋转的 `sweepGradient` 圆角描边，周期固定为 1,600 ms、线性循环。动画层不接收指针事件，且不动画宽高、内边距或底栏内容。

- [ ] **Step 4: 运行 Composer 测试并确认通过**

Run: `./gradlew.bat :desktopApp:test --tests "com.agent.app.chat.component.ChatScreenPresentationTest"`

Expected: PASS。

- [ ] **Step 5: 保持变更未提交**

根据仓库规则不创建 git 提交；仅保留已验证的工作区变更。

### Task 5: 集成验证

**Files:**
- Modify: `docs/superpowers/specs/2026-08-07-batched-answers-design.md`
- Modify: `docs/superpowers/plans/2026-08-07-batched-answers-and-composer-motion.md`

**Interfaces:**
- Consumes: 前四项任务的全部测试。
- Produces: 与规格一致的已验证实现。

- [ ] **Step 1: 运行受影响模块的完整测试与编译**

Run: `./gradlew.bat :shared:jvmTest :desktopApp:test :desktopApp:compileKotlin`

Expected: BUILD SUCCESSFUL，且没有 Kotlin 编译错误。

- [ ] **Step 2: 在 IDE 中检查改动文件问题**

使用 IDEA 的 `get_file_problems` 检查所有改动的生产与测试 Kotlin 文件。

Expected: 每个文件的 `errors` 为空。

- [ ] **Step 3: 检查最终 diff**

Run: `git diff --check`

Expected: 不输出空白错误。

- [ ] **Step 4: 记录验证结果，不提交**

在最终交付中报告：批量提问、每题五项上限、单次恢复、Answers 摘要、运行态流光和执行过的 Gradle 命令。未经明确授权不提交。

## Self-Review

- 协议、状态、问卷、时间线摘要和 Composer 动效均有对应任务；候选项上限、单次恢复和不显示工具名由 Task 1–3 覆盖。
- 流光仅在运行态启用、且不改变布局的约束由 Task 4 覆盖。
- 每个任务都包含失败测试、失败验证、最小实现和通过验证；没有待定项或未定义的接口名。
