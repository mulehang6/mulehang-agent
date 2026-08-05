package com.agent.shared.chat.persistence

import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * 验证 SQLite 任务仓库对完整持久化快照的读写语义。
 */
class SqliteTaskRepositoryTest {
    private val databaseDirectory = Files.createTempDirectory("mulehang-task-repository-test")

    /**
     * 删除临时数据库目录，避免测试污染用户目录。
     */
    @AfterTest
    @OptIn(ExperimentalPathApi::class)
    fun tearDown() {
        databaseDirectory.deleteRecursively()
    }

    /**
     * 原始 reasoning 与完整工具结果在数据库往返后必须逐字段保留。
     */
    @Test
    fun `should round trip raw reasoning and complete tool output`() = runTest {
        val repository = SqliteTaskRepository(databaseDirectory.resolve("tasks.db"))
        val expected = taskSnapshot()

        repository.saveAll(listOf(expected))

        assertEquals(listOf(expected), repository.loadAll())
    }

    /**
     * 删除任务必须级联删除它的时间线与 Agent history，避免留下不可见敏感数据。
     */
    @Test
    fun `should cascade delete task timeline and history`() = runTest {
        val repository = SqliteTaskRepository(databaseDirectory.resolve("tasks.db"))
        repository.saveAll(listOf(taskSnapshot()))

        repository.delete("task-1")

        assertTrue(repository.loadAll().isEmpty())
    }

    /**
     * updated_at 时间戳必须随快照往返，且加载按最近更新倒序返回。
     */
    @Test
    fun `should round trip updated at and load newest first`() = runTest {
        val repository = SqliteTaskRepository(databaseDirectory.resolve("tasks.db"))
        val older = taskSnapshot().copy(id = "task-old", updatedAt = 100L)
        val newer = taskSnapshot().copy(id = "task-new", updatedAt = 300L)

        repository.saveAll(listOf(older, newer))

        assertEquals(listOf(newer, older), repository.loadAll())
    }

    /**
     * 会话绑定的非默认 profile 与权限档位也必须通过真实 SQLite 仓库完整往返。
     */
    @Test
    fun `should round trip non default profile id and permission preset`() = runTest {
        val repository = SqliteTaskRepository(databaseDirectory.resolve("tasks.db"))
        val expected = taskSnapshot().copy(
            id = "task-2",
            profileId = "deepseek:deepseek-v4-pro",
            permissionPreset = "BRAVE",
        )

        repository.saveAll(listOf(expected))

        assertEquals(listOf(expected), repository.loadAll())
    }

    /**
     * v1 遗留数据库缺少 profile_id/permission_preset 列时，打开仓库应自动迁移且不丢数据。
     */
    @Test
    fun `should migrate legacy v1 database and preserve existing rows`() = runTest {
        val databasePath = databaseDirectory.resolve("legacy.db")
        createLegacyV1Database(databasePath)

        val repository = SqliteTaskRepository(databasePath)
        val tasks = repository.loadAll()

        assertEquals(1, tasks.size)
        val migratedTask = tasks.single()
        assertEquals("task-legacy", migratedTask.id)
        assertEquals("旧版本任务", migratedTask.title)
        assertEquals(null, migratedTask.profileId)
        assertEquals("DEFAULT", migratedTask.permissionPreset)
    }

    /**
     * 手工建立不含 v2 列的 v1 schema 数据库，模拟迁移前遗留下来的真实数据文件。
     */
    private fun createLegacyV1Database(databasePath: java.nio.file.Path) {
        java.sql.DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE schema_migration (
                        version INTEGER PRIMARY KEY,
                        applied_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate("INSERT INTO schema_migration(version, applied_at) VALUES (1, 0)")
                statement.executeUpdate(
                    """
                    CREATE TABLE task (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        workspace_path TEXT NOT NULL,
                        reasoning_effort TEXT NOT NULL,
                        context_usage_fraction REAL NOT NULL,
                        execution_state TEXT NOT NULL,
                        execution_error_title TEXT,
                        execution_error_message TEXT,
                        attachments_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE task_timeline_item (
                        task_id TEXT NOT NULL REFERENCES task(id) ON DELETE CASCADE,
                        sequence INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        payload_json TEXT NOT NULL,
                        PRIMARY KEY (task_id, sequence)
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE task_history_item (
                        task_id TEXT NOT NULL REFERENCES task(id) ON DELETE CASCADE,
                        sequence INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        payload_json TEXT NOT NULL,
                        PRIMARY KEY (task_id, sequence)
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    INSERT INTO task(
                        id, title, workspace_path, reasoning_effort, context_usage_fraction,
                        execution_state, execution_error_title, execution_error_message,
                        attachments_json, created_at, updated_at
                    ) VALUES (
                        'task-legacy', '旧版本任务', 'D:\workspace', 'MEDIUM', 0.0,
                        'IDLE', NULL, NULL, '[]', 0, 0
                    )
                    """.trimIndent(),
                )
            }
        }
    }

    /**
     * 构造含完整原始负载的固定任务快照，不依赖被测仓库的实现细节。
     */
    private fun taskSnapshot(): PersistedTask = PersistedTask(
        id = "task-1",
        title = "持久化测试",
        workspacePath = "D:\\workspace",
        reasoningEffort = "HIGH",
        contextUsageFraction = 0.5f,
        executionState = "IDLE",
        executionErrorTitle = null,
        executionErrorMessage = null,
        attachmentsJson = "[{\"path\":\"D:/workspace/input.txt\",\"name\":\"input.txt\"}]",
        timeline = listOf(
            PersistedTimelineItem(
                sequence = 0,
                type = "reasoning",
                payloadJson = "{\"summaryText\":\"摘要\",\"rawText\":\"原始推理内容\"}",
            ),
            PersistedTimelineItem(
                sequence = 1,
                type = "tool_event",
                payloadJson = "{\"toolName\":\"run_powershell\",\"arguments\":\"Get-Content secret.txt\",\"resultDisplay\":\"完整工具输出\"}",
            ),
        ),
        history = listOf(
            PersistedHistoryItem(
                sequence = 0,
                type = "assistant",
                payloadJson = "{\"parts\":[{\"type\":\"reasoning\",\"rawText\":\"原始推理内容\"}]}",
            ),
        ),
    )
}
