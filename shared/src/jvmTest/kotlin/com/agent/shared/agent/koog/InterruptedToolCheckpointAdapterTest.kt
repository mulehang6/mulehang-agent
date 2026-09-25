@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.agent.shared.agent.koog

import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.GraphCheckpointProperties
import ai.koog.serialization.JSONNull
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.persistence.DesktopPersistenceDatabase
import com.agent.shared.tool.interaction.InteractionRequestRepository
import com.agent.shared.tool.model.ApprovalRequest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/** 工具执行中断后的恢复点必须绕过原工具节点。 */
class InterruptedToolCheckpointAdapterTest {
    /** 未完成调用被转换成带原参数、部分输出和结果未知说明的合成结果。 */
    @Test
    fun `interrupted tool resumes after synthetic result`() = runTest {
        val path = Files.createTempDirectory("mulehang-interrupted-tool").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(task()))
            database.write { queries ->
                queries.upsertAgentRun("run", "conversation", "turn", "INTERRUPTED", "model", "strategy", "tools", 1, 1, null, null)
            }
            val journal = AgentRunEventJournal(database, "conversation", "run") { 2L }
            journal.record(AgentStreamEvent.ToolCallStarted("call-1", "run_powershell", "preview", argumentsJson = "{\"script\":\"side effect\"}"))
            journal.record(AgentStreamEvent.ToolOutputDelta("call-1", "run_powershell", "partial", AgentStreamEvent.ToolOutputStream.Stdout))
            val checkpoint = checkpoint("agent/call_llm_streaming")

            val adapted = InterruptedToolCheckpointAdapter(database, "run").adapt(checkpoint)
            val graph = requireNotNull(adapted.checkpoint.graphProperties)
            val results = Json.decodeFromJsonElement(ReceivedToolResults.serializer(), graph.lastOutput.toKotlinxJsonElement())

            assertEquals("agent/nodeExecuteTool", graph.nodePath)
            assertEquals(1, adapted.unknownCalls.size)
            assertTrue(results.toolResults.single().output.contains("结果未知"))
            assertTrue(results.toolResults.single().output.contains("side effect"))
            assertTrue(results.toolResults.single().output.contains("partial"))
        }
    }

    /** 无调用 ID 时，不能把同名旧工具的完成结果误当作当前结果。 */
    @Test
    fun `tool call without id does not reuse a finished invocation`() = runTest {
        val path = Files.createTempDirectory("mulehang-finished-tool").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(task()))
            database.write { queries ->
                queries.upsertAgentRun("run", "conversation", "turn", "INTERRUPTED", "model", "strategy", "tools", 1, 1, null, null)
            }
            AgentRunEventJournal(database, "conversation", "run") { 2L }.record(
                AgentStreamEvent.ToolCallFailed(
                    toolCallId = "previous-call",
                    name = "run_powershell",
                    reason = "旧调用的失败结果",
                ),
            )

            val adapted = InterruptedToolCheckpointAdapter(database, "run").adapt(
                checkpoint("agent/call_llm_streaming", callId = null),
            )
            val results = Json.decodeFromJsonElement(
                ReceivedToolResults.serializer(),
                requireNotNull(adapted.checkpoint.graphProperties).lastOutput.toKotlinxJsonElement(),
            )

            assertEquals(1, adapted.unknownCalls.size)
            assertTrue(results.toolResults.single().output.contains("结果未知"))
        }
    }

    /** 已安全完成的节点不应被改写。 */
    @Test
    fun `completed tool node keeps original recovery point`() {
        val path = Files.createTempDirectory("mulehang-safe-node").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(path).use { database ->
            val original = checkpoint("agent/nodeExecuteTool")
            assertSame(original, InterruptedToolCheckpointAdapter(database, "run").adapt(original).checkpoint)
        }
    }

    /** 单个待审批工具的答复尚未交付时，可以安全重入原节点让桥消费答复。 */
    @Test
    fun `answered but unconsumed approval keeps tool node executable`() = runTest {
        val path = Files.createTempDirectory("mulehang-answered-tool").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(task()))
            database.write { queries ->
                queries.upsertAgentRun("run", "conversation", "turn", "INTERRUPTED", "model", "strategy", "tools", 1, 1, null, null)
            }
            val repository = InteractionRequestRepository(database)
            repository.recordApproval("conversation", "run", ApprovalRequest("approval", "run_powershell", "执行命令"))
            repository.answer("approval", "false")
            val original = checkpoint("agent/call_llm_streaming")
            assertSame(original, InterruptedToolCheckpointAdapter(database, "run").adapt(original).checkpoint)
        }
    }

    /** 构造一个模型刚决定调用工具后的 Koog 图检查点。 */
    private fun checkpoint(nodePath: String, callId: String? = "call-1"): AgentCheckpointData = AgentCheckpointData(
        checkpointId = "checkpoint",
        createdAt = Instant.fromEpochMilliseconds(1),
        messageHistory = buildConversationMessages(
            history = listOf(
                AgentConversationHistoryMessage.User("执行任务"),
                AgentConversationHistoryMessage.Assistant(listOf(
                    AgentConversationHistoryPart.ToolCall(callId, "run_powershell", "{\"script\":\"side effect\"}"),
                )),
            ),
            prompt = "continue",
        ),
        version = 1,
        graphProperties = GraphCheckpointProperties(nodePath, JSONNull),
    )

    /** 创建带外键父行的最小会话。 */
    private fun task() = PersistedTask(
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
    )
}
