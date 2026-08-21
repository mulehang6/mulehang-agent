package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证浏览器滚轮输入在 Swing 分发前的合并规则。 */
class DiagramBrowserWheelBatcherTest {

    /** 同一修饰键状态的连续输入应合并为一次时间线更新。 */
    @Test
    fun mergesConsecutiveInputsWithTheSameModifierState() {
        val batcher = DiagramBrowserWheelBatcher()

        assertTrue(batcher.enqueue(wheelInput(controlDown = false, deltaY = 100f)))
        assertFalse(batcher.enqueue(wheelInput(controlDown = false, deltaY = 50f, x = 48f, y = 36f)))

        assertEquals(
            listOf(wheelInput(controlDown = false, deltaY = 150f, x = 48f, y = 36f)),
            batcher.drain(),
        )
    }

    /** Ctrl 状态变化必须保留为两次独立交互，不能把滚动和缩放混合。 */
    @Test
    fun preservesInputsAcrossModifierStateChanges() {
        val batcher = DiagramBrowserWheelBatcher()

        assertTrue(batcher.enqueue(wheelInput(controlDown = false, deltaY = 100f)))
        assertFalse(batcher.enqueue(wheelInput(controlDown = true, deltaY = -100f)))

        assertEquals(
            listOf(
                wheelInput(controlDown = false, deltaY = 100f),
                wheelInput(controlDown = true, deltaY = -100f),
            ),
            batcher.drain(),
        )
    }

    /** 方向相反的同类输入在同一批内抵消后不应再触发无效滚动。 */
    @Test
    fun dropsCancelledInputsWithinTheSameBatch() {
        val batcher = DiagramBrowserWheelBatcher()

        assertTrue(batcher.enqueue(wheelInput(controlDown = false, deltaY = 100f)))
        assertFalse(batcher.enqueue(wheelInput(controlDown = false, deltaY = -100f)))

        assertEquals(emptyList(), batcher.drain())
    }

    /** 创建供批处理测试复用的完整页面滚轮输入。 */
    private fun wheelInput(
        controlDown: Boolean,
        deltaY: Float,
        x: Float = 20f,
        y: Float = 30f,
    ): DiagramBrowserWheelInput = DiagramBrowserWheelInput(
        controlDown = controlDown,
        deltaY = deltaY,
        x = x,
        y = y,
    )
}
