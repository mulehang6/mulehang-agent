package com.agent.shared.agent.koog

import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.api.AgentStreamEvent
import com.agent.shared.agent.hook.AgentHookDispatcher
import com.agent.shared.agent.hook.AgentHookDispatchRequest
import com.agent.shared.agent.hook.AgentHookDispatchResult
import com.agent.shared.agent.hook.AgentHookDecision
import com.agent.shared.settings.model.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 验证多轮请求之间的 Hook 配置冻结和启动状态转换。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KoogHookLifecycleTest {
    /** 同一会话更新或移除 Hook 后，后续请求使用新规则且不会重复启动。 */
    @Test
    fun `should refresh hooks between runs without repeating session start`() = runTest {
        val events = mutableListOf<Pair<AgentHookSettings, AgentHookEvent>>()
        val gateway = KoogAgentGateway(
            executionDispatcher = UnconfinedTestDispatcher(testScheduler),
            agentRunner = { _, _, _, _ -> "done" },
            hookDispatcherFactory = { runRequest ->
                object : AgentHookDispatcher {
                    override suspend fun dispatch(request: AgentHookDispatchRequest): AgentHookDispatchResult {
                        events += runRequest.hookSettings to request.event
                        return AgentHookDispatchResult()
                    }
                }
            },
        )
        val hooks = AgentHookSettings(mapOf(
            AgentHookEvent.STOP to listOf(AgentHookMatcher(hooks = listOf(AgentHookCommand(command = "echo old")))),
        ))
        gateway.run(request().copy(hookSettings = hooks)).toList()
        gateway.run(request()).toList()
        assertEquals(listOf(hooks, AgentHookSettings()), events.filter { it.second == AgentHookEvent.STOP }.map { it.first })
        assertEquals(1, events.count { it.second == AgentHookEvent.SESSION_START })
        gateway.endSession("session", "")
        assertEquals(AgentHookSettings() to AgentHookEvent.SESSION_END, events.last())
    }

    /** 启动被阻断后重试仍须重新执行 SessionStart，不能跳过拒绝规则。 */
    @Test
    fun `should retry a blocked session start`() = runTest {
        var starts = 0
        val gateway = KoogAgentGateway(
            executionDispatcher = UnconfinedTestDispatcher(testScheduler),
            agentRunner = { _, _, _, _ -> error("blocked run must not execute") },
            hookDispatcherFactory = {
                object : AgentHookDispatcher {
                    override suspend fun dispatch(request: AgentHookDispatchRequest): AgentHookDispatchResult {
                        if (request.event == AgentHookEvent.SESSION_START) starts++
                        return AgentHookDispatchResult(AgentHookDecision.BLOCK)
                    }
                }
            },
        )
        repeat(2) { assertIs<AgentStreamEvent.Failed>(gateway.run(request()).toList().last()) }
        assertEquals(2, starts)
    }

    /** 创建无 MCP 和外部请求的运行输入。 */
    private fun request() = AgentRunRequest(
        profile = ConfigProfile(
            id = "test", providerType = ProviderType.OPENAI_RESPONSES,
            baseUrl = "https://example.test", apiKey = "placeholder", model = "test",
            enabled = true, layer = ConfigLayer.USER,
        ),
        prompt = "hello", sessionId = "session",
    )
}
