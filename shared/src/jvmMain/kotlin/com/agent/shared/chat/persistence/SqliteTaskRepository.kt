package com.agent.shared.chat.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 使用本机 SQLite 文件保存完整任务快照的仓库。
 */
class SqliteTaskRepository(
    private val databasePath: Path,
) : TaskRepository {
    /**
     * 读取按最近更新时间排序的全部任务及其关联内容。
     */
    override suspend fun loadAll(): List<PersistedTask> = withContext(Dispatchers.IO) {
        openConnection().use { connection ->
            connection.prepareStatement(
                """
                SELECT id, title, workspace_path, reasoning_effort, profile_id, permission_preset,
                    context_usage_fraction, execution_state, execution_error_title, execution_error_message, attachments_json
                FROM task
                ORDER BY updated_at DESC, id ASC
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            val taskId = resultSet.getString("id")
                            add(
                                PersistedTask(
                                    id = taskId,
                                    title = resultSet.getString("title"),
                                    workspacePath = resultSet.getString("workspace_path"),
                                    reasoningEffort = resultSet.getString("reasoning_effort"),
                                    profileId = resultSet.getString("profile_id"),
                                    permissionPreset = resultSet.getString("permission_preset"),
                                    contextUsageFraction = resultSet.getFloat("context_usage_fraction"),
                                    executionState = resultSet.getString("execution_state"),
                                    executionErrorTitle = resultSet.getString("execution_error_title"),
                                    executionErrorMessage = resultSet.getString("execution_error_message"),
                                    attachmentsJson = resultSet.getString("attachments_json"),
                                    timeline = loadTimeline(connection, taskId),
                                    history = loadHistory(connection, taskId),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 以单个事务替换数据库中的任务集合，保证删除和新增不会留下孤立负载。
     */
    override suspend fun saveAll(tasks: List<PersistedTask>) = withContext(Dispatchers.IO) {
        openConnection().use { connection ->
            connection.inTransaction {
                createStatement().use { statement ->
                    statement.executeUpdate("DELETE FROM task")
                }
                tasks.forEach { task ->
                    insertTask(this, task)
                    insertTimeline(this, task)
                    insertHistory(this, task)
                }
            }
        }
    }

    /**
     * 删除一个任务，SQLite 外键会级联清理时间线和 history。
     */
    override suspend fun delete(taskId: String) {
        withContext(Dispatchers.IO) {
        openConnection().use { connection ->
            connection.prepareStatement("DELETE FROM task WHERE id = ?").use { statement ->
                statement.setString(1, taskId)
                statement.executeUpdate()
            }
        }
        }
    }

    /**
     * 打开配置完成且已迁移的 SQLite 连接。
     */
    private fun openConnection(): Connection {
        databasePath.parent?.let(Files::createDirectories)
        val connection = DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}")
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA journal_mode = WAL")
        }
        migrate(connection)
        return connection
    }

    /**
     * 应用当前 SQLite schema 的第一版迁移。
     */
    private fun migrate(connection: Connection) {
        connection.inTransaction {
            createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS schema_migration (
                        version INTEGER PRIMARY KEY,
                        applied_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
            if (!isMigrationApplied(this, INITIAL_SCHEMA_VERSION)) {
                createStatement().use { statement ->
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
                    statement.executeUpdate("CREATE INDEX task_workspace_updated_idx ON task(workspace_path, updated_at DESC)")
                }
                recordMigration(this, INITIAL_SCHEMA_VERSION)
            }
            if (!isMigrationApplied(this, SESSION_PREFERENCES_SCHEMA_VERSION)) {
                createStatement().use { statement ->
                    statement.executeUpdate("ALTER TABLE task ADD COLUMN profile_id TEXT")
                    statement.executeUpdate("ALTER TABLE task ADD COLUMN permission_preset TEXT NOT NULL DEFAULT 'DEFAULT'")
                }
                recordMigration(this, SESSION_PREFERENCES_SCHEMA_VERSION)
            }
        }
    }

    /**
     * 判断迁移版本是否已经成功写入迁移日志。
     */
    private fun isMigrationApplied(connection: Connection, version: Int): Boolean =
        connection.prepareStatement("SELECT 1 FROM schema_migration WHERE version = ?").use { statement ->
            statement.setInt(1, version)
            statement.executeQuery().use { resultSet -> resultSet.next() }
        }

    private fun recordMigration(connection: Connection, version: Int) {
        connection.prepareStatement("INSERT INTO schema_migration(version, applied_at) VALUES (?, ?)").use { statement ->
            statement.setInt(1, version)
            statement.setLong(2, System.currentTimeMillis())
            statement.executeUpdate()
        }
    }

    /**
     * 查询单个任务的有序时间线。
     */
    private fun loadTimeline(connection: Connection, taskId: String): List<PersistedTimelineItem> =
        connection.prepareStatement(
            "SELECT sequence, type, payload_json FROM task_timeline_item WHERE task_id = ? ORDER BY sequence ASC",
        ).use { statement ->
            statement.setString(1, taskId)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            PersistedTimelineItem(
                                sequence = resultSet.getInt("sequence"),
                                type = resultSet.getString("type"),
                                payloadJson = resultSet.getString("payload_json"),
                            ),
                        )
                    }
                }
            }
        }

    /**
     * 查询单个任务的有序 Agent history。
     */
    private fun loadHistory(connection: Connection, taskId: String): List<PersistedHistoryItem> =
        connection.prepareStatement(
            "SELECT sequence, type, payload_json FROM task_history_item WHERE task_id = ? ORDER BY sequence ASC",
        ).use { statement ->
            statement.setString(1, taskId)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            PersistedHistoryItem(
                                sequence = resultSet.getInt("sequence"),
                                type = resultSet.getString("type"),
                                payloadJson = resultSet.getString("payload_json"),
                            ),
                        )
                    }
                }
            }
        }

    /**
     * 插入任务的可查询元数据。
     */
    private fun insertTask(connection: Connection, task: PersistedTask) {
        val now = System.currentTimeMillis()
        connection.prepareStatement(
            """
            INSERT INTO task(
                id, title, workspace_path, reasoning_effort, profile_id, permission_preset,
                context_usage_fraction, execution_state, execution_error_title, execution_error_message,
                attachments_json, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, task.id)
            statement.setString(2, task.title)
            statement.setString(3, task.workspacePath)
            statement.setString(4, task.reasoningEffort)
            statement.setString(5, task.profileId)
            statement.setString(6, task.permissionPreset)
            statement.setFloat(7, task.contextUsageFraction)
            statement.setString(8, task.executionState)
            statement.setString(9, task.executionErrorTitle)
            statement.setString(10, task.executionErrorMessage)
            statement.setString(11, task.attachmentsJson)
            statement.setLong(12, now)
            statement.setLong(13, now)
            statement.executeUpdate()
        }
    }

    /**
     * 插入任务时间线的全部有序负载。
     */
    private fun insertTimeline(connection: Connection, task: PersistedTask) {
        connection.prepareStatement(
            "INSERT INTO task_timeline_item(task_id, sequence, type, payload_json) VALUES (?, ?, ?, ?)",
        ).use { statement ->
            task.timeline.sortedBy(PersistedTimelineItem::sequence).forEach { item ->
                statement.setString(1, task.id)
                statement.setInt(2, item.sequence)
                statement.setString(3, item.type)
                statement.setString(4, item.payloadJson)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /**
     * 插入任务 Agent history 的全部有序负载。
     */
    private fun insertHistory(connection: Connection, task: PersistedTask) {
        connection.prepareStatement(
            "INSERT INTO task_history_item(task_id, sequence, type, payload_json) VALUES (?, ?, ?, ?)",
        ).use { statement ->
            task.history.sortedBy(PersistedHistoryItem::sequence).forEach { item ->
                statement.setString(1, task.id)
                statement.setInt(2, item.sequence)
                statement.setString(3, item.type)
                statement.setString(4, item.payloadJson)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /**
     * 在块失败时回滚，在成功时提交当前事务。
     */
    private inline fun <T> Connection.inTransaction(block: Connection.() -> T): T {
        val previousAutoCommit = autoCommit
        autoCommit = false
        return try {
            block().also { commit() }
        } catch (exception: Exception) {
            rollback()
            throw exception
        } finally {
            autoCommit = previousAutoCommit
        }
    }

    private companion object {
        const val INITIAL_SCHEMA_VERSION = 1
        const val SESSION_PREFERENCES_SCHEMA_VERSION = 2
    }
}
