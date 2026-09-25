package com.agent.shared.tool.interaction

import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.persistence.DesktopPersistenceDatabase
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.QuestionPrompt
import com.agent.shared.tool.model.QuestionRequest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** 验证交互答复跨进程保存且只能被恢复的工具消费一次。 */
class InteractionRequestRepositoryTest {
    /** 重新打开数据库后，用内容签名匹配新建工具实例的随机请求 ID。 */
    @Test
    fun `question answer survives restart and is consumed once`() = runTest {
        val path = Files.createTempDirectory("mulehang-interaction").resolve("db.sqlite")
        val request = QuestionRequest("first", "ask_user", listOf(QuestionPrompt("选择范围", listOf("当前", "全部"))))
        DesktopPersistenceDatabase.open(path).use { database ->
            prepareRun(database)
            val repository = InteractionRequestRepository(database)
            repository.recordQuestion("conversation", "run", request)
            assertIs<SavedInteractionRequest.Question>(repository.pending("conversation"))
            assertTrue(repository.answer("first", "当前"))
            assertTrue(repository.hasUnconsumedAnswerForTool("run", "ask_user"))
        }
        DesktopPersistenceDatabase.open(path).use { database ->
            val repository = InteractionRequestRepository(database)
            assertEquals("当前", repository.claimQuestion("run", request.copy(requestId = "second")))
            assertNull(repository.claimQuestion("run", request.copy(requestId = "third")))
            assertFalse(repository.hasUnconsumedAnswerForTool("run", "ask_user"))
        }
    }

    /** 审批拒绝按同样规则恢复，并且不会被误当作批准。 */
    @Test
    fun `approval rejection replays as false`() = runTest {
        val path = Files.createTempDirectory("mulehang-approval").resolve("db.sqlite")
        DesktopPersistenceDatabase.open(path).use { database ->
            prepareRun(database)
            val repository = InteractionRequestRepository(database)
            val request = ApprovalRequest("first", "apply_patch", "写入文件", targetPath = "src/main.kt")
            repository.recordApproval("conversation", "run", request)
            assertIs<SavedInteractionRequest.Approval>(repository.pending("conversation"))
            assertTrue(repository.answer("first", "false"))
            assertEquals(false, repository.claimApproval("run", request.copy(requestId = "second")))
            assertNull(repository.claimApproval("run", request.copy(requestId = "third")))
        }
    }

    /** 建立外键引用所需的最小会话和运行。 */
    private suspend fun prepareRun(database: DesktopPersistenceDatabase) {
        SqliteTaskRepository(database).saveAll(listOf(PersistedTask(
            id = "conversation",
            title = "测试",
            workspacePath = "D:/workspace",
            reasoningEffort = "MEDIUM",
            contextUsageFraction = 0f,
            executionState = "INTERRUPTED",
            executionErrorTitle = null,
            executionErrorMessage = null,
            attachmentsJson = "[]",
            timeline = emptyList(),
            history = emptyList(),
        )))
        database.write { queries ->
            queries.upsertAgentRun("run", "conversation", "turn", "INTERRUPTED", "model", "strategy", "tools", 1, 1, null, null)
        }
    }
}
