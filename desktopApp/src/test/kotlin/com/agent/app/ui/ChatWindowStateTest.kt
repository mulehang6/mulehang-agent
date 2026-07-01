package com.agent.app.ui

import com.agent.shared.agent.AgentGateway
import com.agent.shared.agent.AgentConversationHistoryMessage
import com.agent.shared.agent.AgentConversationHistoryPart
import com.agent.shared.agent.AgentRunRequest
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.agent.ApprovalRequest
import com.agent.shared.agent.QuestionRequest
import com.agent.shared.agent.ReasoningEffort
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ModelLimit
import com.agent.shared.config.ProviderType
import com.agent.shared.state.AppError
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ConversationItem
import com.agent.shared.state.ExecutionState
import com.agent.shared.state.PermissionPreset
import com.agent.shared.state.ReasoningItem
import com.agent.shared.state.ToolEventItem
import com.agent.shared.state.ToolEventStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 验证桌面聊天窗口状态的消息发送流转。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatWindowStateTest {
    private val dispatcher = StandardTestDispatcher()

    /**
     * 将 Main dispatcher 替换为测试 dispatcher。
     */
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /**
     * 恢复 Main dispatcher，避免污染其他测试。
     */
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 多个文本增量应折叠到同一条助手消息，并在完成后回到空闲态。
     */
    @Test
    fun `should merge text deltas into a single assistant message and return idle`() = runTest(dispatcher) {
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.TextDelta("hel"),
                AgentStreamEvent.TextDelta("lo"),
                AgentStreamEvent.Completed("hello"),
            )
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("hi")
        advanceUntilIdle()

        assertEquals(2, state.state.items.size)
        val userItem = state.state.items.first() as ChatMessageItem
        val assistantItem = state.state.items.last() as ChatMessageItem
        assertEquals(ChatRole.User, userItem.message.role)
        assertEquals(ChatRole.Assistant, assistantItem.message.role)
        assertEquals("hello", assistantItem.message.content)
        assertEquals(ExecutionState.Idle, state.state.executionState)
    }

    /**
     * Agent 失败时应暴露可被 UI 展示的错误文本。
     */
    @Test
    fun `should expose visible error message after failed event`() = runTest(dispatcher) {
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.Failed("invalid api key"),
            )
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("hi")
        advanceUntilIdle()

        assertEquals("Agent 执行失败: invalid api key", state.errorMessage)
    }

    /**
     * Agent 在工具调用进行中失败时，应将错误就地附加到最后一个 Started 工具事件，
     * 使其变为 Failed 状态并携带 errorMessage，而非在面板顶部单独展示。
     */
    @Test
    fun `should attach failure to last started tool event`() = runTest(dispatcher) {
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.ToolCallStarted(
                    toolCallId = "call-1",
                    name = "read_file",
                    argumentsPreview = """{"path":"README.md"}""",
                ),
                AgentStreamEvent.Failed("file not found"),
            )
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("hi")
        advanceUntilIdle()

        val toolEvent = state.state.items.lastOrNull { it is ToolEventItem } as? ToolEventItem
        assertEquals(ToolEventStatus.Failed, toolEvent?.status)
        assertEquals("read_file", toolEvent?.toolName)
        assertEquals("file not found", toolEvent?.errorMessage)
        assertEquals("Agent 执行失败: file not found", state.errorMessage)
    }

    /**
     * Agent 在没有任何工具调用进行中失败时，应追加一条独立的 Failed 工具事件，
     * 使错误信息仍在时间线中可见。
     */
    @Test
    fun `should append standalone failed tool event when no started tool exists`() = runTest(dispatcher) {
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.Failed("invalid api key"),
            )
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("hi")
        advanceUntilIdle()

        val failedEvent = state.state.items.lastOrNull { it is ToolEventItem } as? ToolEventItem
        assertEquals(ToolEventStatus.Failed, failedEvent?.status)
        assertEquals("invalid api key", failedEvent?.errorMessage)
    }

    /**
     * 上一轮已完成的工具调用不应被下一轮的 Failed 事件误伤。
     * 复现：第一轮 ToolCallStarted + ToolCallFinished + Completed，
     * 第二轮在新工具调用之前直接 Failed。
     * 此时 indexOfLast 会命中上一轮旧的 Started，但该 Started 后已有 Finished，
     * 所以应追加独立 Failed 事件，而不是改写历史成功工具事件。
     */
    @Test
    fun `should not mark previous turn finished tool event as failed`() = runTest(dispatcher) {
        var callCount = 0
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = when (callCount++) {
                0 -> flowOf(
                    AgentStreamEvent.Started,
                    AgentStreamEvent.ToolCallStarted(
                        toolCallId = "call-1",
                        name = "read_file",
                        argumentsPreview = """{"path":"README.md"}""",
                    ),
                    AgentStreamEvent.ToolCallFinished(
                        toolCallId = "call-1",
                        name = "read_file",
                        resultPreview = "ok",
                    ),
                    AgentStreamEvent.Completed("done"),
                )

                else -> flowOf(
                    AgentStreamEvent.Started,
                    AgentStreamEvent.Failed("network timeout"),
                )
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("first")
        advanceUntilIdle()
        state.send("second")
        advanceUntilIdle()

        val toolEvents = state.state.items.filterIsInstance<ToolEventItem>()
        // 第一轮的 Started 和 Finished 应保持原样
        val startedEvent = toolEvents[0]
        val finishedEvent = toolEvents[1]
        assertEquals(ToolEventStatus.Started, startedEvent.status)
        assertEquals(ToolEventStatus.Finished, finishedEvent.status)
        assertEquals(null, startedEvent.errorMessage)
        // 应追加一条独立 Failed 事件，而非改写历史 Started
        val failedEvent = toolEvents.last()
        assertEquals(ToolEventStatus.Failed, failedEvent.status)
        assertEquals("network timeout", failedEvent.errorMessage)
        assertEquals("error", failedEvent.toolName)
    }

    /**
     * 第二轮发送应读取第一轮沉淀下来的结构化历史，且不把 status 事件写入历史。
     */
    @Test
    fun `should send second turn with first turn structured history`() = runTest(dispatcher) {
        val capturedRequests = mutableListOf<AgentRunRequest>()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                capturedRequests += request
                return flowOf(
                    AgentStreamEvent.Started,
                    AgentStreamEvent.ReasoningDelta(summary = "先分析", rawText = "先分析原始思考"),
                    AgentStreamEvent.ToolCallStarted(
                        toolCallId = "call-1",
                        name = "read_file",
                        argumentsPreview = """{"path":"README.md"}""",
                    ),
                    AgentStreamEvent.ToolCallFinished(
                        toolCallId = "call-1",
                        name = "read_file",
                        resultPreview = "ok",
                    ),
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
                            id = "call-1",
                            name = "read_file",
                            argumentsPreview = """{"path":"README.md"}""",
                        ),
                        AgentConversationHistoryPart.ToolResult(
                            id = "call-1",
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

    /**
     * 工具调用事件应被保留为单独的时间线项，避免混入助手正文。
     */
    @Test
    fun `should append tool events as standalone timeline items`() = runTest(dispatcher) {
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.ToolCallStarted(name = "read_file", argumentsPreview = """{"path":"README.md"}"""),
                AgentStreamEvent.ToolCallFinished(name = "read_file", resultPreview = "ok"),
                AgentStreamEvent.TextDelta("done"),
                AgentStreamEvent.Completed("done"),
            )
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("hi")
        advanceUntilIdle()

        assertEquals(4, state.state.items.size)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[0].kind)
        assertEquals(ConversationItem.Kind.ToolEvent, state.state.items[1].kind)
        assertEquals(ConversationItem.Kind.ToolEvent, state.state.items[2].kind)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[3].kind)
        val started = state.state.items[1] as ToolEventItem
        val finished = state.state.items[2] as ToolEventItem
        assertEquals("read_file", started.toolName)
        assertEquals("read_file", finished.toolName)
    }

    /**
     * 如果正文增量早于后续工具/思考事件到达，最终完成态仍应把回答落在当前轮次最末尾。
     */
    @Test
    fun `should keep completed assistant message at end of current turn`() = runTest(dispatcher) {
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.ReasoningDelta(summary = "先分析问题", rawText = "先分析问题"),
                AgentStreamEvent.TextDelta("先给一个草稿回答"),
                AgentStreamEvent.ToolCallStarted(name = "read_file", argumentsPreview = """{"path":"README.md"}"""),
                AgentStreamEvent.ToolCallFinished(name = "read_file", resultPreview = "ok"),
                AgentStreamEvent.ReasoningDelta(summary = "结合文件结果继续分析", rawText = "结合文件结果继续分析"),
                AgentStreamEvent.Completed("最终回答"),
            )
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("hi")
        advanceUntilIdle()

        assertEquals(6, state.state.items.size)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[0].kind)
        assertEquals(ConversationItem.Kind.Reasoning, state.state.items[1].kind)
        assertEquals(ConversationItem.Kind.ToolEvent, state.state.items[2].kind)
        assertEquals(ConversationItem.Kind.ToolEvent, state.state.items[3].kind)
        assertEquals(ConversationItem.Kind.Reasoning, state.state.items[4].kind)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[5].kind)
        val assistantItem = state.state.items[5] as ChatMessageItem
        assertEquals(ChatRole.Assistant, assistantItem.message.role)
        assertEquals("最终回答", assistantItem.message.content)
    }

    /**
     * 思考流在工具前后出现时，应拆成多个默认展开的思考块，并保持正文单独流式拼接。
     */
    @Test
    fun `should split reasoning blocks around tool events and keep them expanded by default`() = runTest(dispatcher) {
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.ReasoningDelta(summary = "先分析问题", rawText = "先分析问题的原始内容"),
                AgentStreamEvent.ReasoningDelta(summary = "继续分析", rawText = "继续分析的原始内容"),
                AgentStreamEvent.ToolCallStarted(name = "search_web", argumentsPreview = """{"q":"kotlin"}"""),
                AgentStreamEvent.ToolCallFinished(name = "search_web", resultPreview = "ok"),
                AgentStreamEvent.ReasoningDelta(summary = "结合工具结果继续推理", rawText = "第二段原始思考"),
                AgentStreamEvent.TextDelta("answer"),
                AgentStreamEvent.Completed("answer"),
            )
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("hi")
        advanceUntilIdle()

        assertEquals(6, state.state.items.size)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[0].kind)
        assertEquals(ConversationItem.Kind.Reasoning, state.state.items[1].kind)
        assertEquals(ConversationItem.Kind.ToolEvent, state.state.items[2].kind)
        assertEquals(ConversationItem.Kind.ToolEvent, state.state.items[3].kind)
        assertEquals(ConversationItem.Kind.Reasoning, state.state.items[4].kind)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[5].kind)

        val firstReasoning = state.state.items[1] as ReasoningItem
        val secondReasoning = state.state.items[4] as ReasoningItem
        assertEquals("先分析问题继续分析", firstReasoning.displayText)
        assertEquals("先分析问题的原始内容继续分析的原始内容", firstReasoning.rawText)
        assertEquals(true, firstReasoning.expanded)
        assertEquals(false, firstReasoning.isStreaming)
        assertEquals("结合工具结果继续推理", secondReasoning.displayText)
        assertEquals(true, secondReasoning.expanded)
    }

    /**
     * reasoning 完成事件晚于正文增量到达时，不应额外补出第二个思考块。
     */
    @Test
    fun `should not duplicate reasoning block when completion arrives after text delta`() = runTest(dispatcher) {
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.ReasoningDelta(summary = "先分析问题", rawText = "先分析问题的原始内容"),
                AgentStreamEvent.TextDelta("answer"),
                AgentStreamEvent.ReasoningCompleted(
                    summary = "先分析问题总结",
                    rawText = "先分析问题的完整原始思考",
                ),
                AgentStreamEvent.Completed("answer"),
            )
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("hi")
        advanceUntilIdle()

        assertEquals(3, state.state.items.size)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[0].kind)
        assertEquals(ConversationItem.Kind.Reasoning, state.state.items[1].kind)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[2].kind)

        val reasoningItem = state.state.items[1] as ReasoningItem
        assertEquals("先分析问题总结", reasoningItem.displayText)
        assertEquals("先分析问题的完整原始思考", reasoningItem.rawText)
        assertEquals(false, reasoningItem.isStreaming)
        val assistantHistory = state.ui.activeConversation.history.last() as AgentConversationHistoryMessage.Assistant
        assertEquals(
            listOf(
                AgentConversationHistoryPart.Reasoning(
                    summary = "先分析问题总结",
                    rawText = "先分析问题的完整原始思考",
                ),
                AgentConversationHistoryPart.Text("answer"),
            ),
            assistantHistory.parts,
        )
    }

    /**
     * 首次启动且没有选择工作区时，不应创建占位工作区。
     */
    @Test
    fun `should start without workspace when project path is blank`() = runTest(dispatcher) {
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
        )

        assertEquals(emptyList(), state.ui.workspaceGroups)
        assertNull(state.ui.activeConversationOrNull)
        assertNull(state.errorMessage)
    }

    /**
     * 当 summary 缺失时，思考块应回退显示原始 reasoning 文本。
     */
    @Test
    fun `should fall back to raw reasoning text when summary is missing`() = runTest(dispatcher) {
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.ReasoningDelta(summary = null, rawText = "只拿到了原始思考"),
                AgentStreamEvent.Completed(""),
            )
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("hi")
        advanceUntilIdle()

        val reasoningItem = state.state.items[1] as ReasoningItem
        assertEquals("只拿到了原始思考", reasoningItem.displayText)
        assertEquals("只拿到了原始思考", reasoningItem.rawText)
    }

    /**
     * 新建对话时如果当前会话仍是空白默认会话，应替换它而不是留下两个“新对话”。
     */
    @Test
    fun `should replace current empty conversation when creating a new workspace conversation`() = runTest(dispatcher) {
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        val originalConversationId = state.ui.activeConversationId
        state.createConversationForWorkspace("E:\\abc\\def")

        assertEquals("def", state.ui.workspaceGroups.single().label)
        assertEquals(1, state.ui.workspaceGroups.single().conversations.size)
        assertNotEquals(originalConversationId, state.ui.activeConversationId)
        assertEquals(emptyList(), state.ui.activeConversation.attachments)
    }

    /**
     * 当前会话已有内容时，新建对话应保留历史线程并切换到新的空线程。
     */
    @Test
    fun `should keep existing non-empty conversation when creating a new one`() = runTest(dispatcher) {
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(streamingGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )
        val originalConversationId = state.ui.activeConversationId

        state.send("hello")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\abc\\def")

        assertEquals(2, state.ui.workspaceGroups.single().conversations.size)
        assertNotEquals(originalConversationId, state.ui.activeConversationId)
        assertEquals(2, state.findConversation(originalConversationId).items.size)
        assertEquals(emptyList(), state.ui.activeConversation.items)
    }

    /**
     * 对话标题应从首条消息生成一个短标题，去掉多行和冗余空白。
     */
    @Test
    fun `should build compact conversation title from first user prompt`() {
        assertEquals(
            "重构 ChatWindowState",
            buildConversationTitle("  重构 ChatWindowState\n避免重复的新对话  "),
        )
        assertEquals("新对话", buildConversationTitle("   "))
    }

    /**
     * task-first 侧栏应把仍在进行中的线程和已完成线程拆到两个分组。
     */
    @Test
    fun `should expose running and done task sections from task list`() = runTest(dispatcher) {
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(streamingGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("first")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\abc\\def")

        val sections = state.ui.taskSections.associateBy { it.group }
        assertEquals(1, sections[ChatTaskGroup.RUNNING]?.tasks?.size)
        assertEquals(1, sections[ChatTaskGroup.DONE]?.tasks?.size)
        assertEquals("新对话", sections[ChatTaskGroup.RUNNING]?.tasks?.single()?.title)
        assertEquals("first", sections[ChatTaskGroup.DONE]?.tasks?.single()?.title)
    }

    /**
     * 等待用户输入和等待审批仍属于进行中的任务，不应被归入 Done。
     */
    @Test
    fun `should keep waiting tasks in running section`() {
        assertEquals(
            ChatTaskGroup.RUNNING,
            taskGroupFor(
                ChatConversationUiState(
                    id = "waiting-question",
                    title = "Question",
                    workspacePath = "E:\\abc\\def",
                    items = listOf(ChatMessageItem(com.agent.shared.state.ChatMessage(ChatRole.User, "pending"))),
                    executionState = ExecutionState.WaitingForUserInput,
                ),
            ),
        )
        assertEquals(
            ChatTaskGroup.RUNNING,
            taskGroupFor(
                ChatConversationUiState(
                    id = "waiting-approval",
                    title = "Approval",
                    workspacePath = "E:\\abc\\def",
                    items = listOf(ChatMessageItem(com.agent.shared.state.ChatMessage(ChatRole.User, "pending"))),
                    executionState = ExecutionState.WaitingForApproval,
                ),
            ),
        )
    }

    /**
     * 空线程但执行已失败时应归入 Done，而非 Running。
     * 复现：sendDraft 的并发保护会在无 item 时直接设为 Failed。
     */
    @Test
    fun `should classify empty failed conversation as done`() {
        assertEquals(
            ChatTaskGroup.DONE,
            taskGroupFor(
                ChatConversationUiState(
                    id = "failed-empty",
                    title = "Failed",
                    workspacePath = "E:\\abc\\def",
                    items = emptyList(),
                    executionState = ExecutionState.Failed(AppError("已有任务在执行", "请等待")),
                ),
            ),
        )
    }

    /**
     * 空线程且空闲时应归入 Running，表示可接收输入。
     */
    @Test
    fun `should classify empty idle conversation as running`() {
        assertEquals(
            ChatTaskGroup.RUNNING,
            taskGroupFor(
                ChatConversationUiState(
                    id = "empty-idle",
                    title = "新对话",
                    workspacePath = "E:\\abc\\def",
                    items = emptyList(),
                    executionState = ExecutionState.Idle,
                ),
            ),
        )
    }

    /**
     * task-first 主导航切换时，应同步更新活动 task 与活动工作区标签。
     */
    @Test
    fun `should switch active task when selecting another conversation`() = runTest(dispatcher) {
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(streamingGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("seed")
        advanceUntilIdle()
        val firstConversationId = state.ui.activeConversationId
        state.createConversationForWorkspace("E:\\abc\\ghi")
        val secondConversationId = state.ui.activeConversationId

        state.selectConversation(firstConversationId)

        assertEquals(firstConversationId, state.ui.activeTaskId)
        assertEquals("def", state.ui.activeWorkspaceLabel)
        state.selectConversation(secondConversationId)
        assertEquals("ghi", state.ui.activeWorkspaceLabel)
    }

    /**
     * 刷新配置快照不应清空已经打开的多个工作区会话。
     */
    @Test
    fun `should keep workspace groups when session snapshot updates`() = runTest(dispatcher) {
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )
        state.createConversationForWorkspace("E:\\abc\\ghi")
        val activeConversationId = state.ui.activeConversationId

        state.updateSessionSnapshot(
            AppSessionSnapshot(
                profiles = listOf(profile(model = "gpt-4.1-mini")),
                activeProfile = profile(model = "gpt-4.1-mini"),
            ),
        )

        assertEquals(listOf("ghi", "def"), state.ui.workspaceGroups.map { it.label })
        assertEquals(activeConversationId, state.ui.activeConversationId)
        assertEquals("openai:gpt-4.1-mini", state.activeProfile?.id)
    }

    /**
     * snapshot 刷新后如果 profile context limit 变化，会话占比应立即重算。
     */
    @Test
    fun `should recalculate context usage when session snapshot reloads current profile`() = runTest(dispatcher) {
        val initialProfile = profile(
            model = "deepseek-v4-pro",
            limit = ModelLimit(context = 100, output = 20),
        )
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(initialProfile),
                activeProfile = initialProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("a".repeat(80))
        advanceUntilIdle()
        state.updateSessionSnapshot(
            AppSessionSnapshot(
                profiles = listOf(
                    initialProfile.copy(limit = ModelLimit(context = 200, output = 20)),
                ),
                activeProfile = initialProfile.copy(limit = ModelLimit(context = 200, output = 20)),
            ),
        )

        assertEquals(0.1f, state.ui.activeConversation.contextUsageFraction)
    }

    /**
     * 附件选择结果应挂到当前活动会话的输入区，而不是进入消息正文。
     */
    @Test
    fun `should attach selected files to active conversation draft`() = runTest(dispatcher) {
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.attachFiles(listOf("D:\\tmp\\ChatScreen.kt", "D:\\tmp\\design.png"))

        assertEquals(
            listOf("ChatScreen.kt", "design.png"),
            state.ui.activeConversation.attachments.map { it.name },
        )
    }

    /**
     * 发送动作只能影响当前激活的会话，不应回写到其他线程。
     */
    @Test
    fun `should append messages only to the active conversation`() = runTest(dispatcher) {
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(streamingGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )
        val firstConversationId = state.ui.activeConversationId
        state.send("seed")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\abc\\def")
        val secondConversationId = state.ui.activeConversationId

        state.updateDraft("hello")
        state.sendDraft()
        advanceUntilIdle()

        val firstConversation = state.findConversation(firstConversationId)
        val secondConversation = state.findConversation(secondConversationId)
        assertEquals(2, firstConversation.items.size)
        assertEquals(2, secondConversation.items.filterIsInstance<ChatMessageItem>().size)
    }

    /**
     * 当前会话的 thinking level 应进入发送请求，避免只停留在 hover 展示层。
     */
    @Test
    fun `should send active conversation reasoning effort with draft`() = runTest(dispatcher) {
        var capturedRequest: AgentRunRequest? = null
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                capturedRequest = request
                return flowOf(
                    AgentStreamEvent.Started,
                    AgentStreamEvent.Completed("ok"),
                )
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile(model = "deepseek-r1")),
                activeProfile = profile(model = "deepseek-r1"),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.updateReasoningEffort(ReasoningEffort.HIGH)
        state.updateDraft("hello")
        state.sendDraft()
        advanceUntilIdle()

        assertEquals(ReasoningEffort.HIGH, state.ui.activeConversation.reasoningEffort)
        assertEquals(ReasoningEffort.HIGH, capturedRequest?.reasoningEffort)
    }

    /**
     * 当前会话默认档位不在模型 variants 内时，应使用 profile 能力默认档位发送。
     */
    @Test
    fun `should send profile default reasoning effort when conversation effort is unsupported`() = runTest(dispatcher) {
        var capturedRequest: AgentRunRequest? = null
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                capturedRequest = request
                return flowOf(
                    AgentStreamEvent.Started,
                    AgentStreamEvent.Completed("ok"),
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

        state.updateDraft("hello")
        state.sendDraft()
        advanceUntilIdle()

        assertEquals(ReasoningEffort.HIGH, capturedRequest?.reasoningEffort)
    }

    /**
     * 当前模型不支持 thinking 时，发送请求不应携带 reasoning effort。
     */
    @Test
    fun `should omit reasoning effort for unsupported active profile`() = runTest(dispatcher) {
        var capturedRequest: AgentRunRequest? = null
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                capturedRequest = request
                return flowOf(
                    AgentStreamEvent.Started,
                    AgentStreamEvent.Completed("ok"),
                )
            }
        }
        val unsupportedProfile = profile(model = "claude-sonnet-4")
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(unsupportedProfile),
                activeProfile = unsupportedProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        state.updateReasoningEffort(ReasoningEffort.HIGH)
        state.updateDraft("hello")
        state.sendDraft()
        advanceUntilIdle()

        assertEquals(null, capturedRequest?.reasoningEffort)
    }

    /**
     * 同一个 provider 下切换模型时，应只切换运行时 profile，不改变 provider 分组语义。
     */
    @Test
    fun `should select model under the same provider`() = runTest(dispatcher) {
        val flash = profile(model = "deepseek-v4-flash")
        val pro = profile(model = "deepseek-v4-pro").copy(
            id = "deepseek:deepseek-v4-pro",
            providerId = "deepseek",
            providerLabel = "DeepSeek",
        )
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(flash, pro),
                activeProfile = flash,
            ),
            projectPath = "E:\\abc\\def",
        )

        state.selectProfile("deepseek:deepseek-v4-pro")

        assertEquals("deepseek", state.activeProfile?.providerId)
        assertEquals("deepseek-v4-pro", state.activeProfile?.model)
    }

    /**
     * Provider 分组应使用配置 providerId，而不是 providerType。
     */
    @Test
    fun `should group model picker entries by provider id`() {
        val profiles = listOf(
            profile(model = "deepseek-v4-flash"),
            profile(model = "deepseek-v4-pro").copy(
                id = "deepseek:deepseek-v4-pro",
                providerId = "deepseek",
                providerLabel = "DeepSeek",
            ),
            profile(model = "openai/gpt-5-codex").copy(
                id = "openrouter:openai/gpt-5-codex",
                providerId = "openrouter",
                providerLabel = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
            ),
        )

        val grouped = groupProfilesByProvider(profiles)

        assertEquals(setOf("deepseek", "openrouter"), grouped.keys)
        assertEquals(listOf("deepseek-v4-flash", "deepseek-v4-pro"), grouped["deepseek"]?.map { it.model })
    }

    /**
     * 上下文圆环应使用当前 profile 的 context limit 作为分母，而不是固定展示占比。
     */
    @Test
    fun `should estimate context usage from active profile context limit`() = runTest(dispatcher) {
        val limitedProfile = profile(
            model = "deepseek-v4-pro",
            limit = ModelLimit(context = 100, output = 20),
        )
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(limitedProfile),
                activeProfile = limitedProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("a".repeat(80))
        advanceUntilIdle()

        assertEquals(0.2f, state.ui.activeConversation.contextUsageFraction)
    }

    /**
     * 空会话的上下文占用应按 profile limit 初始化为 0，而不是沿用占位百分比。
     */
    @Test
    fun `should initialize context usage from active profile context limit`() = runTest(dispatcher) {
        val limitedProfile = profile(
            model = "deepseek-v4-pro",
            limit = ModelLimit(context = 100, output = 20),
        )
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(limitedProfile),
                activeProfile = limitedProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        assertEquals(0f, state.ui.activeConversation.contextUsageFraction)
    }

    /**
     * 切换 profile 后应使用新模型的 context limit 重算已有会话占比。
     */
    @Test
    fun `should recalculate context usage when active profile changes`() = runTest(dispatcher) {
        val smallContextProfile = profile(
            model = "deepseek-v4-pro",
            limit = ModelLimit(context = 100, output = 20),
        )
        val largeContextProfile = profile(
            model = "deepseek-v4-flash",
            limit = ModelLimit(context = 200, output = 20),
        )
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(
                profiles = listOf(smallContextProfile, largeContextProfile),
                activeProfile = smallContextProfile,
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("a".repeat(80))
        advanceUntilIdle()
        state.selectProfile(largeContextProfile.id)

        assertEquals(0.1f, state.ui.activeConversation.contextUsageFraction)
    }

    /**
     * 执行中再次触发主按钮时应取消当前轮次，并恢复到空闲态。
     */
    @Test
    fun `should cancel active run and return idle when requested`() = runTest(dispatcher) {
        val cancelled = CompletableDeferred<Unit>()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("long running")
        advanceUntilIdle()
        assertEquals(ExecutionState.Running, state.ui.activeConversation.executionState)

        state.cancelActiveRun()
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertTrue(cancelled.isCompleted)
    }

    /**
     * 当前交互桥仍是单轮挂起模型时，应明确禁止第二个 task 并发发送。
     */
    @Test
    fun `should reject concurrent send from another task while one run is active`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val capturedRequests = mutableListOf<AgentRunRequest>()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                capturedRequests += request
                emit(AgentStreamEvent.Started)
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("task-a")
        advanceUntilIdle()
        state.createConversationForWorkspace("E:\\abc\\ghi")
        state.send("task-b")
        advanceUntilIdle()

        assertEquals(1, capturedRequests.size)
        assertEquals("task-a", capturedRequests.single().prompt)
        assertEquals(
            "已有任务在执行: 请等待当前任务完成，或先停止当前任务再启动新的 task。",
            state.errorMessage,
        )
        assertEquals(ExecutionState.Running, state.findConversation(state.ui.tasks.last().id).executionState)
        assertTrue(started.isCompleted)
    }

    /**
     * ask_user 期间应保持同一轮次挂起，回答后继续执行而不是新开一轮。
     */
    @Test
    fun `should keep same turn running while waiting for ask user response`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                val question = QuestionRequest(
                    requestId = "q1",
                    toolCallId = "call-1",
                    question = "Pick one",
                    options = listOf("Option A", "Option B"),
                )
                emit(AgentStreamEvent.QuestionRequested(question))
                val answer = coordinator.requestQuestion(question)
                emit(AgentStreamEvent.Completed("selected: $answer"))
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("start")
        advanceUntilIdle()

        assertEquals(ExecutionState.WaitingForUserInput, state.ui.activeConversation.executionState)
        assertEquals("Pick one", state.ui.activeConversation.pendingQuestion?.question)

        state.answerPendingQuestion("Option A")
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals(null, state.ui.activeConversation.pendingQuestion)
        val assistantItem = state.state.items.last() as ChatMessageItem
        assertEquals("selected: Option A", assistantItem.message.content)
    }

    /**
     * 回答挂起问题时，应定向恢复原会话，而不是当前选中的其他 task。
     */
    @Test
    fun `should resume question on owning conversation even after switching tasks`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                val question = QuestionRequest(
                    requestId = "q1",
                    toolCallId = "call-1",
                    question = "Pick one",
                    options = listOf("Option A", "Option B"),
                )
                emit(AgentStreamEvent.QuestionRequested(question))
                val answer = coordinator.requestQuestion(question)
                emit(AgentStreamEvent.Completed("selected: $answer"))
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("start")
        advanceUntilIdle()
        val ownerConversationId = state.ui.activeConversationId
        state.createConversationForWorkspace("E:\\abc\\ghi")
        val otherConversationId = state.ui.activeConversationId

        state.answerPendingQuestion("Option A")
        advanceUntilIdle()

        val ownerConversation = state.findConversation(ownerConversationId)
        val otherConversation = state.findConversation(otherConversationId)
        assertEquals(ExecutionState.Idle, ownerConversation.executionState)
        assertEquals(null, ownerConversation.pendingQuestion)
        assertEquals("selected: Option A", (ownerConversation.items.last() as ChatMessageItem).message.content)
        assertEquals(ExecutionState.Idle, otherConversation.executionState)
        assertEquals(null, otherConversation.pendingQuestion)
    }

    /**
     * 当前会话的工作区和权限档位必须进入 agent 运行请求。
     */
    @Test
    fun `should send workspace path and permission preset from active conversation`() = runTest(dispatcher) {
        var capturedRequest: AgentRunRequest? = null
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                capturedRequest = request
                return flowOf(
                    AgentStreamEvent.Started,
                    AgentStreamEvent.Completed("ok"),
                )
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.updatePermission(PermissionPreset.BRAVE)
        state.send("hello")
        advanceUntilIdle()

        assertEquals("E:\\abc\\def", capturedRequest?.workspacePath)
        assertEquals(PermissionPreset.BRAVE, capturedRequest?.permissionPreset)
    }

    /**
     * 审批请求应进入等待态，提交结果后继续当前轮次。
     */
    @Test
    fun `should keep same turn running while waiting for approval response`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                val approval = ApprovalRequest(
                    requestId = "a1",
                    toolName = "run_powershell",
                    summary = "执行 PowerShell 7 脚本",
                    payloadPreview = "Get-Location",
                )
                emit(AgentStreamEvent.ApprovalRequested(approval))
                val approved = coordinator.requestApproval(approval)
                emit(AgentStreamEvent.Completed("approved: $approved"))
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("run command")
        advanceUntilIdle()

        assertEquals(ExecutionState.WaitingForApproval, state.ui.activeConversation.executionState)
        assertEquals("run_powershell", state.ui.activeConversation.pendingApproval?.toolName)

        state.answerPendingApproval(true)
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals(null, state.ui.activeConversation.pendingApproval)
        val assistantItem = state.state.items.last() as ChatMessageItem
        assertEquals("approved: true", assistantItem.message.content)
    }

    /**
     * 提交审批时，应只恢复发起审批请求的会话。
     */
    @Test
    fun `should resume approval on owning conversation even after switching tasks`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                val approval = ApprovalRequest(
                    requestId = "a1",
                    toolName = "run_powershell",
                    summary = "执行 PowerShell 7 脚本",
                    payloadPreview = "Get-Location",
                )
                emit(AgentStreamEvent.ApprovalRequested(approval))
                val approved = coordinator.requestApproval(approval)
                emit(AgentStreamEvent.Completed("approved: $approved"))
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("run command")
        advanceUntilIdle()
        val ownerConversationId = state.ui.activeConversationId
        state.createConversationForWorkspace("E:\\abc\\ghi")
        val otherConversationId = state.ui.activeConversationId

        state.answerPendingApproval(true)
        advanceUntilIdle()

        val ownerConversation = state.findConversation(ownerConversationId)
        val otherConversation = state.findConversation(otherConversationId)
        assertEquals(ExecutionState.Idle, ownerConversation.executionState)
        assertEquals(null, ownerConversation.pendingApproval)
        assertEquals("approved: true", (ownerConversation.items.last() as ChatMessageItem).message.content)
        assertEquals(ExecutionState.Idle, otherConversation.executionState)
        assertEquals(null, otherConversation.pendingApproval)
    }

    /**
     * 取消正在等待用户输入的轮次时，应清理挂起状态、释放运行槽位并恢复空闲态。
     */
    @Test
    fun `should cancel waiting for user input and clear pending state`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                emit(
                    AgentStreamEvent.QuestionRequested(
                        QuestionRequest(
                            requestId = "q1",
                            toolCallId = "call-1",
                            question = "Pick one",
                            options = listOf("Option A", "Option B"),
                        )
                    )
                )
                awaitCancellation()
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("start")
        advanceUntilIdle()

        assertEquals(ExecutionState.WaitingForUserInput, state.ui.activeConversation.executionState)
        assertEquals("Pick one", state.ui.activeConversation.pendingQuestion?.question)

        state.cancelActiveRun()
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals(null, state.ui.activeConversation.pendingQuestion)

        state.send("after cancel")
        advanceUntilIdle()

        val lastItem = state.ui.activeConversation.items.last() as ChatMessageItem
        assertEquals(ChatRole.User, lastItem.message.role)
        assertEquals("after cancel", lastItem.message.content)
    }

    /**
     * 取消正在等待审批的轮次时，应清理挂起状态、释放运行槽位并恢复空闲态。
     */
    @Test
    fun `should cancel waiting for approval and clear pending state`() = runTest(dispatcher) {
        val coordinator = DesktopToolInteractionCoordinator()
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flow {
                emit(AgentStreamEvent.Started)
                emit(
                    AgentStreamEvent.ApprovalRequested(
                        ApprovalRequest(
                            requestId = "a1",
                            toolName = "run_powershell",
                            summary = "执行 PowerShell 脚本",
                            payloadPreview = "Get-Location",
                        )
                    )
                )
                awaitCancellation()
            }
        }
        val state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
            toolInteractionCoordinator = coordinator,
        )

        state.send("run command")
        advanceUntilIdle()

        assertEquals(ExecutionState.WaitingForApproval, state.ui.activeConversation.executionState)
        assertEquals("run_powershell", state.ui.activeConversation.pendingApproval?.toolName)

        state.cancelActiveRun()
        advanceUntilIdle()

        assertEquals(ExecutionState.Idle, state.ui.activeConversation.executionState)
        assertEquals(null, state.ui.activeConversation.pendingApproval)
    }

    private fun profile(
        model: String = "gpt-4.1",
        limit: ModelLimit? = null,
    ): ConfigProfile = ConfigProfile(
        id = if (model.startsWith("deepseek", ignoreCase = true)) {
            "deepseek:$model"
        } else {
            "openai:$model"
        },
        providerId = if (model.startsWith("deepseek", ignoreCase = true)) "deepseek" else "openai",
        providerLabel = if (model.startsWith("deepseek", ignoreCase = true)) "DeepSeek" else "OpenAI",
        providerType = if (model.startsWith("deepseek", ignoreCase = true)) {
            ProviderType.OPENAI_CHAT_COMPLETIONS
        } else {
            ProviderType.OPENAI_RESPONSES
        },
        baseUrl = if (model.startsWith("deepseek", ignoreCase = true)) {
            "https://api.deepseek.com/v1"
        } else {
            "https://api.openai.com/v1"
        },
        apiKey = "key",
        model = model,
        enabled = true,
        layer = ConfigLayer.PROJECT,
        limit = limit,
    )

    private fun idleGateway(): AgentGateway = object : AgentGateway {
        override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
            AgentStreamEvent.Started,
            AgentStreamEvent.Completed(""),
        )
    }

    private fun streamingGateway(): AgentGateway = object : AgentGateway {
        override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
            AgentStreamEvent.Started,
            AgentStreamEvent.TextDelta("hel"),
            AgentStreamEvent.TextDelta("lo"),
            AgentStreamEvent.Completed("hello"),
        )
    }
}
