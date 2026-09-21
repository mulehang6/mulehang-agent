package com.agent.app.chat.component

import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 覆盖会话树的 Pi 式筛选、扁平化、折叠与标签策略。 */
class ConversationEntryTreeDialogTest {
    /** 已可见选择不滚动，越界键盘选择只移动到刚好可见的位置。 */
    @Test
    fun `keyboard selection uses minimal scrolling`() {
        assertEquals(null, minimalScrollStartForSelection(5, 2, 8))
        assertEquals(1, minimalScrollStartForSelection(1, 2, 8))
        assertEquals(4, minimalScrollStartForSelection(10, 2, 8))
        assertEquals(7, minimalScrollStartForSelection(7, null, null))
    }

    /** 纯线性会话在分支概览中显示空状态，不重复完整条目列表。 */
    @Test
    fun `linear conversation has no branch overview items`() {
        val root = message("root", null, ChatRole.User, "问题", 1L)
        val answer = message("answer", root.id, ChatRole.Assistant, "回答", 2L)

        assertTrue(buildConversationBranchOverview(listOf(root, answer), answer.id, answer.id).isEmpty())
    }

    /** 真实分叉被压缩成末端，线性中间条目以跳过数量展示。 */
    @Test
    fun `branch overview compresses linear segments to leaves`() {
        val root = message("root", null, ChatRole.User, "问题", 1L)
        val left = message("left", root.id, ChatRole.Assistant, "左分支", 2L)
        val leftTail = message("left-tail", left.id, ChatRole.Assistant, "左结论", 3L)
        val right = message("right", root.id, ChatRole.Assistant, "右分支", 4L)

        val overview = buildConversationBranchOverview(
            entries = listOf(root, left, leftTail, right),
            activeEntryId = right.id,
            headEntryId = leftTail.id,
        )

        assertEquals(setOf("left-tail", "right"), overview.mapTo(mutableSetOf()) { it.leafEntryId })
        assertEquals(1, overview.single { it.leafEntryId == "left-tail" }.skippedEntryCount)
        assertTrue(overview.single { it.leafEntryId == "right" }.isActiveLeaf)
        assertTrue(overview.single { it.leafEntryId == "left-tail" }.isHeadLeaf)
    }

    /** 普通对话是单子节点链，视觉缩进必须始终为零。 */
    @Test
    fun `linear conversation never drifts to the right`() {
        val entries = buildList {
            var parentId: String? = null
            repeat(20) { index ->
                val entry = message("message-$index", parentId, ChatRole.User, "消息 $index", index.toLong())
                add(entry)
                parentId = entry.id
            }
        }

        val rows = flattenConversationEntryTree(
            entries = entries,
            visibleIds = entries.mapTo(mutableSetOf(), ConversationEntry::id),
            activeEntryId = entries.last().id,
        )

        assertEquals(List(entries.size) { 0 }, rows.map(ConversationEntryTreeRow::indent))
    }

    /** 只有真实兄弟分支增加缩进，分支后的单链不会逐条继续加深。 */
    @Test
    fun `branch indentation grows only at branch points`() {
        val root = message("root", null, ChatRole.User, "根", 1L)
        val left = message("left", root.id, ChatRole.Assistant, "左", 2L)
        val right = message("right", root.id, ChatRole.Assistant, "右", 3L)
        val leftOne = message("left-1", left.id, ChatRole.User, "左一", 4L)
        val leftTwo = message("left-2", leftOne.id, ChatRole.Assistant, "左二", 5L)
        val rightOne = message("right-1", right.id, ChatRole.User, "右一", 6L)
        val entries = listOf(root, left, right, leftOne, leftTwo, rightOne)

        val rows = flattenConversationEntryTree(
            entries = entries,
            visibleIds = entries.mapTo(mutableSetOf(), ConversationEntry::id),
            activeEntryId = rightOne.id,
        )

        assertEquals(
            listOf("root", "right", "right-1", "left", "left-1", "left-2"),
            rows.map { it.entry.id },
        )
        assertEquals(listOf(0, 1, 2, 1, 2, 2), rows.map(ConversationEntryTreeRow::indent))
    }

    /** 极深的真实分支仍会封顶，避免正文区域被无限挤向右侧。 */
    @Test
    fun `deep branch indentation is capped`() {
        val entries = mutableListOf<ConversationEntry>()
        var parent = message("root", null, ChatRole.User, "根", 0L).also(entries::add)
        repeat(12) { index ->
            val path = message("path-$index", parent.id, ChatRole.Assistant, "路径 $index", index * 2L + 1L)
            val sibling = message("sibling-$index", parent.id, ChatRole.Assistant, "分支 $index", index * 2L + 2L)
            entries += path
            entries += sibling
            parent = path
        }

        val rows = flattenConversationEntryTree(
            entries = entries,
            visibleIds = entries.mapTo(mutableSetOf(), ConversationEntry::id),
            activeEntryId = parent.id,
        )

        assertEquals(CONVERSATION_ENTRY_TREE_MAX_INDENT_LEVEL, rows.maxOf(ConversationEntryTreeRow::indent))
    }

    /** 空摘要不得遮住供应商实际返回的原始推理文本。 */
    @Test
    fun `reasoning preview falls back from blank summary to raw text`() {
        val reasoning = ConversationEntry.Reasoning(
            id = "reasoning",
            parentId = null,
            createdAt = 1L,
            summaryText = "",
            rawText = "正在检查项目结构",
        )

        assertEquals("正在检查项目结构", entryPreview(reasoning))
    }

