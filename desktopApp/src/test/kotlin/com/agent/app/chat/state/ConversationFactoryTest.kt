package com.agent.app.chat.state

import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.session.AppSessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证聊天窗口初始状态与空白对话工厂规则。
 */
class ConversationFactoryTest {
    @Test
    fun `blank project starts without a placeholder conversation`() {
        val result = initialUiState(
            snapshot = AppSessionSnapshot(profiles = emptyList(), activeProfile = null),
            projectPath = "",
        )

        assertEquals(emptyList(), result.tasks)
        assertEquals("", result.activeTaskId)
    }

    @Test
    fun `new conversation preserves the default empty conversation semantics`() {
        val result = newConversation("E:\\workspace", contextWindow = 100)

        assertEquals("新建对话", result.title)
        assertEquals("E:\\workspace", result.workspacePath)
        assertEquals(ExecutionState.Idle, result.executionState)
        assertEquals(0f, result.contextUsageFraction)
        assertTrue(result.isEmptyDefaultConversation())
        assertEquals("重构 ChatWindowState", buildConversationTitle("  重构 ChatWindowState\n第二行  "))
    }
}
