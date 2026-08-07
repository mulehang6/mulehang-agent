package com.agent.app.design

import androidx.compose.ui.graphics.TransformOrigin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证下拉菜单与右键菜单共享的生长动效目标和空间原点。
 */
class MenuGrowthMotionTest {
    /** 菜单关闭时保持紧凑透明，打开后恢复完整尺寸与不透明度。 */
    @Test
    fun `should grow menus from compact transparent state`() {
        assertEquals(MenuGrowthTargets(scale = 0.96f, alpha = 0f, translationYDp = -4f), menuGrowthTargets(false))
        assertEquals(MenuGrowthTargets(scale = 0.96f, alpha = 0f, translationYDp = 6f), menuGrowthTargets(false, opensUpward = true))
        assertEquals(MenuGrowthTargets(scale = 1f, alpha = 1f, translationYDp = 0f), menuGrowthTargets(true))
    }

    /** 下拉菜单关联触发器顶部，右键菜单关联指针点击位置。 */
    @Test
    fun `should use trigger and pointer menu origins`() {
        assertEquals(TransformOrigin(0.5f, 1f), menuGrowthTransformOrigin(MenuGrowthOrigin.Dropdown))
        assertEquals(TransformOrigin(0f, 0f), menuGrowthTransformOrigin(MenuGrowthOrigin.Context))
    }

    /** 下拉菜单必须按框架最终放置的方向决定动画原点，不能固定为向上。 */
    @Test
    fun `should infer select menu growth direction from popup placement`() {
        assertEquals(true, selectMenuOpensUpward(popupTopPx = 120f, anchorTopPx = 200f))
        assertEquals(false, selectMenuOpensUpward(popupTopPx = 240f, anchorTopPx = 200f))
    }

    /** Air 分隔光带以鼠标位置为峰值，并向影响范围边缘逐步衰减。 */
    @Test
    fun `should fade divider highlight away from the pointer peak`() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0A6CD9), DividerAirBlue)
        assertEquals(0.5f, dividerHighlightPeakFraction(pointerPositionPx = 100f, trackLengthPx = 200f))
        assertEquals(0f, dividerHighlightPeakFraction(pointerPositionPx = -10f, trackLengthPx = 200f))
        assertEquals(1f, dividerHighlightPeakFraction(pointerPositionPx = 240f, trackLengthPx = 200f))
        assertEquals(1f, dividerHighlightIntensity(distancePx = 0f, radiusPx = 36f))
        assertEquals(0.5f, dividerHighlightIntensity(distancePx = 18f, radiusPx = 36f))
        assertEquals(0f, dividerHighlightIntensity(distancePx = 36f, radiusPx = 36f))
        assertEquals(0f, dividerHighlightIntensity(distancePx = 48f, radiusPx = 36f))
    }

    /** 菜单项从紧贴前一项的位置错峰滑入，且首项不应有额外延迟。 */
    @Test
    fun `should stagger select menu item card entrances`() {
        assertEquals(MenuItemEntranceTargets(scale = 0.985f, alpha = 0f, translationYDp = -12f), selectMenuItemEntranceTargets(false))
        assertEquals(MenuItemEntranceTargets(scale = 0.985f, alpha = 0f, translationYDp = 12f), selectMenuItemEntranceTargets(false, opensUpward = true))
        assertEquals(MenuItemEntranceTargets(scale = 1f, alpha = 1f, translationYDp = 0f), selectMenuItemEntranceTargets(true))
        assertEquals(0, selectMenuItemEntranceDelayMillis(0))
        assertEquals(60, selectMenuItemEntranceDelayMillis(1))
        assertEquals(120, selectMenuItemEntranceDelayMillis(2))
        assertEquals(0, selectMenuItemEntranceDelayMillis(-1))
        assertEquals(0, selectMenuItemEntranceIndex(index = 0, itemCount = 3, opensUpward = false))
        assertEquals(1, selectMenuItemEntranceIndex(index = 1, itemCount = 3, opensUpward = false))
        assertEquals(2, selectMenuItemEntranceIndex(index = 2, itemCount = 3, opensUpward = false))
        assertEquals(2, selectMenuItemEntranceIndex(index = 0, itemCount = 3, opensUpward = true))
        assertEquals(1, selectMenuItemEntranceIndex(index = 1, itemCount = 3, opensUpward = true))
        assertEquals(0, selectMenuItemEntranceIndex(index = 2, itemCount = 3, opensUpward = true))
    }
}
