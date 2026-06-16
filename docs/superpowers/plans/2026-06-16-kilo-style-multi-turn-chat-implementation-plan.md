# Kilo Style Multi Turn Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前聊天链路从“每次只发送当前 prompt”的单轮模式，升级为按 kilo 语义维护结构化会话历史的多轮对话，并先打通当前实际使用的 `DeepSeek + openai-chat-completions` 链路。

**Architecture:** 在 `shared` 中新增结构化会话历史模型，作为模型上下文的真相源；`AgentRunRequest` 承载“历史 + 当前输入”；`ChatWindowState` 同时维护结构化历史和现有 UI 时间线；DeepSeek 请求体从结构化历史生成 provider messages，而不是从 `ConversationItem` 反推。其它 provider 本轮保持编译可用，但不要求实现真正的多轮历史映射。

**Tech Stack:** Kotlin 2.4, Compose Multiplatform Desktop, Kotlin Multiplatform shared module, kotlin.test, JUnit 5, existing Koog/DeepSeek adapter

---

## File Structure

- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentConversationHistory.kt`
  - 定义用户/助手历史消息与 assistant parts 结构。
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentRunRequest.kt`
  - 为发送请求增加结构化历史字段，同时保留当前 `prompt` 作为本轮输入。
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/application/SendMessageUseCase.kt`
  - 继续保持冷流语义，但转发包含历史的请求对象。
- Modify: `shared/src/commonTest/kotlin/com/agent/shared/application/SendMessageUseCaseTest.kt`
  - 先写失败测试，锁定请求历史转发契约。
- Modify: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DeepSeekChatCompletionsStreamer.kt`
  - 把结构化历史映射为 DeepSeek chat-completions messages。
- Modify: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DeepSeekChatCompletionsStreamerTest.kt`
  - 先写失败测试，锁定历史序列与 assistant parts 的序列化格式。
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`
  - 为每个会话增加结构化历史与流式中的 assistant 历史索引，并在事件归并时同步更新。
- Modify: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatWindowStateTest.kt`
  - 先写失败测试，锁定第二轮请求读取第一轮历史、status 不回灌、会话隔离三类行为。
- Modify: `shared/src/desktopTest/kotlin/com/agent/shared/agent/KoogAgentGatewayTest.kt`
  - 确认 `AgentRunRequest` 扩展后原有 generic gateway 事件映射仍可工作。

### Note

本计划不包含 `git commit` 步骤。仓库规则要求只有在用户明确允许时才提交。

## Task 1: 定义结构化会话历史与发送请求契约

**Files:**
- Create: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentConversationHistory.kt`
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/agent/AgentRunRequest.kt`
- Modify: `shared/src/commonMain/kotlin/com/agent/shared/application/SendMessageUseCase.kt`
- Modify: `shared/src/commonTest/kotlin/com/agent/shared/application/SendMessageUseCaseTest.kt`

- [ ] **Step 1: 先写失败测试，锁定 `SendMessageUseCase` 必须原样转发结构化历史**

```kotlin
@Test
fun `should forward structured history to gateway request`() = runTest {
    var capturedRequest: AgentRunRequest? = null
    val gateway = object : AgentGateway {
        override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
            capturedRequest = request
            return flowOf(AgentStreamEvent.Started, AgentStreamEvent.Completed("done"))
        }
    }

    val history = listOf(
        AgentConversationHistoryMessage.User(content = "first user turn"),
        AgentConversationHistoryMessage.Assistant(
            parts = listOf(
                AgentConversationHistoryPart.Text(text = "first assistant turn"),
            ),
        ),
    )

    SendMessageUseCase(gateway).invoke(
        AgentRunRequest(
            prompt = "second user turn",
            profile = profile(),
            history = history,
            reasoningEffort = ReasoningEffort.MEDIUM,
        ),
    ).toList()

    assertEquals(history, capturedRequest?.history)
    assertEquals("second user turn", capturedRequest?.prompt)
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.application.SendMessageUseCaseTest"
```

Expected:

```text
FAIL
Cannot find a parameter with this name: history
Unresolved reference: AgentConversationHistoryMessage
Unresolved reference: AgentConversationHistoryPart
```

- [ ] **Step 3: 新增结构化历史模型，并扩展 `AgentRunRequest`**

```kotlin
sealed interface AgentConversationHistoryMessage {
    data class User(
        val content: String,
    ) : AgentConversationHistoryMessage

