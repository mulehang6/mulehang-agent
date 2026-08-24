package com.agent.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证 DWM 边框颜色哨兵值的抑制与恢复映射。 */
class WindowsWindowBorderTest {
    /** 抑制边框必须使用 NONE，恢复时必须使用 DEFAULT。 */
    @Test
    fun mapsBorderSuppressionToWindowsSdkSentinelValues() {
        assertEquals(-1, windowsDwmBorderColor(suppressed = true))
        assertEquals(-2, windowsDwmBorderColor(suppressed = false))
    }
}