    /** 默认筛选隐藏设置记录，隐藏工具筛选额外去掉工具调用和结果。 */
    @Test
    fun `filters match pi five-level behavior`() {
        val user = message("user", null, ChatRole.User, "问题", 1L)
        val model = ConversationEntry.ModelChange("model", user.id, 2L, "profile")
        val assistant = message("assistant", model.id, ChatRole.Assistant, "回答", 3L)
        val call = ConversationEntry.ToolCall("call", assistant.id, 4L, "read_file")
        val result = ConversationEntry.ToolResult(
            id = "result",
            parentId = call.id,
            createdAt = 5L,
            toolName = "read_file",
            status = com.agent.shared.chat.model.ToolEventStatus.Finished,
        )
        val entries = listOf(user, model, assistant, call, result)

        assertEquals(
            setOf("user", "assistant", "call", "result"),
            visibleConversationEntryIds(entries, "", ConversationEntryFilter.DEFAULT, emptySet()),
        )
        assertEquals(
            setOf("user", "assistant"),
            visibleConversationEntryIds(entries, "", ConversationEntryFilter.NO_TOOLS, emptySet()),
        )
        assertEquals(
            setOf("user"),
            visibleConversationEntryIds(entries, "", ConversationEntryFilter.USER_ONLY, emptySet()),
        )
        assertEquals(
            setOf("assistant"),
            visibleConversationEntryIds(entries, "", ConversationEntryFilter.LABELED_ONLY, setOf("assistant")),
        )
        assertEquals(
            entries.mapTo(mutableSetOf(), ConversationEntry::id),
            visibleConversationEntryIds(entries, "", ConversationEntryFilter.ALL, emptySet()),
        )
    }

    /** 过滤掉的中间设置条目会被跳过，后代挂到最近的可见祖先。 */
    @Test
    fun `hidden intermediates reconnect to nearest visible ancestor`() {
        val root = message("root", null, ChatRole.User, "问题", 1L)
        val model = ConversationEntry.ModelChange("model", root.id, 2L, "profile")
        val effort = ConversationEntry.ReasoningEffortChange("effort", model.id, 3L, "HIGH")
        val assistant = message("assistant", effort.id, ChatRole.Assistant, "回答", 4L)
        val entries = listOf(root, model, effort, assistant)
        val visible = visibleConversationEntryIds(entries, "", ConversationEntryFilter.DEFAULT, emptySet())

        val rows = flattenConversationEntryTree(entries, visible, assistant.id)

        assertEquals(listOf("root", "assistant"), rows.map { it.entry.id })
        assertEquals(listOf(0, 0), rows.map(ConversationEntryTreeRow::indent))
        assertEquals(root.id, rows.last().visibleParentId)
    }

    /** 折叠只隐藏目标段的后代，不影响其他根。 */
    @Test
    fun `collapse hides descendants but keeps sibling roots`() {
        val root = message("root", null, ChatRole.User, "根", 1L)
        val child = message("child", root.id, ChatRole.Assistant, "子", 2L)
        val other = message("other", null, ChatRole.User, "另一个根", 3L)
        val entries = listOf(root, child, other)
        val visible = entries.mapTo(mutableSetOf(), ConversationEntry::id)

        val rows = flattenConversationEntryTree(entries, visible, child.id, collapsedIds = setOf(root.id))

        assertEquals(listOf("root", "other"), rows.map { it.entry.id })
        assertTrue(rows.first().foldable)
    }

    /** 搜索只保留直接命中项，并把它提升为最近可见层级，避免隐藏路径制造缩进。 */
    @Test
    fun `search rebuilds visual structure from matching entries`() {
        val root = message("root", null, ChatRole.User, "开始", 1L)
        val assistant = message("assistant", root.id, ChatRole.Assistant, "普通回答", 2L)
        val tool = ConversationEntry.ToolCall("tool", assistant.id, 3L, "read_file", "目标文件")
        val entries = listOf(root, assistant, tool)

        val visible = visibleConversationEntryIds(entries, "目标文件", ConversationEntryFilter.ALL, emptySet())
        val rows = flattenConversationEntryTree(entries, visible, tool.id)

        assertEquals(setOf(tool.id), visible)
        assertEquals(0, rows.single().indent)
        assertEquals(null, rows.single().visibleParentId)
    }

    /** 空标签不能创建；已有标签清空时则明确显示为清除操作。 */
    @Test
    fun `label action distinguishes add update and clear`() {
        val empty = conversationEntryLabelAction(null, "   ")
        val add = conversationEntryLabelAction(null, "检查点")
        val unchanged = conversationEntryLabelAction("检查点", "检查点")
        val clear = conversationEntryLabelAction("检查点", "")

        assertEquals("添加标签", empty.label)
        assertFalse(empty.enabled)
        assertTrue(add.enabled)
        assertFalse(unchanged.enabled)
        assertEquals("清除标签", clear.label)
        assertTrue(clear.enabled)
    }

    /** 创建带稳定时间戳的消息条目。 */
    private fun message(
        id: String,
        parentId: String?,
        role: ChatRole,
        content: String,
        createdAt: Long,
    ): ConversationEntry.Message = ConversationEntry.Message(
        id = id,
        parentId = parentId,
        createdAt = createdAt,
        message = ChatMessage(role, content),
    )
}
