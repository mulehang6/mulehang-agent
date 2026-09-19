package com.agent.app.chat.state

import com.agent.app.chat.presentation.buildSecondaryStatus
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.chat.model.ExecutionState
import kotlin.test.*

/** 等待文案属于临时 UI 状态，不能伪装成工具或混入历史。 */
class AgentProgressTest {
    /** 多次阶段更新仅替换一条提示，不追加卡片。 */
    @Test
    fun replacesProgressWithoutTimelineOrHistory() {
        val initial = ChatConversationUiState("test", "test", workspacePath = "workspace", executionState = ExecutionState.Running)
        val waiting = reduceAgentEvent(initial, AgentStreamEvent.Status("正在准备工具…"), null)
        val next = reduceAgentEvent(waiting, AgentStreamEvent.Status("正在等待模型响应…"), null)
        assertEquals("正在等待模型响应…", buildSecondaryStatus(next))
        assertEquals(initial.items, next.items)
        assertEquals(initial.history, next.history)
    }

    /** 输出、工具执行和终态都移除已经过期的等待文案。 */
    @Test
    fun clearsProgressWhenRunAdvances() {
        val waiting = ChatConversationUiState("test", "test", workspacePath = "workspace",
            executionState = ExecutionState.Running, progressMessage = "正在等待模型响应…")
        val events = listOf(
            AgentStreamEvent.Started,
            AgentStreamEvent.TextDelta("你好"),
            AgentStreamEvent.ReasoningDelta(summary = "思考", rawText = null),
            AgentStreamEvent.ToolCallStarted(name = "read_file", argumentsPreview = "README.md"),
            AgentStreamEvent.Completed("完成"),
            AgentStreamEvent.Failed("连接失败"),
        )
        events.forEach { event ->
            assertNull(reduceAgentEvent(waiting, event, null).progressMessage, event.toString())
        }
    }

    /** 停止后的会话即使仍带有旧提示，也不能继续展示等待。 */
    @Test
    fun idleConversationDoesNotDisplayProgress() {
        val idle = ChatConversationUiState("test", "test", workspacePath = "workspace", progressMessage = "等待")
        assertNull(buildSecondaryStatus(idle))
    }
}
