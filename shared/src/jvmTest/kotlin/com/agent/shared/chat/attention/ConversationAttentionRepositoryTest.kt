package com.agent.shared.chat.attention

import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** 验证查看与完成行动各自的持久化清除规则。 */
class ConversationAttentionRepositoryTest {
    /** 查看只清除完成和失败，回答后才清除行动提醒。 */
    @Test
    fun `viewing clears terminal events but keeps unresolved actions`() = runTest {
        val path = Files.createTempDirectory("mulehang-attention").resolve("mulehang.db")
        val approvalId: String
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(task()))
            val repository = ConversationAttentionRepository(database)
            approvalId = repository.create("conversation", "entry-a", ConversationAttentionType.APPROVAL, "审批", "请求").id
            repository.create("conversation", "entry-b", ConversationAttentionType.COMPLETED, "完成", "结果")
            repository.markViewed("conversation")
            assertEquals(listOf(ConversationAttentionType.APPROVAL), repository.unread().map { it.type })
        }
        DesktopPersistenceDatabase.open(path).use { database ->
            val repository = ConversationAttentionRepository(database)
            assertEquals("entry-a", repository.unread().single().entryId)
            repository.resolve(approvalId)
            assertEquals(emptyList(), repository.unread())
        }
    }

    /** 外键引用所需的最小持久会话。 */
    private fun task() = PersistedTask(
        id = "conversation",
        title = "测试",
        workspacePath = "D:/workspace",
        reasoningEffort = "MEDIUM",
        contextUsageFraction = 0f,
        executionState = "IDLE",
        executionErrorTitle = null,
        executionErrorMessage = null,
        attachmentsJson = "[]",
        timeline = emptyList(),
        history = emptyList(),
    )
}
