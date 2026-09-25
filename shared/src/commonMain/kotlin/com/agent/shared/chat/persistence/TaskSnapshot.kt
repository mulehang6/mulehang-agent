package com.agent.shared.chat.persistence

import kotlinx.serialization.Serializable

/**
 * 单条任务的完整可持久化快照。
 */
@Serializable
data class PersistedTask(
    val id: String,
    val title: String,
    val workspacePath: String,
    /** 用户为工作区指定的显示名称；为空时由路径末级目录回退。 */
    val workspaceName: String? = null,
    /** 工作区解除关联前的目录，用于后续精确恢复历史。 */
    val detachedWorkspacePath: String? = null,
    /** 工作区解除关联前的显示名称，用于恢复时保留用户命名。 */
    val detachedWorkspaceName: String? = null,
    /** 侧栏会话树中的直接父会话。 */
    val parentConversationId: String? = null,
    /** fork 创建时所选的源条目；clone 与普通会话为空。 */
    val forkedFromEntryId: String? = null,
    /** 当前投影时间线使用的活动 leaf。 */
    val activeEntryId: String? = null,
    /** 会话持久主线的末端；浏览历史分支时不会随 [activeEntryId] 移动。 */
    val headEntryId: String? = null,
    /** 归档时间；为空表示活跃。 */
    val archivedAt: Long? = null,
    /** 0 表示旧线性会话，正数表示条目树格式。 */
    val treeFormatVersion: Int = 0,
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
    /** 条目图的完整节点集合；旧线性会话为空。 */
    val entries: List<PersistedTaskEntry> = emptyList(),
    /** 任务最后被操作的时间戳（毫秒）；旧数据为 0。 */
    val updatedAt: Long = 0L,
)

/** 条目图中一个节点的数据库无关表示。 */
@Serializable
data class PersistedTaskEntry(
    val id: String,
    val parentId: String?,
    val createdAt: Long,
    val type: String,
    val payloadJson: String,
)

/**
 * 时间线中按顺序保存的一条类型化 JSON 负载。
 */
@Serializable
data class PersistedTimelineItem(
    val sequence: Int,
    val type: String,
    val payloadJson: String,
)

/**
 * Agent 上下文历史中按顺序保存的一条类型化 JSON 负载。
 */
@Serializable
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

    /** 原子写入已接受的用户消息和其发送前回退点。 */
    suspend fun saveUserTurn(
        tasks: List<PersistedTask>,
        before: PersistedTask?,
        conversationId: String,
        userEntryId: String,
    ) {
        saveAll(tasks)
    }

    /** 将指定用户消息及其后续会话内容回退到发送前的永久快照。 */
    suspend fun rollbackUserTurn(conversationId: String, userEntryId: String): UserTurnSnapshot? = null

    /**
     * 删除指定任务及其关联的时间线和 history。
     */
    suspend fun delete(taskId: String)
}
