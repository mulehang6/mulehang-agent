package com.agent.app.chat.state

import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.chat.attention.ConversationAttentionEvent
import com.agent.shared.chat.attention.ConversationAttentionRepository
import com.agent.shared.chat.attention.ConversationAttentionType
import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.persistence.DesktopPersistenceDatabase
import com.agent.shared.session.AppSessionSnapshot
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** 验证后台提醒、前台抑制和终态查看后的侧栏规则。 */
class ChatAttentionControllerTest : ChatWindowTestFixture() {
    /** 后台完成提醒在进入会话后清除，行动提醒在查看后仍保留。 */
    @Test
    fun `background events notify and viewed terminal status disappears`() = runTest(dispatcher) {
        val path = Files.createTempDirectory("mulehang-attention-ui").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(task()))
            val delivered = mutableListOf<ConversationAttentionEvent>()
            val state = ChatWindowState(
                sendMessageUseCase = SendMessageUseCase(idleGateway()),
                snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
                projectPath = "D:/workspace",
                attentionRepository = ConversationAttentionRepository(database),
                onAttentionNotification = delivered::add,
                resourceDispatcher = dispatcher,
            )
            state.restoreTasks(listOf(ChatConversationUiState("conversation", "测试", workspacePath = "D:/workspace")))
            state.runController.applyAgentEvent("conversation", AgentStreamEvent.Completed("完成"))
            assertEquals(1, delivered.size)
            assertEquals(ChatTaskStatus.DONE, taskStatusFor(state.findConversation("conversation")))

            state.selectConversation("conversation")
            assertEquals(ChatTaskStatus.NONE, taskStatusFor(state.findConversation("conversation")))
            state.runController.applyAgentEvent("conversation", AgentStreamEvent.Completed("再次完成"))
            assertEquals(1, delivered.size)
            assertEquals(ChatTaskStatus.NONE, taskStatusFor(state.findConversation("conversation")))
            state.attentionController.setWindowVisibleAndFocused(false)
            state.runController.applyAgentEvent("conversation", AgentStreamEvent.Failed("失败"))
            assertEquals(2, delivered.size)
            state.attentionController.setWindowVisibleAndFocused(true)
            assertEquals(ChatTaskStatus.NONE, taskStatusFor(state.findConversation("conversation")))
        }
    }

    /** 前台收到终态无需系统提醒，未处理的问题保持最高优先级。 */
    @Test
    fun `visible completion stays quiet and action has priority`() = runTest(dispatcher) {
        val completed = ConversationAttentionEvent(
            "complete", "conversation", null, ConversationAttentionType.COMPLETED,
            "完成", "结果", false, null, 1L,
        )
        val question = completed.copy(id = "question", type = ConversationAttentionType.QUESTION)
        val conversation = ChatConversationUiState(
            id = "conversation", title = "测试", workspacePath = "D:/workspace",
            attentionEvents = listOf(completed, question),
        )
        assertEquals(ChatTaskStatus.WAITING, taskStatusFor(conversation))
    }

    /** 关注事件需引用真实会话。 */
    private fun task() = PersistedTask(
        id = "conversation", title = "测试", workspacePath = "D:/workspace",
        reasoningEffort = "MEDIUM", contextUsageFraction = 0f,
        executionState = "IDLE", executionErrorTitle = null, executionErrorMessage = null,
        attachmentsJson = "[]", timeline = emptyList(), history = emptyList(),
    )
}
