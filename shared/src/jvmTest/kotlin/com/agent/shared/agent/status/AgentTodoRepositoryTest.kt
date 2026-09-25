package com.agent.shared.agent.status

import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

/** 验证 TODO 的跨轮持久化和原子状态变更。 */
class AgentTodoRepositoryTest {
    /** 重写和单项状态变更重开数据库后仍可读取。 */
    @Test
    fun `todo changes persist across database reopening`() = runTest {
        val path = Files.createTempDirectory("mulehang-todos").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(task()))
            val repository = AgentTodoRepository(database)
            repository.rewrite("conversation", listOf(
                AgentTodoDraft("inspect", "检查现状", AgentTodoStatus.IN_PROGRESS),
                AgentTodoDraft("finish", "完成实现", AgentTodoStatus.PENDING),
            ))
            repository.updateStatus("conversation", "inspect", AgentTodoStatus.COMPLETED)
            assertEquals(2, repository.list("conversation").size)
        }
        DesktopPersistenceDatabase.open(path).use { database ->
            assertEquals(
                listOf(AgentTodoStatus.COMPLETED, AgentTodoStatus.PENDING),
                AgentTodoRepository(database).list("conversation").map(AgentTodoItem::status),
            )
        }
    }

    /** 无效重写与未知 ID 不能损坏已有清单。 */
    @Test
    fun `invalid updates leave current todos intact`() = runTest {
        val path = Files.createTempDirectory("mulehang-todos-invalid").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(task()))
            val repository = AgentTodoRepository(database)
            val original = listOf(AgentTodoDraft("one", "保留", AgentTodoStatus.PENDING))
            repository.rewrite("conversation", original)

            assertFailsWith<IllegalArgumentException> {
                repository.rewrite("conversation", original + original)
            }
            assertFailsWith<IllegalArgumentException> {
                repository.updateStatus("conversation", "missing", AgentTodoStatus.COMPLETED)
            }
            assertEquals(listOf("one"), repository.list("conversation").map(AgentTodoItem::id))
        }
    }

    /** 创建带外键父行的最小会话。 */
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
