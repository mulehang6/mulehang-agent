package com.agent.app.chat.component

import com.agent.app.design.RightRailGlyph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 右侧工具栏上下分组选择规则的回归测试。 */
class RightToolSelectionTest {

    /** 同组按钮互相替换，再次点击当前按钮则关闭该组。 */
    @Test
    fun `should replace and close tools within same group`() {
        val notifications = RightToolSelection().toggle(UpperRightTool.NOTIFICATIONS)
        val settings = notifications.toggle(UpperRightTool.SETTINGS)
        val closed = settings.toggle(UpperRightTool.SETTINGS)

        assertEquals(UpperRightTool.NOTIFICATIONS, notifications.upper)
        assertEquals(UpperRightTool.SETTINGS, settings.upper)
        assertEquals(null, closed.upper)
    }

    /** 上下两组可以同时打开，并分别在工具栏中保持选中。 */
    @Test
    fun `should keep upper and lower tools open together`() {
        val selection = RightToolSelection()
            .toggle(UpperRightTool.NOTIFICATIONS)
            .toggle(LowerRightTool.CONVERSATION_TREE)

        assertTrue(selection.visible)
        assertEquals(
            setOf(RightRailGlyph.NOTIFICATIONS, RightRailGlyph.CONVERSATION_TREE),
            selection.selectedGlyphs,
        )
        assertFalse(selection.toggle(LowerRightTool.CONVERSATION_TREE).selectedGlyphs.contains(RightRailGlyph.CONVERSATION_TREE))
    }
}
