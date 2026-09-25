package com.agent.shared.chat.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/** 只读旧版 SQLite 任务库，并转换为当前存储无关的会话快照。 */
internal object LegacyTaskDatabaseReader {
    private val taskFields = listOf(
        TaskField("id", "''"),
        TaskField("title", "''"),
        TaskField("workspace_path", "''"),
        TaskField("workspace_name"),
        TaskField("detached_workspace_path"),
        TaskField("detached_workspace_name"),
        TaskField("parent_conversation_id"),
        TaskField("forked_from_entry_id"),
        TaskField("active_entry_id"),
        TaskField("head_entry_id"),
        TaskField("archived_at"),
        TaskField("tree_format_version", "0"),
        TaskField("reasoning_effort", "'MEDIUM'"),
        TaskField("profile_id"),
        TaskField("permission_preset", "'DEFAULT'"),
        TaskField("context_usage_fraction", "0.0"),
        TaskField("execution_state", "'IDLE'"),
        TaskField("execution_error_title"),
        TaskField("execution_error_message"),
        TaskField("attachments_json", "'[]'"),
        TaskField("updated_at", "0"),
    )

    /** 数据库不存在或尚无旧任务表时返回 null；有效空库返回空列表。 */
    fun read(databasePath: Path): List<PersistedTask>? {
        if (!Files.isRegularFile(databasePath)) return null
        val absolutePath = databasePath.toAbsolutePath().normalize()
        return DriverManager.getConnection("jdbc:sqlite:$absolutePath").use { connection ->
            connection.createStatement().use { it.execute("PRAGMA query_only = ON") }
            if (!connection.hasTable("task")) {
                null
            } else {
                readTasks(connection)
            }
        }
    }

    /** 按旧版表结构读取会话与三组有序负载。 */
    private fun readTasks(connection: Connection): List<PersistedTask> {
        val columns = connection.tableColumns("task")
        val projections = taskFields.joinToString(", ") { field ->
            if (field.name in columns) field.name else "${field.fallback} AS ${field.name}"
        }
        val hasTimeline = connection.hasTable("task_timeline_item")
        val hasHistory = connection.hasTable("task_history_item")
        val hasEntries = connection.hasTable("task_entry")
        return connection.prepareStatement(
            "SELECT $projections FROM task ORDER BY updated_at DESC, id",
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val id = rows.getString("id").orEmpty()
                        if (id.isBlank()) continue
                        add(
                            PersistedTask(
                                id = id,
                                title = rows.getString("title").orEmpty(),
                                workspacePath = rows.getString("workspace_path").orEmpty(),
                                workspaceName = rows.getString("workspace_name"),
                                detachedWorkspacePath = rows.getString("detached_workspace_path"),
                                detachedWorkspaceName = rows.getString("detached_workspace_name"),
                                parentConversationId = rows.getString("parent_conversation_id"),
                                forkedFromEntryId = rows.getString("forked_from_entry_id"),
                                activeEntryId = rows.getString("active_entry_id"),
                                headEntryId = rows.getString("head_entry_id"),
                                archivedAt = rows.getNullableLong("archived_at"),
                                treeFormatVersion = rows.getInt("tree_format_version"),
                                reasoningEffort = rows.getString("reasoning_effort").orEmpty(),
                                profileId = rows.getString("profile_id"),
                                permissionPreset = rows.getString("permission_preset").orEmpty(),
                                contextUsageFraction = rows.getFloat("context_usage_fraction"),
                                executionState = rows.getString("execution_state").orEmpty(),
                                executionErrorTitle = rows.getString("execution_error_title"),
                                executionErrorMessage = rows.getString("execution_error_message"),
                                attachmentsJson = rows.getString("attachments_json").orEmpty(),
                                timeline = if (hasTimeline) connection.readTimeline(id) else emptyList(),
                                history = if (hasHistory) connection.readHistory(id) else emptyList(),
                                entries = if (hasEntries) connection.readEntries(id) else emptyList(),
                                updatedAt = rows.getLong("updated_at"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** 检查固定表名是否存在，表名仅由本类常量调用。 */
    private fun Connection.hasTable(tableName: String): Boolean = prepareStatement(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
    ).use { statement ->
        statement.setString(1, tableName)
        statement.executeQuery().use { it.next() }
    }

    /** 读取表的列名，兼容旧版任务表逐步增加的可选列。 */
    private fun Connection.tableColumns(tableName: String): Set<String> =
        createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($tableName)").use { rows ->
                buildSet {
                    while (rows.next()) add(rows.getString("name"))
                }
            }
        }

    /** 读取指定会话的时间线负载。 */
    private fun Connection.readTimeline(taskId: String): List<PersistedTimelineItem> = prepareStatement(
        "SELECT sequence, type, payload_json FROM task_timeline_item WHERE task_id = ? ORDER BY sequence",
    ).use { statement ->
                statement.setString(1, taskId)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                PersistedTimelineItem(
                                    sequence = rows.getInt("sequence"),
                                    type = rows.getString("type"),
                                    payloadJson = rows.getString("payload_json"),
                                ),
                            )
                        }
                    }
                }
    }

    /** 读取指定会话提供给 Agent 的历史负载。 */
    private fun Connection.readHistory(taskId: String): List<PersistedHistoryItem> = prepareStatement(
        "SELECT sequence, type, payload_json FROM task_history_item WHERE task_id = ? ORDER BY sequence",
    ).use { statement ->
                statement.setString(1, taskId)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                PersistedHistoryItem(
                                    sequence = rows.getInt("sequence"),
                                    type = rows.getString("type"),
                                    payloadJson = rows.getString("payload_json"),
                                ),
                            )
                        }
                    }
                }
    }

    /** 读取指定会话的分支条目图。 */
    private fun Connection.readEntries(taskId: String): List<PersistedTaskEntry> = prepareStatement(
        "SELECT id, parent_id, created_at, type, payload_json FROM task_entry WHERE task_id = ? ORDER BY created_at, id",
    ).use { statement ->
        statement.setString(1, taskId)
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) {
                    add(
                        PersistedTaskEntry(
                            id = rows.getString("id"),
                            parentId = rows.getString("parent_id"),
                            createdAt = rows.getLong("created_at"),
                            type = rows.getString("type"),
                            payloadJson = rows.getString("payload_json"),
                        ),
                    )
                }
            }
        }
    }

    /** 保留 SQL NULL 与时间戳 0 的区别。 */
    private fun java.sql.ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    /** 一个可能随旧版 schema 演进而尚不存在的列。 */
    private data class TaskField(val name: String, val fallback: String = "NULL")
}
