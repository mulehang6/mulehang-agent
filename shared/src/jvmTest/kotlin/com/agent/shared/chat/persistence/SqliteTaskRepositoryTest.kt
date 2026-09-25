package com.agent.shared.chat.persistence

import com.agent.shared.agent.status.AgentTodoDraft
import com.agent.shared.agent.status.AgentTodoRepository
import com.agent.shared.agent.status.AgentTodoStatus
import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/** 验证 SQLDelight 会话仓库的新库语义。 */
class SqliteTaskRepositoryTest {
    /** 回退用户轮次时恢复发送前 TODO，删除本轮 Agent 新增的项目。 */
    @Test
    fun `user turn rollback restores previous todos`() = runTest {
        val databasePath = Files.createTempDirectory("mulehang-turn-todos").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(databasePath).use { database ->
            val repository = SqliteTaskRepository(database)
            val todos = AgentTodoRepository(database)
            val before = taskSnapshot().copy(id = "conversation", entries = taskSnapshot().entries.take(1))
            val after = before.copy(entries = taskSnapshot().entries)
            repository.saveAll(listOf(before))
            todos.rewrite(before.id, listOf(AgentTodoDraft("existing", "保留", AgentTodoStatus.IN_PROGRESS)))
            repository.saveUserTurn(listOf(after), before, after.id, "entry-2")
            todos.rewrite(after.id, listOf(AgentTodoDraft("new", "新项目", AgentTodoStatus.COMPLETED)))

            repository.rollbackUserTurn(after.id, "entry-2")

            assertEquals(listOf("existing"), todos.list(after.id).map { it.id })
            assertEquals(AgentTodoStatus.IN_PROGRESS, todos.list(after.id).single().status)
        }
    }

