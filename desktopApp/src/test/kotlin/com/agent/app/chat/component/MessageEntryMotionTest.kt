package com.agent.app.chat.component

import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 验证新用户消息只在时间线最终位置执行一次克制的进入动效。
 */
class MessageEntryMotionTest {
    /** 删除淡入、缩放或上移中的任一视觉分量都应使本测试失败。 */
    @Test
    fun `should reveal a new message with visible upward growth`() {
        assertEquals(
            MessageEntryVisuals(alpha = 0f, scale = MESSAGE_ENTRY_INITIAL_SCALE, translationY = 24f),
            messageEntryVisuals(progress = 0f, travelDistancePx = 24f),
        )
        assertEquals(
            MessageEntryVisuals(alpha = 1f, scale = 1f, translationY = 0f),
            messageEntryVisuals(progress = 1f, travelDistancePx = 24f),
        )
    }

    /** 起始缩放必须明显小于最终尺寸，否则展开动效在实际界面上看不出来。 */
    @Test
    fun `should start noticeably smaller than the settled card`() {
        assertTrue(MESSAGE_ENTRY_INITIAL_SCALE <= 0.92f)
    }

    /** 淡入要在位移结束前完成，避免整段动画都停留在半透明状态。 */
    @Test
    fun `should finish fading in before the card stops moving`() {
        val midway = messageEntryVisuals(progress = 0.5f, travelDistancePx = 24f)

        assertEquals(1f, midway.alpha)
        assertTrue(midway.translationY > 0f)
    }

    /** 弹性收尾会让进度短暂超过 1，此时透明度不能溢出。 */
    @Test
    fun `should keep alpha within bounds while the spring overshoots`() {
        assertEquals(1f, messageEntryVisuals(progress = 1.05f, travelDistancePx = 24f).alpha)
    }

    /** 相同内容重复发送时只能选择最新用户消息，不能让历史消息重新动画。 */
    @Test
    fun `should animate only the newest matching user message`() {
        val older = ChatMessageItem(ChatMessage(role = ChatRole.User, content = "same"))
        val assistant = ChatMessageItem(ChatMessage(role = ChatRole.Assistant, content = "same"))
        val newest = ChatMessageItem(ChatMessage(role = ChatRole.User, content = "same"))

        assertSame(newest, latestMatchingUserMessage(listOf(older, assistant, newest), "same"))
        assertNull(latestMatchingUserMessage(listOf(older, newest), null))
    }
}