    data class Assistant(
        val parts: List<AgentConversationHistoryPart> = emptyList(),
    ) : AgentConversationHistoryMessage
}

sealed interface AgentConversationHistoryPart {
    data class Text(val text: String) : AgentConversationHistoryPart

    data class Reasoning(
        val summary: String?,
        val rawText: String?,
    ) : AgentConversationHistoryPart

    data class ToolCall(
        val name: String,
        val argumentsPreview: String? = null,
    ) : AgentConversationHistoryPart

    data class ToolResult(
        val name: String,
        val resultPreview: String? = null,
    ) : AgentConversationHistoryPart
}

data class AgentRunRequest(
    val prompt: String,
    val profile: ConfigProfile,
    val reasoningEffort: ReasoningEffort? = ReasoningEffort.MEDIUM,
    val history: List<AgentConversationHistoryMessage> = emptyList(),
)
```

- [ ] **Step 4: 让 `SendMessageUseCase` 继续保持冷流，但完整转发扩展后的请求对象**

```kotlin
class SendMessageUseCase(
    private val agentGateway: AgentGateway,
) {
    operator fun invoke(prompt: String, profile: ConfigProfile): Flow<AgentStreamEvent> {
        return invoke(
            AgentRunRequest(
                prompt = prompt,
                profile = profile,
            ),
        )
    }

    operator fun invoke(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
        if (request.prompt.isBlank()) {
            throw IllegalArgumentException("提示词不能为空")
        }
        if (request.profile.id.isBlank()) {
            throw IllegalConfigExceptions { "profile id 不能为空" }
        }
        agentGateway.run(request).collect(::emit)
    }
}
```

- [ ] **Step 5: 运行共享请求测试确认通过**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.application.SendMessageUseCaseTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 2: 把结构化历史映射为 DeepSeek chat-completions 请求

**Files:**
- Modify: `shared/src/desktopMain/kotlin/com/agent/shared/agent/DeepSeekChatCompletionsStreamer.kt`
- Modify: `shared/src/desktopTest/kotlin/com/agent/shared/agent/DeepSeekChatCompletionsStreamerTest.kt`

- [ ] **Step 1: 先写失败测试，定义 DeepSeek 请求体的历史序列**

```kotlin
@Test
fun `should map structured history into deepseek messages before current user prompt`() = runTest {
    var capturedRequest: DeepSeekChatCompletionRequest? = null
    val streamer = DeepSeekChatCompletionsStreamer(
        chunkRunner = { request, _ ->
            capturedRequest = request
            flowOf(
                DeepSeekChatCompletionChunk(
                    id = "chatcmpl-history",
                    created = 1L,
                    model = "deepseek-v4-flash",
                ),
            )
        },
    )

    streamer.stream(
        AgentRunRequest(
            prompt = "second turn",
            profile = deepSeekProfile(),
            history = listOf(
                AgentConversationHistoryMessage.User("first turn"),
                AgentConversationHistoryMessage.Assistant(
                    parts = listOf(
                        AgentConversationHistoryPart.Reasoning(
                            summary = "先分析",
                            rawText = "先分析原始思考",
                        ),
                        AgentConversationHistoryPart.ToolCall(
                            name = "read_file",
                            argumentsPreview = """{"path":"README.md"}""",
                        ),
                        AgentConversationHistoryPart.ToolResult(
                            name = "read_file",
                            resultPreview = "ok",
                        ),
                        AgentConversationHistoryPart.Text("最终回答"),
                    ),
                ),
            ),
        ),
    ).toList()

    assertEquals(
        listOf(
            DeepSeekChatMessage(role = "user", content = "first turn"),
            DeepSeekChatMessage(
                role = "assistant",
                content = """
[reasoning]
先分析原始思考
[/reasoning]

[tool_call:read_file]
{"path":"README.md"}
[/tool_call]

[tool_result:read_file]
ok
[/tool_result]

最终回答
                """.trimIndent(),
            ),
            DeepSeekChatMessage(role = "user", content = "second turn"),
        ),
        capturedRequest?.messages,
    )
}
```

- [ ] **Step 2: 运行 DeepSeek 适配测试确认失败**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DeepSeekChatCompletionsStreamerTest"
```

