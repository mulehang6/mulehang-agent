package com.agent.app.chat.component

import java.util.ArrayDeque

/**
 * 在 Swing 分发尚未执行时合并同一图表的连续滚轮输入，避免消息队列积压后反复驱动时间线重绘。
 */
internal class DiagramBrowserWheelBatcher {
    private val pendingInputs = ArrayDeque<DiagramBrowserWheelInput>()
    private var dispatchScheduled = false

    /**
     * 接收一条浏览器输入；仅在调用方需要安排一次新的 Swing 分发时返回 `true`。
     */
    fun enqueue(input: DiagramBrowserWheelInput): Boolean = synchronized(this) {
        mergeWithLastInput(input)
        if (dispatchScheduled) {
            false
        } else {
            dispatchScheduled = true
            true
        }
    }

    /** 取出当前等待分发的输入，并允许下一批输入重新安排一次分发。 */
    fun drain(): List<DiagramBrowserWheelInput> = synchronized(this) {
        dispatchScheduled = false
        pendingInputs.toList().also { pendingInputs.clear() }
    }

    /** 仅合并修饰键状态相同的相邻输入，避免将滚动与缩放语义混在一起。 */
    private fun mergeWithLastInput(input: DiagramBrowserWheelInput) {
        val previousInput = pendingInputs.peekLast()
        if (previousInput == null || previousInput.controlDown != input.controlDown) {
            pendingInputs.addLast(input)
            return
        }
        pendingInputs.removeLast()
        val mergedDeltaY = previousInput.deltaY + input.deltaY
        when {
            !mergedDeltaY.isFinite() -> pendingInputs.addLast(input)
            mergedDeltaY != 0f -> pendingInputs.addLast(
                input.copy(deltaY = mergedDeltaY),
            )
        }
    }
}
