package com.agent.shared.application

import com.agent.shared.agent.AgentGateway
import com.agent.shared.agent.AgentConversationHistoryMessage
import com.agent.shared.agent.AgentConversationHistoryPart
import com.agent.shared.agent.AgentRunRequest
import com.agent.shared.agent.AgentStreamEvent
import com.agent.shared.agent.ReasoningEffort
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ModelLimit
import com.agent.shared.config.ProviderType
import com.agent.shared.state.PermissionPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.TextDelta("hello"),
                AgentStreamEvent.Completed("hello world"),
            )
        }

        val events = SendMessageUseCase(gateway).invoke("hi", profile()).toList()

        assertEquals(3, events.size)
        assertEquals(AgentStreamEvent.Completed("hello world"), events.last())
    }

    /**
     * 用例应把 reasoning effort 透明转发到底层 gateway。
     */
    @Test
    fun `should forward reasoning effort to gateway request`() = runTest {
        var capturedRequest: AgentRunRequest? = null
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                capturedRequest = request
                return flowOf(AgentStreamEvent.Started, AgentStreamEvent.Completed("done"))
            }
        }

        SendMessageUseCase(gateway).invoke(
            AgentRunRequest(
                prompt = "hi",
                profile = profile(),
                reasoningEffort = ReasoningEffort.LOW,
            ),
        ).toList()

        assertEquals(ReasoningEffort.LOW, capturedRequest?.reasoningEffort)
    }

    /**
     * 用例应把结构化历史原样转发到底层 gateway。
     */
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

    /**
     * 请求诊断摘要应包含关键发送参数，但不能暴露 prompt 或 apiKey。
     */
    @Test
    fun `should build safe send request diagnostic summary`() {
        val request = AgentRunRequest(
            prompt = "secret prompt",
            profile = profile().copy(
                id = "deepseek:deepseek-v4-pro",
                providerId = "deepseek",
                providerLabel = "DeepSeek",
                model = "deepseek-v4-pro",
                limit = ModelLimit(context = 1_000_000, output = 384_000),
            ),
            reasoningEffort = ReasoningEffort.MAX,
        )

        val summary = buildAgentRunRequestDiagnostic(request)

        assertEquals(
            "Agent request: provider=deepseek model=deepseek-v4-pro reasoning_effort=max context=1000000 output=384000",
            summary,
        )
        assertEquals(false, summary.contains("secret prompt"))
        assertEquals(false, summary.contains("key"))
    }

    /**
     * 发送请求应保持冷流语义，每次收集都重新进入 gateway。
     */
    @Test
    fun `should invoke gateway for every flow collection`() = runTest {
        var calls = 0
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                calls += 1
                return flow {
                    emit(AgentStreamEvent.Started)
                    emit(AgentStreamEvent.Completed("done"))
                }
            }
        }
        val flow = SendMessageUseCase(gateway).invoke(
            AgentRunRequest(
                prompt = "hi",
                profile = profile(),
                reasoningEffort = ReasoningEffort.MAX,
            ),
        )

        flow.toList()
        flow.toList()

        assertEquals(2, calls)
    }

    /**
     * 工作区路径与权限档位应透传到底层 gateway 请求。
     */
    @Test
    fun `should forward workspace path and permission preset in run request`() = runTest {
        var capturedRequest: AgentRunRequest? = null
        val gateway = object : AgentGateway {
            override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                capturedRequest = request
                return flowOf(AgentStreamEvent.Started, AgentStreamEvent.Completed("done"))
            }
        }

        SendMessageUseCase(gateway).invoke(
            AgentRunRequest(
                prompt = "hello",
                profile = profile(),
                workspacePath = "D:\\repo",
                permissionPreset = PermissionPreset.DEFAULT,
            ),
        ).toList()

        assertEquals("D:\\repo", capturedRequest?.workspacePath)
        assertEquals(PermissionPreset.DEFAULT, capturedRequest?.permissionPreset)
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
