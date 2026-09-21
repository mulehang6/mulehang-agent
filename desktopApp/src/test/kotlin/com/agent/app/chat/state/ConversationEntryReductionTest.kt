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
