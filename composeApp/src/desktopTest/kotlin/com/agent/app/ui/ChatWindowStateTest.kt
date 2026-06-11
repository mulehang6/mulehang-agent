package com.agent.app.ui

import com.agent.shared.agent.AgentGateway
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ConversationItem
import com.agent.shared.state.ExecutionState
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
            override fun run(prompt: String, config: ConfigProfile): Flow<AgentStreamEvent> = flowOf(
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
            override fun run(prompt: String, config: ConfigProfile): Flow<AgentStreamEvent> = flowOf(
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
            override fun run(prompt: String, config: ConfigProfile): Flow<AgentStreamEvent> = flowOf(
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

    private fun profile(): ConfigProfile = ConfigProfile(
        id = "openai-main",
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://api.openai.com/v1",
        apiKey = "key",
        model = "gpt-4.1",
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
