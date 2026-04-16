package com.agent.runtime

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 capability router 只暴露与 runtime 契约相关的抽象。
 */
class RuntimeCapabilityRouterTest {

    @Test
    fun `should expose transport agnostic routing contract`() = runTest {
        val context = RuntimeRequestContext(sessionId = "session-1", requestId = "request-1")
        val request = RuntimeCapabilityRequest(capabilityId = "mcp.list")
        val router: RuntimeCapabilityRouter = object : RuntimeCapabilityRouter {
            override suspend fun route(
                context: RuntimeRequestContext,
                request: CapabilityRequest,
            ): RuntimeResult = RuntimeSuccess(
                events = listOf(RuntimeInfoEvent(message = "${context.requestId}:${request.capabilityId}")),
            )
        }

        val result = router.route(context = context, request = request)

        assertTrue(RuntimeCapabilityRouter::class.java.isInterface)
        assertEquals(listOf("route"), RuntimeCapabilityRouter::class.java.declaredMethods.map { it.name }.distinct())
        assertEquals(
            RuntimeSuccess(events = listOf(RuntimeInfoEvent(message = "request-1:mcp.list"))),
            result,
        )
    }
}
