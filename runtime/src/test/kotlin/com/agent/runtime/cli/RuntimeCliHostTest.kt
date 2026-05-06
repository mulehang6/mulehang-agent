package com.agent.runtime.cli

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 stdio host 按事件流即时写出，而不是等待整轮 runtime 调用完成。
 */
class RuntimeCliHostTest {

    private val originalLogbackConfig = System.getProperty(LOGBACK_CONFIGURATION_FILE_PROPERTY)

    @AfterTest
    fun restoreLoggingProperty() {
        if (originalLogbackConfig == null) {
            System.clearProperty(LOGBACK_CONFIGURATION_FILE_PROPERTY)
            return
        }

        System.setProperty(LOGBACK_CONFIGURATION_FILE_PROPERTY, originalLogbackConfig)
    }

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

    @Test
    fun `should switch cli host logging to dedicated stderr configuration`() {
        System.clearProperty(LOGBACK_CONFIGURATION_FILE_PROPERTY)

        configureCliHostLogging()

        val configuredPath = System.getProperty(LOGBACK_CONFIGURATION_FILE_PROPERTY).orEmpty()
        assertTrue(
            actual = configuredPath.endsWith("logback-cli-host.xml"),
            message = "CLI host 应切换到专用 logback-cli-host.xml，当前值为：$configuredPath",
        )
    }

    private fun streamingMessages(
        block: suspend kotlinx.coroutines.flow.FlowCollector<RuntimeCliOutboundMessage>.() -> Unit,
    ): Flow<RuntimeCliOutboundMessage> = flow(block)
}
