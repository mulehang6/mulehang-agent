package com.agent.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 确保 Toast 激活参数准确携带事件、会话和条目。 */
class ToastActivationTargetTest {
    /** 包含特殊字符的标识可无损往返。 */
    @Test
    fun `round trips an exact target`() {
        val target = ToastActivationTarget("event+1", "conversation/2", "entry ? 3")
        assertEquals(target, ToastActivationTarget.parse(target.toArguments()))
    }

    /** 缺少事件或会话时拒绝激活。 */
    @Test
    fun `rejects incomplete activation arguments`() {
        assertNull(ToastActivationTarget.parse("eventId=x"))
        assertNull(ToastActivationTarget.parse("conversationId=x"))
        assertNull(ToastActivationTarget.parse("invalid"))
    }
}
