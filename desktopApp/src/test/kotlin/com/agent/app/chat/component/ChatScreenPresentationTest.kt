package com.agent.app.chat.component

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.buildWorkspaceLabel
import com.agent.app.chat.state.isStoppable
import com.agent.app.chat.presentation.shouldExpandToolEventByDefault
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.AirSidebarStyle
import com.agent.app.design.AppHeaderBackground
import com.agent.app.design.AppRailBackground
import com.agent.app.design.AppAccent
import com.agent.app.design.AppDanger
import com.agent.app.design.AppReasoning
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.AppTypography
import com.agent.app.design.AppUiFontFamily
import com.agent.app.design.AppWorkspaceBackground
import com.agent.app.design.COMPOSER_PRIMARY_GLYPH_SIZE_DP
import com.agent.app.design.RAIL_ACTION_SIZE_DP
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.PopupMenuBackground
import com.agent.app.design.PopupMenuHoverBackground
import com.agent.app.design.PopupMenuItemShape
import com.agent.app.design.PopupMenuSelectedBackground
import com.agent.app.design.PopupMenuContactShadow
import com.agent.app.design.PopupMenuDiffuseShadow
import com.agent.app.design.PopupMenuShadowInset
import com.agent.app.design.SELECT_POPUP_FOCUSABLE
import com.agent.app.design.SELECT_MENU_HOVER_TRANSITION_DURATION_MILLIS
import com.agent.app.design.buildHeaderActions
import com.agent.app.design.buildRightRailGroups
import com.agent.app.design.desiredSelectExpandedState
import com.agent.app.design.menuGrowthTargets
import com.agent.app.design.ringPrimaryButtonContentPadding
import com.agent.app.design.selectMenuItemBackground
import com.agent.app.design.selectChipChevronRotation
import com.agent.app.design.selectChipTriggerBackground
import com.agent.app.design.shouldShowSelectChipArrow
import com.agent.app.design.workspaceBackdropOffset
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import com.agent.shared.tool.model.PermissionPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * 验证仍与 ChatScreen Compose 组件同包的展示规则。
 */
class ChatScreenPresentationTest {

