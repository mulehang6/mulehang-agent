package com.agent.app.tool.interaction

import com.agent.shared.tool.model.ApprovalRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证桌面工具审批协调器的单次与持续授权行为。 */
class DesktopToolInteractionCoordinatorTest {
    /** Hook 强制确认不能被先前的持续授权跳过。 */
    @Test
    fun `should require a fresh answer when hook forces manual approval`() = runTest {
        val coordinator = DesktopToolInteractionCoordinator()
        val request = ApprovalRequest("first", "read_file", "读取")
        val first = async { coordinator.requestApproval(request) }
        yield()
        coordinator.submitApproval(ApprovalResponse.APPROVE_TOOL_TYPE)
        assertTrue(first.await())
        val forced = request.copy(requestId = "forced", forceManual = true)
        assertFalse(coordinator.isApprovalAutoApproved(forced))
        val next = async { coordinator.requestApproval(forced) }
        yield()
        assertFalse(next.isCompleted)
        coordinator.submitApproval(ApprovalResponse.REJECT_AND_STOP)
        assertFalse(next.await())
    }

    /** 用户选择持续允许后，同一工具类型的后续请求无需等待 UI。 */
    @Test
    fun `should auto approve later requests of an approved tool type`() = runTest {
        val coordinator = DesktopToolInteractionCoordinator()
        val request = ApprovalRequest("first", "run_powershell", "读取进程列表")
        val firstResult = async { coordinator.requestApproval(request) }

        yield()
        assertTrue(coordinator.submitApproval(ApprovalResponse.APPROVE_TOOL_TYPE))
        assertTrue(firstResult.await())
        assertTrue(coordinator.isApprovalAutoApproved(request.copy(requestId = "second")))
    }

    /** 拒绝并停止应以拒绝结果释放等待中的工具调用。 */
    @Test
    fun `should reject a pending tool approval when stopping`() = runTest {
        val coordinator = DesktopToolInteractionCoordinator()
        val result = async {
            coordinator.requestApproval(ApprovalRequest("reject", "run_powershell", "读取进程列表"))
        }

        yield()
        assertTrue(coordinator.submitApproval(ApprovalResponse.REJECT_AND_STOP))
        assertFalse(result.await())
    }

    /** 从数据库恢复的持续授权应放行本轮后续同类工具调用。 */
    @Test
    fun `remembered approval allows later matching tool calls`() = runTest {
        val coordinator = DesktopToolInteractionCoordinator()
        val request = ApprovalRequest("request", "run_powershell", "执行命令")

        coordinator.rememberApproval(request)

        assertTrue(coordinator.isApprovalAutoApproved(request))
        assertTrue(coordinator.requestApproval(request))
    }
}
