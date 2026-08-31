package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证 Jewel 终端滚动条与 JediTerm 模型之间的纯比例换算。 */
class TerminalJewelScrollbarTest {

    /** 中间历史位置应映射到 Jewel 代理范围的中点。 */
    @Test
    fun `should map terminal model position to jewel proxy offset`() {
        val snapshot = TerminalScrollSnapshot(
            minimum = 5,
            maximum = 105,
            extent = 20,
            value = 45,
        )

        assertEquals(20, snapshot.viewportSize)
        assertEquals(100, snapshot.contentSize)
        assertEquals(0.5f, terminalScrollFraction(snapshot))
        assertEquals(120, terminalProxyOffsetForModel(snapshot, proxyMaximum = 240))
    }

    /** Jewel 滑块拖动必须回写同一比例的位置，而非把像素偏移直接当成终端行号。 */
    @Test
    fun `should map jewel proxy fraction back to terminal model value`() {
        val snapshot = TerminalScrollSnapshot(
            minimum = 5,
            maximum = 105,
            extent = 20,
            value = 5,
        )

        assertEquals(45, terminalModelValueForProxyFraction(snapshot, fraction = 0.5f))
        assertEquals(85, terminalModelValueForProxyFraction(snapshot, fraction = 1f))
        assertEquals(5, terminalModelValueForProxyFraction(snapshot, fraction = -1f))
    }

    /** 没有超出可见区域的终端不能生成 NaN、负位置或越界模型值。 */
    @Test
    fun `should keep non scrollable terminal at zero proxy offset`() {
        val snapshot = TerminalScrollSnapshot(
            minimum = 0,
            maximum = 24,
            extent = 24,
            value = 0,
        )

        assertEquals(0f, terminalScrollFraction(snapshot))
        assertEquals(0, terminalProxyOffsetForModel(snapshot, proxyMaximum = 200))
        assertEquals(0, terminalModelValueForProxyFraction(snapshot, fraction = 1f))
        assertEquals(0f, terminalProxyFraction(proxyOffset = 12, proxyMaximum = 0))
    }
}
