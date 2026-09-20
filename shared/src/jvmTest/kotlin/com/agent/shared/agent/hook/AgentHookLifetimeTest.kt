package com.agent.shared.agent.hook

import com.agent.shared.settings.model.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/** 用受控阻塞命令验证后台所有权，避免依赖模型或外部索引器。 */
class AgentHookLifetimeTest {
    /** 固定 250ms 的测试命令只用于对比调度成本，不代表真实模型或索引耗时。 */
    @Test
    fun comparesDisabledSynchronousAndBackgroundHooks() = runBlocking {
        val samples = linkedMapOf<String, Long>()
        for (mode in listOf("disabled", "synchronous", "background")) {
            val owner = AgentHookLifetime()
            val dispatcher = WindowsAgentHookDispatcher(
                if (mode == "disabled") AgentHookSettings() else settings(mode == "background"),
                commandExecutor = { _, _, _, _ -> Thread.sleep(250); success() },
            )
            dispatcher.bindLifetime(owner)
            try {
                val started = System.nanoTime()
                dispatcher.dispatch(request())
                samples[mode] = (System.nanoTime() - started) / 1_000_000
                withTimeout(2_000.milliseconds) { owner.awaitPending() }
            } finally { owner.close() }
        }
        assertTrue(samples.getValue("synchronous") >= 200)
        val output = java.nio.file.Path.of("build/verification")
        java.nio.file.Files.createDirectories(output)
        java.nio.file.Files.writeString(output.resolve("hook-timing.txt"), samples.entries.joinToString("\n") { "${it.key}_ms=${it.value}" })
        Unit
    }
    /** 后台命令立即返回；同一会话换 dispatcher 后仍运行，关闭会话才中断。 */
    @Test
    fun backgroundSurvivesRuleRefreshAndClosesIdempotently() = runBlocking {
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val release = CountDownLatch(1)
        val owner = AgentHookLifetime()
        val dispatcher = WindowsAgentHookDispatcher(settings(true), commandExecutor = { _, _, _, _ ->
            started.countDown()
            try { release.await(); success() } finally { stopped.countDown() }
        })
        dispatcher.bindLifetime(owner)
        try {
            val result = withTimeout(1_000.milliseconds) { dispatcher.dispatch(request()) }
            assertEquals(AgentHookDispatchResult(), result)
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val replacement = WindowsAgentHookDispatcher(AgentHookSettings())
            replacement.bindLifetime(owner)
            replacement.dispatch(request())
            assertEquals(1L, stopped.count)
            owner.close()
            owner.close()
            assertTrue(stopped.await(2, TimeUnit.SECONDS))
        } finally { release.countDown(); owner.close() }
    }

    /** 同步命令按配置顺序运行；阻断后不执行下一条，取消不能被错误回退吞掉。 */
    @Test
    fun sequentialDecisionAndCancellation() = runBlocking<Unit> {
        val calls = mutableListOf<String>()
        val config = AgentHookSettings(mapOf(AgentHookEvent.SESSION_START to listOf(
            AgentHookMatcher(hooks = listOf("first", "block", "unreachable").map { AgentHookCommand(command = it) }),
        )))
        val dispatcher = WindowsAgentHookDispatcher(config, commandExecutor = { command, _, _, _ ->
            calls += command
            AgentHookCommandResult(if (command == "block") """{"decision":"block"}""" else "context", "", 0, false)
        })
        assertEquals(AgentHookDecision.BLOCK, dispatcher.dispatch(request()).decision)
        assertEquals(listOf("first", "block"), calls)
        dispatcher.close()
        val cancelled = WindowsAgentHookDispatcher(settings(false), commandExecutor = { _, _, _, _ ->
            throw CancellationException("cancelled")
        })
        try {
            assertFailsWith<CancellationException> { cancelled.dispatch(request()) }
        } finally { cancelled.close() }
    }

    /** 后台返回的阻断和输入替换不得影响当前请求。 */
    @Test
    fun backgroundOutputCannotChangeRequest() = runBlocking {
        val owner = AgentHookLifetime()
        val dispatcher = WindowsAgentHookDispatcher(settings(true), commandExecutor = { _, _, _, _ ->
            AgentHookCommandResult("""{"decision":"block","updatedInput":{"prompt":"changed"}}""", "", 0, false)
        })
        dispatcher.bindLifetime(owner)
        try {
            assertEquals(AgentHookDispatchResult(), dispatcher.dispatch(request()))
            withTimeout(2_000.milliseconds) { owner.awaitPending() }
        } finally { owner.close() }
    }

    /** 构造一个最小启动规则。 */
    private fun settings(background: Boolean) = AgentHookSettings(mapOf(
        AgentHookEvent.SESSION_START to listOf(AgentHookMatcher(hooks = listOf(
            AgentHookCommand(command = "index", runAsync = background, timeout = 2),
        ))),
    ))

    /** 无真实命令的固定会话输入。 */
    private fun request() = AgentHookDispatchRequest(AgentHookEvent.SESSION_START, "lifetime-test", ".")

    /** 空输出代表正常完成。 */
    private fun success() = AgentHookCommandResult("", "", 0, false)
}
