package com.agent.shared.chat.persistence

import com.agent.shared.agent.status.AgentTodoItem
import com.agent.shared.agent.status.AgentTodoStatus
import com.agent.shared.persistence.DesktopPersistenceDatabase
import com.agent.shared.persistence.db.Conversation
import com.agent.shared.persistence.db.MulehangDatabaseQueries
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 使用统一 SQLDelight 数据库保存完整会话快照的仓库。
 *
 * 快照更新只替换对应会话的时间线、历史与条目，不会误删运行检查点、TODO 或通知等关联数据。
 */
class SqliteTaskRepository(
    private val persistence: DesktopPersistenceDatabase,
) : TaskRepository {
    /** 为测试和独立调用保留的便捷构造函数。 */
    constructor(databasePath: Path) : this(DesktopPersistenceDatabase.open(databasePath))

    /** 将旧版 tasks.db 中尚未进入统一库的会话导入一次，并保留原数据库。 */
    suspend fun importLegacyDatabase(legacyDatabasePath: Path): Boolean = withContext(Dispatchers.IO) {
        val alreadyImported = persistence.read { queries ->
            queries.selectUiState(LEGACY_TASK_DATABASE_MIGRATION_KEY).executeAsOneOrNull() != null
        }
        if (alreadyImported) return@withContext false
        val legacyTasks = LegacyTaskDatabaseReader.read(legacyDatabasePath) ?: return@withContext false
        persistence.write { queries ->
            if (queries.selectUiState(LEGACY_TASK_DATABASE_MIGRATION_KEY).executeAsOneOrNull() != null) {
                return@write false
            }
            val existingIds = queries.selectConversationIds().executeAsList().toHashSet()
            val tasksToImport = legacyTasks.filterNot { it.id in existingIds }
            upsertTaskRows(queries, tasksToImport)
            queries.upsertUiState(
                state_key = LEGACY_TASK_DATABASE_MIGRATION_KEY,
                payload_version = 1L,
                payload_json = "{\"completed\":true}",
                updated_at = System.currentTimeMillis(),
            )
            true
        }
    }

    /** 读取按最近更新时间排序的全部会话及其关联内容。 */
    override suspend fun loadAll(): List<PersistedTask> = withContext(Dispatchers.IO) {
        persistence.read(::loadAllRows)
    }

    /** 在已有数据库访问区中组装全部会话，供读取和原子回退共用。 */
    private fun loadAllRows(queries: MulehangDatabaseQueries): List<PersistedTask> =
        queries.selectAllConversations().executeAsList().map { conversation ->
                conversation.toPersistedTask(
                    timeline = queries.selectTimelineForConversation(conversation.id)
                        .executeAsList()
                        .map { item ->
                            PersistedTimelineItem(
                                sequence = item.sequence.toInt(),
                                type = item.type,
                                payloadJson = item.payload_json,
                            )
                        },
                    history = queries.selectHistoryForConversation(conversation.id)
                        .executeAsList()
                        .map { item ->
                            PersistedHistoryItem(
                                sequence = item.sequence.toInt(),
                                type = item.type,
                                payloadJson = item.payload_json,
                            )
                        },
                    entries = queries.selectEntriesForConversation(conversation.id)
                        .executeAsList()
                        .map { entry ->
                            PersistedTaskEntry(
                                id = entry.id,
                                parentId = entry.parent_id,
                                createdAt = entry.created_at,
                                type = entry.type,
                                payloadJson = entry.payload_json,
                            )
                        },
                )
        }

    /** 在单个事务中同步当前会话集合，并保留其余统一持久化数据。 */
    override suspend fun saveAll(tasks: List<PersistedTask>) = withContext(Dispatchers.IO) {
        persistence.write { queries ->
            saveAllRows(queries, tasks)
        }
    }

    /** 在同一事务中创建会话、用户消息和永久 USER_TURN 回退点。 */
    override suspend fun saveUserTurn(
        tasks: List<PersistedTask>,
        before: PersistedTask?,
        conversationId: String,
        userEntryId: String,
    ) {
        withContext(Dispatchers.IO) {
            persistence.write { queries ->
                saveAllRows(queries, tasks)
                val now = System.currentTimeMillis()
                queries.insertCheckpoint(
                    id = UUID.randomUUID().toString(),
                    conversation_id = conversationId,
                    run_id = null,
                    entry_id = userEntryId,
                    kind = "USER_TURN",
                    koog_version = "",
                    strategy_fingerprint = "",
                    model_fingerprint = "",
                    tool_fingerprint = "",
                    payload_version = 1L,
                    payload_json = Json.encodeToString(UserTurnSnapshot(
                        before = before,
                        todos = queries.selectTodosForConversation(conversationId).executeAsList().map { row ->
                            AgentTodoItem(
                                id = row.id,
                                content = row.content,
                                status = AgentTodoStatus.valueOf(row.status),
                                sortOrder = row.sort_order.toInt(),
                                createdAt = row.created_at,
                                updatedAt = row.updated_at,
                            )
                        },
                    )),
                    recovery_state = "READY",
                    created_at = now,
                    updated_at = now,
                )
            }
        }
    }

    /** 以 USER_TURN 快照原子恢复目标会话，并清除从该轮开始的运行和派生记录。 */
    override suspend fun rollbackUserTurn(conversationId: String, userEntryId: String): UserTurnSnapshot? =
        withContext(Dispatchers.IO) {
            persistence.write { queries ->
                val checkpoint = queries.selectUserTurnCheckpoint(conversationId, userEntryId)
                    .executeAsOneOrNull() ?: return@write null
                require(checkpoint.payload_version == PAYLOAD_VERSION) { "不支持的用户回退点版本。" }
                val snapshot = Json.decodeFromString<UserTurnSnapshot>(checkpoint.payload_json)
                require(snapshot.version == 1 && snapshot.before?.id.orEmpty().let { it.isEmpty() || it == conversationId }) {
                    "用户回退点内容不匹配。"
                }
                val timestamp = checkpoint.created_at
                val checkpointOrder = queries.selectCheckpointOrder(checkpoint.id).executeAsOne()
                val currentTasks = loadAllRows(queries)
                val retainedEntryIds = snapshot.before?.entries?.mapTo(mutableSetOf(), PersistedTaskEntry::id).orEmpty()
                val detachedChildren = currentTasks.filter { task ->
                    task.parentConversationId == conversationId &&
                        (snapshot.before == null || task.forkedFromEntryId !in retainedEntryIds)
                }.map(PersistedTask::id)
                queries.deleteAttentionEventsFrom(conversationId, timestamp)
                queries.deleteToolAuditsFrom(conversationId, timestamp)
                queries.deleteToolInvocationsFrom(conversationId, timestamp)
                queries.deleteStatusSnapshotsFrom(conversationId, timestamp)
                queries.deleteCompactionsFrom(conversationId, timestamp)
                queries.deleteAgentRunsFrom(conversationId, timestamp)
                queries.deleteUserTurnCheckpointsFrom(conversationId, checkpointOrder)
                if (snapshot.before == null) {
                    queries.deleteConversation(conversationId)
                } else {
                    detachedChildren.forEach { childId -> queries.promoteChildConversation(childId, conversationId) }
                    saveAllRows(queries, currentTasks.map { task ->
                        if (task.id == conversationId) snapshot.before else task
                    }.map { task ->
                        if (task.id in detachedChildren) task.copy(parentConversationId = null) else task
                    })
                    snapshot.todos?.let { todos ->
                        queries.deleteTodosForConversation(conversationId)
                        todos.forEach { todo ->
                            queries.insertTodo(
                                conversation_id = conversationId,
                                id = todo.id,
                                content = todo.content,
                                status = todo.status.name,
                                sort_order = todo.sortOrder.toLong(),
                                created_at = todo.createdAt,
                                updated_at = todo.updatedAt,
                            )
                        }
                    }
                }
                snapshot
            }
        }

    /** 仅在调用方持有数据库写事务时同步会话与关联内容。 */
    private fun saveAllRows(queries: MulehangDatabaseQueries, tasks: List<PersistedTask>) {
        val incomingIds = tasks.mapTo(mutableSetOf(), PersistedTask::id)
        queries.selectConversationIds().executeAsList()
            .filterNot(incomingIds::contains)
            .forEach(queries::deleteConversation)

        upsertTaskRows(queries, tasks)
    }

    /** 在已有数据库访问区写入会话及其可替换负载，不删除集合以外的会话。 */
    private fun upsertTaskRows(queries: MulehangDatabaseQueries, tasks: List<PersistedTask>) {
        tasks.sortedByParentDependency().forEach { task ->
            val now = System.currentTimeMillis()
            queries.upsertConversation(
                id = task.id,
                title = task.title,
                workspace_path = task.workspacePath,
                workspace_name = task.workspaceName,
                detached_workspace_path = task.detachedWorkspacePath,
                detached_workspace_name = task.detachedWorkspaceName,
                parent_conversation_id = task.parentConversationId,
                forked_from_entry_id = task.forkedFromEntryId,
                active_entry_id = task.activeEntryId,
                head_entry_id = task.headEntryId,
                archived_at = task.archivedAt,
                tree_format_version = task.treeFormatVersion.toLong(),
                reasoning_effort = task.reasoningEffort,
                profile_id = task.profileId,
                permission_preset = task.permissionPreset,
                context_usage_fraction = task.contextUsageFraction.toDouble(),
                execution_state = task.executionState,
                execution_error_title = task.executionErrorTitle,
                execution_error_message = task.executionErrorMessage,
                attachments_json = task.attachmentsJson,
                created_at = now,
                updated_at = task.updatedAt,
            )
            queries.deleteTimelineForConversation(task.id)
            task.timeline.forEach { item ->
                queries.insertTimelineItem(
                    conversation_id = task.id,
                    sequence = item.sequence.toLong(),
                    type = item.type,
                    payload_version = PAYLOAD_VERSION,
                    payload_json = item.payloadJson,
                )
            }
            queries.deleteHistoryForConversation(task.id)
            task.history.forEach { item ->
                queries.insertHistoryItem(
                    conversation_id = task.id,
                    sequence = item.sequence.toLong(),
                    type = item.type,
                    payload_version = PAYLOAD_VERSION,
                    payload_json = item.payloadJson,
                )
            }
            queries.deleteEntriesForConversation(task.id)
            task.entries.forEach { entry ->
                queries.insertConversationEntry(
                    conversation_id = task.id,
                    id = entry.id,
                    parent_id = entry.parentId,
                    created_at = entry.createdAt,
                    type = entry.type,
                    payload_version = PAYLOAD_VERSION,
                    payload_json = entry.payloadJson,
                )
            }
        }
    }

    /** 删除一个会话；子会话由数据库外键自动提升为根。 */
    override suspend fun delete(taskId: String) {
        withContext(Dispatchers.IO) {
            persistence.write { queries ->
                queries.deleteConversation(taskId)
                Unit
            }
        }
    }

    /** 将关系化会话行与三个有序负载集合组合为既有快照模型。 */
    private fun Conversation.toPersistedTask(
        timeline: List<PersistedTimelineItem>,
        history: List<PersistedHistoryItem>,
        entries: List<PersistedTaskEntry>,
    ): PersistedTask = PersistedTask(
        id = id,
        title = title,
        workspacePath = workspace_path,
        workspaceName = workspace_name,
        detachedWorkspacePath = detached_workspace_path,
        detachedWorkspaceName = detached_workspace_name,
        parentConversationId = parent_conversation_id,
        forkedFromEntryId = forked_from_entry_id,
        activeEntryId = active_entry_id,
        headEntryId = head_entry_id,
        archivedAt = archived_at,
        treeFormatVersion = tree_format_version.toInt(),
        reasoningEffort = reasoning_effort,
        profileId = profile_id,
        permissionPreset = permission_preset,
        contextUsageFraction = context_usage_fraction.toFloat(),
        executionState = execution_state,
        executionErrorTitle = execution_error_title,
        executionErrorMessage = execution_error_message,
        attachmentsJson = attachments_json,
        timeline = timeline,
        history = history,
        entries = entries,
        updatedAt = updated_at,
    )

    /** 父会话优先插入，满足自引用外键；循环引用仍由数据库拒绝。 */
    private fun List<PersistedTask>.sortedByParentDependency(): List<PersistedTask> {
        val remaining = associateBy(PersistedTask::id).toMutableMap()
        val ordered = mutableListOf<PersistedTask>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.values.filter { task ->
                task.parentConversationId == null || task.parentConversationId !in remaining
            }
            if (ready.isEmpty()) {
                ordered += remaining.values
                break
            }
            ready.forEach { task ->
                ordered += task
                remaining.remove(task.id)
            }
        }
        return ordered
    }

    private companion object {
        const val PAYLOAD_VERSION: Long = 1L
        const val LEGACY_TASK_DATABASE_MIGRATION_KEY: String = "migration:legacy-tasks-db-v1"
    }
}
