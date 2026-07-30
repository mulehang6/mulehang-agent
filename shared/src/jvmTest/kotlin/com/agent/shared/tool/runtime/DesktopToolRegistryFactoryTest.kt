package com.agent.shared.tool.runtime

import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.PermissionPreset
import com.agent.shared.tool.model.QuestionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
                "say_to_user",
                "exit",
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
}

internal fun fakeBridge(): DesktopToolInteractionBridge = object : DesktopToolInteractionBridge {
    override suspend fun requestQuestion(request: QuestionRequest): String = "answer"

    override suspend fun requestApproval(request: ApprovalRequest): Boolean = true
}
