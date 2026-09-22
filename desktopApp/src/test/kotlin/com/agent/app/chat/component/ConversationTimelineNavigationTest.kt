package com.agent.app.chat.component

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.withEntryProjection
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.CURRENT_CONVERSATION_TREE_FORMAT_VERSION
import com.agent.shared.chat.model.ToolEventStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 覆盖时间线轮次投影、活动刻度和滚动定位的纯展示规则。 */
class ConversationTimelineNavigationTest {
    /** 树节点定位保留完整分支，依次偏好当前路径、持久末端路径和最近的其他 leaf。 */
    @Test
    fun `entry navigation resolves a complete branch leaf`() {
        val root = message("root", null, ChatRole.User, "root", 1L)
        val active = message("active", root.id, ChatRole.Assistant, "active", 2L)
        val activeTail = message("active-tail", active.id, ChatRole.Assistant, "active tail", 3L)
        val head = message("head", root.id, ChatRole.Assistant, "head", 4L)
        val headTail = message("head-tail", head.id, ChatRole.Assistant, "head tail", 5L)
        val other = message("other", root.id, ChatRole.Assistant, "other", 6L)
        val otherOld = message("other-old", other.id, ChatRole.Assistant, "old", 7L)
        val otherNew = message("other-new", other.id, ChatRole.Assistant, "new", 8L)
        val label = ConversationEntry.Label("label", activeTail.id, 9L, head.id, "checkpoint")
        val entries = listOf(root, active, activeTail, head, headTail, other, otherOld, otherNew, label)

        assertEquals(
            activeTail.id,
            conversationEntryNavigationLeaf(entries, root.id, activeTail.id, headTail.id),
        )
        assertEquals(
            headTail.id,
            conversationEntryNavigationLeaf(entries, head.id, activeTail.id, headTail.id),
        )
        assertEquals(
            otherNew.id,
            conversationEntryNavigationLeaf(entries, other.id, activeTail.id, headTail.id),
        )
        assertEquals(head.id, conversationEntryNavigationAnchorId(entries, label.id))
        assertEquals(
            headTail.id,
            conversationEntryNavigationLeaf(entries, label.id, activeTail.id, headTail.id),
        )
    }

    /** 每个渲染段保留来源条目 ID，工具调用与结果定位到同一个时间线卡片。 */
    @Test
    fun `display entry ids follow projected timeline groups`() {
        val user = message("user", null, ChatRole.User, "question", 1L)
        val model = ConversationEntry.ModelChange("model", user.id, 2L, "profile")
        val reasoning = ConversationEntry.Reasoning(
            id = "reasoning",
            parentId = model.id,
            createdAt = 3L,
            summaryText = "thinking",
            isStreaming = false,
        )
        val call = ConversationEntry.ToolCall("call", reasoning.id, 4L, "read_file", toolCallId = "tool-1")
        val result = ConversationEntry.ToolResult(
            id = "result",
            parentId = call.id,
            createdAt = 5L,
            toolName = "read_file",
            status = ToolEventStatus.Finished,
            toolCallId = "tool-1",
        )
        val assistant = message("assistant", result.id, ChatRole.Assistant, "answer", 6L)
        val conversation = ChatConversationUiState(
            id = "tree",
            title = "tree",
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
            entries = listOf(user, model, reasoning, call, result, assistant),
            activeEntryId = assistant.id,
            headEntryId = assistant.id,
        ).withEntryProjection()

        assertEquals(
            listOf(
                setOf(user.id, model.id),
                setOf(reasoning.id),
                setOf(call.id, result.id),
                setOf(assistant.id),
            ),
            buildTimelineDisplayEntryIds(conversation),
        )
    }

