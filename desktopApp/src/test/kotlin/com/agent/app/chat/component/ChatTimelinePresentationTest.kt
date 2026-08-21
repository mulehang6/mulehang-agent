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
import com.agent.app.chat.presentation.TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX
import com.agent.app.design.HeaderGlyph
import com.agent.app.design.AppHeaderBackground
import com.agent.app.design.AppAccent
import com.agent.app.design.AppDanger
import com.agent.app.design.AppReasoning
import com.agent.app.design.AppMuted
import com.agent.app.design.AppText
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.RightRailGlyph
import com.agent.app.design.buildRightRailGroups
import com.agent.app.design.desktopPalette
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

/** 验证会话时间线、工具事件与 Markdown Islands 的展示策略。 */
class ChatTimelinePresentationTest {
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

    /** 接近时间线底部时仍应视为跟随最新输出，避免微小滚动切换状态。 */
    @Test
    fun `should follow latest output within the scroll threshold`() {
        val maxScrollValue = 1_000

        assertEquals(
            true,
            isTimelineFollowingLatest(
                scrollValue = maxScrollValue - TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX,
                maxScrollValue = maxScrollValue,
            ),
        )
        assertEquals(
            false,
            isTimelineFollowingLatest(
                scrollValue = maxScrollValue - TIMELINE_SCROLL_FOLLOW_THRESHOLD_PX - 1,
                maxScrollValue = maxScrollValue,
            ),
        )
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
                presentationToolEvent("first", ToolEventStatus.Finished),
                presentationToolEvent("second", ToolEventStatus.Finished),
                presentationToolEvent("broken", ToolEventStatus.Failed),
                presentationToolEvent("third", ToolEventStatus.Finished),
                presentationToolEvent("status", ToolEventStatus.Status),
                presentationToolEvent("fourth", ToolEventStatus.Finished),
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
                presentationToolEvent("read_file", ToolEventStatus.Finished),
                presentationToolEvent("ask_user", ToolEventStatus.Started),
                presentationToolEvent("list_dir", ToolEventStatus.Finished),
            ),
        )

        assertEquals(listOf(1, 1), displayItems.map(TimelineDisplayItem::itemCount))
        assertTrue(displayItems.all { it is TimelineDisplayItem.Content })
    }

    /** 全部结束的工具组中仍有失败项时，应优先暴露失败而非绿色成功反馈。 */
    @Test
    fun `should prioritize failure in a completed tool group`() {
        val items = listOf(
            presentationToolEvent("read_file", ToolEventStatus.Finished),
            presentationToolEvent("apply_patch", ToolEventStatus.Failed),
            presentationToolEvent("list_dir", ToolEventStatus.Finished),
        )

        assertEquals(TimelineToolGlyph.EDIT, timelineToolGroupGlyph(items))
        assertEquals(AppDanger, timelineToolGroupTint(items))
        assertEquals(listOf("Read files", "Edited files", "Searched files"), toolGroupSummaries(items))
        assertEquals(listOf("apply_patch", "list_dir"), visibleToolCardStack(items).map(ToolEventItem::toolName))
    }

    /** 运行中的每条工具调用都必须驱动自己的类型图标动画。 */
    @Test
    fun `should animate every running tool glyph`() {
        assertEquals(TimelineToolGlyph.TERMINAL, timelineToolGlyph(presentationToolEvent("run_powershell", ToolEventStatus.Started)))
        assertEquals(TimelineToolGlyph.SEARCH, timelineToolGlyph(presentationToolEvent("search_in_files", ToolEventStatus.Started)))
        assertEquals(TimelineToolGlyph.SEARCH, timelineToolGlyph(presentationToolEvent("glob_files", ToolEventStatus.Started)))
        assertEquals(TimelineToolGlyph.READ, timelineToolGlyph(presentationToolEvent("read_file", ToolEventStatus.Started)))
        assertEquals(TimelineToolGlyph.EDIT, timelineToolGlyph(presentationToolEvent("edit_file", ToolEventStatus.Started)))
        assertEquals(TimelineToolGlyph.GENERIC, timelineToolGlyph(presentationToolEvent("custom_tool", ToolEventStatus.Started)))
        assertEquals(true, shouldAnimateTimelineToolGlyph(ToolEventStatus.Started))
        assertEquals(false, shouldAnimateTimelineToolGlyph(ToolEventStatus.Finished))
        assertEquals(false, shouldAnimateTimelineToolGlyph(ToolEventStatus.Failed))
    }

    /** 单独展示的终端工具完成后应自动收起其输出面板。 */
    @Test
    fun `should auto collapse completed standalone terminal tool`() {
        assertEquals(true, shouldAutoCollapseStandaloneTerminalTool(presentationToolEvent("run_powershell", ToolEventStatus.Finished)))
        assertEquals(false, shouldAutoCollapseStandaloneTerminalTool(presentationToolEvent("run_powershell", ToolEventStatus.Started)))
        assertEquals(false, shouldAutoCollapseStandaloneTerminalTool(presentationToolEvent("read_file", ToolEventStatus.Finished)))
    }

    /** 工具组内所有调用结束后，应回归中性的通用控制图标。 */
    @Test
    fun `should show a muted generic controls glyph for a completed tool group`() {
        val completed = listOf(
            presentationToolEvent("list_dir", ToolEventStatus.Finished),
            presentationToolEvent("glob_files", ToolEventStatus.Finished),
        )

        assertEquals(TimelineToolGlyph.GENERIC, timelineToolGroupGlyph(completed))
        assertEquals(AppMuted, timelineToolGroupTint(completed))
        assertEquals(TimelineToolGlyph.SEARCH, timelineToolGroupGlyph(listOf(
            presentationToolEvent("glob_files", ToolEventStatus.Started),
        )))
        assertEquals(AppAccent, timelineToolGroupTint(listOf(
            presentationToolEvent("glob_files", ToolEventStatus.Started),
        )))
    }

    /** 工具组摘要应按首次执行顺序追加，并对同类工具去重。 */
    @Test
    fun `should append stable semantic summaries for tool groups`() {
        assertEquals(
            listOf("Searched files", "Ran commands", "Edited files", "Used tools"),
            toolGroupSummaries(
                listOf(
                    presentationToolEvent("list_directory", ToolEventStatus.Started),
                    presentationToolEvent("grep", ToolEventStatus.Started),
                    presentationToolEvent("run_powershell", ToolEventStatus.Started),
                    presentationToolEvent("run_powershell", ToolEventStatus.Started),
                    presentationToolEvent("apply_patch", ToolEventStatus.Started),
                    presentationToolEvent("custom_tool", ToolEventStatus.Started),
                ),
            ),
        )
        assertEquals(
            androidx.compose.ui.unit.DpOffset(108.dp, (-10).dp),
            contextMenuOffsetForPointer(
                pointerPosition = Offset(200f, 60f),
                anchorHeightPixels = 80,
                density = 2f,
            ),
        )
    }

    /** 目录与编辑工具应使用区别于通用搜索和读取的专属图标。 */
    @Test
    fun `should resolve semantic tool glyphs for directory and editing work`() {
        assertEquals(
            TimelineToolGlyph.DIRECTORY,
            timelineToolPresentation(presentationToolEvent("list_directory", ToolEventStatus.Started)).glyph,
        )
        assertEquals(
            TimelineToolGlyph.EDIT,
            timelineToolPresentation(presentationToolEvent("apply_patch", ToolEventStatus.Started)).glyph,
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
        assertEquals(160, TOOL_GROUP_EXPAND_DURATION_MILLIS)
        assertEquals(140, TOOL_GROUP_COLLAPSE_DURATION_MILLIS)
        assertEquals(150, TOOL_ROW_EXPAND_DURATION_MILLIS)
        assertEquals(120, TOOL_ROW_COLLAPSE_DURATION_MILLIS)
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

    /** 思考内容保持块级展示时，标题与正文都应具备清晰的可读字号。 */
    @Test
    fun `should use larger typography for the reasoning block`() {
        assertEquals(16, REASONING_HEADLINE_FONT_SIZE_SP)
        assertEquals(15, REASONING_BODY_FONT_SIZE_SP)
    }

    /** 思考正文的展开与收起应使用短促且不突兀的过渡节奏。 */
    @Test
    fun `should animate reasoning body expansion and collapse`() {
        assertEquals(160, REASONING_BODY_EXPAND_DURATION_MILLIS)
        assertEquals(140, REASONING_BODY_COLLAPSE_DURATION_MILLIS)
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
        val startedTool = TimelineDisplayItem.ToolGroup(listOf(presentationToolEvent("first", ToolEventStatus.Started)))
        val failedTool = TimelineDisplayItem.ToolGroup(listOf(presentationToolEvent("broken", ToolEventStatus.Failed)))
        val statusText = TimelineDisplayItem.Content(presentationToolEvent("status", ToolEventStatus.Status))

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

    /** 历史、运行中和完成态工具均从收起状态开始。 */
    @Test
    fun `should default tool groups and events to collapsed`() {
        assertEquals(false, initialTimelineToolGroupExpanded())
        ToolEventStatus.entries.forEach { status ->
            assertEquals(false, shouldExpandToolEventByDefault(presentationToolEvent("read_file", status)))
        }
    }
}
