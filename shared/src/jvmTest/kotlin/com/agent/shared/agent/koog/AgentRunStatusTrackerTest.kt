package com.agent.shared.agent.koog

import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.status.AgentStatusSnapshot
import com.agent.shared.agent.status.AgentTodoDraft
import com.agent.shared.agent.status.AgentTodoRepository
import com.agent.shared.agent.status.AgentTodoStatus
import com.agent.shared.agent.status.toModelMessage
import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.persistence.DesktopPersistenceDatabase
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/** 验证状态消息与实际数据库详情保持逐字一致。 */
class AgentRunStatusTrackerTest {
    /** TODO、工具数与错误变化进入下一次模型请求的持久化消息。 */
    @Test
    fun `status message persists exact text and changes with run state`() = runTest {
        val path = Files.createTempDirectory("mulehang-status").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(PersistedTask(
                id = "conversation",
                title = "test",
                workspacePath = "D:/workspace",
                reasoningEffort = "MEDIUM",
                contextUsageFraction = 0.4f,
                executionState = "RUNNING",
                executionErrorTitle = null,
                executionErrorMessage = null,
                attachmentsJson = "[]",
                timeline = emptyList(),
                history = emptyList(),
            )))
            database.write { queries ->
                queries.upsertAgentRun("run", "conversation", "turn", "RUNNING", "model", "strategy", "tools", 1, 1, null, null)
            }
            AgentTodoRepository(database).rewrite("conversation", listOf(
                AgentTodoDraft("inspect", "检查", AgentTodoStatus.IN_PROGRESS),
            ))
            val tracker = AgentRunStatusTracker(
                database,
                AgentRunRequest(
                    prompt = "hello",
                    profile = ConfigProfile(
                        id = "profile",
                        providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
                        baseUrl = "https://example.com",
                        apiKey = "key",
                        model = "model",
                        enabled = true,
                        layer = ConfigLayer.PROJECT,
                    ),
                    sessionId = "conversation",
                    userEntryId = "turn",
                    contextUsageFraction = 0.4f,
                    contextWindow = 100_000,
                ),
                "run",
                clock = { 1_000L },
                gitStatus = { "main；已跟踪文件更改 0 项" },
            )
            val (first, text) = assertNotNull(tracker.pendingSnapshot())
            tracker.persist(first, text)
            assertNull(tracker.pendingSnapshot())
            tracker.toolStarted()
            tracker.toolFailed("操作失败")
            tracker.updateUsage(50_000)
            val (updated, updatedText) = assertNotNull(tracker.pendingSnapshot())
            tracker.persist(updated, updatedText)

            val saved = database.read { it.selectLatestStatusSnapshot("conversation").executeAsOne() }
            val decoded = Json.decodeFromString<AgentStatusSnapshot>(saved.payload_json)
            assertEquals(updated, decoded)
            assertEquals(decoded.toModelMessage(), saved.model_message_text)
            assertEquals(1, decoded.toolCallCount)
            assertEquals(0.5f, decoded.contextUsageFraction)
            assertTrue(saved.model_message_text.contains("操作失败"))
        }
    }
}
