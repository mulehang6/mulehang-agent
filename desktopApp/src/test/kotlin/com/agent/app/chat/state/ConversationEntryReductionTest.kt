package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.CURRENT_CONVERSATION_TREE_FORMAT_VERSION
import com.agent.shared.chat.model.ConversationEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** 覆盖树格式会话的流式条目归并。 */
class ConversationEntryReductionTest {
    /** 从历史 leaf 发送用户消息时，新分支必须立即成为持久 head。 */
    @Test
    fun `user message from history promotes new branch to head`() {
        val root = ConversationEntry.Message(
            id = "root",
            parentId = null,
            createdAt = 1L,
            message = ChatMessage(ChatRole.User, "first"),
        )
        val oldHead = ConversationEntry.Message(
            id = "old-head",
            parentId = root.id,
            createdAt = 2L,
            message = ChatMessage(ChatRole.Assistant, "answer"),
        )
        val initial = ChatConversationUiState(
            id = "conversation",
            title = DEFAULT_CONVERSATION_TITLE,
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
            entries = listOf(root, oldHead),
            activeEntryId = root.id,
            headEntryId = oldHead.id,
        ).withEntryProjection()

        val updated = appendUserConversationEntry(
            conversation = initial,
            prompt = "new branch",
            inputParts = listOf(UserInputPart.Text("new branch")),
            entryId = "new-head",
            createdAt = 3L,
        )

        assertEquals("new-head", updated.activeEntryId)
        assertEquals("new-head", updated.headEntryId)
        assertEquals("root", updated.entries.last().parentId)
    }

    /** 后续流式条目在 active 等于 head 时同步推进二者。 */
    @Test
    fun `stream event advances active and head together`() {
        val initial = ChatConversationUiState(
            id = "conversation",
            title = DEFAULT_CONVERSATION_TITLE,
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
        )

        val updated = applyConversationEntryEvent(
            conversation = initial,
            event = AgentStreamEvent.TextDelta("answer"),
            idFactory = { "assistant" },
            clock = { 1L },
        )

        assertEquals("assistant", updated.activeEntryId)
        assertEquals("assistant", updated.headEntryId)
    }

    /** 只有摘要增量的供应商也要把内容保留为可展示的原始推理。 */
    @Test
    fun `reasoning summary delta is retained as raw fallback`() {
        val initial = ChatConversationUiState(
            id = "conversation",
            title = DEFAULT_CONVERSATION_TITLE,
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
            reasoningEffort = ReasoningEffort.MEDIUM,
        )

        val updated = applyConversationEntryEvent(
            conversation = initial,
            event = AgentStreamEvent.ReasoningDelta(summary = "检查结构", rawText = null),
            idFactory = { "reasoning" },
            clock = { 1L },
        )
        val reasoning = updated.entries.single() as ConversationEntry.Reasoning

        assertEquals("检查结构", reasoning.summaryText)
        assertEquals("检查结构", reasoning.rawText)
    }

    /** 正文与推理交错到达时必须切换到新的树条目，不能回写旧正文。 */
    @Test
    fun `interleaved reasoning and assistant text keep separate path entries`() {
        val initial = ChatConversationUiState(
            id = "conversation",
            title = DEFAULT_CONVERSATION_TITLE,
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
        )

        val withFirstText = applyConversationEntryEvent(
            conversation = initial,
            event = AgentStreamEvent.TextDelta("先说"),
            idFactory = { "assistant-1" },
            clock = { 1L },
        )
        val withReasoning = applyConversationEntryEvent(
            conversation = withFirstText,
            event = AgentStreamEvent.ReasoningDelta(summary = "思考", rawText = "原始思考"),
            idFactory = { "reasoning" },
            clock = { 2L },
        )
        val updated = applyConversationEntryEvent(
            conversation = withReasoning,
            event = AgentStreamEvent.TextDelta("后说"),
            idFactory = { "assistant-2" },
            clock = { 3L },
        )

        assertEquals(listOf("assistant-1", "reasoning", "assistant-2"), updated.entries.map { it.id })
        assertEquals("先说", (updated.entries[0] as ConversationEntry.Message).message.content)
        assertEquals("原始思考", (updated.entries[1] as ConversationEntry.Reasoning).rawText)
        assertEquals("assistant-1", updated.entries[1].parentId)
        assertEquals("reasoning", updated.entries[2].parentId)
        assertFalse((updated.entries[1] as ConversationEntry.Reasoning).isStreaming)
        assertNull(updated.streamingReasoningEntryId)
        assertEquals("assistant-2", updated.streamingAssistantEntryId)
    }

