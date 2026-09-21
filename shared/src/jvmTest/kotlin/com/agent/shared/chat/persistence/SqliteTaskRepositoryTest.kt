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
        val backupDirectory = databaseDirectory.resolve("tasks-backups")
        Files.createDirectories(backupDirectory)
        repeat(3) { index ->
            Files.writeString(backupDirectory.resolve("legacy-00000000000$index.db"), "previous backup")
        }

        val repository = SqliteTaskRepository(databasePath)
        val tasks = repository.loadAll()

        assertEquals(1, tasks.size)
        val migratedTask = tasks.single()
        assertEquals("task-legacy", migratedTask.id)
        assertEquals("旧版本任务", migratedTask.title)
        assertEquals(null, migratedTask.workspaceName)
        assertEquals(null, migratedTask.detachedWorkspacePath)
        assertEquals(null, migratedTask.detachedWorkspaceName)
        assertEquals(null, migratedTask.profileId)
        assertEquals("DEFAULT", migratedTask.permissionPreset)
        assertTrue(Files.isDirectory(backupDirectory))
        Files.list(backupDirectory).use { backups ->
            assertEquals(3L, backups.count())
        }
    }

    /**
     * v4 数据库升级到 v5 后必须保留旧任务，并为条目图字段提供兼容默认值。
     */
    @Test
    fun `should migrate v4 database to conversation tree schema`() = runTest {
        val databasePath = databaseDirectory.resolve("legacy-v4.db")
        createLegacyV4Database(databasePath)

        val migratedTask = SqliteTaskRepository(databasePath).loadAll().single()

        assertEquals("task-v4", migratedTask.id)
        assertEquals(null, migratedTask.parentConversationId)
        assertEquals(null, migratedTask.forkedFromEntryId)
        assertEquals(null, migratedTask.activeEntryId)
        assertEquals(null, migratedTask.headEntryId)
        assertEquals(null, migratedTask.archivedAt)
        assertEquals(0, migratedTask.treeFormatVersion)
        assertTrue(migratedTask.entries.isEmpty())
        Files.list(databaseDirectory.resolve("tasks-backups")).use { backups ->
            assertEquals(1L, backups.count())
        }
    }

    /** v5 树会话升级到 v6 时，持久末端从原活动 leaf 回填。 */
    @Test
    fun `should backfill head from active entry during v6 migration`() = runTest {
        val databasePath = databaseDirectory.resolve("legacy-v5.db")
        createLegacyV5Database(databasePath)

        val migratedTask = SqliteTaskRepository(databasePath).loadAll().single()

        assertEquals("entry-v5", migratedTask.activeEntryId)
        assertEquals("entry-v5", migratedTask.headEntryId)
        assertEquals(1, migratedTask.treeFormatVersion)
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

    /** 在既有 v4 固定数据上补齐 v5 结构，供 v6 head 回填测试使用。 */
    private fun createLegacyV5Database(databasePath: java.nio.file.Path) {
        createLegacyV4Database(databasePath)
        java.sql.DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("ALTER TABLE task ADD COLUMN parent_conversation_id TEXT")
                statement.executeUpdate("ALTER TABLE task ADD COLUMN forked_from_entry_id TEXT")
                statement.executeUpdate("ALTER TABLE task ADD COLUMN active_entry_id TEXT")
                statement.executeUpdate("ALTER TABLE task ADD COLUMN archived_at INTEGER")
                statement.executeUpdate("ALTER TABLE task ADD COLUMN tree_format_version INTEGER NOT NULL DEFAULT 0")
                statement.executeUpdate(
                    """
                    CREATE TABLE task_entry (
                        task_id TEXT NOT NULL REFERENCES task(id) ON DELETE CASCADE,
                        id TEXT NOT NULL,
                        parent_id TEXT,
                        created_at INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        payload_json TEXT NOT NULL,
                        PRIMARY KEY (task_id, id)
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate("CREATE INDEX task_entry_parent_idx ON task_entry(task_id, parent_id, created_at)")
                statement.executeUpdate("UPDATE task SET active_entry_id = 'entry-v5', tree_format_version = 1")
                statement.executeUpdate(
                    """
                    INSERT INTO task_entry(task_id, id, parent_id, created_at, type, payload_json)
                    VALUES ('task-v4', 'entry-v5', NULL, 1, 'message', '{"role":"USER","parts":[]}')
                    """.trimIndent(),
                )
                statement.executeUpdate("INSERT INTO schema_migration(version, applied_at) VALUES (5, 0)")
            }
        }
    }

    /**
     * 建立已完成前四版迁移的数据库，用于隔离验证 v5 条目图迁移。
     */
    private fun createLegacyV4Database(databasePath: java.nio.file.Path) {
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
                (1..4).forEach { version ->
                    statement.executeUpdate("INSERT INTO schema_migration(version, applied_at) VALUES ($version, 0)")
                }
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
                        updated_at INTEGER NOT NULL,
                        profile_id TEXT,
                        permission_preset TEXT NOT NULL DEFAULT 'DEFAULT',
                        workspace_name TEXT,
                        detached_workspace_path TEXT,
                        detached_workspace_name TEXT
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
                        attachments_json, created_at, updated_at, profile_id, permission_preset,
                        workspace_name, detached_workspace_path, detached_workspace_name
                    ) VALUES (
                        'task-v4', '第四版任务', 'D:\workspace', 'MEDIUM', 0.0,
                        'IDLE', NULL, NULL, '[]', 0, 40, NULL, 'DEFAULT',
                        '工作区', NULL, NULL
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
        workspaceName = "测试工作区",
        detachedWorkspacePath = "D:\\previous-workspace",
        detachedWorkspaceName = "旧工作区",
        parentConversationId = "task-parent",
        forkedFromEntryId = "entry-source",
        activeEntryId = "entry-2",
        headEntryId = "entry-2",
        archivedAt = 987L,
        treeFormatVersion = 1,
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
        entries = listOf(
            PersistedTaskEntry(
                id = "entry-1",
                parentId = null,
                createdAt = 10L,
                type = "message",
                payloadJson = "{\"role\":\"USER\",\"parts\":[{\"type\":\"text\",\"text\":\"开始\"}]}",
            ),
            PersistedTaskEntry(
                id = "entry-2",
                parentId = "entry-1",
                createdAt = 20L,
                type = "label",
                payloadJson = "{\"targetEntryId\":\"entry-1\",\"label\":\"检查点\"}",
            ),
        ),
    )
}
