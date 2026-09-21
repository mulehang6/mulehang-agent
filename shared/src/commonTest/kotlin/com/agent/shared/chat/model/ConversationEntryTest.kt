package com.agent.shared.chat.model

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 覆盖条目图路径、投影、标签与单路径复制。 */
class ConversationEntryTest {
    /** 活动 leaf 只投影自身祖先，不混入兄弟分支。 */
    @Test
    fun activeLeafProjectsOnlyItsAncestorPath() {
        val root = message("root", null, ChatRole.User, "root")
        val left = message("left", root.id, ChatRole.Assistant, "left")
        val right = message("right", root.id, ChatRole.Assistant, "right")

        val projection = projectConversationEntries(listOf(root, left, right), left.id)

        assertEquals(listOf(root.id, left.id), projection.path.map { it.id })
        assertEquals(2, projection.timeline.size)
        assertFalse(projection.timeline.filterIsInstance<ChatMessageItem>().any { it.message.content == "right" })
    }

    /** 工具调用与结果在时间线合并，在模型历史中保持结构化先后关系。 */
    @Test
    fun toolResultCompletesTimelineCardAndKeepsHistoryParts() {
        val call = ConversationEntry.ToolCall("call", null, 1L, "read", "a.txt", toolCallId = "tool-1")
        val result = ConversationEntry.ToolResult(
            id = "result",
            parentId = call.id,
            createdAt = 2L,
            toolName = "read",
            status = ToolEventStatus.Finished,
            toolCallId = "tool-1",
            resultPreview = "ok",
        )

        val projection = projectConversationEntries(listOf(call, result), result.id)

        val timeline = assertIs<ToolEventItem>(projection.timeline.single())
        assertEquals(ToolEventStatus.Finished, timeline.status)
        val assistant = assertIs<AgentConversationHistoryMessage.Assistant>(projection.history.single())
        assertIs<AgentConversationHistoryPart.ToolCall>(assistant.parts[0])
        assertIs<AgentConversationHistoryPart.ToolResult>(assistant.parts[1])
    }

    /** 标签不进入模型上下文，分支摘要按固定边界包装为用户消息。 */
    @Test
    fun labelsStayOutOfContextAndBranchSummaryUsesBoundary() {
        val summary = ConversationEntry.BranchSummary("summary", null, 1L, "old", "important")
        val label = ConversationEntry.Label("label", summary.id, 2L, summary.id, "checkpoint")

        val projection = projectConversationEntries(listOf(summary, label), label.id)

        assertEquals("checkpoint", projection.labels[summary.id])
        val history = assertIs<AgentConversationHistoryMessage.User>(projection.history.single())
        assertEquals(branchSummaryContext("important"), history.content)
        assertTrue(history.content.contains("<summary>"))
    }

    /** 克隆只复制目标路径，并同步重写标签等内部引用。 */
    @Test
    fun copyingPathDropsSiblingAndRemapsInternalReferences() {
        val root = message("root", null, ChatRole.User, "root")
        val left = message("left", root.id, ChatRole.Assistant, "left")
        val right = message("right", root.id, ChatRole.Assistant, "right")
        val label = ConversationEntry.Label("label", left.id, 4L, left.id, "chosen")
        var nextId = 0

        val copy = copyConversationEntryPath(listOf(root, left, right, label), left.id) { "copy-${nextId++}" }

        assertEquals(3, copy.entries.size)
        assertFalse(copy.entries.filterIsInstance<ConversationEntry.Message>().any { it.message.content == "right" })
        val copiedLabel = copy.entries.filterIsInstance<ConversationEntry.Label>().single()
        val copiedLeft = copy.entries.filterIsInstance<ConversationEntry.Message>().single { it.message.content == "left" }
        assertEquals(copiedLeft.id, copiedLabel.targetEntryId)
        assertEquals(copiedLeft.id, copy.activeEntryId)
    }

    /** 侧挂标签不改变活动 leaf，但仍会出现在该路径的投影标签中。 */
    @Test
    fun sideLabelIsProjectedWithoutBecomingTheLeaf() {
        val root = message("root", null, ChatRole.User, "root")
        val answer = message("answer", root.id, ChatRole.Assistant, "answer")
        val label = ConversationEntry.Label("label", answer.id, 3L, answer.id, "checkpoint")

        val projection = projectConversationEntries(listOf(root, answer, label), answer.id)

        assertEquals(listOf(root.id, answer.id), projection.path.map { it.id })
        assertEquals("checkpoint", projection.labels[answer.id])
    }

    /** 创建简短消息条目。 */
    private fun message(
        id: String,
        parentId: String?,
        role: ChatRole,
        content: String,
    ): ConversationEntry.Message = ConversationEntry.Message(
        id = id,
        parentId = parentId,
        createdAt = id.length.toLong(),
        message = ChatMessage(role, content),
    )
}