    /** 只有完整收尾事件也必须创建已完成的推理条目。 */
    @Test
    fun `reasoning completion without deltas is retained`() {
        val initial = ChatConversationUiState(
            id = "conversation",
            title = DEFAULT_CONVERSATION_TITLE,
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
        )

        val updated = applyConversationEntryEvent(
            conversation = initial,
            event = AgentStreamEvent.ReasoningCompleted(summary = "完整摘要", rawText = "完整原文"),
            idFactory = { "reasoning" },
            clock = { 7L },
        )
        val reasoning = updated.entries.single() as ConversationEntry.Reasoning

        assertEquals("完整摘要", reasoning.summaryText)
        assertEquals("完整原文", reasoning.rawText)
        assertFalse(reasoning.isStreaming)
        assertEquals(0L, reasoning.durationMillis)
        assertNull(updated.streamingReasoningEntryId)
    }

    /** 新轮次只有完整推理时，不得覆盖活动路径上的上一轮推理。 */
    @Test
    fun `reasoning completion without deltas starts a new current-turn entry`() {
        val oldUser = ConversationEntry.Message(
            id = "old-user",
            parentId = null,
            createdAt = 1L,
            message = ChatMessage(ChatRole.User, "上一轮"),
        )
        val oldReasoning = ConversationEntry.Reasoning(
            id = "old-reasoning",
            parentId = oldUser.id,
            createdAt = 2L,
            summaryText = "旧摘要",
            rawText = "旧原文",
            isStreaming = false,
            durationMillis = 1L,
        )
        val oldAssistant = ConversationEntry.Message(
            id = "old-assistant",
            parentId = oldReasoning.id,
            createdAt = 3L,
            message = ChatMessage(ChatRole.Assistant, "旧回答"),
        )
        val initial = ChatConversationUiState(
            id = "conversation",
            title = DEFAULT_CONVERSATION_TITLE,
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
            entries = listOf(oldUser, oldReasoning, oldAssistant),
            activeEntryId = oldAssistant.id,
            headEntryId = oldAssistant.id,
        ).withEntryProjection()
        val newTurn = appendUserConversationEntry(
            conversation = initial,
            prompt = "新一轮",
            inputParts = listOf(UserInputPart.Text("新一轮")),
            entryId = "new-user",
            createdAt = 4L,
        )

        val updated = applyConversationEntryEvent(
            conversation = newTurn,
            event = AgentStreamEvent.ReasoningCompleted(summary = "新摘要", rawText = "新原文"),
            idFactory = { "new-reasoning" },
            clock = { 5L },
        )

        assertEquals(oldReasoning, updated.entries.single { it.id == oldReasoning.id })
        val newReasoning = updated.entries.single { it.id == "new-reasoning" } as ConversationEntry.Reasoning
        assertEquals("new-user", newReasoning.parentId)
        assertEquals("新摘要", newReasoning.summaryText)
        assertFalse(newReasoning.isStreaming)
    }

    /** 失败事件必须关闭推理条目并清理两种流式归属。 */
    @Test
    fun `failed event closes streaming entries before appending error`() {
        val initial = ChatConversationUiState(
            id = "conversation",
            title = DEFAULT_CONVERSATION_TITLE,
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
        )
        val streaming = applyConversationEntryEvent(
            conversation = initial,
            event = AgentStreamEvent.ReasoningDelta(summary = "正在思考", rawText = "原文"),
            idFactory = { "reasoning" },
            clock = { 1L },
        ).copy(streamingAssistantEntryId = "stale-assistant")

        val updated = applyConversationEntryEvent(
            conversation = streaming,
            event = AgentStreamEvent.Failed("失败"),
            idFactory = { "error" },
            clock = { 5L },
        )
        val reasoning = updated.entries.filterIsInstance<ConversationEntry.Reasoning>().single()
        val error = updated.entries.filterIsInstance<ConversationEntry.ToolResult>().single()

        assertFalse(reasoning.isStreaming)
        assertEquals(4L, reasoning.durationMillis)
        assertNull(updated.streamingAssistantEntryId)
        assertNull(updated.streamingReasoningEntryId)
        assertEquals("失败", error.errorMessage)
    }

    /** 空的收尾字段不得覆盖流式阶段已经收集到的推理文本。 */
    @Test
    fun `blank reasoning completion preserves streamed content`() {
        val initial = ChatConversationUiState(
            id = "conversation",
            title = DEFAULT_CONVERSATION_TITLE,
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
            entries = listOf(
                ConversationEntry.Reasoning(
                    id = "reasoning",
                    parentId = null,
                    createdAt = 1L,
                    summaryText = "",
                    rawText = "已有推理",
                    isStreaming = true,
                ),
            ),
            activeEntryId = "reasoning",
            streamingReasoningEntryId = "reasoning",
            reasoningEffort = ReasoningEffort.MEDIUM,
        )

        val updated = applyConversationEntryEvent(
            conversation = initial,
            event = AgentStreamEvent.ReasoningCompleted(summary = "", rawText = ""),
            idFactory = { error("不应创建新条目") },
            clock = { 2L },
        )
        val reasoning = updated.entries.single() as ConversationEntry.Reasoning

        assertEquals("已有推理", reasoning.rawText)
    }
}
