package com.agent.app.tool.interaction

import com.agent.shared.tool.model.ApprovalRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证桌面工具审批协调器的会话级审批选择。
 */
class DesktopToolInteractionCoordinatorTest {

    /**
     * 用户选择始终允许后，同一工具类型的后续请求不应再次阻塞等待 UI。
     */
    @Test
    fun `should auto approve later requests of an approved tool type`() = runBlocking {
        val coordinator = DesktopToolInteractionCoordinator()
        val request = ApprovalRequest(
            requestId = "first",
            toolName = "run_powershell",
            summary = "读取进程列表",
        )
        val firstResult = async { coordinator.requestApproval(request) }

        yield()
        assertTrue(coordinator.submitApproval(ApprovalResponse.APPROVE_TOOL_TYPE))
        assertTrue(firstResult.await())
        assertTrue(coordinator.isApprovalAutoApproved(request.copy(requestId = "second")))
    }

    /**
     * 拒绝并停止应以拒绝结果释放等待中的工具调用。
     */
    @Test
    fun `should reject a pending tool approval when stopping`() = runBlocking {
        val coordinator = DesktopToolInteractionCoordinator()
        val result = async {
            coordinator.requestApproval(
                ApprovalRequest(
                    requestId = "reject",
                    toolName = "run_powershell",
                    summary = "读取进程列表",
                ),
            )
        }

        yield()
        assertTrue(coordinator.submitApproval(ApprovalResponse.REJECT_AND_STOP))
        assertFalse(result.await())
    }
}
