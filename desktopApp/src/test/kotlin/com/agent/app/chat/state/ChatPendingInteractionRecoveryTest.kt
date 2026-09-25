package com.agent.app.chat.state

import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentGateway
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.koog.AgentRunRecoveryRepository
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 重开窗口时从关系表恢复可回答的挂起卡片。 */
@OptIn(ExperimentalCoroutinesApi::class)
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

    /** 旧版线性会话没有树条目 ID，恢复时应沿用持久运行记录里的用户轮次 ID。 */
    @Test
    fun `legacy linear conversation resumes with persisted user entry id`() = runTest(dispatcher) {
        val path = Files.createTempDirectory("mulehang-legacy-run").resolve("db.sqlite")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(PersistedTask(
                id = "conversation", title = "旧任务", workspacePath = "D:/workspace",
                reasoningEffort = "MEDIUM", contextUsageFraction = 0f,
                executionState = "INTERRUPTED", executionErrorTitle = null,
                executionErrorMessage = null, attachmentsJson = "[]",
                timeline = emptyList(), history = emptyList(),
            )))
            database.write { queries ->
                queries.upsertAgentRun(
                    "old-run", "conversation", "legacy-turn", "INTERRUPTED", "model", "strategy", "tools", 1, 1, null, null,
                )
            }
            var resumedRequest: AgentRunRequest? = null
            val gateway = object : AgentGateway {
                override fun run(request: AgentRunRequest): Flow<AgentStreamEvent> {
                    resumedRequest = request
                    return flowOf(AgentStreamEvent.Started, AgentStreamEvent.Completed("done"))
                }
            }
            val profile = profile()
            val state = ChatWindowState(
                sendMessageUseCase = SendMessageUseCase(gateway),
                snapshot = AppSessionSnapshot(profiles = listOf(profile), activeProfile = profile),
                projectPath = "D:/workspace",
                recoveryRepository = AgentRunRecoveryRepository(database),
                resourceDispatcher = dispatcher,
            )
            state.restoreTasks(listOf(ChatConversationUiState(
                id = "conversation",
                title = "旧任务",
                workspacePath = "D:/workspace",
                history = listOf(AgentConversationHistoryMessage.User("继续旧任务")),
                treeFormatVersion = 0,
                executionState = ExecutionState.Interrupted,
            )))
            state.selectConversation("conversation")

            state.resumeActiveRun()
            advanceUntilIdle()

            assertEquals("legacy-turn", resumedRequest?.userEntryId)
            assertEquals("继续旧任务", resumedRequest?.prompt)
        }
    }
}
