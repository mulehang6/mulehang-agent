package com.agent.runtime

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * 验证 dispatcher 会沿统一 runtime 管线转发请求。
 */
class RuntimeRequestDispatcherTest {

    @Test
    fun `should dispatch request through unified runtime pipeline`() = runTest {
        val session = RuntimeSession(id = "session-1")
        val context = RuntimeRequestContext(sessionId = session.id, requestId = "request-1")
        val request = RuntimeCapabilityRequest(capabilityId = "agent.run")
        val expected = RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "routed")))
        val router = RecordingRouter(result = expected)
        val dispatcher = RuntimeRequestDispatcher(capabilityRouter = router)

        val result = dispatcher.dispatch(
            session = session,
            context = context,
            request = request,
        )

        assertSame(expected, result)
        assertEquals(context, router.receivedContext)
        assertEquals(request, router.receivedRequest)
    }

    /**
     * 记录 dispatcher 转发参数的测试替身。
     */
    private class RecordingRouter(
        private val result: RuntimeResult,
    ) : RuntimeCapabilityRouter {
        var receivedContext: RuntimeRequestContext? = null
        var receivedRequest: CapabilityRequest? = null

        override suspend fun route(
            context: RuntimeRequestContext,
            request: CapabilityRequest,
        ): RuntimeResult {
            receivedContext = context
            receivedRequest = request
            return result
        }
    }
}
