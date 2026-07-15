package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 验证结构化助手历史的纯归并规则。
 */
class ConversationHistoryReducerTest {
    @Test
    fun `final text replaces streaming text instead of duplicating it`() {
        val conversation = ChatConversationUiState(
            id = "conversation",
            title = "Title",
            workspacePath = "E:\\workspace",
        )

        val streaming = appendAssistantTextHistory(conversation, "draft")
        val completed = finalizeAssistantTextHistory(streaming, "final").copy(
            streamingAssistantHistoryIndex = null,
        )

        assertEquals(
            listOf(AgentConversationHistoryPart.Text("final")),
            (completed.history.single() as AgentConversationHistoryMessage.Assistant).parts,
        )
        assertNull(completed.streamingAssistantHistoryIndex)
    }
}
