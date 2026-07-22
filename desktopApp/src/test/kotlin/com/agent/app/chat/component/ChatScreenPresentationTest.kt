package com.agent.app.chat.component

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.agent.app.chat.state.buildWorkspaceLabel
import com.agent.app.chat.state.isStoppable
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.AirSidebarStyle
import com.agent.app.design.AppWorkspaceBackground
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.SELECT_POPUP_FOCUSABLE
import com.agent.app.design.SELECT_TOOLTIP_DELAY_MILLIS
import com.agent.app.design.buildHeaderActions
import com.agent.app.design.buildRightRailGroups
import com.agent.app.design.desiredSelectExpandedState
import com.agent.app.design.workspaceBackdropOffset
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * 验证仍与 ChatScreen Compose 组件同包的展示规则。
 */
class ChatScreenPresentationTest {

    /**
     * pwd 分组标题应只显示末级目录名。
     */
    @Test
    fun `should map workspace path to terminal folder label`() {
        assertEquals("def", buildWorkspaceLabel("E:\\abc\\def"))
        assertEquals("repo-x", buildWorkspaceLabel("D:\\work\\repo-x"))
    }

    /**
     * isStoppable 应覆盖运行、等待输入和等待审批，但不包括空闲和失败。
     */
    @Test
    fun `should identify stoppable execution states`() {
        assertEquals(true, ExecutionState.Running.isStoppable())
        assertEquals(true, ExecutionState.WaitingForUserInput.isStoppable())
        assertEquals(true, ExecutionState.WaitingForApproval.isStoppable())
        assertEquals(false, ExecutionState.Idle.isStoppable())
        assertEquals(false, ExecutionState.Failed(AppError("err", "msg")).isStoppable())
    }

    /**
     * composer 应支持 Enter 发送，同时保留 Shift+Enter 换行。
     */
    @Test
    fun `should submit composer on enter without shift only`() {
        assertEquals(true, shouldSubmitComposerKey(Key.Enter, KeyEventType.KeyUp, isShiftPressed = false))
        assertEquals(false, shouldSubmitComposerKey(Key.Enter, KeyEventType.KeyUp, isShiftPressed = true))
        assertEquals(false, shouldSubmitComposerKey(Key.Enter, KeyEventType.KeyDown, isShiftPressed = false))
    }

    /**
     * 只有真实 update_plan 工具事件携带 plan 列表时，才显示 Plan 卡片。
     */
    @Test
    fun `should show plan card only when update plan tool payload exists`() {
        val visible = extractPlanCard(
            listOf(
                ToolEventItem(
                    toolName = "update_plan",
                    status = ToolEventStatus.Started,
                    preview = """{"explanation":"sync","plan":[{"step":"Inspect files","status":"completed"},{"step":"Refactor UI","status":"in_progress"}]}""",
                ),
            ),
        )
        val hidden = extractPlanCard(
            listOf(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Started,
                    preview = """{"path":"README.md"}""",
                ),
            ),
        )

