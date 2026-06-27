package com.agent.shared.agent

import java.io.File

/**
 * 执行 PowerShell 7 脚本的桌面工具实现。
 */
class DesktopPowerShellTool(
    private val shellVersionProbe: () -> String = Companion::probeVersion,
    private val commandRunner: (Args) -> ExecutionResult = Companion::runPowerShell,
) {
    /**
     * PowerShell 执行参数。
     */
    data class Args(
        val script: String,
        val workingDirectory: String? = null,
    )

    /**
     * PowerShell 执行结果。
     */
    data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        /**
         * 将执行结果转换为稳定的文本输出。
         */
        fun toDisplayString(): String {
            val lines = mutableListOf("exitCode=$exitCode")
            if (stdout.isNotBlank()) {
                lines += "stdout:"
                lines += stdout.trimEnd()
            }
            if (stderr.isNotBlank()) {
                lines += "stderr:"
                lines += stderr.trimEnd()
            }
            return lines.joinToString(separator = "\n")
        }
    }

    /**
     * 执行 PowerShell 脚本；若不是 7.x，直接返回不支持提示。
     */
    fun execute(args: Args): String {
        val version = shellVersionProbe().trim()
        if (!version.startsWith("7.")) {
            return "当前工具仅支持 PowerShell 7，请先升级后再使用。检测到版本: $version"
        }
        return commandRunner(args).toDisplayString()
    }

    /**
     * 检测本机 pwsh 版本。
     */
    companion object {
        /**
         * 检测本机 pwsh 版本。
         */
        private fun probeVersion(): String {
            val process = ProcessBuilder(
                "pwsh",
                "-NoLogo",
                "-NoProfile",
                "-Command",
                "\$PSVersionTable.PSVersion.ToString()",
            ).start()
            val stdout = process.inputReader().readText().trim()
            val stderr = process.errorReader().readText().trim()
            process.waitFor()
            return stdout.ifBlank { stderr.ifBlank { "unknown" } }
        }

        /**
         * 用 pwsh 执行实际脚本。
         */
        private fun runPowerShell(args: Args): ExecutionResult {
            val process = ProcessBuilder(
                "pwsh",
                "-NoLogo",
                "-NoProfile",
                "-Command",
                args.script,
            ).apply {
                args.workingDirectory?.let { directory(File(it)) }
            }.start()
            val stdout = process.inputReader().readText().trimEnd()
            val stderr = process.errorReader().readText().trimEnd()
            val exitCode = process.waitFor()
            return ExecutionResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
            )
        }
    }
}