    /** ask_user 由问题卡承载，不得占用普通时间线展示段的条目 slot。 */
    @Test
    fun `ask user entries do not shift projected timeline ids`() {
        val user = message("user", null, ChatRole.User, "question", 1L)
        val askCall = ConversationEntry.ToolCall(
            id = "ask-call",
            parentId = user.id,
            createdAt = 2L,
            toolName = ASK_USER_TOOL_NAME,
            toolCallId = ASK_USER_TOOL_NAME,
        )
        val askResult = ConversationEntry.ToolResult(
            id = "ask-result",
            parentId = askCall.id,
            createdAt = 3L,
            toolName = ASK_USER_TOOL_NAME,
            status = ToolEventStatus.Finished,
            toolCallId = ASK_USER_TOOL_NAME,
        )
        val assistant = message("assistant", askResult.id, ChatRole.Assistant, "answer", 4L)
        val conversation = ChatConversationUiState(
            id = "tree",
            title = "tree",
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
            entries = listOf(user, askCall, askResult, assistant),
            activeEntryId = assistant.id,
            headEntryId = assistant.id,
        ).withEntryProjection()

        assertEquals(
            listOf(setOf(user.id), setOf(assistant.id)),
            buildTimelineDisplayEntryIds(conversation),
        )
    }

    /** 节点点击只允许定位当前活动路径，离开分支必须由显式切换提交。 */
    @Test
    fun `entry reveal is limited to active path`() {
        val root = message("root", null, ChatRole.User, "root", 1L)
        val active = message("active", root.id, ChatRole.Assistant, "active", 2L)
        val sibling = message("sibling", root.id, ChatRole.Assistant, "sibling", 3L)
        val entries = listOf(root, active, sibling)

        assertTrue(isConversationEntryOnActivePath(entries, active.id, root.id))
        assertTrue(isConversationEntryOnActivePath(entries, active.id, active.id))
        assertFalse(isConversationEntryOnActivePath(entries, active.id, sibling.id))
    }

    /** 树会话使用用户条目 ID，并保存下一轮之前最后一条非空助手正文。 */
    @Test
    fun `tree turns use entry ids and final assistant preview`() {
        val entries = listOf(
            message("u1", null, ChatRole.User, "first"),
            message("a1", "u1", ChatRole.Assistant, "draft"),
            message("a2", "a1", ChatRole.Assistant, "final answer"),
            message("u2", "a2", ChatRole.User, "second"),
        )
        val conversation = ChatConversationUiState(
            id = "tree",
            title = "tree",
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
            entries = entries,
            activeEntryId = "u2",
            headEntryId = "u2",
        ).withEntryProjection()

        val turns = buildTimelineTurnPresentations(conversation)

        assertEquals(listOf("u1", "u2"), turns.map { it.anchorId })
        assertEquals("final answer", turns.first().assistantText)
        assertEquals("u2", turns.last().sourceUserEntryId)
        assertNull(turns.last().assistantText)
    }

    /** 旧线性会话获得序号锚点，且不会暴露不受支持的编辑与新会话入口。 */
    @Test
    fun `legacy turns use ordinal anchors without source entry ids`() {
        val conversation = ChatConversationUiState(
            id = "legacy",
            title = "legacy",
            workspacePath = "D:/workspace",
            items = listOf(
                ChatMessageItem(ChatMessage(ChatRole.User, "first")),
                ChatMessageItem(ChatMessage(ChatRole.Assistant, "answer")),
                ChatMessageItem(ChatMessage(ChatRole.User, "second")),
            ),
        )

        val turns = buildTimelineTurnPresentations(conversation)

        assertEquals(listOf("legacy-user-0", "legacy-user-1"), turns.map { it.anchorId })
        assertTrue(turns.all { it.sourceUserEntryId == null })
        assertEquals("answer", turns.first().assistantText)
    }

    /** 旧线性轮次没有来源条目 ID 时，空闲状态不得误判为正在运行。 */
    @Test
    fun `legacy operation state does not match two null ids`() {
        assertFalse(isTimelineOperationInProgress(operationEntryId = null, sourceUserEntryId = null))
        assertTrue(isTimelineOperationInProgress(operationEntryId = "user-1", sourceUserEntryId = "user-1"))
        assertFalse(isTimelineOperationInProgress(operationEntryId = "user-1", sourceUserEntryId = "user-2"))
    }

