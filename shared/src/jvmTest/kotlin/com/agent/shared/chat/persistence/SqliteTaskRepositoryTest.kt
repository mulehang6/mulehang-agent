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
