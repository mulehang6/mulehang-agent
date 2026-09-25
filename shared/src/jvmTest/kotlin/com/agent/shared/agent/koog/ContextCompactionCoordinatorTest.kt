package com.agent.shared.agent.koog

import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.chat.persistence.PersistedTask
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.persistence.DesktopPersistenceDatabase
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.ConfigProfile
import com.agent.shared.settings.model.ProviderType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

/** 验证触发阈值、完整轮次边界、失败回退及摘要重用。 */
class ContextCompactionCoordinatorTest {
    /** 工具调用和结果同属助手消息，边界只落在下一条用户消息前。 */
    @Test
    fun `planner retains a complete recent turn and tool result boundary`() {
        val request = request().copy(history = history())
        val plan = requireNotNull(planContextCompaction(request))
        assertEquals(2, plan.prefixCount)
        assertTrue(request.history[plan.prefixCount] is AgentConversationHistoryMessage.User)
        assertNull(planContextCompaction(request.copy(contextUsageFraction = 0.5f)))
    }

    /** 成功摘要仅替换模型上下文，数据库保留原始会话历史供下轮重用。 */
    @Test
    fun `successful summary persists and is reused without another model call`() = runTest {
        val path = Files.createTempDirectory("mulehang-compaction").resolve("mulehang.db")
        val original = request().copy(history = history())
        var calls = 0
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(task()))
            database.write { queries ->
                queries.upsertAgentRun("run", "conversation", "turn", "RUNNING", "model", "strategy", "tools", 1, 1, null, null)
            }
            val coordinator = ContextCompactionCoordinator(database) { _, prefix ->
                calls++
                assertEquals(2, prefix.size)
                "保留目标和已执行工具结果"
            }
            val compacted = coordinator.prepare(original, "run")
            assertEquals(1, calls)
            assertEquals(3, compacted.history.size)
            assertTrue((compacted.history.first() as AgentConversationHistoryMessage.User).content.contains("保留目标"))
            assertEquals(4, original.history.size)
            val reused = coordinator.prepare(original.copy(contextUsageFraction = 0.2f), "run")
            assertEquals(compacted.history, reused.history)
            assertEquals(1, calls)
            assertEquals(3, coordinator.prepare(original.copy(history = original.history.drop(1), contextUsageFraction = 0.2f), "run").history.size)
        }
    }

    /** 摘要请求失败不得改变模型收到的原文。 */
    @Test
    fun `failed summary keeps original history`() = runTest {
        val path = Files.createTempDirectory("mulehang-compaction-failure").resolve("mulehang.db")
        DesktopPersistenceDatabase.open(path).use { database ->
            SqliteTaskRepository(database).saveAll(listOf(task()))
            database.write { queries ->
                queries.upsertAgentRun("run", "conversation", "turn", "RUNNING", "model", "strategy", "tools", 1, 1, null, null)
            }
            val request = request().copy(history = history())
            val result = ContextCompactionCoordinator(database) { _, _ -> error("network") }.prepare(request, "run")
            assertEquals(request.history, result.history)
        }
    }

    /** 溢出后仅当压缩减少历史时启动一次新的模型运行。 */
    @Test
    fun `context overflow compacts and retries only once`() = runTest {
        var attempts = 0
        val events = mutableListOf<AgentStreamEvent>()
        val result = runWithContextOverflowRetry(
            request = request().copy(history = history()),
            database = null,
            emitEvent = events::add,
            compact = { input, _ -> input.copy(history = input.history.drop(2), contextAlreadyCompacted = true) },
        ) { attempt, _ ->
            attempts++
            if (attempts == 1) error("maximum context length exceeded")
            assertTrue(attempt.contextAlreadyCompacted)
            "已恢复"
        }
        assertEquals("已恢复", result)
        assertEquals(2, attempts)
        assertEquals(1, events.filterIsInstance<AgentStreamEvent.Status>().size)
    }

    /** 工具开始后不能重跑整轮，以免再次产生文件或命令副作用。 */
    @Test
    fun `overflow after tool start does not retry`() = runTest {
        var attempts = 0
        assertFailsWith<IllegalStateException> {
            runWithContextOverflowRetry(
                request = request().copy(history = history()),
                database = null,
                emitEvent = {},
                compact = { input, _ -> input.copy(history = input.history.drop(2)) },
            ) { _, emit ->
                attempts++
                emit(AgentStreamEvent.ToolCallStarted("id", "apply_patch", "{}"))
                error("context_length_exceeded")
            }
        }
        assertEquals(1, attempts)
    }

    /** 普通网络错误不能误判为上下文超限。 */
    @Test
    fun `overflow detection requires provider context signal`() {
        assertTrue(isContextOverflowMessage("context_length_exceeded"))
        assertTrue(isContextOverflowMessage("maximum context length is 128000 tokens"))
        assertTrue(!isContextOverflowMessage("network timeout"))
    }

    /** 构造达到压缩阈值的三轮历史。 */
    private fun history(): List<AgentConversationHistoryMessage> = listOf(
        AgentConversationHistoryMessage.User("旧目标" + "x".repeat(100_000)),
        AgentConversationHistoryMessage.Assistant(listOf(
            AgentConversationHistoryPart.ToolCall("tool-1", "apply_patch", "{}"),
            AgentConversationHistoryPart.ToolResult("tool-1", "apply_patch", "patched"),
        )),
        AgentConversationHistoryMessage.User("近期用户消息"),
        AgentConversationHistoryMessage.Assistant(listOf(AgentConversationHistoryPart.Text("近期回复"))),
    )

    /** 提供当前运行的模型和窗口。 */
    private fun request() = AgentRunRequest(
        prompt = "继续", profile = ConfigProfile(
            id = "profile", providerType = ProviderType.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://example.com", apiKey = "key", model = "model",
            enabled = true, layer = ConfigLayer.PROJECT,
        ),
        sessionId = "conversation", contextWindow = 30_000, contextUsageFraction = 0.9f,
    )

    /** 为摘要关系表提供持久会话。 */
    private fun task() = PersistedTask(
        id = "conversation", title = "测试", workspacePath = "D:/workspace",
        reasoningEffort = "MEDIUM", contextUsageFraction = 0.9f,
        executionState = "RUNNING", executionErrorTitle = null, executionErrorMessage = null,
        attachmentsJson = "[]", timeline = emptyList(), history = emptyList(),
    )
}