    /** 导航轨只在宽布局和至少两个用户轮次时出现。 */
    @Test
    fun `timeline visibility requires turns and left gutter`() {
        assertTrue(shouldShowTimelineNavigation(2, compact = false, leftGutterDp = 48f))
        assertFalse(shouldShowTimelineNavigation(1, compact = false, leftGutterDp = 80f))
        assertFalse(shouldShowTimelineNavigation(2, compact = true, leftGutterDp = 80f))
        assertFalse(shouldShowTimelineNavigation(2, compact = false, leftGutterDp = 47f))
    }

    /** 轨道位置以实际正文边界为准，异常密度或正文越界时回退为零留白。 */
    @Test
    fun `left gutter uses measured content bounds`() {
        assertEquals(80f, timelineLeftGutterDp(20f, 140f, density = 1.5f))
        assertEquals(0f, timelineLeftGutterDp(140f, 120f, density = 1.5f))
        assertEquals(0f, timelineLeftGutterDp(20f, 140f, density = 0f))
    }

    /** T3 刻度宽度按与活动轮次的距离逐级缩短。 */
    @Test
    fun `tick widths follow active distance`() {
        assertEquals(listOf(8, 10, 16, 24, 16, 10, 8), (0..6).map { timelineTickWidthDp(it, 3) })
        assertEquals(List(7) { 8 }, (0..6).map { timelineTickWidthDp(it, null) })
    }

    /** 少量轮次不得被强行摊满整个视口，轮次过多时仍能压缩到可用高度。 */
    @Test
    fun `tick travel stays compact until the rail is full`() {
        assertEquals(14f, timelineTickTravelDp(500f, turnCount = 2))
        assertEquals(56f, timelineTickTravelDp(500f, turnCount = 5))
        assertEquals(46f, timelineTickTravelDp(60f, turnCount = 10))
    }

    /** 活动轮次跟随视口焦点线，轨道点击则选择最近刻度。 */
    @Test
    fun `active turn and rail offset select stable indices`() {
        val turns = listOf("u1", "u2", "u3").map { id ->
            TimelineTurnPresentation(id, id, id, null)
        }

        assertEquals(
            1,
            activeTimelineTurnIndex(
                turns = turns,
                anchorTops = mapOf("u1" to 40f, "u2" to 220f, "u3" to 520f),
                viewportTop = 100f,
                viewportBottom = 600f,
            ),
        )
        assertEquals(0, timelineTurnIndexAtOffset(0f, railHeight = 100f, turnCount = 3))
        assertEquals(1, timelineTurnIndexAtOffset(50f, railHeight = 100f, turnCount = 3))
        assertEquals(2, timelineTurnIndexAtOffset(100f, railHeight = 100f, turnCount = 3))
    }

    /** 窗口坐标滚动换算会应用顶部留白并约束到合法范围。 */
    @Test
    fun `scroll target uses viewport delta and clamps`() {
        assertEquals(380, timelineScrollTarget(200, 300f, 100f, 1_000, 20f))
        assertEquals(680, timelineScrollTarget(1_000, -200f, 100f, 2_000, 20f))
        assertEquals(980, timelineScrollTarget(200, 900f, 100f, 2_000, 20f))
        assertEquals(0, timelineScrollTarget(10, 20f, 100f, 1_000, 20f))
        assertEquals(1_000, timelineScrollTarget(900, 400f, 100f, 1_000, 20f))
    }

    /** 创建测试消息条目。 */
    private fun message(
        id: String,
        parentId: String?,
        role: ChatRole,
        content: String,
        createdAt: Long = id.length.toLong(),
    ): ConversationEntry.Message = ConversationEntry.Message(
        id = id,
        parentId = parentId,
        createdAt = createdAt,
        message = ChatMessage(role, content),
    )
}
