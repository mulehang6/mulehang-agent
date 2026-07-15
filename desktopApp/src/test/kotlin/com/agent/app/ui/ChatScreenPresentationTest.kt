package com.agent.app.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.agent.app.chat.state.buildWorkspaceLabel
import com.agent.app.chat.state.isStoppable
import com.agent.shared.chat.model.AppError
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(true, visible?.entries?.last()?.active)
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
        assertEquals(listOf(false, true), planCard?.entries?.map { it.active })
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
        assertEquals(listOf(false, true), planCard?.entries?.map { it.active })
    }

    /**
     * 右侧 rail 应保持三段分组，并且只有第一个 code 按钮默认高亮。
     */
    @Test
    fun `should expose grouped right rail buttons with only first item active`() {
        val groups = buildRightRailGroups()

        assertEquals(listOf(3, 2, 2), groups.map { it.size })
        assertEquals(RightRailGlyph.CODE, groups.first().first().glyph)
        assertEquals(RightRailGlyph.UPLOAD, groups[1].first().glyph)
        assertEquals(true, groups.first().first().active)
        assertEquals(
            1,
            groups.flatten().count { it.active },
        )
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
}
