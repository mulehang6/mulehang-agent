package com.agent.shared.agent.recording

import com.agent.shared.agent.api.AgentGateway
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证回合记录装饰器仅在终态事件后写入完整诊断记录。
 */
class RecordingAgentGatewayTest {
    /**
     * 流式增量必须先透传；完成时只写入一条聚合后的完整记录。
     */
    @Test
    fun `records one completed run after all stream events arrive`() = runTest {
        val records = mutableListOf<AgentRunRecord>()
        val delegate = object : AgentGateway {
            override fun run(request: AgentRunRequest) = flowOf(
                AgentStreamEvent.Started,
                AgentStreamEvent.ReasoningDelta(summary = "分析", rawText = "先分析"),
                AgentStreamEvent.ReasoningCompleted(summary = "分析完成", rawText = "完整思考"),
                AgentStreamEvent.ToolCallStarted(
                    toolCallId = "tool-1",
                    name = "read_file",
                    argumentsPreview = "README.md",
                ),
                AgentStreamEvent.ToolCallFinished(
                    toolCallId = "tool-1",
                    name = "read_file",
                    resultPreview = "short",
                    resultDisplay = "完整工具输出",
                ),
                AgentStreamEvent.TextDelta("草稿"),
                AgentStreamEvent.Completed("最终回复"),
            )
        }
        val gateway = RecordingAgentGateway(
            delegate = delegate,
            recorder = AgentRunRecorder(records::add),
            runIdFactory = { "run-1" },
        )

        val events = gateway.run(request()).toList()

        assertEquals(7, events.size)
        assertEquals(1, records.size)
        assertEquals("run-1", records.single().runId)
        assertEquals("最终回复", records.single().finalText)
        assertEquals(
            listOf(AgentRunReasoningRecord(summary = "分析完成", rawText = "完整思考")),
            records.single().reasoning,
        )
        assertEquals(
            AgentRunToolRecord(
                toolCallId = "tool-1",
                name = "read_file",
                arguments = "README.md",
                result = "完整工具输出",
            ),
            records.single().tools.single(),
        )
        assertEquals(null, records.single().failureReason)
    }

    private fun request(): AgentRunRequest = AgentRunRequest(
        prompt = "请阅读 README",
        profile = ConfigProfile(
            id = "profile",
            providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test-key",
            model = "gpt-4.1",
            enabled = true,
            layer = ConfigLayer.PROJECT,
        ),
    )
}
