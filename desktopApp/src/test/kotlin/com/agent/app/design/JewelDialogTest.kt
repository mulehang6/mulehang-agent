package com.agent.app.design

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证 Islands 模态浮层始终位于当前应用窗口的可见范围内。 */
class JewelDialogTest {
    /** 常规窗口中居中，小于浮层的窗口中贴到左上可见边界。 */
    @Test
    fun `centered popup position stays visible`() {
        val centered = CenteredPopupPositionProvider.calculatePosition(
            anchorBounds = IntRect(700, 20, 760, 60),
            windowSize = IntSize(1200, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(600, 400),
        )
        val constrained = CenteredPopupPositionProvider.calculatePosition(
            anchorBounds = IntRect.Zero,
            windowSize = IntSize(500, 300),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(600, 400),
        )

        assertEquals(IntOffset(300, 200), centered)
        assertEquals(IntOffset.Zero, constrained)
    }
}
