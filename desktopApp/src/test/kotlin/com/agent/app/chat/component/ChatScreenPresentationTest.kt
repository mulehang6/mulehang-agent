package com.agent.app.chat.component

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.buildWorkspaceLabel
import com.agent.app.chat.state.isStoppable
import com.agent.app.chat.presentation.shouldExpandToolEventByDefault
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.AppAccent
import com.agent.app.design.AppDanger
import com.agent.app.design.AppReasoning
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.PopupMenuBackground
import com.agent.app.design.PopupMenuHoverBackground
import com.agent.app.design.PopupMenuSelectedBackground
import com.agent.app.design.buildRightRailGroups
import com.agent.app.design.selectMenuItemBackground
import com.agent.app.design.desktopPalette
import com.agent.app.design.IDEA_TITLE_BAR_HEIGHT
import com.agent.app.design.IDEA_TITLE_BAR_SEPARATOR_HEIGHT
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
import javax.swing.BorderFactory
import javax.swing.JPanel
import java.nio.file.Path

/**
 * 验证仍与 ChatScreen Compose 组件同包的展示规则。
 */
class ChatScreenPresentationTest {

    /** 复制分支应发出全局 toast 文案，不得替换标题栏中的分支文本。 */
    @Test
    fun `should use a global feedback message after copying a branch`() {
        assertEquals("已复制", headerBranchCopiedFeedbackMessage())
        assertEquals(24, APP_FEEDBACK_BOTTOM_PADDING_DP)
    }

    /** 无活动任务时，标题栏仍应展示父项目名称和稳定的项目徽章。 */
    @Test
    fun `should fall back to the parent project in the title bar`() {
        assertEquals(
            "Agent-dev",
            titleBarProjectLabel(workspacePath = null, workspaceName = null, projectRoot = Path.of("D:/projects/Agent-dev")),
        )
        assertEquals("AG", titleBarProjectMonogram("Agent-dev"))
    }

    /** 工作区位于父 IDEA 项目内时，标题栏必须展示最近的 `.idea` 祖先目录。 */
    @Test
    fun `should resolve nearest idea project root for title bar`() {
        val workspaceRoot = Path.of("D:/projects/Agent-dev/mulehang-agent")

        assertEquals(
            Path.of("D:/projects/Agent-dev").toAbsolutePath().normalize(),
            titleBarProjectRoot(workspaceRoot) { it.fileName.toString() == "Agent-dev" },
        )
    }

    /** 标题栏和侧栏任务入口必须展示完全相同的操作菜单。 */
    @Test
    fun `should expose the same task context menu actions everywhere`() {
        assertEquals(listOf("Fork", "删除", "Archive", "重命名"), taskContextMenuLabels())
        assertEquals(listOf("编辑", "删除"), workspaceContextMenuLabels())
    }

    /** Jewel 任务菜单保持适合任务名称的紧凑宽度。 */
    @Test
    fun `should keep task context menu compact`() {
        assertEquals(180.dp, TaskContextMenuWidth)
    }

    /** 右键菜单以鼠标位置为锚点，并从条目底部坐标修正为光标附近。 */
    @Test
    fun `should place task context menu beside pointer`() {
        assertEquals(
            androidx.compose.ui.unit.DpOffset(58.dp, (-20).dp),
            contextMenuOffsetForPointer(
                pointerPosition = Offset(100f, 40f),
                anchorHeightPixels = 80,
                density = 2f,
            ),
        )
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
     * 右侧 rail 在顶部保留终端入口，并在底部提供设置入口。
     */
    @Test
    fun `should expose terminal and settings rail buttons`() {
        val groups = buildRightRailGroups()

        assertEquals(listOf(1, 1), groups.map { it.size })
        assertEquals(RightRailGlyph.TERMINAL, groups.first().single().glyph)
        assertEquals(RightRailGlyph.SETTINGS, groups.last().single().glyph)
        assertEquals(false, groups.flatten().any { it.active })
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

        synchronizeTerminalInteropBackground(terminal, desktopPalette(DesktopThemeMode.DARK).terminal)

        val terminalBackground = java.awt.Color(25, 26, 28)
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
        assertEquals(40, TOOL_RAIL_ACTION_SIZE_DP)
        assertEquals(22, TOOL_RAIL_ICON_SIZE_DP)
    }

    /**
     * Rail 透明承接根画布，并通过自身的 40dp 命中目标与标题栏建立一致密度。
     */
    @Test
    fun `should align the right rail with the workspace below the title bar`() {
        assertEquals(16, TOOL_RAIL_TOP_PADDING_DP)
        assertEquals(Color.Transparent, TOOL_RAIL_BACKGROUND)
        assertEquals(54, IDEA_TITLE_BAR_HEIGHT.value.toInt())
        assertEquals(1, IDEA_TITLE_BAR_SEPARATOR_HEIGHT.value.toInt())
        assertEquals(40, TITLE_BAR_ACTION_HEIGHT_DP)
        assertEquals(20, HEADER_PROJECT_ICON_SIZE_DP)
        assertEquals("sidebar-toggle", TITLE_BAR_SIDEBAR_CLIENT_REGION_KEY)
        assertEquals("project-selector", TITLE_BAR_PROJECT_CLIENT_REGION_KEY)
        assertEquals("branch-menu", TITLE_BAR_BRANCH_CLIENT_REGION_KEY)
    }

    /** 标题栏项目与分支下拉必须暴露当前实施要求的关键动作。 */
    @Test
    fun `should expose workspace and branch actions in the title bar`() {
        assertEquals("选择工作区…", TITLE_BAR_PROJECT_SELECT_ACTION_LABEL)
        assertEquals("刷新分支", TITLE_BAR_BRANCH_REFRESH_ACTION_LABEL)
        assertEquals("复制分支名", TITLE_BAR_BRANCH_COPY_ACTION_LABEL)
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
}

/**
 * 构造用于展示规则测试的最小工具事件。
 */
internal fun presentationToolEvent(name: String, status: ToolEventStatus): ToolEventItem = ToolEventItem(
    toolName = name,
    status = status,
)
