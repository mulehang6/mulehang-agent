package com.agent.app.ui

import com.agent.shared.state.ChatMessage
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.state.ReasoningItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证聊天时间线展示文案的最小规则。
 */
class ChatScreenPresentationTest {

    /**
     * 聊天正文应直接显示内容，不再拼接角色前缀。
     */
    @Test
    fun `should render chat message without role prefix`() {
        val userItem = ChatMessageItem(ChatMessage(role = ChatRole.User, content = "你好"))
        val assistantItem = ChatMessageItem(ChatMessage(role = ChatRole.Assistant, content = "世界"))

        assertEquals("你好", buildChatMessageText(userItem))
        assertEquals("世界", buildChatMessageText(assistantItem))
    }

    /**
     * 思考块标题应保留 Thinking 文案，并区分流式中与完成态。
     */
    @Test
    fun `should keep thinking headline for reasoning block`() {
        assertEquals("Thinking: 思考中...", buildReasoningHeadline(ReasoningItem(isStreaming = true)))
        assertEquals("Thinking:", buildReasoningHeadline(ReasoningItem(isStreaming = false)))
    }
}