Expected:

```text
FAIL
Expected DeepSeekChatMessage history list but was only current prompt
```

- [ ] **Step 3: 在 `DeepSeekChatCompletionsStreamer` 中实现结构化历史到 provider messages 的最小映射**

```kotlin
internal fun buildRequest(request: AgentRunRequest): DeepSeekChatCompletionRequest =
    DeepSeekChatCompletionRequest(
        model = request.profile.model,
        messages = request.history.map(::toDeepSeekHistoryMessage) +
            DeepSeekChatMessage(role = "user", content = request.prompt),
        stream = true,
        streamOptions = DeepSeekStreamOptions(includeUsage = true),
        thinking = DeepSeekThinking(type = "enabled"),
        reasoningEffort = request.reasoningEffort?.wireValue,
    )

private fun toDeepSeekHistoryMessage(message: AgentConversationHistoryMessage): DeepSeekChatMessage =
    when (message) {
        is AgentConversationHistoryMessage.User -> DeepSeekChatMessage(
            role = "user",
            content = message.content,
        )

        is AgentConversationHistoryMessage.Assistant -> DeepSeekChatMessage(
            role = "assistant",
            content = serializeAssistantParts(message.parts),
        )
    }

private fun serializeAssistantParts(parts: List<AgentConversationHistoryPart>): String =
    parts.joinToString(separator = "\n\n") { part ->
        when (part) {
            is AgentConversationHistoryPart.Text -> part.text
            is AgentConversationHistoryPart.Reasoning ->
                "[reasoning]\n${part.rawText ?: part.summary.orEmpty()}\n[/reasoning]"
            is AgentConversationHistoryPart.ToolCall ->
                "[tool_call:${part.name}]\n${part.argumentsPreview.orEmpty()}\n[/tool_call]"
            is AgentConversationHistoryPart.ToolResult ->
                "[tool_result:${part.name}]\n${part.resultPreview.orEmpty()}\n[/tool_result]"
        }
    }.trim()
```

- [ ] **Step 4: 保持原有 reasoning/text chunk 到 `StreamFrame` 的映射逻辑不变**

```kotlin
chunkRunner(deepSeekRequest, request.profile).collect { chunk ->
    chunk.choices.firstOrNull()?.let { choice ->
        choice.delta.reasoningContent?.takeIf { it.isNotBlank() }?.let { reasoning ->
            emit(StreamFrame.ReasoningDelta(text = reasoning, index = choice.index))
        }
        choice.delta.content?.takeIf { it.isNotBlank() }?.let { content ->
            emit(StreamFrame.TextDelta(text = content, index = choice.index))
        }
        choice.finishReason?.let { finishReason = it }
    }
    chunk.usage?.let { usage ->
        metaInfo = ResponseMetaInfo.create(
            clock = KoogClock.System,
            totalTokensCount = usage.totalTokens,
            inputTokensCount = usage.promptTokens,
            outputTokensCount = usage.completionTokens,
            modelId = chunk.model,
        )
    }
}
```

- [ ] **Step 5: 运行 DeepSeek 适配测试确认通过**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.agent.DeepSeekChatCompletionsStreamerTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 3: 让 `ChatWindowState` 同时维护结构化历史与 UI 时间线

**Files:**
- Modify: `composeApp/src/desktopMain/kotlin/com/agent/app/ui/ChatWindowState.kt`
- Modify: `composeApp/src/desktopTest/kotlin/com/agent/app/ui/ChatWindowStateTest.kt`

- [ ] **Step 1: 先写失败测试，锁定第二轮请求必须读取第一轮结构化历史**

