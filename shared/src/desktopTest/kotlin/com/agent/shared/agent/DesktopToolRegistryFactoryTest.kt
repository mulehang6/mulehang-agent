package com.agent.shared.agent

import com.agent.shared.state.PermissionPreset
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
     * PowerShell 审批标题应带上脚本首行，避免所有运行脚本请求都显示同一个固定标题。
     */
    @Test
    fun `powershell approval summary should include script headline`() {
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

        assertFailsWith<IllegalStateException> {
            toolSet.run_powershell("Get-Location\nGet-ChildItem")
        }

        assertEquals("执行 PowerShell: Get-Location", capturedApproval?.summary)
    }

    /**
     * PowerShell 审批标题生成应折叠空白并在空脚本时回退到稳定默认值。
     */
    @Test
    fun `powershell approval summary builder should normalize script text`() {
        assertEquals(
            "执行 PowerShell: Get-ChildItem -Force",
            buildPowerShellApprovalSummary("  Get-ChildItem   -Force  ")
        )
        assertEquals("执行 PowerShell 7 脚本", buildPowerShellApprovalSummary("  \n  "))
    }
}

internal fun fakeBridge(): DesktopToolInteractionBridge = object : DesktopToolInteractionBridge {
    override suspend fun requestQuestion(request: QuestionRequest): String = "answer"

    override suspend fun requestApproval(request: ApprovalRequest): Boolean = true
}
