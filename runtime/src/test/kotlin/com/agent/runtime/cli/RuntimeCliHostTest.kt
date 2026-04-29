package com.agent.runtime.cli

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 stdio host 按事件流即时写出，而不是等待整轮 runtime 调用完成。
 */
class RuntimeCliHostTest {

    @Test
    fun `should write outbound messages as stream emits them`() = runTest {
        val written = mutableListOf<RuntimeCliOutboundMessage>()
        var observedAfterFirstEmit = 0
        val session = RuntimeCliHostSession(
            streamRequest = {
                streamingMessages {
                    emit(RuntimeCliStatusMessage(status = "run.started", sessionId = "session-1", requestId = "request-1"))
                    observedAfterFirstEmit = written.size
                    emit(RuntimeCliResultMessage(sessionId = "session-1", requestId = "request-1", output = null, mode = "agent"))
                }
            },
            inputLines = {
                sequenceOf("""{"type":"run","sessionId":"session-1","prompt":"hello"}""")
            },
            writeMessage = { message -> written.add(message) },
        )

        session.run()

        assertEquals(1, observedAfterFirstEmit)
        assertEquals(
            listOf("status", "result"),
            written.map { message ->
                when (message) {
                    is RuntimeCliStatusMessage -> "status"
                    is RuntimeCliResultMessage -> "result"
                    else -> "other"
                }
            },
        )
    }

    private fun streamingMessages(
        block: suspend kotlinx.coroutines.flow.FlowCollector<RuntimeCliOutboundMessage>.() -> Unit,
    ): Flow<RuntimeCliOutboundMessage> = flow(block)
}
