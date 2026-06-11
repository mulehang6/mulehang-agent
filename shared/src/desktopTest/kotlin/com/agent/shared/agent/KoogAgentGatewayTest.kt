package com.agent.shared.agent

import ai.koog.prompt.streaming.StreamFrame
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 验证 Koog gateway 的本地错误分支。
 */
class KoogAgentGatewayTest {

    /**
     * 当前阶段不支持的 provider 应转换为 UI 可消费的失败事件。
     */
    @Test
    fun `should emit failed event for unsupported provider`() = runTest {
        val events = KoogAgentGateway().run(
            prompt = "hello",
            config = ConfigProfile(
                id = "google-main",
                providerType = ProviderType.GOOGLE,
                baseUrl = "https://generativelanguage.googleapis.com",
                apiKey = "key",
                model = "gemini-2.5-pro",
                enabled = true,
                layer = ConfigLayer.PROJECT,
            ),
        ).toList()

        assertEquals(2, events.size)
        assertEquals(AgentStreamEvent.Started, events.first())
        val failed = assertIs<AgentStreamEvent.Failed>(events.last())
        assertTrue(failed.reason.contains("暂不支持"))
    }

    /**
     * 流式文本与工具调用 frame 应被转换为应用层事件流。
     */
    @Test
    fun `should map stream frames into text and tool events`() = runTest {
        val gateway = KoogAgentGateway(
            streamRunner = { _, _ ->
                flowOf(
                    StreamFrame.TextDelta("hel"),
                    StreamFrame.ToolCallComplete(
                        id = "call-1",
                        name = "read_file",
                        content = """{"path":"README.md"}""",
                    ),
                    StreamFrame.TextDelta("lo"),
                    StreamFrame.End(),
                )
            },
        )

        val events = gateway.run("hello", openAiProfile()).toList()

        assertEquals(6, events.size)
        assertEquals(AgentStreamEvent.Started, events[0])
        assertEquals(AgentStreamEvent.TextDelta("hel"), events[1])
        assertEquals(
            AgentStreamEvent.ToolCallStarted(
                name = "read_file",
                argumentsPreview = """{"path":"README.md"}""",
            ),
            events[2],
        )
        assertEquals(
            AgentStreamEvent.ToolCallFinished(
                name = "read_file",
                resultPreview = """{"path":"README.md"}""",
            ),
            events[3],
        )
        assertEquals(AgentStreamEvent.TextDelta("lo"), events[4])
        assertEquals(AgentStreamEvent.Completed("hello"), events[5])
    }

    /**
     * reasoning frame 应映射为 summary 优先的思考流事件。
     */
    @Test
    fun `should map reasoning frames into reasoning events`() = runTest {
        val gateway = KoogAgentGateway(
            streamRunner = { _, _ ->
                flowOf(
                    StreamFrame.ReasoningDelta(
                        id = "r1",
                        text = "raw-1",
                        summary = "summary-1",
                    ),
                    StreamFrame.ReasoningComplete(
                        id = "r1",
                        content = listOf("raw-1", "raw-2"),
                        summary = listOf("summary-1", "summary-2"),
                    ),
                    StreamFrame.End(),
                )
            },
        )

        val events = gateway.run("hello", openAiProfile()).toList()

        assertEquals(4, events.size)
        assertEquals(AgentStreamEvent.Started, events[0])
        assertEquals(
            AgentStreamEvent.ReasoningDelta(
                summary = "summary-1",
                rawText = "raw-1",
            ),
            events[1],
        )
        assertEquals(
            AgentStreamEvent.ReasoningCompleted(
                summary = "summary-1summary-2",
                rawText = "raw-1raw-2",
            ),
            events[2],
        )
        assertEquals(AgentStreamEvent.Completed(""), events[3])
    }

    private fun openAiProfile(): ConfigProfile = ConfigProfile(
        id = "openai-main",
        providerType = ProviderType.OPENAI_RESPONSES,
        baseUrl = "https://api.openai.com/v1",
        apiKey = "key",
        model = "gpt-4.1",
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
