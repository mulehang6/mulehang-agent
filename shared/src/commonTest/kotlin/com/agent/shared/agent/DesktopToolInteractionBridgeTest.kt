package com.agent.shared.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证桌面工具交互桥的挂起恢复语义。
 */
class DesktopToolInteractionBridgeTest {
    /**
     * 问题请求应在用户提交答案后恢复。
     */
    @Test
    fun `question bridge should resume with submitted answer`() = runTest {
        val answer = CompletableDeferred<String>()
        val bridge = object : DesktopToolInteractionBridge {
            override suspend fun requestQuestion(request: QuestionRequest): String = answer.await()

            override suspend fun requestApproval(request: ApprovalRequest): Boolean = true
        }

        val deferred = async {
            bridge.requestQuestion(
                QuestionRequest(
                    requestId = "q1",
                    toolCallId = "call-1",
                    question = "Pick one",
                    options = listOf("A", "B"),
                ),
            )
        }

        answer.complete("B")

        assertEquals("B", deferred.await())
    }
}
