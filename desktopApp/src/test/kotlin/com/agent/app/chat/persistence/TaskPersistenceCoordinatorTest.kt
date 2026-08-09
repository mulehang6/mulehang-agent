package com.agent.app.chat.persistence

import java.io.IOException
import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * 验证任务持久化的错误提示策略。
 */
class TaskPersistenceCoordinatorTest {
    /**
     * 被新快照取消的旧保存属于正常调度，侧栏不应显示错误。
     */
    @Test
    fun `should not report cancellation as a persistence error`() {
        assertFalse(shouldReportTaskPersistenceError(CancellationException()))
    }

    /**
     * 真实 I/O 失败仍应保留错误提示，避免掩盖数据库故障。
     */
    @Test
    fun `should report a real persistence error`() {
        assertTrue(shouldReportTaskPersistenceError(IOException("disk unavailable")))
    }

    /** 启动历史恢复完成前，初始空会话不得触发任何覆盖式保存。 */
    @Test
    fun `should not save before successful history activation`() = runTest {
        val repository = RecordingTaskRepository()
        val coordinator = TaskPersistenceCoordinator(
            repository = repository,
            scope = this,
            reportError = {},
        )

        coordinator.schedule(emptyList())
        coordinator.flush(emptyList()) {}

        assertEquals(0, repository.saveCalls)

        val flushed = CompletableDeferred<Unit>()
        coordinator.activate(emptyList())
        coordinator.flush(emptyList()) { flushed.complete(Unit) }
        flushed.await()

        assertEquals(1, repository.saveCalls)
    }
}

/** 记录协调器是否写入的最小内存仓库。 */
private class RecordingTaskRepository : TaskRepository {
    var saveCalls: Int = 0

    override suspend fun loadAll(): List<PersistedTask> = emptyList()

    override suspend fun saveAll(tasks: List<PersistedTask>) {
        saveCalls += 1
    }

    override suspend fun delete(taskId: String) = Unit
}
