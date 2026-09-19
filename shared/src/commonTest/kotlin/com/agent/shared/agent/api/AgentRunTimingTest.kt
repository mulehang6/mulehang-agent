package com.agent.shared.agent.api

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/** 验证阶段提示的边界与取消，不依赖机器速度。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentRunTimingTest {
    /** 299ms 不提示，300ms 显示当前阶段，结束后不再追加提示。 */
    @Test
    fun stageHintUses300MillisThreshold() = runTest {
        val events = mutableListOf<AgentStreamEvent>()
        val release = CompletableDeferred<Unit>()
        val job = launch { AgentRunTiming("test").phase("prepare", "准备", events::add) { release.await() } }
        runCurrent()
        advanceTimeBy(299.milliseconds)
        runCurrent()
        assertTrue(events.isEmpty())
        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(listOf<AgentStreamEvent>(AgentStreamEvent.Status("准备")), events)
        release.complete(Unit)
        job.join()
        advanceUntilIdle()
        assertEquals(1, events.size)
    }

    /** 快速完成和取消都应撤销定时提示。 */
    @Test
    fun fastAndCancelledStagesNeverEmitLateHints() = runTest {
        val events = mutableListOf<AgentStreamEvent>()
        AgentRunTiming("fast").phase("prepare", "准备", events::add) { delay(100.milliseconds) }
        val job = launch { AgentRunTiming("cancel").phase("prepare", "准备", events::add) { awaitCancellation() } }
        runCurrent()
        job.cancelAndJoin()
        advanceUntilIdle()
        assertTrue(events.isEmpty())
    }
}
