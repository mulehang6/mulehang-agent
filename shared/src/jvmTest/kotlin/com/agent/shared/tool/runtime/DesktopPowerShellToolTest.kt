package com.agent.shared.tool.runtime

import java.io.IOException
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

    /**
     * 同一工具实例的版本探测只应在首次执行时发生一次。
     */
    @Test
    fun `should cache PowerShell version probe`() {
        var probeCount = 0
        val tool = DesktopPowerShellTool(
            shellVersionProbe = {
                probeCount += 1
                "7.5.1"
            },
            commandRunner = {
                DesktopPowerShellTool.ExecutionResult(
                    exitCode = 0,
                    stdout = "ok",
                    stderr = "",
                )
            },
        )

        tool.execute(DesktopPowerShellTool.Args(script = "Write-Output 'first'"))
        tool.execute(DesktopPowerShellTool.Args(script = "Write-Output 'second'"))

        assertEquals(1, probeCount)
    }

    /**
     * 超时和截断必须以可读文本反馈给模型，而不是伪装成普通退出码。
     */
    @Test
    fun `should report timeout and output truncation`() {
        val tool = DesktopPowerShellTool(
            shellVersionProbe = { "7.5.1" },
            commandRunner = {
                DesktopPowerShellTool.ExecutionResult(
                    exitCode = null,
                    stdout = "partial",
                    stderr = "error",
                    stdoutTruncated = true,
                    stderrTruncated = true,
                    outcome = DesktopProcessRunner.Outcome.TIMED_OUT,
                )
            },
        )

        val result = tool.execute(DesktopPowerShellTool.Args(script = "Start-Sleep 999"))

        assertTrue(result.contains("超时"))
        assertTrue(result.contains("stdout 已在内存安全上限处截断"))
        assertTrue(result.contains("stderr 已在内存安全上限处截断"))
    }

    /**
     * pwsh 无法启动时，工具必须返回可读错误而不是让整轮 Agent 崩溃。
     */
    @Test
    fun `should return readable error when PowerShell cannot start`() {
        val tool = DesktopPowerShellTool(
            shellVersionProbe = { "7.5.1" },
            commandRunner = { throw IOException("pwsh not found") },
        )

        val result = runCatching {
            tool.execute(DesktopPowerShellTool.Args(script = "Get-Location"))
        }.getOrNull()

        assertTrue(result.orEmpty().contains("无法启动 PowerShell"))
    }
}