```kotlin
@Test
fun `should send second turn with first turn structured history`() = runTest(dispatcher) {
    val capturedRequests = mutableListOf<AgentRunRequest>()
    val gateway = object : AgentGateway {
        override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
            capturedRequests += request
            return flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.ReasoningDelta(summary = "先分析", rawText = "先分析原始思考"),
                AgentStreamEvent.ToolCallStarted(name = "read_file", argumentsPreview = """{"path":"README.md"}"""),
                AgentStreamEvent.ToolCallFinished(name = "read_file", resultPreview = "ok"),
                AgentStreamEvent.Status("searching"),
                AgentStreamEvent.TextDelta("done"),
                AgentStreamEvent.Completed("done"),
            )
        }
    }

    val state = ChatWindowState(
        sendMessageUseCase = SendMessageUseCase(gateway),
        snapshot = AppSessionSnapshot(
            profiles = listOf(profile(model = "deepseek-v4-flash")),
            activeProfile = profile(model = "deepseek-v4-flash"),
        ),
        projectPath = "E:\\abc\\def",
    )

    state.send("first")
    advanceUntilIdle()
    state.send("second")
    advanceUntilIdle()

    assertEquals(2, capturedRequests.size)
    assertEquals(emptyList(), capturedRequests[0].history)
    assertEquals(
        listOf(
            AgentConversationHistoryMessage.User("first"),
            AgentConversationHistoryMessage.Assistant(
                parts = listOf(
                    AgentConversationHistoryPart.Reasoning(
                        summary = "先分析",
                        rawText = "先分析原始思考",
                    ),
                    AgentConversationHistoryPart.ToolCall(
                        name = "read_file",
                        argumentsPreview = """{"path":"README.md"}""",
                    ),
                    AgentConversationHistoryPart.ToolResult(
                        name = "read_file",
                        resultPreview = "ok",
                    ),
                    AgentConversationHistoryPart.Text("done"),
                ),
            ),
        ),
        capturedRequests[1].history,
    )
}
```

- [ ] **Step 2: 运行窗口状态测试确认失败**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected:

```text
FAIL
Expected structured history in second request but was []
```

- [ ] **Step 3: 为 `ChatConversationUiState` 增加结构化历史字段与流式中的 assistant 历史索引**

```kotlin
data class ChatConversationUiState(
    val id: String,
    val title: String,
    val workspacePath: String,
    val items: List<ConversationItem> = emptyList(),
    val attachments: List<ChatAttachmentUiState> = emptyList(),
    val history: List<AgentConversationHistoryMessage> = emptyList(),
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val executionState: ExecutionState = ExecutionState.Idle,
    val streamingAssistantItemIndex: Int? = null,
    val streamingReasoningItemIndex: Int? = null,
    val streamingAssistantHistoryIndex: Int? = null,
    val contextUsageFraction: Float = 0.72f,
)
```

- [ ] **Step 4: 在发送前写入用户历史，并在流式事件中同步回填 assistant 历史**

```kotlin
fun sendDraft() {
    val prompt = ui.draft.trim()
    if (prompt.isBlank()) return

    val profile = activeProfile ?: return
    val targetConversationId = ui.activeConversationId
    val requestHistory = findConversation(targetConversationId).history

    mutateConversation(targetConversationId) { conversation ->
        val nextItems = conversation.items + ChatMessageItem(ChatMessage(ChatRole.User, prompt))
        conversation.copy(
            items = nextItems,
            history = conversation.history + AgentConversationHistoryMessage.User(content = prompt),
            executionState = ExecutionState.Running,
            attachments = emptyList(),
            streamingAssistantItemIndex = null,
            streamingReasoningItemIndex = null,
            streamingAssistantHistoryIndex = null,
        )
    }

    scope.launch {
        sendMessageUseCase(
            AgentRunRequest(
                prompt = prompt,
                profile = profile,
                history = requestHistory,
                reasoningEffort = supportedReasoningEffort(profile, findConversation(targetConversationId)),
            ),
        ).collect { event ->
            applyAgentEvent(targetConversationId, event)
        }
    }
}
```

