package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertEquals
import com.agent.app.chat.state.ChatTaskGroup
import com.agent.app.chat.state.ConversationTitleState

/**
 * 验证任务侧栏的列表密度约束。
 */
class TaskSidebarLayoutTest {
    /**
     * 任务标题之间只保留四 dp 的视觉间距，行本身不应加入额外垂直内边距。
     */
    @Test
    fun `should keep task title spacing at four dp without row padding`() {
        assertEquals(0, TASK_LIST_ITEM_VERTICAL_PADDING_DP)
        assertEquals(4, TASK_LIST_ITEM_GAP_DP)
    }

    /**
     * 新建、分组和任务条目需采用相近的可点击高度，避免侧栏尺度失衡。
     */
    @Test
    fun `should use balanced sidebar control heights`() {
        assertEquals(40, TASK_CREATE_BUTTON_HEIGHT_DP)
        assertEquals(36, TASK_SECTION_ROW_HEIGHT_DP)
        assertEquals(40, TASK_LIST_ITEM_HEIGHT_DP)
    }

    /**
     * 工作区与状态分组、分组标题与其首条任务之间应紧密相连。
     */
    @Test
    fun `should keep hierarchy headers and their content close together`() {
        assertEquals(2, TASK_WORKSPACE_CONTENT_GAP_DP)
        assertEquals(2, TASK_SECTION_CONTENT_GAP_DP)
    }

    /** 工作区、状态分组和具体任务应具有清晰的逐级缩进。 */
    @Test
    fun `should indent sections and tasks beneath their workspace`() {
        assertEquals(12, TASK_SECTION_INDENT_DP)
        assertEquals(16, TASK_LIST_ITEM_INDENT_DP)
    }

    /**
     * 标题生成状态必须使用三点提示，避免与执行中任务的旋转进度圈混淆。
     */
    @Test
    fun `should use three dots for generating title state`() {
        assertEquals(3, TITLE_GENERATING_DOT_COUNT)
    }

    /**
     * 工作区折叠状态必须独立于内部的进行中和已完成分组。
     */
    @Test
    fun `should use a dedicated workspace collapse key`() {
        assertEquals("workspace:D:\\repo\\mulehang-agent", workspaceCollapseKey("D:\\repo\\mulehang-agent"))
    }

    /** 标题生成中只显示三点占位，不能继续显示首条用户消息。 */
    @Test
    fun `should hide task title while generated title is pending`() {
        assertEquals(false, shouldShowConversationTitleText(ConversationTitleState.GENERATING))
        assertEquals(true, shouldShowConversationTitleText(ConversationTitleState.GENERATED))
    }

    /** 工作区和状态分组的折叠箭头只在对应行悬浮时显示。 */
    @Test
    fun `should show section chevrons only while their row hovers`() {
        assertEquals(true, shouldShowTaskSectionChevron(hovered = true))
        assertEquals(false, shouldShowTaskSectionChevron(hovered = false))
    }

    /** 已完成任务默认收起，但正在进行中的任务保持可见。 */
    @Test
    fun `should collapse done task sections by default`() {
        assertEquals(true, shouldCollapseTaskSectionByDefault(ChatTaskGroup.DONE))
        assertEquals(false, shouldCollapseTaskSectionByDefault(ChatTaskGroup.RUNNING))
    }
}
