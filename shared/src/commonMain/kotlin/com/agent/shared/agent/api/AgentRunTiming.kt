package com.agent.shared.agent.api

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/** 为一次请求记录不包含正文和凭据的阶段耗时。 */
class AgentRunTiming(private val traceId: String) {
    private val started = TimeSource.Monotonic.markNow()

    /** 记录相对当前计时器起点的里程碑，各阶段通过 trace 关联。 */
    fun mark(stage: String) {
        log.info { "event=agent_timing trace=$traceId stage=$stage elapsed_ms=${started.elapsedNow().inWholeMilliseconds}" }
    }

    /** 阶段超过 300ms 才提示，完成或取消时同步撤销尚未发送的提示。 */
    suspend fun <T> phase(
        stage: String,
        message: String,
        emit: suspend (AgentStreamEvent) -> Unit,
        block: suspend () -> T,
    ): T = coroutineScope {
        val phaseStart = TimeSource.Monotonic.markNow()
        val hint = launch { delay(300); emit(AgentStreamEvent.Status(message)) }
        try {
            block()
        } finally {
            hint.cancel()
            log.info { "event=agent_timing trace=$traceId stage=$stage duration_ms=${phaseStart.elapsedNow().inWholeMilliseconds}" }
        }
    }

    private companion object {
        val log = KotlinLogging.logger { }
    }
}
