package com.agent.app.chat.state

import com.agent.shared.agent.api.*
import com.agent.shared.chat.model.*
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.tool.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

/** 验证ChatWindowStateTest的状态转换。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatWindowStateTest : ChatWindowTestFixture() {
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
            resourceDispatcher = dispatcher,
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
            resourceDispatcher = dispatcher,
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
            resourceDispatcher = dispatcher,
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
            resourceDispatcher = dispatcher,
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
        // 第一轮输入与输出应合并为一张完成卡片。
        val finishedEvent = toolEvents[0]
        assertEquals(ToolEventStatus.Finished, finishedEvent.status)
        assertEquals(null, finishedEvent.errorMessage)
        // 应追加一条独立 Failed 事件，而非改写历史完成卡片。
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
            resourceDispatcher = dispatcher,
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
            resourceDispatcher = dispatcher,
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
        assertEquals(ConversationItem.Kind.ToolEvent, state.state.items[1].kind)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[2].kind)
        val toolEvent = state.state.items[1] as ToolEventItem
        assertEquals("read_file", toolEvent.toolName)
        assertEquals(ToolEventStatus.Finished, toolEvent.status)
        assertEquals("ok", toolEvent.resultPreview)
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
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(gateway),
            snapshot = AppSessionSnapshot(
                profiles = listOf(profile()),
                activeProfile = profile(),
            ),
            projectPath = "E:\\abc\\def",
        )

        state.send("hi")
        advanceUntilIdle()

        assertEquals(5, state.state.items.size)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[0].kind)
        assertEquals(ConversationItem.Kind.Reasoning, state.state.items[1].kind)
        assertEquals(ConversationItem.Kind.ToolEvent, state.state.items[2].kind)
        assertEquals(ConversationItem.Kind.Reasoning, state.state.items[3].kind)
        assertEquals(ConversationItem.Kind.ChatMessage, state.state.items[4].kind)

        val firstReasoning = state.state.items[1] as ReasoningItem
        val secondReasoning = state.state.items[3] as ReasoningItem
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
            resourceDispatcher = dispatcher,
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
            resourceDispatcher = dispatcher,
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
            resourceDispatcher = dispatcher,
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

    /** 旧版无来源隐藏历史仅在用户明确恢复后归入所选工作区。 */
    @Test
    fun `should restore legacy unlinked history only after explicit confirmation`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\active",
            workspaceDirectoryExists = { true },
        )
        state.restoreTasks(
            listOf(
                ChatConversationUiState(
                    id = "legacy-history",
                    title = "旧版隐藏历史",
                    workspacePath = "",
                    items = listOf(ChatMessageItem(ChatMessage(ChatRole.User, "需要恢复"))),
                ),
            ),
        )

        assertEquals(1, state.legacyUnlinkedHistoryCount)
        assertEquals(null, state.restoreLegacyUnlinkedHistory("E:\\crud"))

        assertEquals(0, state.legacyUnlinkedHistoryCount)
        assertEquals("E:\\crud", state.findConversation("legacy-history").workspacePath)
        assertEquals(null, state.findConversation("legacy-history").detachedWorkspacePath)
    }

}
