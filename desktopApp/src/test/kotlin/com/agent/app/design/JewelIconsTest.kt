package com.agent.app.design

import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 验证 Composer 主操作使用的 Jewel 图标映射。 */
class JewelIconsTest {

    /** 空闲态主操作必须使用右向执行图标，而不是直向上箭头。 */
    @Test
    fun `should map composer send glyph to execute icon`() {
        assertEquals(AllIconsKeys.Actions.Execute, HeaderGlyph.SEND.iconKey)
    }

    /** 运行态主操作必须使用 Jewel 的红色停止方块，避免发送图标覆盖停止语义。 */
    @Test
    fun `should map composer stop glyph to suspend icon`() {
        assertEquals(AllIconsKeys.Actions.Suspend, HeaderGlyph.STOP.iconKey)
    }
}