    /**
     * JBR 原生标题栏中的菜单必须先由 AWT 客户区命中组件接收事件，再触发 Compose 侧栏动作。
     */
    @Test
    fun `should route native title bar menu click through awt hit target`() {
        var clientAreaRequests = 0
        var clicks = 0
        val hitTarget = createNativeTitleBarMenuHitTarget(
            onClientMouseEvent = { clientAreaRequests += 1 },
            onClick = { clicks += 1 },
        ).apply {
            setSize(36, 36)
        }

        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_ENTERED))
        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_MOVED))
        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1))
        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1))

        assertEquals(4, clientAreaRequests)
        assertEquals(1, clicks)
    }

    /**
     * 原生标题栏内的任务上下文也必须通过 AWT 客户区命中层接收点击，不能依赖 Compose clickable。
     */
    @Test
    fun `should route native title bar task context click through awt hit target`() {
        var clientAreaRequests = 0
        var clicks = 0
        var pointerPosition: Offset? = null
        val hitTarget = createNativeTitleBarTaskHitTarget(
            onClientMouseEvent = { clientAreaRequests += 1 },
            onClick = { clicks += 1 },
            onPointerMoved = { pointerPosition = it },
        ).apply {
            setSize(360, 36)
        }

        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_ENTERED))
        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_MOVED))
        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1))
        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1))
        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3))
        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON3))

        assertEquals(6, clientAreaRequests)
        assertEquals(2, clicks)
        assertEquals(Offset(18f, 18f), pointerPosition)
    }

    /**
     * 原生标题栏的任务命中层覆盖在 Compose 之上时，悬浮反馈必须由自身绘制而非依赖被遮挡的 Compose 背景。
     */
    @Test
    fun `should paint hover feedback for native title bar task hit target`() {
        val hitTarget = createNativeTitleBarTaskHitTarget(
            onClientMouseEvent = {},
            onClick = {},
        ).apply {
            setSize(120, 36)
        }
        hitTarget.dispatchEvent(mouseEvent(hitTarget, MouseEvent.MOUSE_ENTERED))
        val image = BufferedImage(120, 36, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            hitTarget.paint(graphics)
        } finally {
            graphics.dispose()
        }

        assertEquals(java.awt.Color(0x35, 0x38, 0x3E).rgb, image.getRGB(8, 18))
    }

    /** 标题栏任务胶囊应保持紧凑，不能跟随整条标题栏的高度膨胀。 */
    @Test
    fun `should keep title bar task capsule compact`() {
        assertEquals(36, HEADER_TASK_CHIP_HEIGHT_DP)
        assertEquals(8, HEADER_TASK_CHIP_HORIZONTAL_PADDING_DP)
    }

    /** 分支名的原生标题栏命中区应与当前任务胶囊共享紧凑高度。 */
    @Test
    fun `should keep header branch hover aligned with the task chip`() {
        assertEquals(HEADER_TASK_CHIP_HEIGHT_DP, HEADER_BRANCH_CHIP_HEIGHT_DP)
        assertEquals(4, HEADER_BRANCH_CHIP_HORIZONTAL_PADDING_DP)
    }

    /** 复制分支应发出全局 toast 文案，不得替换标题栏中的分支文本。 */
    @Test
    fun `should use a global feedback message after copying a branch`() {
        assertEquals("已复制", headerBranchCopiedFeedbackMessage())
        assertEquals(24, APP_FEEDBACK_BOTTOM_PADDING_DP)
    }

    /** 原生标题栏任务入口不应显示平台默认风格的系统提示气泡。 */
    @Test
    fun `should not show system tooltip for native title bar task target`() {
        val hitTarget = createNativeTitleBarTaskHitTarget(
            onClientMouseEvent = {},
            onClick = {},
        )

        assertNull(hitTarget.toolTipText)
    }

    /**
     * Swing 互操作命中组件必须使用标题栏底色，不能在菜单四周露出默认白色画布。
     */
    @Test
    fun `should paint native title bar menu host with header background`() {
        val hitTarget = createNativeTitleBarMenuHitTarget(
            onClientMouseEvent = {},
            onClick = {},
        )

        assertEquals(java.awt.Color(0x1E, 0x1F, 0x22), hitTarget.background)
    }

    /**
     * SwingPanel 外层宿主也必须同步标题栏底色，避免组件边缘露出 Look & Feel 的浅色背景。
     */
    @Test
    fun `should synchronize native title bar menu interop host background`() {
        val interopHost = JPanel().apply {
            background = java.awt.Color(0xEE, 0xEE, 0xEE)
        }
        val hitTarget = createNativeTitleBarMenuHitTarget(
            onClientMouseEvent = {},
            onClick = {},
        )
        interopHost.add(hitTarget)

        hitTarget.updateActions(
            onClientMouseEvent = {},
            onClick = {},
            onHoverChanged = {},
        )

        assertEquals(java.awt.Color(0x1E, 0x1F, 0x22), interopHost.background)
    }

    /**
     * 标题任务命中层的 Swing 宿主必须使用深色标题栏底色，不能让透明面板回退为白色画布。
     */
    @Test
    fun `should synchronize native title bar task hit overlay background`() {
        val interopHost = JPanel().apply {
            background = java.awt.Color(0xEE, 0xEE, 0xEE)
        }
        val hitTarget = createNativeTitleBarTaskHitTarget(
            onClientMouseEvent = {},
            onClick = {},
        )
        interopHost.add(hitTarget)

        synchronizeNativeTitleBarTaskInteropBackground(hitTarget)

        assertEquals(java.awt.Color(0x1E, 0x1F, 0x22), interopHost.background)
        assertEquals(true, interopHost.isOpaque)
    }

    /** 标题栏和侧栏任务入口必须展示完全相同的操作菜单。 */
    @Test
    fun `should expose the same task context menu actions everywhere`() {
        assertEquals(listOf("Fork", "删除", "Archive", "重命名"), taskContextMenuLabels())
        assertEquals(listOf("编辑", "删除"), workspaceContextMenuLabels())
    }

    /** 右键菜单只在悬浮可用操作时显示明确的选中蓝色。 */
    @Test
    fun `should use selected blue only while task menu item hovers`() {
        assertEquals(Color.Transparent, taskContextMenuItemBackground(hovered = false, enabled = true))
        assertEquals(Color(0xFF194474), TaskContextMenuHoverBackground)
        assertEquals(PopupMenuSelectedBackground, taskContextMenuItemBackground(hovered = true, enabled = true))
        assertEquals(Color.Transparent, taskContextMenuItemBackground(hovered = true, enabled = false))
        assertEquals(PopupMenuItemShape, TaskContextMenuItemShape)
    }

    /** 下拉菜单与右键菜单使用共享的向下双层阴影。 */
    @Test
    fun `should use bottom focused shared popup menu shadows`() {
        assertEquals(6.dp, PopupMenuDiffuseShadow.radius)
        assertEquals(0.dp, PopupMenuDiffuseShadow.spread)
        assertEquals(Color.Black, PopupMenuDiffuseShadow.color)
        assertEquals(0.dp, PopupMenuDiffuseShadow.offset.x)
        assertEquals(4.dp, PopupMenuDiffuseShadow.offset.y)
        assertEquals(0.36f, PopupMenuDiffuseShadow.alpha)
        assertEquals(1.dp, PopupMenuContactShadow.radius)
        assertEquals(0.dp, PopupMenuContactShadow.spread)
        assertEquals(Color.Black, PopupMenuContactShadow.color)
        assertEquals(0.dp, PopupMenuContactShadow.offset.x)
        assertEquals(2.dp, PopupMenuContactShadow.offset.y)
        assertEquals(0.55f, PopupMenuContactShadow.alpha)
        assertEquals(10.dp, PopupMenuShadowInset)
    }

    /** Air 风格菜单应保持紧凑，不能按参考截图的物理像素尺寸直接放大。 */
    @Test
    fun `should keep air task context menu compact`() {
        assertEquals(180.dp, TaskContextMenuWidth)
        assertEquals(36.dp, TaskContextMenuItemHeight)
    }

    /** Air 菜单边框按物理像素计算，高 DPI 下不能加粗成多个屏幕像素。 */
    @Test
    fun `should keep task context menu border to one physical pixel`() {
        assertEquals(1.dp, onePhysicalPixel(density = 1f))
        assertEquals(0.5.dp, onePhysicalPixel(density = 2f))
    }

    /** 右键菜单项悬浮反馈必须紧跟指针，不能保留可感知的颜色延迟。 */
    @Test
    fun `should react to task context menu hover without delay`() {
        assertEquals(80, TASK_CONTEXT_MENU_HOVER_TRANSITION_DURATION_MILLIS)
    }

    /** 右键菜单以鼠标位置为锚点，并从条目底部坐标修正为光标附近。 */
    @Test
    fun `should place task context menu beside pointer`() {
        assertEquals(
            androidx.compose.ui.unit.DpOffset(48.dp, (-30).dp),
            contextMenuOffsetForPointer(
                pointerPosition = Offset(100f, 40f),
                anchorHeightPixels = 80,
                density = 2f,
                shadowInset = PopupMenuShadowInset,
            ),
        )
    }

    /**
     * 高 DPI 混合模式下宿主需向左覆盖一个逻辑像素，但菜单内容应保持原位。
     */
    @Test
    fun `should cover native title bar menu interop seam without moving content`() {
        val interopHost = JPanel().apply {
            layout = null
            setBounds(12, 6, 36, 36)
        }
        val hitTarget = createNativeTitleBarMenuHitTarget(
            onClientMouseEvent = {},
            onClick = {},
        ).apply {
            setBounds(0, 0, 36, 36)
        }
        interopHost.add(hitTarget)

        coverNativeTitleBarMenuInteropSeam(hitTarget)

        assertEquals(java.awt.Rectangle(11, 6, 37, 36), interopHost.bounds)
        assertEquals(java.awt.Rectangle(1, 0, 36, 36), hitTarget.bounds)
    }

    /**
     * 菜单默认态应融入标题栏，既没有圆角填充，也没有按钮描边。
     */
    @Test
    fun `should keep native title bar menu idle surface unselected`() {
        val hitTarget = createNativeTitleBarMenuHitTarget(
            onClientMouseEvent = {},
            onClick = {},
        ).apply {
            setSize(36, 36)
        }
        val image = BufferedImage(36, 36, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            hitTarget.paint(graphics)
        } finally {
            graphics.dispose()
        }
        val headerRgb = java.awt.Color(0x1E, 0x1F, 0x22).rgb

        assertEquals(headerRgb, image.getRGB(4, 18))
        assertEquals(headerRgb, image.getRGB(18, 4))
    }

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
     * 终端标签右键菜单应提供创建和关闭会话所需的最小操作。
     */
    @Test
    fun `should expose terminal tab context menu actions`() {
        assertEquals(
            listOf("新建终端", "关闭当前终端", "关闭其他终端"),
            terminalTabContextMenuLabels(),
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
     * 分隔线拖动后的终端宽度必须同时保护主区域和终端的可用空间。
     */
    @Test
    fun `should clamp terminal width to split pane bounds`() {
        assertEquals(180f, clampTerminalWidth(-20f, 800f, 180f, 280f))
        assertEquals(520f, clampTerminalWidth(700f, 800f, 180f, 280f))
        assertEquals(310f, clampTerminalWidth(310f, 800f, 180f, 280f))
        assertEquals(120f, clampTerminalWidth(200f, 320f, 180f, 200f))
    }

    /** 终端打开、收起和关闭均使用可感知但不拖沓的面板过渡。 */
    @Test
    fun `should animate terminal panel visibility changes`() {
        assertEquals(420, TERMINAL_PANEL_ENTER_DURATION_MILLIS)
        assertEquals(360, TERMINAL_PANEL_EXIT_DURATION_MILLIS)
        assertEquals(32L, TERMINAL_PANEL_CLOSE_DELAY_MILLIS)
        assertEquals(800f, workspaceWidthDuringTerminalMotion(800f, 280f, 0f))
        assertEquals(520f, workspaceWidthDuringTerminalMotion(800f, 280f, 1f))
    }

    /** 分隔高亮必须围绕指针定位，并在轨道两端裁剪。 */
    @Test
    fun `should clip pointer following divider highlight to its track`() {
        assertEquals(64f, com.agent.app.design.dividerHighlightStartPx(200f, 100f, 72f))
        assertEquals(0f, com.agent.app.design.dividerHighlightStartPx(200f, 0f, 72f))
        assertEquals(128f, com.agent.app.design.dividerHighlightStartPx(200f, 200f, 72f))
        assertEquals(0f, com.agent.app.design.dividerHighlightStartPx(40f, 20f, 72f))
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
        assertEquals(Color(0xFF18191B), AppWorkspaceBackground)
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

    /** 下拉菜单在 Islands 外层岛内区分静止、悬浮和选中条目。 */
    @Test
    fun `should use shared popup menu item colors`() {
        assertEquals(Color.Transparent, selectMenuItemBackground(selected = false, hovered = false, enabled = true))
        assertEquals(PopupMenuHoverBackground, selectMenuItemBackground(selected = false, hovered = true, enabled = true))
        assertEquals(PopupMenuSelectedBackground, selectMenuItemBackground(selected = true, hovered = false, enabled = true))
        assertEquals(PopupMenuSelectedBackground, selectMenuItemBackground(selected = true, hovered = true, enabled = true))
        assertEquals(Color(0xFF252629), PopupMenuBackground)
        assertEquals(0, SELECT_MENU_HOVER_TRANSITION_DURATION_MILLIS)
    }

    /** 下拉弹出层从触发器一侧轻微放大进入，以保持展开过程有空间感且不拖沓。 */
    @Test
    fun `should give select popup a subtle growth entry scale`() {
        assertEquals(0.96f, menuGrowthTargets(expanded = false).scale)
        assertEquals(1f, menuGrowthTargets(expanded = true).scale)
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

    /**
     * 终端入口应使用 IDEA 风格右侧栏的宽度和点击区域。
     */
    @Test
    fun `should expose idea style right rail metrics`() {
        assertEquals(48, TOOL_RAIL_WIDTH_DP)
        assertEquals(40, RAIL_ACTION_SIZE_DP)
    }

    /**
     * 右侧栏应从标题栏下方开始，并与标题栏使用同一底色、与主工作区保持顶部节奏。
     */
    @Test
    fun `should align the right rail with the workspace below the title bar`() {
        assertEquals(16, TOOL_RAIL_TOP_PADDING_DP)
        assertEquals(AppHeaderBackground, AppRailBackground)
    }

    /**
     * Composer 主动作、终端标签和关闭按钮应保持可辨识的点击尺寸。
     */
    @Test
    fun `should expose readable composer and terminal action metrics`() {
        assertEquals(24, COMPOSER_PRIMARY_GLYPH_SIZE_DP)
        assertEquals(30, TERMINAL_TAB_HEIGHT_DP)
        assertEquals(24, TERMINAL_CLOSE_BUTTON_SIZE_DP)
    }

    /** 当前终端标签使用常态蓝色描边标识选中状态，不依赖悬浮。 */
    @Test
    fun `should keep selected terminal tab border blue at rest`() {
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFF2F81D6),
            terminalTabBorderColor(selected = true),
        )
        assertEquals(
            androidx.compose.ui.graphics.Color.Transparent,
            terminalTabBorderColor(selected = false),
        )
    }

    /** 终端操作图标没有外溢高光，新增操作的悬浮底色由按钮自身负责。 */
    @Test
    fun `should keep terminal action icons free from glow`() {
        assertEquals(0f, terminalActionGlowAlpha(hovered = false))
        assertEquals(0f, terminalActionGlowAlpha(hovered = true))
    }

    /** 终端的新建与关闭操作保持静态，不在悬浮时出现额外高光。 */
    @Test
    fun `should keep terminal actions free from hover glow`() {
        assertEquals(0f, terminalActionGlowAlpha(hovered = false))
        assertEquals(0f, terminalActionGlowAlpha(hovered = true))
    }

    /** 新建终端仅在悬浮时显示参考图中的浅灰底，不产生图标光晕。 */
    @Test
    fun `should show a hover surface for the terminal add action`() {
        assertEquals(androidx.compose.ui.graphics.Color.Transparent, terminalAddButtonBackground(hovered = false))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF24272D), terminalAddButtonBackground(hovered = true))
    }

    /**
     * 问题和审批卡片必须共享短促且不突兀的进出场时长。
     */
    @Test
    fun `should use calm pending interaction card motion timings`() {
        assertEquals(180, PENDING_CARD_ENTER_DURATION_MILLIS)
        assertEquals(120, PENDING_CARD_EXIT_DURATION_MILLIS)
    }

    /**
     * 初次组合时即使已有挂起交互，也必须先隐藏一帧，才能触发卡片入场动画。
     */
    @Test
    fun `should defer an already pending interaction card until entry animation is ready`() {
        assertEquals(
            false,
            pendingInteractionCardVisibility(
                isReadyForEntryAnimation = false,
                hasPendingQuestion = true,
                hasPendingApproval = false,
            ),
        )
        assertEquals(
            true,
            pendingInteractionCardVisibility(
                isReadyForEntryAnimation = true,
                hasPendingQuestion = false,
                hasPendingApproval = true,
            ),
        )
    }

    /** 交互卡进入时应从可感知但不突兀的轻微缩放开始。 */
    @Test
    fun `should scale pending interaction card in on entry`() {
        assertEquals(0.96f, PENDING_CARD_ENTER_INITIAL_SCALE)
    }

    /**
     * 工具详情箭头在收起与展开状态之间必须提供明确的方向提示。
     */
    @Test
    fun `should rotate tool event chevron when details expand`() {
        assertEquals(0f, toolEventChevronRotation(expanded = false))
        assertEquals(90f, toolEventChevronRotation(expanded = true))
    }

    /** 工具组与工具行的箭头仅在鼠标悬浮时显示，减少静态视觉噪声。 */
    @Test
    fun `should show tool chevron only while hovered`() {
        assertEquals(false, shouldShowTimelineToolChevron(hovered = false))
        assertEquals(true, shouldShowTimelineToolChevron(hovered = true))
    }

    /** 已提交问答行以文字悬浮代替默认 Material 点击底色。 */
    @Test
    fun `should highlight answers title only while hovered`() {
        assertEquals(AppText, timelineAnswersTitleTint(hovered = false))
        assertEquals(AppAccent, timelineAnswersTitleTint(hovered = true))
    }

    /** 悬浮只提高工具标题的对比度，不引入背景或图标状态变化。 */
    @Test
    fun `should highlight tool titles only while hovered`() {
        assertEquals(AppMuted, timelineToolTitleTint(hovered = false, restingTint = AppMuted))
        assertEquals(AppAccent, timelineToolTitleTint(hovered = true, restingTint = AppMuted))
        assertEquals(AppAccent, timelineToolTitleTint(hovered = true, restingTint = AppDanger))
    }

    /** 两层工具行的箭头间距应保持稳定，避免展开控件远离所属文字。 */
    @Test
    fun `should keep compact tool chevron gaps`() {
        assertEquals(16, TOOL_GROUP_CHEVRON_GAP_DP)
        assertEquals(8, TOOL_ROW_CHEVRON_GAP_DP)
    }

    /**
     * 工具结果抵达后，既有卡片的展开状态标识不得随结果文本改变而改变。
     */
    @Test
    fun `should keep tool expansion identity when result display arrives`() {
        val pending = ToolEventItem(
            toolName = "run_powershell",
            status = ToolEventStatus.Started,
            toolCallId = "call-1",
        )
        val completed = pending.copy(
            status = ToolEventStatus.Finished,
            resultDisplay = "stdout: completed",
        )

        assertEquals(toolEventExpansionIdentity(pending), toolEventExpansionIdentity(completed))
    }

    /**
     * 用户离开底部时应显示一键回到最新输出的按钮。
     */
    @Test
    fun `should show scroll to bottom button only away from latest output`() {
        assertEquals(true, shouldShowScrollToBottomButton(isFollowingLatest = false))
        assertEquals(false, shouldShowScrollToBottomButton(isFollowingLatest = true))
        assertEquals(
            false,
            shouldShowScrollToBottomButton(
                isFollowingLatest = false,
                hasTimelineContent = false,
            ),
        )
    }

    /**
     * 标题栏应直接展示工作区、分支和当前任务名；新建会话保留默认任务名。
     */
    @Test
    fun `should build compact header label for new conversation`() {
        assertEquals(
            "mulehang-agent : main / 新建对话",
            buildHeaderConversationLabel("mulehang-agent", "main", "新建对话"),
        )
    }

    /** 标题生成中仍须显示项目和分支，只将任务标题槽替换为加载指示器。 */
    @Test
    fun `should keep workspace context while header title generates`() {
        assertEquals(
            "mulehang-agent : main /",
            buildHeaderConversationPrefix("mulehang-agent", "main"),
        )
    }

    /** 原生标题栏由 Swing 命中层绘制悬浮态，Compose 外层不能叠加第二个背景框。 */
    @Test
    fun `should leave compose header hover background transparent with native hit overlay`() {
        assertEquals(
            androidx.compose.ui.graphics.Color.Transparent,
            titleBarComposeHoverBackground(
                nativeHitOverlayEnabled = true,
                hovered = true,
                pressed = false,
            ),
        )
        assertEquals(
            com.agent.app.design.AppHoverBackground.copy(alpha = 0.72f),
            titleBarComposeHoverBackground(
                nativeHitOverlayEnabled = false,
                hovered = true,
                pressed = false,
            ),
        )
    }

    /** 标题栏当前任务名必须比辅助文本更醒目。 */
    @Test
    fun `should use a larger title bar task font`() {
        assertEquals(16, HEADER_TASK_TITLE_FONT_SIZE_SP)
    }

    /** 分支名的 hover Chip 需与当前任务 Chip 共享紧凑高度。 */
    @Test
    fun `should align branch chip height with the task chip`() {
        assertEquals(HEADER_TASK_CHIP_HEIGHT_DP, HEADER_BRANCH_CHIP_HEIGHT_DP)
    }

    /** 复制反馈只有在提供指针坐标时才切换为鼠标锚定模式。 */
    @Test
    fun `should retain the optional pointer anchor for branch copy feedback`() {
        assertEquals(Offset(48f, 24f), feedbackToastAnchor(Offset(48f, 24f)))
        assertNull(feedbackToastAnchor(null))
        assertEquals(1L, nextAppFeedbackToken(0L))
        assertEquals(42L, nextAppFeedbackToken(41L))
    }

    /** 拖选到输入框上下边缘时，滚动方向必须跟随指针方向。 */
    @Test
    fun `should scroll composer while dragging selection near its edges`() {
        assertEquals(-24f, composerSelectionScrollDelta(pointerY = 4f, viewportHeight = 100))
        assertEquals(24f, composerSelectionScrollDelta(pointerY = 96f, viewportHeight = 100))
        assertEquals(0f, composerSelectionScrollDelta(pointerY = 50f, viewportHeight = 100))
    }

    /** Shift 加方向键扩展选择范围时，输入框需要同步滚动。 */
    @Test
    fun `should scroll composer for keyboard text selection`() {
        assertEquals(-28f, composerKeyboardSelectionScrollDelta(Key.DirectionUp, isShiftPressed = true))
        assertEquals(28f, composerKeyboardSelectionScrollDelta(Key.DirectionDown, isShiftPressed = true))
        assertEquals(0f, composerKeyboardSelectionScrollDelta(Key.DirectionUp, isShiftPressed = false))
    }

    /**
     * 底部操作卡片撑高输入区时，原本跟随最新输出的时间线仍应保持在底部。
     */
    @Test
    fun `should keep timeline at bottom when viewport height changes`() {
        assertEquals(true, shouldKeepTimelineAtBottomAfterViewportChange(isFollowingLatest = true))
        assertEquals(false, shouldKeepTimelineAtBottomAfterViewportChange(isFollowingLatest = false))
    }

    /**
     * 提问和审批都会在 composer 上方展示独立交互卡片。
     */
    @Test
    fun `should show pending interaction card for questions and approvals`() {
        assertEquals(
            true,
            shouldShowPendingInteractionCard(hasPendingQuestion = false, hasPendingApproval = true),
        )
        assertEquals(
            true,
            shouldShowPendingInteractionCard(hasPendingQuestion = true, hasPendingApproval = false),
        )
        assertEquals(
            false,
            shouldShowPendingInteractionCard(hasPendingQuestion = false, hasPendingApproval = false),
        )
    }

    /**
     * 主内容超过可视区域时才显示滚动条。
     */
    @Test
    fun `should show timeline scrollbar only when content overflows`() {
        assertEquals(false, shouldShowTimelineScrollbar(maxScrollValue = 0))
        assertEquals(true, shouldShowTimelineScrollbar(maxScrollValue = 1))
    }

    /**
     * 长消息输入不能挤占整个主区域，输入框最多使用可用主区域的一半高度。
     */
    @Test
    fun `should cap composer input to half of workspace height`() {
        assertEquals(400.dp, maxComposerInputHeight(800.dp))
        assertEquals(false, shouldShowComposerInputScrollbar(maxScrollValue = 0))
        assertEquals(true, shouldShowComposerInputScrollbar(maxScrollValue = 1))
    }

    /**
     * 所有工具输出使用统一的最大可视高度，超出后只在卡片内部滚动。
     */
    @Test
    fun `should constrain overflowing tool output and show its scrollbar`() {
        assertEquals(320.dp, TOOL_EVENT_OUTPUT_MAX_HEIGHT)
        assertEquals(false, shouldShowToolOutputScrollbar(maxScrollValue = 0))
        assertEquals(true, shouldShowToolOutputScrollbar(maxScrollValue = 1))
    }

    /**
     * 任务状态分组标题不应使用过小字号。
     */
    @Test
    fun `should expose readable task sidebar typography`() {
        assertEquals(13, TASK_SECTION_TITLE_FONT_SIZE_SP)
    }

    /**
     * 相邻的成功工具调用应成为一个展示组；失败与状态事件必须成为明确边界。
     */
    @Test
    fun `should group adjacent tools including failures while keeping statuses separate`() {
        val displayItems = groupTimelineItems(
            listOf(
                toolEvent("first", ToolEventStatus.Finished),
                toolEvent("second", ToolEventStatus.Finished),
                toolEvent("broken", ToolEventStatus.Failed),
                toolEvent("third", ToolEventStatus.Finished),
                toolEvent("status", ToolEventStatus.Status),
                toolEvent("fourth", ToolEventStatus.Finished),
            ),
        )

        assertEquals(listOf(4, 1, 1), displayItems.map(TimelineDisplayItem::itemCount))
        assertTrue(displayItems[0] is TimelineDisplayItem.ToolGroup)
        assertTrue(displayItems[1] is TimelineDisplayItem.Content)
        assertTrue(displayItems[2] is TimelineDisplayItem.Content)
    }

    /** 连续 reasoning part 没有工具、正文或状态边界时，应合并成一个思考块。 */
    @Test
    fun `should merge adjacent reasoning parts without an intervening event`() {
        val first = ReasoningItem(
            summaryText = "先分析",
            rawText = "first raw",
            isStreaming = false,
            durationMillis = 2_000,
        )
        val second = ReasoningItem(
            summaryText = "再验证",
            rawText = "second raw",
            isStreaming = false,
            durationMillis = 3_000,
        )

        val displayItems = groupTimelineItems(listOf(first, second))

        assertEquals(listOf(2), displayItems.map(TimelineDisplayItem::itemCount))
        val group = displayItems.single() as TimelineDisplayItem.ReasoningGroup
        assertEquals("先分析\n\n再验证", mergeReasoningItems(group.items).displayText)
        assertEquals(5_000, mergeReasoningItems(group.items).durationMillis)
    }

    /** ask_user 只驱动提问卡，不应生成时间线工具项或把相邻工具合并。 */
    @Test
    fun `should omit ask user tool event from timeline groups`() {
        val displayItems = groupTimelineItems(
            listOf(
                toolEvent("read_file", ToolEventStatus.Finished),
                toolEvent("ask_user", ToolEventStatus.Started),
                toolEvent("list_dir", ToolEventStatus.Finished),
            ),
        )

        assertEquals(listOf(1, 1), displayItems.map(TimelineDisplayItem::itemCount))
        assertTrue(displayItems.all { it is TimelineDisplayItem.Content })
    }

    /** 全部结束的工具组中仍有失败项时，应优先暴露失败而非绿色成功反馈。 */
    @Test
    fun `should prioritize failure in a completed tool group`() {
        val items = listOf(
            toolEvent("read_file", ToolEventStatus.Finished),
            toolEvent("apply_patch", ToolEventStatus.Failed),
            toolEvent("list_dir", ToolEventStatus.Finished),
        )

        assertEquals(TimelineToolGlyph.EDIT, timelineToolGroupGlyph(items))
        assertEquals(AppDanger, timelineToolGroupTint(items))
        assertEquals(listOf("Read files", "Edited files", "Searched files"), toolGroupSummaries(items))
        assertEquals(listOf("apply_patch", "list_dir"), visibleToolCardStack(items).map(ToolEventItem::toolName))
    }

    /** 运行中的每条工具调用都必须驱动自己的类型图标动画。 */
    @Test
    fun `should animate every running tool glyph`() {
        assertEquals(TimelineToolGlyph.TERMINAL, timelineToolGlyph(toolEvent("run_powershell", ToolEventStatus.Started)))
        assertEquals(TimelineToolGlyph.SEARCH, timelineToolGlyph(toolEvent("search_in_files", ToolEventStatus.Started)))
        assertEquals(TimelineToolGlyph.SEARCH, timelineToolGlyph(toolEvent("glob_files", ToolEventStatus.Started)))
        assertEquals(TimelineToolGlyph.READ, timelineToolGlyph(toolEvent("read_file", ToolEventStatus.Started)))
        assertEquals(TimelineToolGlyph.EDIT, timelineToolGlyph(toolEvent("edit_file", ToolEventStatus.Started)))
        assertEquals(TimelineToolGlyph.GENERIC, timelineToolGlyph(toolEvent("custom_tool", ToolEventStatus.Started)))
        assertEquals(true, shouldAnimateTimelineToolGlyph(ToolEventStatus.Started))
        assertEquals(false, shouldAnimateTimelineToolGlyph(ToolEventStatus.Finished))
        assertEquals(false, shouldAnimateTimelineToolGlyph(ToolEventStatus.Failed))
    }

    /** 单独展示的终端工具完成后应自动收起其输出面板。 */
    @Test
    fun `should auto collapse completed standalone terminal tool`() {
        assertEquals(true, shouldAutoCollapseStandaloneTerminalTool(toolEvent("run_powershell", ToolEventStatus.Finished)))
        assertEquals(false, shouldAutoCollapseStandaloneTerminalTool(toolEvent("run_powershell", ToolEventStatus.Started)))
        assertEquals(false, shouldAutoCollapseStandaloneTerminalTool(toolEvent("read_file", ToolEventStatus.Finished)))
    }

    /** 工具组内所有调用结束后，应回归中性的通用控制图标。 */
    @Test
    fun `should show a muted generic controls glyph for a completed tool group`() {
        val completed = listOf(
            toolEvent("list_dir", ToolEventStatus.Finished),
            toolEvent("glob_files", ToolEventStatus.Finished),
        )

        assertEquals(TimelineToolGlyph.GENERIC, timelineToolGroupGlyph(completed))
        assertEquals(AppMuted, timelineToolGroupTint(completed))
        assertEquals(TimelineToolGlyph.SEARCH, timelineToolGroupGlyph(listOf(
            toolEvent("glob_files", ToolEventStatus.Started),
        )))
        assertEquals(AppAccent, timelineToolGroupTint(listOf(
            toolEvent("glob_files", ToolEventStatus.Started),
        )))
    }

    /** 工具组摘要应按首次执行顺序追加，并对同类工具去重。 */
    @Test
    fun `should append stable semantic summaries for tool groups`() {
        assertEquals(
            listOf("Searched files", "Ran commands", "Edited files", "Used tools"),
            toolGroupSummaries(
                listOf(
                    toolEvent("list_directory", ToolEventStatus.Started),
                    toolEvent("grep", ToolEventStatus.Started),
                    toolEvent("run_powershell", ToolEventStatus.Started),
                    toolEvent("run_powershell", ToolEventStatus.Started),
                    toolEvent("apply_patch", ToolEventStatus.Started),
                    toolEvent("custom_tool", ToolEventStatus.Started),
                ),
            ),
        )
        assertEquals(
            androidx.compose.ui.unit.DpOffset(98.dp, (-20).dp),
            contextMenuOffsetForPointer(
                pointerPosition = Offset(200f, 60f),
                anchorHeightPixels = 80,
                density = 2f,
                shadowInset = PopupMenuShadowInset,
            ),
        )
    }

    /** 目录与编辑工具应使用区别于通用搜索和读取的专属图标。 */
    @Test
    fun `should resolve semantic tool glyphs for directory and editing work`() {
        assertEquals(
            TimelineToolGlyph.DIRECTORY,
            timelineToolPresentation(toolEvent("list_directory", ToolEventStatus.Started)).glyph,
        )
        assertEquals(
            TimelineToolGlyph.EDIT,
            timelineToolPresentation(toolEvent("apply_patch", ToolEventStatus.Started)).glyph,
        )
    }

    /** 终端收起行只显示命令，其他工具输入仅在展开内容中显示。 */
    @Test
    fun `should separate terminal commands from non terminal tool inputs`() {
        val terminal = ToolEventItem(
            toolName = "run_powershell",
            status = ToolEventStatus.Started,
            preview = "{\"script\":\"Get-ChildItem\"}",
        )
        val directory = ToolEventItem(
            toolName = "list_dir",
            status = ToolEventStatus.Started,
            preview = "{\"path\":\".\"}",
        )

        assertEquals("Get-ChildItem", timelineToolRowHeadline(terminal))
        assertEquals(
            "run_powershell",
            timelineToolRowHeadline(terminal.copy(preview = null)),
        )
        assertEquals("list_dir", timelineToolRowHeadline(directory))
        assertEquals("{\"path\":\".\"}", timelineToolExpandedInput(directory))
        assertNull(timelineToolExpandedInput(terminal))
    }

    /** 工具区域需采用较大的文字与从容的展开节奏，标题不再整段替换。 */
    @Test
    fun `should use readable slow motion metrics for tool activity`() {
        assertEquals(16, TOOL_GROUP_TITLE_FONT_SIZE_SP)
        assertEquals(15, TOOL_ROW_FONT_SIZE_SP)
        assertEquals(560, TOOL_GROUP_EXPAND_DURATION_MILLIS)
        assertEquals(440, TOOL_GROUP_COLLAPSE_DURATION_MILLIS)
        assertEquals(340, TOOL_ROW_EXPAND_DURATION_MILLIS)
        assertEquals(260, TOOL_ROW_COLLAPSE_DURATION_MILLIS)
    }

    /**
     * 终端开合必须通过实际布局宽度驱动，避免 SwingPanel 在动画结束后才补绘。
     */
    @Test
    fun `should animate terminal container through actual layout width`() {
        assertEquals(0f, terminalContainerWidthDuringMotion(270f, 0f))
        assertEquals(135f, terminalContainerWidthDuringMotion(270f, 0.5f))
        assertEquals(270f, terminalContainerWidthDuringMotion(270f, 1f))
    }

    /** 工具组首次渲染始终收起，不再等待完成状态异步收起。 */
    @Test
    fun `should start every tool group collapsed`() {
        assertEquals(false, initialTimelineToolGroupExpanded())
    }

    /** Git 失败文本不得进入标题栏，空分支也不应渲染分支胶囊。 */
    @Test
    fun `should hide failed git branch output from header`() {
        assertEquals("", resolveHeaderBranchOutput(128, "fatal: cannot change to 'missing'"))
        assertEquals("main", resolveHeaderBranchOutput(0, " main\n"))
        assertEquals(false, shouldShowHeaderBranchChip(""))
        assertEquals(true, shouldShowHeaderBranchChip("main"))
    }

    /** 工作目录修复卡片属于 Composer 上方的交互区域。 */
    @Test
    fun `should show workspace repair above the composer`() {
        assertEquals(true, shouldShowWorkspaceRepairCard("工作目录已不存在"))
        assertEquals(false, shouldShowWorkspaceRepairCard(null))
    }

    /** 修复卡片使用的文字按钮必须保留可点击的内容内边距。 */
    @Test
    fun `should keep padding for workspace repair text buttons`() {
        assertTrue(ringPrimaryButtonContentPadding(false).calculateLeftPadding(LayoutDirection.Ltr) > 0.dp)
        assertEquals(0.dp, ringPrimaryButtonContentPadding(true).calculateLeftPadding(LayoutDirection.Ltr))
    }

    /** 思考内容保持块级展示时，标题与正文都应具备清晰的可读字号。 */
    @Test
    fun `should use larger typography for the reasoning block`() {
        assertEquals(16, REASONING_HEADLINE_FONT_SIZE_SP)
        assertEquals(15, REASONING_BODY_FONT_SIZE_SP)
    }

    /** 思考正文的展开与收起应使用短促且不突兀的过渡节奏。 */
    @Test
    fun `should animate reasoning body expansion and collapse`() {
        assertEquals(220, REASONING_BODY_EXPAND_DURATION_MILLIS)
        assertEquals(160, REASONING_BODY_COLLAPSE_DURATION_MILLIS)
    }

    /** 思考结束后改用柔和紫色，以区别仍在进行的蓝色呼吸指示。 */
    @Test
    fun `should use a soft purple tint after reasoning completes`() {
        assertEquals(AppAccent, timelineReasoningTint(streaming = true))
        assertEquals(AppReasoning, timelineReasoningTint(streaming = false))
    }

    /**
     * 相邻工具调用保持紧凑；思考或状态文本切换到工具调用时保留清晰的段落间距。
     */
    @Test
    fun `should separate tool groups from other timeline content`() {
        val startedTool = TimelineDisplayItem.ToolGroup(listOf(toolEvent("first", ToolEventStatus.Started)))
        val failedTool = TimelineDisplayItem.ToolGroup(listOf(toolEvent("broken", ToolEventStatus.Failed)))
        val statusText = TimelineDisplayItem.Content(toolEvent("status", ToolEventStatus.Status))

        assertEquals(4, timelineDisplayItemSpacing(startedTool, failedTool))
        assertEquals(4, timelineDisplayItemSpacing(failedTool, startedTool))
        assertEquals(10, timelineDisplayItemSpacing(startedTool, statusText))
    }

    /**
     * 工具调用使用纯文字行，避免交互热区的垂直内边距拉开时间线。
     */
    @Test
    fun `should remove tool row vertical padding for inline density`() {
        assertEquals(0, TOOL_EVENT_ROW_VERTICAL_PADDING_DP)
    }

    /** 工具与问答详情使用统一的 Islands 外层与同色内层表面，不额外显示标签。 */
    @Test
    fun `should use shared islands surfaces for expanded details`() {
        assertEquals(Color(0xFF202125), ToolEventInputPaneBackground)
        assertEquals(DetailIslandBackground, ToolEventOutputPaneBackground)
        assertEquals(Color(0xFF27292E), DetailIslandsOuterBackground)
        assertTrue(DetailIslandsOuterBackground != DetailIslandBackground)
        assertEquals(28, TOOL_EVENT_DETAILS_START_PADDING_DP)
        assertEquals(26, ANSWERS_DETAILS_START_PADDING_DP)
        assertEquals(12, DETAIL_ISLANDS_OUTER_CORNER_RADIUS_DP)
        assertEquals(8, DETAIL_ISLANDS_INNER_CORNER_RADIUS_DP)
        assertEquals(12, DETAIL_ISLANDS_GAP_DP)
        assertEquals(16, DETAIL_ISLANDS_OUTER_PADDING_DP)
    }

    /** 助手 Markdown 应使用柔和灰白，不继承全局纯白正文。 */
    @Test
    fun `should use softened assistant markdown colors`() {
        assertEquals(Color(0xFFC7CBD3), AssistantMarkdownBodyForeground)
        assertEquals(Color(0xFFD8DCE3), AssistantMarkdownStrongForeground)
        assertEquals(AppUiFontFamily, AppTypography.bodyMedium.fontFamily)
    }

    /** 两个及以上工具的收起摘要使用统一英文文案。 */
    @Test
    fun `should use an English executed tools headline`() {
        assertEquals("Executed tools · 2", buildToolGroupHeadline(2))
    }

    /** 权限选择器与菜单必须共享英文模式名及风险色。 */
    @Test
    fun `should use English permission labels with matching semantic colors`() {
        val expectedModes = mapOf(
            PermissionPreset.DEFAULT to ("Ask" to Color(0xFF5A5C60)),
            PermissionPreset.AUTO to ("Auto" to Color(0xFF245286)),
            PermissionPreset.EDIT_ALLOW to ("Allow Edits" to Color(0xFF76561B)),
            PermissionPreset.PLAN to ("Plan" to Color(0xFF55479A)),
            PermissionPreset.BRAVE to ("Full Access" to Color(0xFF8E3541)),
        )

        expectedModes.forEach { (preset, expected) ->
            val presentation = permissionPresentation(preset)

            assertEquals(expected.first, presentation.label)
            assertEquals(expected.second, presentation.tone)
        }
    }

    /** 无框选择器只在悬浮或展开时提供轻量底色。 */
    @Test
    fun `should show compact background only while selector is active`() {
        assertEquals(Color.Transparent, selectChipTriggerBackground(expanded = false, hovered = false))
        assertEquals(Color(0xFF35383E), selectChipTriggerBackground(expanded = false, hovered = true))
        assertEquals(Color(0xFF35383E), selectChipTriggerBackground(expanded = true, hovered = false))
        assertEquals(false, shouldShowSelectChipArrow(expanded = false, hovered = false))
        assertEquals(true, shouldShowSelectChipArrow(expanded = false, hovered = true))
        assertEquals(true, shouldShowSelectChipArrow(expanded = true, hovered = false))
        assertEquals(90f, selectChipChevronRotation(expanded = false))
        assertEquals(-90f, selectChipChevronRotation(expanded = true))
    }

    /** 权限色应改为 Composer 的静态和流动边框色，Ask 沿用既有蓝色。 */
    @Test
    fun `should map permission modes to composer border tones`() {
        val expectedTones = mapOf(
            PermissionPreset.DEFAULT to AppAccent,
            PermissionPreset.AUTO to Color(0xFF245286),
            PermissionPreset.EDIT_ALLOW to Color(0xFF76561B),
            PermissionPreset.PLAN to Color(0xFF55479A),
            PermissionPreset.BRAVE to Color(0xFF8E3541),
        )

        expectedTones.forEach { (preset, expectedTone) ->
            assertEquals(expectedTone, composerBorderTone(preset))
        }
    }

    /** 任务与工作区右键菜单应复用选择菜单的 Islands 外层色板。 */
    @Test
    fun `should reuse popup menu palette for task context menus`() {
        assertEquals(PopupMenuBackground, TaskContextMenuBackground)
        assertEquals(PopupMenuSelectedBackground, TaskContextMenuHoverBackground)
        assertEquals(PopupMenuSelectedBackground, selectMenuItemBackground(selected = true, hovered = false, enabled = true))
    }

    /** 历史、运行中和完成态工具均从收起状态开始。 */
    @Test
    fun `should default tool groups and events to collapsed`() {
        assertEquals(false, initialTimelineToolGroupExpanded())
        ToolEventStatus.entries.forEach { status ->
            assertEquals(false, shouldExpandToolEventByDefault(toolEvent("read_file", status)))
        }
    }
}

/**
 * 构造用于展示规则测试的最小工具事件。
 */
private fun toolEvent(name: String, status: ToolEventStatus): ToolEventItem = ToolEventItem(
    toolName = name,
    status = status,
)

/**
 * 构造发送给原生标题栏菜单命中组件的测试鼠标事件。
 */
private fun mouseEvent(
    source: JPanel,
    id: Int,
    button: Int = MouseEvent.NOBUTTON,
): MouseEvent = MouseEvent(
    source,
    id,
    System.currentTimeMillis(),
    0,
    18,
    18,
    1,
    false,
    button,
)
