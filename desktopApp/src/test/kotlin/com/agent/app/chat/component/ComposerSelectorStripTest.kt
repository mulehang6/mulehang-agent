package com.agent.app.chat.component

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Composer 选择器完整卡片的定位回归测试。 */
class ComposerSelectorStripTest {

    /** 完整卡片只服务于已压缩的选择器组，且在组与卡片间移动指针时不应提前关闭。 */
    @Test
    fun `should keep full selector card only while a compressed group remains active`() {
        assertFalse(
            shouldKeepComposerSelectorCardVisible(
                compressed = false,
                stripHovered = true,
                cardHovered = false,
                menuExpanded = false,
            ),
        )
        assertTrue(
            shouldKeepComposerSelectorCardVisible(
                compressed = true,
                stripHovered = true,
                cardHovered = false,
                menuExpanded = false,
            ),
        )
        assertTrue(
            shouldKeepComposerSelectorCardVisible(
                compressed = true,
                stripHovered = false,
                cardHovered = true,
                menuExpanded = false,
            ),
        )
        assertEquals(160L, COMPOSER_SELECTOR_CARD_CLOSE_DELAY_MILLIS)
    }

    /** 完整控制卡片应覆盖紧凑选择器条所在的一行，以便横向替代完整的选择器组。 */
    @Test
    fun `should align selector card with compact selector strip row`() {
        val provider = ComposerSelectorCardPositionProvider()

        assertEquals(
            androidx.compose.ui.unit.IntOffset(100, 500),
            provider.calculatePosition(
                anchorBounds = IntRect(left = 100, top = 500, right = 420, bottom = 540),
                windowSize = IntSize(800, 700),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(520, 36),
            ),
        )
    }

    /** 卡片用选择器组的左上角定位，即使完整内容覆盖右侧固定操作也不改变锚点。 */
    @Test
    fun `should preserve selector group left edge for wide full card`() {
        val provider = ComposerSelectorCardPositionProvider()

        assertEquals(
            androidx.compose.ui.unit.IntOffset(100, 30),
            provider.calculatePosition(
                anchorBounds = IntRect(left = 100, top = 30, right = 420, bottom = 70),
                windowSize = IntSize(800, 700),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(760, 36),
            ),
        )
    }
}
