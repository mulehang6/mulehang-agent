package com.agent.app.chat.persistence

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
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
}
