package com.agent.shared.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 PowerShell 7 工具的版本边界和执行结果格式。
 */
class DesktopPowerShellToolTest {
    /**
     * 非 PowerShell 7 环境应直接返回不支持提示。
     */
    @Test
    fun `should fail clearly when pwsh is unavailable or not version 7`() {
        val tool = DesktopPowerShellTool(
            shellVersionProbe = { "5.1.22621.2506" },
            commandRunner = { error("should not run") },
        )

        val result = tool.execute(
            DesktopPowerShellTool.Args(script = "Get-Location"),
        )

        assertTrue(result.contains("仅支持 PowerShell 7"))
    }

    /**
     * PowerShell 7 环境应返回执行输出。
     */
    @Test
    fun `should execute script when pwsh 7 is available`() {
        val tool = DesktopPowerShellTool(
            shellVersionProbe = { "7.5.1" },
            commandRunner = {
                DesktopPowerShellTool.ExecutionResult(
                    exitCode = 0,
                    stdout = "ok",
                    stderr = "",
                )
            },
        )

        val result = tool.execute(
            DesktopPowerShellTool.Args(script = "Write-Output 'ok'"),
        )

        assertEquals("exitCode=0\nstdout:\nok", result)
    }
}
