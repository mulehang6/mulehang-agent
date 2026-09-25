package com.agent.app.chat.state

import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.persistence.DesktopPersistenceDatabase
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.tool.interaction.InteractionRequestRepository
import com.agent.shared.tool.model.QuestionPrompt
import com.agent.shared.tool.model.QuestionRequest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** 重开窗口时从关系表恢复可回答的挂起卡片。 */
class ChatPendingInteractionRecoveryTest : ChatWindowTestFixture() {
    /** 回答先写入数据库，随后才安排原轮次继续。 */
    @Test
    fun `restores question card and saves answer before resuming`() = runTest(dispatcher) {
        val path = Files.createTempDirectory("mulehang-pending-card").resolve("db.sqlite")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(PersistedTask(
                id = "conversation", title = "测试", workspacePath = "D:/workspace",
                reasoningEffort = "MEDIUM", contextUsageFraction = 0f,
                executionState = "INTERRUPTED", executionErrorTitle = null,
                executionErrorMessage = null, attachmentsJson = "[]",
                timeline = emptyList(), history = emptyList(),
            )))
            database.write { queries ->
                queries.upsertAgentRun("run", "conversation", "turn", "INTERRUPTED", "model", "strategy", "tools", 1, 1, null, null)
            }
            val repository = InteractionRequestRepository(database)
            repository.recordQuestion("conversation", "run", QuestionRequest(
                requestId = "question", toolCallId = "ask_user",
                questions = listOf(QuestionPrompt("选择范围", listOf("当前", "全部"))),
            ))
            val state = ChatWindowState(
                sendMessageUseCase = SendMessageUseCase(idleGateway()),
                snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
                projectPath = "D:/workspace",
                interactionRequestRepository = repository,
                resourceDispatcher = dispatcher,
            )
            state.restoreTasks(listOf(ChatConversationUiState(
                id = "conversation", title = "测试", workspacePath = "D:/workspace",
                executionState = ExecutionState.Interrupted,
            )))
            val restored = state.findConversation("conversation")
            assertEquals("选择范围", assertNotNull(restored.pendingQuestion).effectiveQuestions.single().question)
            state.selectConversation("conversation")
            state.answerPendingQuestion("当前")
            assertTrue(repository.hasUnconsumedAnswerForTool("run", "ask_user"))
        }
    }
}