    /** 完整的会话元数据、负载和条目树应在数据库重开后原样恢复。 */
    @Test
    fun `should restore complete snapshots after reopening database`() = runTest {
        val databasePath = Files.createTempDirectory("mulehang-task-reopen-test").resolve("mulehang.db")
        val expected = taskSnapshot()
        DesktopPersistenceDatabase.open(databasePath).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(expected))
        }

        val restored = DesktopPersistenceDatabase.open(databasePath).use { database ->
            SqliteTaskRepository(database).loadAll()
        }

        assertEquals(listOf(expected), restored)
    }

    /** 删除父会话时子会话应提升为根，子会话负载不得被级联删除。 */
    @Test
    fun `should promote child conversation when deleting parent`() = runTest {
        val databasePath = Files.createTempDirectory("mulehang-task-parent-test").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(databasePath).use { database ->
            val repository = SqliteTaskRepository(database)
            val parent = taskSnapshot().copy(id = "parent", title = "父会话", updatedAt = 1L)
            val child = taskSnapshot().copy(
                id = "child",
                title = "子会话",
                parentConversationId = parent.id,
                updatedAt = 2L,
            )
            repository.saveAll(listOf(child, parent))

            repository.delete(parent.id)

            val restoredChild = repository.loadAll().single()
            assertEquals("child", restoredChild.id)
            assertNull(restoredChild.parentConversationId)
            assertEquals(child.timeline, restoredChild.timeline)
        }
    }

    /** 第一条消息回退后会话应消失，子会话则由外键提升为根。 */
    @Test
    fun `should remove new conversation on first user turn rollback`() = runTest {
        val databasePath = Files.createTempDirectory("mulehang-first-turn-test").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(databasePath).use { database ->
            val repository = SqliteTaskRepository(database)
            val parent = taskSnapshot().copy(id = "parent", parentConversationId = null)
            repository.saveUserTurn(listOf(parent), null, parent.id, "entry-1")
            val child = taskSnapshot().copy(
                id = "child",
                parentConversationId = parent.id,
                forkedFromEntryId = "entry-1",
            )
            repository.saveAll(listOf(parent, child))

            val snapshot = repository.rollbackUserTurn(parent.id, "entry-1")

            assertNull(snapshot?.before)
            assertEquals(listOf("child"), repository.loadAll().map(PersistedTask::id))
            assertNull(repository.loadAll().single().parentConversationId)
            assertNull(repository.rollbackUserTurn(parent.id, "entry-1"))
        }
    }

    /** 后续消息回退恢复旧主线，并保留从被移除消息派生出的独立子会话。 */
    @Test
    fun `should restore previous turn and promote detached child`() = runTest {
        val databasePath = Files.createTempDirectory("mulehang-later-turn-test").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(databasePath).use { database ->
            val repository = SqliteTaskRepository(database)
            val before = taskSnapshot().copy(id = "parent", entries = taskSnapshot().entries.take(1),
                activeEntryId = "entry-1", headEntryId = "entry-1")
            val after = before.copy(entries = taskSnapshot().entries, activeEntryId = "entry-2", headEntryId = "entry-2")
            repository.saveAll(listOf(before))
            repository.saveUserTurn(listOf(after), before, after.id, "entry-2")
            val child = taskSnapshot().copy(id = "child", parentConversationId = after.id, forkedFromEntryId = "entry-2")
            repository.saveAll(listOf(after, child))

            val snapshot = repository.rollbackUserTurn(after.id, "entry-2")
            val restored = repository.loadAll().associateBy(PersistedTask::id)

            assertEquals(before, snapshot?.before)
            assertEquals(before, restored[after.id])
            assertNull(restored[child.id]?.parentConversationId)
        }
    }

    /** 回退较早轮次时要清理其后的回退点，避免未来消息再次被错误恢复。 */
    @Test
    fun `should remove later user turn checkpoints`() = runTest {
        val databasePath = Files.createTempDirectory("mulehang-turn-order-test").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(databasePath).use { database ->
            val repository = SqliteTaskRepository(database)
            val beforeFirst = taskSnapshot().copy(
                id = "parent",
                entries = emptyList(),
                activeEntryId = null,
                headEntryId = null,
                timeline = emptyList(),
                history = emptyList(),
            )
            val first = taskSnapshot().copy(id = "parent", entries = taskSnapshot().entries.take(1),
                activeEntryId = "entry-1", headEntryId = "entry-1")
            val second = first.copy(entries = taskSnapshot().entries)
            repository.saveAll(listOf(beforeFirst))
            repository.saveUserTurn(listOf(first), beforeFirst, first.id, "entry-1")
            repository.saveUserTurn(listOf(second), first, second.id, "entry-2")

            repository.rollbackUserTurn(first.id, "entry-1")

            assertNull(repository.rollbackUserTurn(first.id, "entry-2"))
            assertEquals(beforeFirst, repository.loadAll().single())
        }
    }

    /** 保存快照失败时事务应回滚，不能留下半套时间线或会话行。 */
    @Test
    fun `should roll back failed write transaction`() {
        val databasePath = Files.createTempDirectory("mulehang-task-rollback-test").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(databasePath).use { database ->
            runCatching {
                database.write { queries ->
                    queries.upsertUiState("desktop", 1L, "{}", 1L)
                    error("模拟事务失败")
                }
            }

            val persisted = database.read { queries -> queries.selectUiState("desktop").executeAsOneOrNull() }
            assertNull(persisted)
        }
    }

    /** 同一数据库实例的并发写入应被串行化且最终文件仍可重开。 */
    @Test
    fun `should serialize concurrent snapshot writes`() = runTest {
        val databasePath = Files.createTempDirectory("mulehang-task-concurrency-test").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(databasePath).use { database ->
            val repository = SqliteTaskRepository(database)
            coroutineScope {
                repeat(8) { index ->
                    launch {
                        repository.saveAll(
                            listOf(taskSnapshot().copy(title = "版本 $index", updatedAt = index.toLong())),
                        )
                    }
                }
            }
        }

        DesktopPersistenceDatabase.open(databasePath).use { database ->
            val restored = SqliteTaskRepository(database).loadAll()
            assertEquals(1, restored.size)
            assertTrue(restored.single().title.startsWith("版本 "))
        }
    }

    /** 构造包含稳定字段、版本化 JSON 和条目图的固定会话。 */
    private fun taskSnapshot(): PersistedTask = PersistedTask(
        id = "task-1",
        title = "持久化测试",
        workspacePath = "D:\\workspace",
        workspaceName = "测试工作区",
        detachedWorkspacePath = "D:\\previous-workspace",
        detachedWorkspaceName = "旧工作区",
        parentConversationId = null,
        forkedFromEntryId = "entry-source",
        activeEntryId = "entry-2",
        headEntryId = "entry-2",
        archivedAt = 987L,
        treeFormatVersion = 1,
        reasoningEffort = "HIGH",
        profileId = "deepseek:deepseek-v4-pro",
        permissionPreset = "BRAVE",
        contextUsageFraction = 0.5f,
        executionState = "IDLE",
        executionErrorTitle = null,
        executionErrorMessage = null,
        attachmentsJson = "[{\"path\":\"D:/workspace/input.txt\",\"name\":\"input.txt\"}]",
        timeline = listOf(
            PersistedTimelineItem(0, "reasoning", "{\"summaryText\":\"摘要\"}"),
            PersistedTimelineItem(1, "tool_event", "{\"toolName\":\"run_powershell\"}"),
        ),
        history = listOf(
            PersistedHistoryItem(0, "assistant", "{\"parts\":[]}"),
        ),
        entries = listOf(
            PersistedTaskEntry("entry-1", null, 10L, "message", "{\"role\":\"USER\"}"),
            PersistedTaskEntry("entry-2", "entry-1", 20L, "label", "{\"label\":\"检查点\"}"),
        ),
        updatedAt = 42L,
    )
}
