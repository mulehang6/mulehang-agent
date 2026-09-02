package com.agent.app.design

import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** 验证 Composer 主操作使用的 Jewel 图标映射。 */
class JewelIconsTest {

    /** 空闲态主操作必须使用应用自带的纸飞机图标，而不是运行或执行图标。 */
    @Test
    fun `should map composer send glyph to custom paper plane icon`() {
        assertEquals(COMPOSER_SEND_ICON_KEY, HeaderGlyph.SEND.iconKey)
    }

    /** 运行态主操作必须使用 Jewel 的红色停止方块，避免发送图标覆盖停止语义。 */
    @Test
    fun `should map composer stop glyph to suspend icon`() {
        assertEquals(AllIconsKeys.Actions.Suspend, HeaderGlyph.STOP.iconKey)
    }

    /** 右侧设置变更入口必须使用 Jewel 自带的通知图标，避免引入额外图标资源。 */
    @Test
    fun `should map settings change notification rail glyph to notification icon`() {
        assertEquals(AllIconsKeys.Toolwindows.Notifications, RightRailGlyph.NOTIFICATIONS.iconKey)
        assertEquals("设置变更通知", RightRailGlyph.NOTIFICATIONS.tooltip)
    }
}
