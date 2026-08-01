package com.agent.shared.tool.runtime

import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.PermissionPreset
import com.agent.shared.tool.model.QuestionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 验证首批桌面工具是否都已注册。
 */
class DesktopToolRegistryFactoryTest {
    /**
     * 工厂创建的注册表应包含首批工具名集合。
     */
    @Test
    fun `registry should contain first batch tool names`() {
        val registry = DesktopToolRegistryFactory(
            workspacePath = "D:\\repo",
            permissionPreset = PermissionPreset.DEFAULT,
            interactionBridge = fakeBridge(),
        ).create()

        val names = registry.tools.map { it.name }.toSet()

        assertEquals(
            setOf(
                "read_file",
                "list_dir",
                "glob_files",
                "grep_code",
                "write_file",
                "edit_file",
                "run_powershell",
                "ask_user",
            ),
            names,
        )
    }

    /**
     * PowerShell 审批请求应保留模型显式传入的操作意图，而不是从命令文本猜测。
     */
    @Test
    fun `powershell approval summary should use explicit operation intent`() {
        var capturedApproval: ApprovalRequest? = null
        val toolSet = DesktopToolSet(
            workspacePath = "D:\\repo",
            permissionPreset = PermissionPreset.DEFAULT,
            interactionBridge = object : DesktopToolInteractionBridge {
                override suspend fun requestQuestion(request: QuestionRequest): String = "answer"

                override suspend fun requestApproval(request: ApprovalRequest): Boolean {
                    capturedApproval = request
                    return false
                }
            },
        )

        val result = toolSet.run_powershell(
            script = "Get-Location\nGet-ChildItem",
            operation_intent = "查看当前工作目录和文件列表",
        )

        assertEquals("用户已拒绝执行此操作。", result)
        assertEquals("查看当前工作目录和文件列表", capturedApproval?.summary)
    }

    /**
     * PowerShell 命令没有可读意图时，不得向用户发起缺少上下文的审批请求。
     */
    @Test
    fun `powershell should reject blank operation intent before requesting approval`() {
        var approvalRequested = false
        val toolSet = DesktopToolSet(
            workspacePath = "D:\\repo",
            permissionPreset = PermissionPreset.DEFAULT,
            interactionBridge = object : DesktopToolInteractionBridge {
                override suspend fun requestQuestion(request: QuestionRequest): String = "answer"

                override suspend fun requestApproval(request: ApprovalRequest): Boolean {
                    approvalRequested = true
                    return true
                }
            },
        )

        assertFailsWith<IllegalStateException> {
            toolSet.run_powershell(script = "Get-Location", operation_intent = " ")
        }

        assertEquals(false, approvalRequested)
    }

    /**
     * Agent 命令应固定在当前工作区执行，并把取消状态交给底层执行器。
     */
    @Test
    fun `powershell should use workspace directory and cancellation signal`() {
        var capturedArgs: DesktopPowerShellTool.Args? = null
        val tool = DesktopPowerShellTool(
            shellVersionProbe = { "7.5.1" },
            commandRunner = { args ->
                capturedArgs = args
                DesktopPowerShellTool.ExecutionResult(
                    exitCode = 0,
                    stdout = "ok",
                    stderr = "",
                )
            },
        )
        val toolSet = DesktopToolSet(
            workspacePath = "D:\\workspace",
            permissionPreset = PermissionPreset.DEFAULT,
            interactionBridge = fakeBridge(),
            isCancelled = { true },
            powerShellTool = tool,
        )

        toolSet.run_powershell(
            script = "Get-Location",
            operation_intent = "查看当前工作目录",
        )

        val args = requireNotNull(capturedArgs)
        assertEquals("D:\\workspace", args.workingDirectory)
        assertTrue(args.isCancelled())
        assertEquals(DesktopPowerShellTool.DEFAULT_TIMEOUT_MILLIS, args.timeoutMillis)
    }
}

internal fun fakeBridge(): DesktopToolInteractionBridge = object : DesktopToolInteractionBridge {
    override suspend fun requestQuestion(request: QuestionRequest): String = "answer"

    override suspend fun requestApproval(request: ApprovalRequest): Boolean = true
}
