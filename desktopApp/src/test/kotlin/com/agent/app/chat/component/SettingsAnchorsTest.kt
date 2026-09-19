package com.agent.app.chat.component

import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** 验证实际布局坐标、动态内容与直接跳转策略。 */
class SettingsAnchorsTest {
    /** 内容展开后使用新坐标，高亮不依赖估算卡片高度。 */
    @Test
    fun highlightTracksLayoutAndBottom() {
        val positions = mapOf(ExtensionAnchor.OVERVIEW to 0, ExtensionAnchor.SKILLS to 300, ExtensionAnchor.MCP to 900)
        assertEquals(ExtensionAnchor.SKILLS, activeExtensionAnchor(positions, 450, 1_000))
        assertEquals(ExtensionAnchor.MCP, activeExtensionAnchor(positions, 1_000, 1_000))
        assertEquals(ExtensionAnchor.OVERVIEW, activeExtensionAnchor(positions + (ExtensionAnchor.SKILLS to 600), 450, 1_000))
    }

    /** 宽度重排后维持段落内阅读偏移；键盘跳转无需动画时钟。 */
    @Test
    fun resizeRestoresSectionOffsetAndKeyboardJumps() = runTest {
        val scroll = ScrollState(350)
        val state = SettingsAnchorState(scroll)
        state.positions.putAll(mapOf(ExtensionAnchor.OVERVIEW to 0, ExtensionAnchor.SKILLS to 300))
        state.updateViewport(900, 0f)
        state.updateViewport(360, 50f)
        state.positions[ExtensionAnchor.SKILLS] = 700
        state.restoreReading()
        assertEquals(750, scroll.value)
        state.navigate(ExtensionAnchor.SKILLS, animate = false)
        assertEquals(700, scroll.value)
    }
}
