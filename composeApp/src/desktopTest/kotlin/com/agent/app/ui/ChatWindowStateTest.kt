package com.agent.app.ui

import com.agent.shared.agent.AgentGateway
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ExecutionState
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
     * 发送消息时应追加用户消息、消费助手增量并回到空闲态。
     */
    @Test
    fun `should append messages and return idle after completed event`() = runTest(dispatcher) {
        val gateway = object : AgentGateway {
            override fun run(prompt: String, config: ConfigProfile): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.Delta("hello"),
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

        assertEquals(2, state.state.messages.size)
        assertEquals(ChatRole.User, state.state.messages.first().role)
        assertEquals(ChatRole.Assistant, state.state.messages.last().role)
        assertEquals(ExecutionState.Idle, state.state.executionState)
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
