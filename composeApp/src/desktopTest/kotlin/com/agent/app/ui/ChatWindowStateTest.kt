package com.agent.app.ui

import com.agent.shared.agent.AgentGateway
import com.agent.shared.agent.AgentRunRequest
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.agent.ReasoningEffort
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ConversationItem
import com.agent.shared.state.ExecutionState
import com.agent.shared.state.ReasoningItem
import com.agent.shared.state.ToolEventItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import kotlin.test.assertNotEquals

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
        )

        state.send("hi")
        advanceUntilIdle()

        assertEquals("Agent 执行失败: invalid api key", state.errorMessage)
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
        )

        state.send("hi")
        advanceUntilIdle()

        val reasoningItem = state.state.items[1] as ReasoningItem
        assertEquals("只拿到了原始思考", reasoningItem.displayText)
        assertEquals("只拿到了原始思考", reasoningItem.rawText)
    }

    /**
     * 新建对话应在当前工作目录分组下追加一条空会话，并切换焦点。
     */
    @Test
    fun `should create a new workspace conversation and switch focus to it`() = runTest(dispatcher) {
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
        assertEquals(2, state.ui.workspaceGroups.single().conversations.size)
        assertNotEquals(originalConversationId, state.ui.activeConversationId)
        assertEquals(emptyList(), state.ui.activeConversation.attachments)
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

    private fun profile(model: String = "gpt-4.1"): ConfigProfile = ConfigProfile(
        id = "openai-main",
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
