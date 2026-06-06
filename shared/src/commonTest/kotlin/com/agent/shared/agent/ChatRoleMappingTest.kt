package com.agent.shared.agent

import ai.koog.prompt.message.Message
import com.agent.shared.state.ChatMessage
import com.agent.shared.state.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证应用层消息角色与 Koog 角色之间的边界映射。
 */
class ChatRoleMappingTest {
    /**
     * Koog 角色应映射为应用层角色。
     */
    @Test
    fun `should map koog roles to chat roles`() {
        assertEquals(ChatRole.System, Message.Role.System.toChatRole())
        assertEquals(ChatRole.User, Message.Role.User.toChatRole())
        assertEquals(ChatRole.Assistant, Message.Role.Assistant.toChatRole())
    }

    /**
     * 应用层角色应映射回 Koog 角色。
     */
    @Test
    fun `should map chat roles to koog roles`() {
        assertEquals(Message.Role.System, ChatRole.System.toKoogRole())
        assertEquals(Message.Role.User, ChatRole.User.toKoogRole())
        assertEquals(Message.Role.Assistant, ChatRole.Assistant.toKoogRole())
    }

    /**
     * 应用层消息模型应只依赖本地角色抽象。
     */
    @Test
    fun `should keep chat message on local role abstraction`() {
        val message = ChatMessage(
            role = ChatRole.User,
            content = "hello",
        )

        assertEquals(ChatRole.User, message.role)
        assertEquals("hello", message.content)
    }
}
