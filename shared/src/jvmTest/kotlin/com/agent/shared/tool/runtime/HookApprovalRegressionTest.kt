package com.agent.shared.tool.runtime

import com.agent.shared.agent.hook.*
import com.agent.shared.settings.model.AgentHookEvent
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/** 验证 Hook 与原生权限预设组合时，人工决策不会被后续自动放行覆盖。 */
class HookApprovalRegressionTest {
    /** 写文件和命令工具在可自动执行档位下仍必须等待 Hook 要求的人工决定。 */
    @Test
    fun `permission ask should bypass automatic reviewers and presets`() {
        for (preset in listOf(PermissionPreset.AUTO, PermissionPreset.EDIT_ALLOW, PermissionPreset.BRAVE)) {
            val workspace = Files.createTempDirectory("hook-approval")
            val bridge = RejectingRecordingBridge()
            val tools = DesktopToolSet(
                workspacePath = workspace.toString(), permissionPreset = preset,
                interactionBridge = bridge,
                approvalAgent = { error("ASK must bypass the reviewer") },
                hookDispatcher = dispatcher(AgentHookEvent.PERMISSION_REQUEST),
            )
            tools.apply_patch("*** Begin Patch\n*** Add File: result.txt\n+hello\n*** End Patch")
            tools.run_powershell("Write-Output hello", "测试审批")
            tools.run_shell("cmd", "echo hello", "测试审批")
            assertEquals(3, bridge.requests.size)
            assertTrue(bridge.requests.all { it.forceManual })
            assertFalse(Files.exists(workspace.resolve("result.txt")))
        }
    }

    /** 外部读取被拒绝时不能读取文件，也不能回退到自动审批。 */
    @Test
    fun `external read should preserve manual hook decision`() {
        val bridge = RejectingRecordingBridge()
        val tools = DesktopToolSet(
            Files.createTempDirectory("hook-workspace").toString(), PermissionPreset.AUTO, bridge,
            approvalAgent = { error("ASK must bypass the reviewer") },
            hookDispatcher = dispatcher(AgentHookEvent.PERMISSION_REQUEST),
        )
        val external = Files.createTempFile("hook-external", ".txt")
        assertFailsWith<IllegalStateException> { tools.read_file(external.toString()) }
        assertTrue(bridge.requests.single().forceManual)
    }

    /** PreToolUse 的确认卡同样必须携带不可复用授权的标记。 */
    @Test
    fun `pre tool ask should force a fresh approval`() {
        val bridge = RejectingRecordingBridge()
        val tools = DesktopToolSet(
            Files.createTempDirectory("hook-pre").toString(), PermissionPreset.BRAVE, bridge,
            hookDispatcher = dispatcher(AgentHookEvent.PRE_TOOL_USE),
        )
        assertFailsWith<IllegalStateException> { tools.run_shell("cmd", "echo hello", "测试审批") }
        assertTrue(bridge.requests.single().forceManual)
    }

    /** 指定事件要求人工确认，其他事件继续。 */
    private fun dispatcher(event: AgentHookEvent) = object : AgentHookDispatcher {
        override suspend fun dispatch(request: AgentHookDispatchRequest) = AgentHookDispatchResult(
            if (request.event == event) AgentHookDecision.ASK else AgentHookDecision.CONTINUE,
        )
    }

    /** 记录审批而拒绝副作用，避免测试实际启动命令或写入目标文件。 */
    private class RejectingRecordingBridge : DesktopToolInteractionBridge {
        val requests = mutableListOf<ApprovalRequest>()
        override suspend fun requestQuestion(request: QuestionRequest): String = error("unexpected question")
        override suspend fun requestApproval(request: ApprovalRequest): Boolean {
            requests += request
            return false
        }
    }
}
