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
        assertEquals(MenuGrowthTargets(scale = 1f, alpha = 1f, translationYDp = 0f), menuGrowthTargets(true))
    }

    /** 下拉菜单关联触发器顶部，右键菜单关联指针点击位置。 */
    @Test
    fun `should use trigger and pointer menu origins`() {
        assertEquals(TransformOrigin(0.5f, 0f), menuGrowthTransformOrigin(MenuGrowthOrigin.Dropdown))
        assertEquals(TransformOrigin(0f, 0f), menuGrowthTransformOrigin(MenuGrowthOrigin.Context))
    }
}