        assertEquals(listOf("Inspect files", "Refactor UI"), visible?.entries?.map { it.text })
        assertEquals(2, visible?.entries?.size)
        assertEquals(TaskPlanStatus.COMPLETED, visible?.entries?.first()?.status)
        assertEquals(TaskPlanStatus.IN_PROGRESS, visible?.entries?.last()?.status)
        assertEquals(null, hidden)
    }

    /**
     * update_plan 的 JSON 字段顺序不固定，步骤文案里也可能包含转义引号。
     */
    @Test
    fun `should parse update plan payload regardless of field order and escaped quotes`() {
        val planCard = extractPlanCard(
            listOf(
                ToolEventItem(
                    toolName = "update_plan",
                    status = ToolEventStatus.Started,
                    preview = """{"plan":[{"status":"completed","step":"Inspect \"Ring\" tokens"},{"status":"in_progress","step":"Port sidebar"}]}""",
                ),
            ),
        )

        assertEquals(listOf("Inspect \"Ring\" tokens", "Port sidebar"), planCard?.entries?.map { it.text })
        assertEquals(
            listOf(TaskPlanStatus.COMPLETED, TaskPlanStatus.IN_PROGRESS),
            planCard?.entries?.map { it.status },
        )
    }

    /**
     * 最近一条 update_plan 事件如果只是结果文本，也应继续回退到更早的参数预览。
     */
    @Test
    fun `should fall back to earlier update plan preview when latest event is not parseable`() {
        val planCard = extractPlanCard(
            listOf(
                ToolEventItem(
                    toolName = "update_plan",
                    status = ToolEventStatus.Started,
                    preview = """{"plan":[{"step":"Inspect files","status":"completed"},{"step":"Port Ring UI","status":"in_progress"}]}""",
                ),
                ToolEventItem(
                    toolName = "update_plan",
                    status = ToolEventStatus.Finished,
                    preview = "plan updated",
                ),
            ),
        )

        assertEquals(listOf("Inspect files", "Port Ring UI"), planCard?.entries?.map { it.text })
        assertEquals(
            listOf(TaskPlanStatus.COMPLETED, TaskPlanStatus.IN_PROGRESS),
            planCard?.entries?.map { it.status },
        )
    }

    /**
     * 右侧 rail 只保留终端入口，关闭终端时不默认高亮。
     */
    @Test
    fun `should expose terminal as the only right rail button`() {
        val groups = buildRightRailGroups()

        assertEquals(listOf(1), groups.map { it.size })
        assertEquals(RightRailGlyph.TERMINAL, groups.single().single().glyph)
        assertEquals(false, groups.single().single().active)
    }

    /**
     * 底部终端打开时，rail 应优先高亮终端按钮。
     */
    @Test
    fun `should select terminal rail button while terminal panel is open`() {
        assertEquals(
            RightRailGlyph.TERMINAL,
            resolveActiveRailGlyph(
                activeRailView = RightRailGlyph.CODE,
                filterToolActivityOnly = false,
                terminalVisible = true,
            ),
        )
        assertEquals(
            RightRailGlyph.CODE,
            resolveActiveRailGlyph(
                activeRailView = RightRailGlyph.CODE,
                filterToolActivityOnly = false,
                terminalVisible = false,
            ),
        )
    }

    /**
     * 终端应使用指定的 Maple Mono 半粗 Nerd Font 字体。
     */
    @Test
    fun `should use maple mono semibold terminal font`() {
        val font = terminalFont()

        assertEquals("Maple Mono NF CN SemiBold", font.name)
        assertEquals(14, font.size)
    }

    /**
     * 只有缓冲内容超过可见范围时才显示终端滚动条。
     */
    @Test
    fun `should show terminal scrollbar only for overflowing content`() {
        assertEquals(false, shouldShowTerminalScrollbar(minimum = 0, maximum = 24, extent = 24))
        assertEquals(true, shouldShowTerminalScrollbar(minimum = 0, maximum = 40, extent = 24))
    }

    /**
     * JediTerm 根组件与内部画布都不能保留 Look & Feel 注入的亮色边框。
     */
    @Test
    fun `should keep the terminal tree free from late swing borders`() {
        val root = JPanel()
        val existingChild = JPanel()
        root.add(existingChild)

        installSwingBorderCleanup(root)

        existingChild.border = BorderFactory.createLineBorder(java.awt.Color.WHITE)
        val lateChild = JPanel().apply {
            border = BorderFactory.createLineBorder(java.awt.Color.WHITE)
        }
        root.add(lateChild)

        assertNull(root.border)
        assertNull(existingChild.border)
        assertNull(lateChild.border)
    }

    /**
     * 终端到窗口根面板的祖先链必须使用终端背景色，避免异步扩张帧露出默认亮色。
     */
    @Test
    fun `should synchronize terminal background through swing interop ancestors`() {
        val windowRoot = JPanel()
        val composeHost = JPanel()
        val interopHost = JPanel()
        val terminal = JPanel()
        windowRoot.add(composeHost)
        composeHost.add(interopHost)
        interopHost.add(terminal)

        synchronizeTerminalInteropBackground(terminal)

        val terminalBackground = java.awt.Color(23, 24, 26)
        assertEquals(
            listOf(terminalBackground, terminalBackground, terminalBackground, terminalBackground),
            listOf(terminal.background, interopHost.background, composeHost.background, windowRoot.background),
        )
    }

    /**
     * 分隔线拖动后的终端高度必须同时保护主区域和终端的可用空间。
     */
    @Test
    fun `should clamp terminal height to split pane bounds`() {
        assertEquals(180f, clampTerminalHeight(-20f, 800f, 180f, 280f))
        assertEquals(520f, clampTerminalHeight(700f, 800f, 180f, 280f))
        assertEquals(310f, clampTerminalHeight(310f, 800f, 180f, 280f))
        assertEquals(120f, clampTerminalHeight(200f, 320f, 180f, 200f))
    }

    /**
     * 浮动侧栏宽度随紧凑布局收敛，但不参与主工作区宽度计算。
     */
    @Test
    fun `should resolve air sidebar width independently from workspace`() {
        assertEquals(224, airSidebarWidthDp(compact = true))
        assertEquals(292, airSidebarWidthDp(compact = false))
    }

    /**
     * Air 侧栏应使用真实磨砂所需的模糊、染色和低对比边界参数。
     */
    @Test
    fun `should use air sidebar glass material tokens`() {
        assertEquals(12, AirSidebarStyle.cornerRadiusDp)
        assertEquals(16, AirSidebarStyle.shadowElevationDp)
        assertEquals(22f, AirSidebarStyle.blurRadiusPx)
        assertEquals(0.78f, AirSidebarStyle.tintAlpha)
        assertEquals(0.075f, AirSidebarStyle.borderAlpha)
        assertEquals(Color(0xFF1D1F21), AirSidebarStyle.fallbackColor)
        assertEquals(Color(0xFF151719), AppWorkspaceBackground)
    }

    /**
     * 玻璃副本必须按工作区与侧栏的根坐标差对齐到原始屏幕位置。
     */
    @Test
    fun `should align workspace backdrop inside sidebar coordinates`() {
        assertEquals(
            Offset(-12f, -8f),
            workspaceBackdropOffset(
                workspaceOrigin = Offset(0f, 48f),
                sidebarOrigin = Offset(12f, 56f),
            ),
        )
        assertEquals(
            Offset(-8f, -56f),
            workspaceBackdropOffset(
                workspaceOrigin = Offset.Zero,
                sidebarOrigin = Offset(8f, 56f),
            ),
        )
    }

    /**
     * 侧栏应从左侧完整移出窗口，而不是在原位置渐显。
     */
    @Test
    fun `should place hidden sidebar beyond left edge`() {
        assertEquals(-304, sidebarHiddenOffsetPx(sidebarWidthPx = 292, edgeGapPx = 12))
    }

    /**
     * 侧栏默认关闭；打开后只响应面板外部的点击。
     */
    @Test
    fun `should dismiss visible sidebar only for outside pointer`() {
        val bounds = Rect(left = 12f, top = 56f, right = 304f, bottom = 800f)

        assertEquals(false, SIDEBAR_VISIBLE_BY_DEFAULT)
        assertEquals(
            false,
            shouldDismissSidebar(
                sidebarVisibleAtPointerPress = true,
                sidebarVisibleOnRelease = true,
                sidebarBounds = bounds,
                pointerPosition = Offset(120f, 120f),
            ),
        )
        assertEquals(
            true,
            shouldDismissSidebar(
                sidebarVisibleAtPointerPress = true,
                sidebarVisibleOnRelease = true,
                sidebarBounds = bounds,
                pointerPosition = Offset(600f, 120f),
            ),
        )
        assertEquals(
            false,
            shouldDismissSidebar(
                sidebarVisibleAtPointerPress = false,
                sidebarVisibleOnRelease = true,
                sidebarBounds = bounds,
                pointerPosition = Offset(600f, 120f),
            ),
        )
    }

    /**
     * 已打开菜单时点击其他触发器应直接切换，再次点击同一触发器才关闭。
     */
    @Test
    fun `should switch composer menus in one click`() {
        assertEquals(false, SELECT_POPUP_FOCUSABLE)
        assertEquals(
            ComposerMenu.MODEL,
            nextComposerMenu(ComposerMenu.PROVIDER, ComposerMenu.MODEL),
        )
        assertEquals(null, nextComposerMenu(ComposerMenu.MODEL, ComposerMenu.MODEL))
        assertEquals(ComposerMenu.PERMISSION, nextComposerMenu(null, ComposerMenu.PERMISSION))
        assertEquals(null, dismissComposerMenu(ComposerMenu.PROVIDER, ComposerMenu.PROVIDER))
        assertEquals(
            ComposerMenu.MODEL,
            dismissComposerMenu(ComposerMenu.MODEL, ComposerMenu.PROVIDER),
        )
        assertEquals(
            false,
            desiredSelectExpandedState(
                expandedAtPointerPress = true,
                expandedAtClick = false,
            ),
        )
        assertEquals(
            true,
            desiredSelectExpandedState(
                expandedAtPointerPress = false,
                expandedAtClick = false,
            ),
        )
        assertEquals(
            false,
            desiredSelectExpandedState(
                expandedAtPointerPress = null,
                expandedAtClick = true,
            ),
        )
    }

    /**
     * Composer 主动作使用矢量发送/停止图标，不再直接渲染文字箭头。
     */
    @Test
    fun `should resolve composer primary action glyph`() {
        assertEquals(HeaderGlyph.SEND, composerPrimaryActionGlyph(danger = false))
        assertEquals(HeaderGlyph.STOP, composerPrimaryActionGlyph(danger = true))
    }

    /**
     * Composer 下拉框说明需要延迟出现，避免快速经过控件时产生视觉噪声。
     */
    @Test
    fun `should delay composer select tooltips`() {
        assertEquals(1500L, SELECT_TOOLTIP_DELAY_MILLIS)
    }

    /**
     * 顶部 header 操作区应保持 menu / share / settings / help 的固定顺序。
     */
    @Test
    fun `should expose header glyph actions in prototype order`() {
        val actions = buildHeaderActions()

        assertEquals(HeaderGlyph.MENU, actions.left.glyph)
        assertEquals(
            listOf(HeaderGlyph.SHARE, HeaderGlyph.SETTINGS, HeaderGlyph.HELP),
            actions.right.map { it.glyph },
        )
    }

    /**
     * 窄窗口应切换为紧凑布局，避免固定侧栏和工具 rail 挤压聊天区。
     */
    @Test
    fun `should use compact layout below desktop width threshold`() {
        assertEquals(true, isCompactDesktopLayout(900))
        assertEquals(false, isCompactDesktopLayout(1200))
    }
}
