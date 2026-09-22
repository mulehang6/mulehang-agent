package com.agent.app.chat.state

import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.api.BranchSummaryGenerator
import com.agent.shared.agent.api.BranchSummaryRequest
import com.agent.shared.agent.api.GeneratedBranchSummary
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.chat.model.CURRENT_CONVERSATION_TREE_FORMAT_VERSION
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 覆盖 fork、clone、Pi 导航以及归档和删除生命周期。 */
class ConversationTreeControllerTest : ChatWindowTestFixture() {
    /** fork 只复制目标用户消息之前的路径，并把该输入与附件恢复到 composer。 */
    @Test
    fun `fork copies path before user entry and restores draft`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        val result = state.conversationTreeController.createConversationFromUserEntry(source.id, "user-2")

        assertTrue(result.succeeded)
        val child = state.ui.activeConversation
        assertEquals(source.id, child.parentConversationId)
        assertEquals("source - fork", child.title)
        assertEquals("user-2", child.forkedFromEntryId)
        assertEquals(listOf("first", "answer"), child.entries.filterIsInstance<ConversationEntry.Message>().map { it.message.content })
        assertEquals("again @input.txt", state.ui.draft)
        assertEquals(listOf("input.txt"), child.attachments.map { it.name })
        assertEquals(2, child.entries.size)
        assertEquals(1L, state.ui.composerFocusRequestId)
        assertEquals(source.entries, state.findConversation(source.id).entries)
    }

    /** clone 复制当前 leaf 的单一路径，并保留源会话中的兄弟分支。 */
    @Test
    fun `clone copies active path without sibling branches`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        val result = state.conversationTreeController.cloneConversation(source.id)

        assertTrue(result.succeeded)
        val child = state.ui.activeConversation
        assertEquals(source.id, child.parentConversationId)
        assertEquals("source - clone", child.title)
        assertEquals(listOf("first", "answer", "again"), child.entries.filterIsInstance<ConversationEntry.Message>().map { it.message.content })
        assertTrue(source.entries.any { it.id == "sibling" })
        assertFalse(child.entries.filterIsInstance<ConversationEntry.Message>().any { it.message.content == "sibling" })
    }

    /** 选择用户条目会回到父节点并恢复输入，旧分支仍完整保留。 */
    @Test
    fun `navigating to user entry moves leaf to parent and preserves old branch`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        assertTrue(state.conversationTreeController.wouldLeaveActiveBranch(source.id, "user-2"))
        val result = state.conversationTreeController.navigateToEntry(source.id, "user-2")

        assertTrue(result.succeeded)
        val navigated = state.ui.activeConversation
        assertEquals("assistant-1", navigated.activeEntryId)
        assertEquals(source.headEntryId, navigated.headEntryId)
        assertEquals("again @input.txt", state.ui.draft)
        assertEquals(source.entries, navigated.entries)
    }

    /** 消息级编辑只移动活动 leaf；重新发送后新旧用户消息成为真正的兄弟分支。 */
    @Test
    fun `edit from user entry creates sibling only after resend`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        val result = state.conversationTreeController.editFromUserEntry(source.id, "user-2")

        assertTrue(result.succeeded)
        assertEquals("assistant-1", state.ui.activeConversation.activeEntryId)
        assertEquals(source.entries, state.ui.activeConversation.entries)
        assertEquals(1L, state.ui.composerFocusRequestId)

        val resent = appendUserConversationEntry(
            conversation = state.ui.activeConversation,
            prompt = "revised",
            inputParts = listOf(UserInputPart.Text("revised")),
            entryId = "user-2-revised",
            createdAt = 10L,
        )

        val siblingUsers = resent.entries.filterIsInstance<ConversationEntry.Message>()
            .filter { it.message.role == ChatRole.User && it.parentId == "assistant-1" }
        assertEquals(listOf("again", "revised"), siblingUsers.map { it.message.content })
        assertEquals("user-2-revised", resent.activeEntryId)
        assertEquals("user-2-revised", resent.headEntryId)
    }

    /** 消息级控制器拒绝助手条目，避免界面把任意树节点误当作可编辑用户输入。 */
    @Test
    fun `edit from user entry rejects non user entries`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        val result = state.conversationTreeController.editFromUserEntry(source.id, "assistant-1")

        assertFalse(result.succeeded)
        assertEquals(source.activeEntryId, state.ui.activeConversation.activeEntryId)
        assertEquals(0L, state.ui.composerFocusRequestId)
    }

    /** 导航到非用户条目时它直接成为 leaf，并清空旧 composer 内容。 */
    @Test
    fun `navigating to non user entry selects it and clears composer`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id, draft = "未发送")

        val result = state.conversationTreeController.navigateToEntry(source.id, "sibling")

        assertTrue(result.succeeded)
        assertEquals("sibling", state.ui.activeConversation.activeEntryId)
        assertEquals("", state.ui.draft)
        assertEquals(source.entries, state.ui.activeConversation.entries)
    }

    /** 分支概览的精确切换不触发用户消息编辑语义，并保留当前草稿与持久末端。 */
    @Test
    fun `exact leaf switch preserves draft and head`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id, draft = "未发送草稿")

        val result = state.conversationTreeController.switchToLeaf(source.id, "sibling")

        assertTrue(result.succeeded)
        assertEquals("sibling", state.ui.activeConversation.activeEntryId)
        assertEquals("user-2", state.ui.activeConversation.headEntryId)
        assertEquals("未发送草稿", state.ui.draft)
        assertTrue(state.conversationTreeController.isAwayFromHead(source.id))
    }

    /** 从历史分支返回 head 会恢复完整主线，并保留尚未发送的草稿。 */
    @Test
    fun `return to head restores persistent end`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source").copy(activeEntryId = "sibling").withEntryProjection()
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id, draft = "草稿")

        val result = state.conversationTreeController.returnToHead(source.id)

        assertTrue(result.succeeded)
        assertEquals("user-2", state.ui.activeConversation.activeEntryId)
        assertEquals("user-2", state.ui.activeConversation.headEntryId)
        assertEquals("草稿", state.ui.draft)
        assertFalse(state.conversationTreeController.isAwayFromHead(source.id))
    }

    /** 标签是侧挂元数据：空新增是无操作，保存和清除都不能改变活动 leaf。 */
    @Test
    fun `label updates do not become the active leaf`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        state.conversationTreeController.setEntryLabel(source.id, "assistant-1", "   ")
        assertEquals(source.entries.size, state.ui.activeConversation.entries.size)

        state.conversationTreeController.setEntryLabel(source.id, "assistant-1", "检查点")
        val labeled = state.ui.activeConversation
        assertEquals(source.activeEntryId, labeled.activeEntryId)
        assertEquals("检查点", labeled.entries.filterIsInstance<ConversationEntry.Label>().single().label)

        state.conversationTreeController.setEntryLabel(source.id, "assistant-1", "")
        val cleared = state.ui.activeConversation
        assertEquals(source.activeEntryId, cleared.activeEntryId)
        assertEquals("", cleared.entries.filterIsInstance<ConversationEntry.Label>().last().label)
    }

    /** 离开当前路径时，自动摘要作为新分支 leaf 注入并保留来源 leaf。 */
    @Test
    fun `automatic summary is appended when leaving current path`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        val result = state.conversationTreeController.navigateToEntry(
            conversationId = source.id,
            entryId = "sibling",
            summary = BranchNavigationSummary(BranchSummaryMode.AUTOMATIC),
        )

        assertTrue(result.succeeded)
        val summary = state.ui.activeConversation.entries.filterIsInstance<ConversationEntry.BranchSummary>().single()
        assertEquals("sibling", summary.parentId)
        assertEquals("user-2", summary.fromEntryId)
        assertEquals(summary.id, state.ui.activeConversation.activeEntryId)
        assertTrue(summary.summary.contains("again"))
    }

    /** 空的自定义摘要提示视为取消，不得移动 leaf 或修改条目图。 */
    @Test
    fun `blank custom summary leaves tree untouched`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        val result = state.conversationTreeController.navigateToEntry(
            conversationId = source.id,
            entryId = "sibling",
            summary = BranchNavigationSummary(BranchSummaryMode.CUSTOM, "   "),
        )

        assertFalse(result.succeeded)
        assertEquals(source.activeEntryId, state.ui.activeConversation.activeEntryId)
        assertEquals(source.entries, state.ui.activeConversation.entries)
    }

    /** 自定义提示只作为摘要指令发送，模型正文才会写入条目。 */
    @Test
    fun `custom summary uses model result and forwards instructions`() = runTest(dispatcher) {
        var capturedRequest: BranchSummaryRequest? = null
        val state = state { request ->
            capturedRequest = request
            GeneratedBranchSummary("模型生成的摘要", inputTokens = 20L, outputTokens = 8L)
        }
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        val result = state.conversationTreeController.navigateToEntry(
            conversationId = source.id,
            entryId = "sibling",
            summary = BranchNavigationSummary(BranchSummaryMode.CUSTOM, "只保留未完成事项"),
        )

        assertTrue(result.succeeded)
        assertEquals("只保留未完成事项", capturedRequest?.customInstructions)
        assertFalse(capturedRequest?.branchContent.orEmpty().contains("User: first"))
        assertTrue(capturedRequest?.branchContent.orEmpty().contains("again"))
        val summaryEntry = state.ui.activeConversation.entries.filterIsInstance<ConversationEntry.BranchSummary>().single()
        assertEquals("模型生成的摘要", summaryEntry.summary)
        assertEquals(20L, summaryEntry.inputTokens)
        assertEquals(8L, summaryEntry.outputTokens)
    }

    /** 摘要请求失败时不得移动 leaf，也不能写入半成品摘要条目。 */
    @Test
    fun `summary failure leaves tree untouched`() = runTest(dispatcher) {
        val state = state { error("服务不可用") }
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        val result = state.conversationTreeController.navigateToEntry(
            source.id,
            "sibling",
            BranchNavigationSummary(BranchSummaryMode.AUTOMATIC),
        )

        assertFalse(result.succeeded)
        assertEquals(source.activeEntryId, state.ui.activeConversation.activeEntryId)
        assertEquals(source.entries, state.ui.activeConversation.entries)
    }

    /** 摘要协程取消时传播取消信号，并始终释放摘要互斥状态。 */
    @Test
    fun `summary cancellation leaves tree untouched and releases guard`() = runTest(dispatcher) {
        val state = state { throw CancellationException("cancelled") }
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)

        assertFailsWith<CancellationException> {
            state.conversationTreeController.navigateToEntry(
                source.id,
                "sibling",
                BranchNavigationSummary(BranchSummaryMode.AUTOMATIC),
            )
        }

        assertEquals(source.activeEntryId, state.ui.activeConversation.activeEntryId)
        assertFalse(state.conversationTreeController.summaryInProgress)
    }

    /** 一个摘要尚未完成时，第二次树导航必须被互斥状态拒绝。 */
    @Test
    fun `navigation is rejected while branch summary is running`() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val state = state {
            started.complete(Unit)
            release.await()
            GeneratedBranchSummary("摘要")
        }
        val source = treeConversation("source")
        state.ui = state.ui.copy(tasks = listOf(source), activeTaskId = source.id)
        val first = async {
            state.conversationTreeController.navigateToEntry(
                source.id,
                "sibling",
                BranchNavigationSummary(BranchSummaryMode.AUTOMATIC),
            )
        }
        started.await()

        val second = state.conversationTreeController.navigateToEntry(source.id, "assistant-1")

        assertFalse(second.succeeded)
        release.complete(Unit)
        assertTrue(first.await().succeeded)
    }

    /** 归档级联到全部后代，恢复只作用于所选节点，删除父节点会将直接子节点提升为根。 */
    @Test
    fun `archive restore and delete follow subtree lifecycle rules`() = runTest(dispatcher) {
        val state = state()
        val root = treeConversation("root")
        val child = treeConversation("child").copy(parentConversationId = root.id)
        val grandchild = treeConversation("grandchild").copy(parentConversationId = child.id)
        val fallback = treeConversation("fallback").copy(updatedAt = 500L)
        state.ui = state.ui.copy(
            tasks = listOf(root, child, grandchild, fallback),
            activeTaskId = root.id,
        )

        val archived = state.conversationTreeController.archiveConversation(root.id)

        assertTrue(archived.succeeded)
        assertTrue(state.findConversation(root.id).archivedAt != null)
        assertTrue(state.findConversation(child.id).archivedAt != null)
        assertTrue(state.findConversation(grandchild.id).archivedAt != null)
        assertEquals(fallback.id, state.ui.activeTaskId)

        state.conversationTreeController.restoreConversation(child.id)
        assertNull(state.findConversation(child.id).archivedAt)
        assertEquals(child.id, state.ui.workspaceTaskSections.single().roots.first { it.task.id == child.id }.task.id)

        val deleted = state.conversationTreeController.deleteConversation(root.id)
        assertTrue(deleted.succeeded)
        assertNull(state.findConversationOrNull(root.id))
        assertNull(state.findConversation(child.id).parentConversationId)
    }

    /** 当前活动会话即使空闲也不可永久删除。 */
    @Test
    fun `active conversation cannot be deleted`() = runTest(dispatcher) {
        val state = state()
        val activeId = state.ui.activeTaskId

        val result = state.conversationTreeController.deleteConversation(activeId)

        assertFalse(result.succeeded)
        assertEquals(
            "当前活动会话不能删除，请先切换到其他会话。",
            state.conversationTreeController.deleteBlockReason(activeId),
        )
        assertTrue(state.findConversationOrNull(activeId) != null)
    }

    /** 子树中任一节点运行或等待交互时，归档入口必须拒绝整棵子树。 */
    @Test
    fun `running descendant blocks subtree archive`() = runTest(dispatcher) {
        val state = state()
        val root = treeConversation("root")
        val runningChild = treeConversation("child").copy(
            parentConversationId = root.id,
            executionState = com.agent.shared.chat.model.ExecutionState.Running,
        )
        val fallback = treeConversation("fallback")
        state.ui = state.ui.copy(tasks = listOf(root, runningChild, fallback), activeTaskId = fallback.id)

        val result = state.conversationTreeController.archiveConversation(root.id)

        assertFalse(result.succeeded)
        assertNull(state.findConversation(root.id).archivedAt)
        assertNull(state.findConversation(runningChild.id).archivedAt)
    }

    /** 后台协程仍占有运行槽时，即使展示状态已空闲也不能永久删除会话。 */
    @Test
    fun `run ownership blocks deletion during cancellation cleanup`() = runTest(dispatcher) {
        val state = state()
        val source = treeConversation("source")
        val fallback = treeConversation("fallback")
        state.ui = state.ui.copy(tasks = listOf(source, fallback), activeTaskId = fallback.id)
        state.activeRunConversationId = source.id

        val result = state.conversationTreeController.deleteConversation(source.id)

        assertFalse(result.succeeded)
        assertTrue(state.findConversationOrNull(source.id) != null)
    }

    /** 创建带一条兄弟分支和第二轮用户输入的树会话。 */
    private fun treeConversation(id: String): ChatConversationUiState {
        val root = message("user-1", null, ChatRole.User, "first")
        val assistant = message("assistant-1", root.id, ChatRole.Assistant, "answer")
        val secondUser = ConversationEntry.Message(
            id = "user-2",
            parentId = assistant.id,
            createdAt = 3L,
            message = ChatMessage(ChatRole.User, "again"),
            inputParts = listOf(
                UserInputPart.Text("again "),
                UserInputPart.FileSnapshot("D:/workspace/input.txt", "content"),
            ),
        )
        val sibling = message("sibling", root.id, ChatRole.Assistant, "sibling")
        return ChatConversationUiState(
            id = id,
            title = id,
            workspacePath = "D:/workspace",
            treeFormatVersion = CURRENT_CONVERSATION_TREE_FORMAT_VERSION,
            entries = listOf(root, assistant, secondUser, sibling),
            activeEntryId = secondUser.id,
            headEntryId = secondUser.id,
            reasoningEffort = ReasoningEffort.MEDIUM,
            updatedAt = 100L,
        ).withEntryProjection()
    }

    /** 创建条目树测试消息。 */
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
        inputParts = if (role == ChatRole.User) listOf(UserInputPart.Text(content)) else emptyList(),
    )

    /** 创建使用受控 gateway 的窗口状态。 */
    private fun state(
        branchSummaryGenerator: BranchSummaryGenerator = BranchSummaryGenerator { request ->
            GeneratedBranchSummary("Summary of ${request.branchContent}")
        },
    ): ChatWindowState = ChatWindowState(
        resourceDispatcher = dispatcher,
        sendMessageUseCase = SendMessageUseCase(idleGateway()),
        snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
        projectPath = "D:/workspace",
        branchSummaryGenerator = branchSummaryGenerator,
    )

    /** 派生标题在长度受限时仍保留操作后缀。 */
    @Test
    fun `derived titles preserve operation suffix`() {
        assertEquals("source - fork", derivedConversationTitle("source", "fork"))
        assertTrue(derivedConversationTitle("abcdefghijklmnopqrstuvwxyz", "clone").endsWith(" - clone"))
        assertEquals(CONVERSATION_TITLE_MAX_LENGTH, derivedConversationTitle("abcdefghijklmnopqrstuvwxyz", "clone").length)
    }
}
