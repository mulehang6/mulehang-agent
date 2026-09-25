@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.agent.shared.agent.koog

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.GraphCheckpointProperties
import ai.koog.serialization.JSONNull
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

/** Koog 的真实结构化图状态必须跨数据库重开完整保留。 */
class SqlDelightPersistenceStorageProviderTest {
    /** 无法序列化的替代状态不应清除最后一个可用恢复点。 */
    @Test
    fun `round trip preserves tool call and rejects incomplete replacement`() = runTest {
        val path = Files.createTempDirectory("mulehang-koog-checkpoint").resolve("db.sqlite")
        val original = AgentCheckpointData(
            checkpointId = "checkpoint",
            createdAt = Instant.fromEpochMilliseconds(1),
            messageHistory = buildConversationMessages(
                history = listOf(
                    AgentConversationHistoryMessage.User("执行任务"),
                    AgentConversationHistoryMessage.Assistant(listOf(
                        AgentConversationHistoryPart.ToolCall("call-1", "run_powershell", "{\"script\":\"echo hi\"}"),
                        AgentConversationHistoryPart.ToolResult("call-1", "run_powershell", "hi"),
                    )),
                ),
                prompt = "继续",
            ),
            version = 1,
            graphProperties = GraphCheckpointProperties("agent/send_tool_results_streaming", JSONNull),
        )
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(task()))
            val provider = provider(database)
            provider.beginRun("model")
            provider.saveCheckpoint("run", original)
            assertFailsWith<IllegalStateException> {
                provider.saveCheckpoint("run", original.copy(graphProperties = null))
            }
        }
        DesktopPersistenceDatabase.open(path).use { database ->
            val recovered = requireNotNull(provider(database).getLatestCheckpoint("run"))
            assertEquals(original.graphProperties?.nodePath, recovered.graphProperties?.nodePath)
            assertEquals(original.messageHistory, recovered.messageHistory)
        }
    }

    /** 创建使用同一组兼容指纹的 provider。 */
    private fun provider(database: DesktopPersistenceDatabase) = SqlDelightPersistenceStorageProvider(
        persistence = database,
        runId = "run",
        conversationId = "conversation",
        modelFingerprint = "model-fingerprint",
        toolFingerprint = "tool-fingerprint",
        userEntryId = "turn",
    )

    /** 建立恢复点外键所需会话。 */
    private fun task() = PersistedTask(
        id = "conversation", title = "测试", workspacePath = "D:/workspace",
        reasoningEffort = "MEDIUM", contextUsageFraction = 0f,
        executionState = "RUNNING", executionErrorTitle = null,
        executionErrorMessage = null, attachmentsJson = "[]",
        timeline = emptyList(), history = emptyList(),
    )
}
