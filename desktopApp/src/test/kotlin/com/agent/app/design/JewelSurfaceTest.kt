package com.agent.app.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证无边框 Surface 不会退化为 Compose 的一像素 hairline。 */
class JewelSurfaceTest {
    /** 明确的零宽度与 Hairline 都不应调用边框绘制修饰符。 */
    @Test
    fun `should skip border drawing for zero width`() {
        assertFalse(shouldDrawJewelSurfaceBorder(0.dp))
        assertFalse(shouldDrawJewelSurfaceBorder(Dp.Hairline))
        assertTrue(shouldDrawJewelSurfaceBorder(1.dp))
    }
}
