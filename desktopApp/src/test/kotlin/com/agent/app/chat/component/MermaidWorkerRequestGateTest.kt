package com.agent.app.chat.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 验证共享 Mermaid 工作器对串行请求、超时和迟到回包的隔离。 */
class MermaidWorkerRequestGateTest {

    /** 工作器只允许一个尚未结束的请求，防止新页面覆盖正在等待的回包。 */
    @Test
    fun permitsOnlyOneActiveRequest() {
        val gate = MermaidWorkerRequestGate()

        gate.activate(1)

        assertFailsWith<IllegalStateException> { gate.activate(2) }
    }

    /** 超时请求清理后，旧页面的迟到响应不能污染后续请求。 */
    @Test
    fun rejectsLateResponseAfterTimeoutAndNextRequest() {
        val gate = MermaidWorkerRequestGate()

        gate.activate(1)
        gate.clear(1)
        gate.activate(2)

        assertFalse(gate.accepts(1))
        assertTrue(gate.accepts(2))
    }
}
