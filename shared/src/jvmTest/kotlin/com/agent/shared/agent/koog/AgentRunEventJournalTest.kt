package com.agent.shared.agent.koog

import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** 验证工具调用在完成前就能留下可恢复的结构化轨迹。 */
class AgentRunEventJournalTest {
    /** 部分输出和原始参数随工具状态变化保存在同一调用行。 */
    @Test
    fun `tool call persists arguments partial output and final result`() = runTest {
        val path = Files.createTempDirectory("mulehang-tool-journal").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(PersistedTask(
                id = "conversation",
                title = "test",
                workspacePath = "D:/workspace",
                reasoningEffort = "MEDIUM",
                contextUsageFraction = 0f,
                executionState = "RUNNING",
                executionErrorTitle = null,
                executionErrorMessage = null,
                attachmentsJson = "[]",
                timeline = emptyList(),
                history = emptyList(),
            )))
            database.write { queries ->
                queries.upsertAgentRun("run", "conversation", null, "RUNNING", "model", "strategy", "tools", 1, 1, null, null)
            }
            val journal = AgentRunEventJournal(database, "conversation", "run") { 2L }
            journal.record(AgentStreamEvent.ToolCallStarted("call", "run_powershell", "preview", argumentsJson = "{\"script\":\"full\"}"))
            journal.record(AgentStreamEvent.ToolOutputDelta("call", "run_powershell", "partial", AgentStreamEvent.ToolOutputStream.Stdout))
            val pending = database.read { it.selectToolInvocationsForRun("run").executeAsOne() }
            assertEquals("RUNNING", pending.state)
            assertEquals("{\"script\":\"full\"}", pending.arguments_json)
            assertEquals("partial", pending.partial_output)

            journal.record(AgentStreamEvent.ToolCallFinished("call", "run_powershell", resultDisplay = "done"))
            val finished = database.read { it.selectToolInvocationsForRun("run").executeAsOne() }
            assertEquals("COMPLETED", finished.state)
            assertEquals("{\"text\":\"done\"}", finished.result_json)
            assertTrue(finished.finished_at != null)
        }
    }
}
