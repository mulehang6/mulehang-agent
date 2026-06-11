package com.agent.shared.application

import com.agent.shared.agent.AgentGateway
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证发送消息用例的事件流转。
 */
class SendMessageUseCaseTest {

    /**
     * 用例应透明转发 agent gateway 事件。
     */
    @Test
    fun `should emit delta and completed events from gateway`() = runTest {
        val gateway = object : AgentGateway {
            override fun run(prompt: String, config: ConfigProfile): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.TextDelta("hello"),
                AgentStreamEvent.Completed("hello world"),
            )
        }

        val events = SendMessageUseCase(gateway).invoke("hi", profile()).toList()

        assertEquals(3, events.size)
        assertEquals(AgentStreamEvent.Completed("hello world"), events.last())
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
