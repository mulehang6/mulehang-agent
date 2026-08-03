package com.agent.shared.chat.persistence

/**
 * 单条任务的完整可持久化快照。
 */
data class PersistedTask(
    val id: String,
    val title: String,
    val workspacePath: String,
    val reasoningEffort: String,
    val profileId: String? = null,
    val permissionPreset: String = "DEFAULT",
    val contextUsageFraction: Float,
    val executionState: String,
    val executionErrorTitle: String?,
    val executionErrorMessage: String?,
    val attachmentsJson: String,
    val timeline: List<PersistedTimelineItem>,
    val history: List<PersistedHistoryItem>,
)

/**
 * 时间线中按顺序保存的一条类型化 JSON 负载。
 */
data class PersistedTimelineItem(
    val sequence: Int,
    val type: String,
    val payloadJson: String,
)

/**
 * Agent 上下文历史中按顺序保存的一条类型化 JSON 负载。
 */
data class PersistedHistoryItem(
    val sequence: Int,
    val type: String,
    val payloadJson: String,
)

/**
 * 屏蔽任务快照的本地存储实现细节。
 */
interface TaskRepository {
    /**
     * 加载本机保存的全部任务快照。
     */
    suspend fun loadAll(): List<PersistedTask>

    /**
     * 事务化保存传入的任务快照。
     */
    suspend fun saveAll(tasks: List<PersistedTask>)

    /**
     * 删除指定任务及其关联的时间线和 history。
     */
    suspend fun delete(taskId: String)
}
