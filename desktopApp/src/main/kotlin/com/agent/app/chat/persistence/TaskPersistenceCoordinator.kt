package com.agent.app.chat.persistence

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.shared.chat.persistence.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 将桌面任务状态合并为后台 SQLite 写入，并在关闭前提供立即落盘能力。
 */
class TaskPersistenceCoordinator(
    private val repository: TaskRepository,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
) {
    private val writeMutex = Mutex()
    private var scheduledWrite: Job? = null

    /**
     * 合并连续的流式状态变化，减少数据库写入频率。
     */
    fun schedule(tasks: List<ChatConversationUiState>) {
        scheduledWrite?.cancel()
        scheduledWrite = scope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            save(tasks)
        }
    }

    /**
     * 取消延迟写入并在回调前完成当前快照保存。
     */
    fun flush(tasks: List<ChatConversationUiState>, onFlushed: () -> Unit) {
        scheduledWrite?.cancel()
        scope.launch {
            save(tasks)
            onFlushed()
        }
    }

    /**
     * 读取并映射此前保存的全部任务。
     */
    suspend fun load(): List<ChatConversationUiState> = withContext(Dispatchers.IO) {
        repository.loadAll().map(ChatTaskSnapshotMapper::toConversation)
    }

    /**
     * 串行保存完整任务集合，防止旧快照覆盖较新的流式内容。
     */
    private suspend fun save(tasks: List<ChatConversationUiState>) = writeMutex.withLock {
        try {
            withContext(Dispatchers.IO) {
                repository.saveAll(tasks.map(ChatTaskSnapshotMapper::toPersistedTask))
            }
        } catch (exception: Throwable) {
            if (!shouldReportTaskPersistenceError(exception)) {
                throw exception
            }
            reportError("任务保存失败")
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS = 300L
    }
}

/**
 * 判断一次持久化异常是否应反馈给用户；协程取消是新快照替换旧快照的正常调度结果。
 */
internal fun shouldReportTaskPersistenceError(exception: Throwable): Boolean =
    exception !is CancellationException
