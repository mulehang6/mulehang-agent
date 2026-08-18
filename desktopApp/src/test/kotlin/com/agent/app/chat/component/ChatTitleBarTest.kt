package com.agent.app.chat.component

import com.agent.app.design.IDEA_TITLE_BAR_HEIGHT
import com.agent.app.design.IDEA_TITLE_BAR_SEPARATOR_HEIGHT
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证 Jewel 标题栏的窗口命中边界、尺寸和菜单业务标签。 */
class ChatTitleBarTest {
    /** 仅四个真实控件可以将 JBR 命中切换为 Compose 客户端区域。 */
    @Test
    fun `should expose exactly four title bar client regions`() {
        assertEquals(
            setOf(
                TITLE_BAR_APPLICATION_CLIENT_REGION_KEY,
                TITLE_BAR_SIDEBAR_CLIENT_REGION_KEY,
                TITLE_BAR_PROJECT_CLIENT_REGION_KEY,
                TITLE_BAR_BRANCH_CLIENT_REGION_KEY,
            ),
            setOf(
                "application-menu",
                "sidebar-toggle",
                "project-selector",
                "branch-menu",
            ),
        )
    }

    /** 标题栏和 Jewel 固定分隔像素必须保持 IDEA Islands 的 54dp 加 1dp 结构。 */
    @Test
    fun `should preserve the Jewel title bar layout metrics`() {
        assertEquals(54, IDEA_TITLE_BAR_HEIGHT.value.toInt())
        assertEquals(1, IDEA_TITLE_BAR_SEPARATOR_HEIGHT.value.toInt())
        assertEquals(40, TITLE_BAR_ACTION_HEIGHT_DP)
        assertEquals(24, HEADER_PROJECT_ICON_SIZE_DP)
    }

    /** 三个 Jewel 下拉菜单继续暴露既有的应用、项目和分支操作。 */
    @Test
    fun `should preserve title bar menu actions`() {
        assertEquals("设置", TITLE_BAR_APPLICATION_SETTINGS_ACTION_LABEL)
        assertEquals("退出", TITLE_BAR_APPLICATION_EXIT_ACTION_LABEL)
        assertEquals("选择工作区…", TITLE_BAR_PROJECT_SELECT_ACTION_LABEL)
        assertEquals("刷新分支", TITLE_BAR_BRANCH_REFRESH_ACTION_LABEL)
        assertEquals("复制分支名", TITLE_BAR_BRANCH_COPY_ACTION_LABEL)
    }
}
