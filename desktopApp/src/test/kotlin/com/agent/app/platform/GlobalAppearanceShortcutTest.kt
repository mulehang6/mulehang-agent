package com.agent.app.platform

import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 验证窗口级全局外观快捷键的主键盘映射。 */
class GlobalAppearanceShortcutTest {

    /** Ctrl 加等号或减号应映射到缩放调整，Shift 不参与此纯映射判断。 */
    @Test
    fun `should map ctrl equals and minus to ui scale actions`() {
        assertEquals(
            UiScaleShortcutAction.INCREASE,
            resolveUiScaleShortcut(KeyEvent.VK_EQUALS, isControlDown = true),
        )
        assertEquals(
            UiScaleShortcutAction.DECREASE,
            resolveUiScaleShortcut(KeyEvent.VK_MINUS, isControlDown = true),
        )
    }

    /** 没有 Ctrl 或来自数字键盘的加减键必须保持原有输入行为。 */
    @Test
    fun `should ignore unmodified and numeric keypad keys`() {
        assertNull(resolveUiScaleShortcut(KeyEvent.VK_EQUALS, isControlDown = false))
        assertNull(resolveUiScaleShortcut(KeyEvent.VK_ADD, isControlDown = true))
        assertNull(resolveUiScaleShortcut(KeyEvent.VK_SUBTRACT, isControlDown = true))
    }
}