- [ ] **Step 5: 在事件归并函数里维护 history，并显式跳过 `Status`**

```kotlin
private fun applyAgentEvent(conversationId: String, event: AgentStreamEvent) {
    when (event) {
        AgentStreamEvent.Started -> mutateConversation(conversationId) { it.copy(executionState = ExecutionState.Running) }

        is AgentStreamEvent.TextDelta -> mutateConversation(conversationId) { conversation ->
            appendAssistantTextHistory(appendAssistantDelta(conversation, event.text), event.text)
        }

        is AgentStreamEvent.ReasoningDelta -> mutateConversation(conversationId) { conversation ->
            appendAssistantReasoningHistory(
                appendReasoningDelta(conversation, event.summary, event.rawText),
                summary = event.summary,
                rawText = event.rawText,
            )
        }

        is AgentStreamEvent.ToolCallStarted -> mutateConversation(conversationId) { conversation ->
            appendAssistantToolCallHistory(
                appendToolEvent(conversation, event.name, ToolEventStatus.Started, event.argumentsPreview),
                event.name,
                event.argumentsPreview,
            )
        }

        is AgentStreamEvent.ToolCallFinished -> mutateConversation(conversationId) { conversation ->
            appendAssistantToolResultHistory(
                appendToolEvent(conversation, event.name, ToolEventStatus.Finished, event.resultPreview),
                event.name,
                event.resultPreview,
            )
        }

        is AgentStreamEvent.Status -> mutateConversation(conversationId) { conversation ->
            appendToolEvent(conversation, "status", ToolEventStatus.Status, event.message)
        }

        is AgentStreamEvent.Completed -> mutateConversation(conversationId) { conversation ->
            completeAssistantMessage(conversation, event.text)
        }

        is AgentStreamEvent.Failed -> mutateConversation(conversationId) { conversation ->
            conversation.copy(executionState = ExecutionState.Failed(AppError(title = "Agent 执行失败", message = event.reason)))
        }
    }
}
```

- [ ] **Step 6: 运行窗口状态测试确认通过**

Run:

```powershell
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

## Task 4: 回归验证 shared / composeApp 链路

**Files:**
- Modify: `shared/src/desktopTest/kotlin/com/agent/shared/agent/KoogAgentGatewayTest.kt`

- [ ] **Step 1: 补一条回归测试，确认扩展后的 `AgentRunRequest` 不会破坏 generic gateway 事件映射**

```kotlin
@Test
fun `should keep mapping stream frames when request carries history`() = runTest {
    val gateway = KoogAgentGateway(
        streamRunner = { _ ->
            flowOf(
                StreamFrame.TextDelta("hel"),
                StreamFrame.TextDelta("lo"),
                StreamFrame.End(),
            )
        },
    )

    val events = gateway.run(
        AgentRunRequest(
            prompt = "hello",
            profile = openAiProfile(),
            history = listOf(
                AgentConversationHistoryMessage.User("previous turn"),
            ),
        ),
    ).toList()

    assertEquals(AgentStreamEvent.Started, events[0])
    assertEquals(AgentStreamEvent.TextDelta("hel"), events[1])
    assertEquals(AgentStreamEvent.TextDelta("lo"), events[2])
    assertEquals(AgentStreamEvent.Completed("hello"), events[3])
}
```

- [ ] **Step 2: 运行 targeted tests，确认多轮改造没有打断现有链路**

Run:

```powershell
.\gradlew.bat :shared:desktopTest --tests "com.agent.shared.application.SendMessageUseCaseTest" --tests "com.agent.shared.agent.DeepSeekChatCompletionsStreamerTest" --tests "com.agent.shared.agent.KoogAgentGatewayTest"
.\gradlew.bat :composeApp:desktopTest --tests "com.agent.app.ui.ChatWindowStateTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 跑全量构建，确认没有跨模块回归**

Run:

```powershell
.\gradlew.bat build
```

Expected:

```text
BUILD SUCCESSFUL
```
