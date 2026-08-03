package com.agent.shared.tool.runtime

import java.io.File

/**
 * 执行 PowerShell 7 脚本的桌面工具实现。
 */
class DesktopPowerShellTool(
    private val shellVersionProbe: () -> String = Companion::probeVersion,
    private val commandRunner: (Args) -> ExecutionResult = Companion::runPowerShell,
) {
    private val detectedVersion: String by lazy(shellVersionProbe)

    /**
     * PowerShell 执行参数。
     */
    data class Args(
        val script: String,
        val workingDirectory: String? = null,
        val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        val isCancelled: () -> Boolean = { false },
        val onOutput: (text: String, isErrorStream: Boolean) -> Unit = { _, _ -> },
    )

    /**
     * PowerShell 执行结果。
     */
    data class ExecutionResult(
        val exitCode: Int?,
        val stdout: String,
        val stderr: String,
        val stdoutTruncated: Boolean = false,
        val stderrTruncated: Boolean = false,
        val outcome: DesktopProcessRunner.Outcome = DesktopProcessRunner.Outcome.COMPLETED,
    ) {
        /**
         * 将执行结果转换为稳定的文本输出。
         */
        fun toDisplayString(): String {
            val lines = mutableListOf<String>()
            when (outcome) {
                DesktopProcessRunner.Outcome.COMPLETED -> exitCode?.let { lines += "exitCode=$it" }
                DesktopProcessRunner.Outcome.TIMED_OUT -> lines += "命令执行超时，已终止。"
                DesktopProcessRunner.Outcome.CANCELLED -> lines += "命令执行已取消，已终止。"
            }
            if (stdout.isNotBlank()) {
                lines += "stdout:"
                lines += stdout.trimEnd()
            }
            if (stderr.isNotBlank()) {
                lines += "stderr:"
                lines += stderr.trimEnd()
            }
            if (stdoutTruncated) lines += "stdout 已在内存安全上限处截断。"
            if (stderrTruncated) lines += "stderr 已在内存安全上限处截断。"
            return lines.joinToString(separator = "\n")
        }
    }

    /**
     * 执行 PowerShell 脚本；若不是 7.x，直接返回不支持提示。
     */
    fun execute(args: Args): String {
        val version = detectedVersion.trim()
        if (!version.startsWith("7.")) {
            return "当前工具仅支持 PowerShell 7，请先升级后再使用。检测到版本: $version"
        }
        return runCatching { commandRunner(args).toDisplayString() }
            .getOrElse { error -> "无法启动 PowerShell: ${error.message ?: error::class.simpleName}" }
    }

    /**
     * 检测本机 pwsh 版本。
     */
    companion object {
        /**
         * 检测本机 pwsh 版本。
         */
        private fun probeVersion(): String {
            val result = runCatching {
                DesktopProcessRunner().run(
                    DesktopProcessRunner.Args(
                        command = listOf(
                            "pwsh",
                            "-NoLogo",
                            "-NoProfile",
                            "-Command",
                            "\$PSVersionTable.PSVersion.ToString()",
                        ),
                        workingDirectory = File(System.getProperty("user.dir")),
                        timeoutMillis = VERSION_PROBE_TIMEOUT_MILLIS,
                    ),
                )
            }.getOrElse { return "unknown" }
            return result.stdout.trim().ifBlank { result.stderr.trim().ifBlank { "unknown" } }
        }

        /**
         * 用 pwsh 执行实际脚本。
         */
        private fun runPowerShell(args: Args): ExecutionResult {
            val result = DesktopProcessRunner().run(
                DesktopProcessRunner.Args(
                    command = listOf(
                        "pwsh",
                        "-NoLogo",
                        "-NoProfile",
                        "-Command",
                        args.script,
                    ),
                    workingDirectory = args.workingDirectory?.let(::File) ?: File(System.getProperty("user.dir")),
                    timeoutMillis = args.timeoutMillis.coerceIn(1, MAX_TIMEOUT_MILLIS),
                    isCancelled = args.isCancelled,
                    onStdoutChunk = { text -> args.onOutput(text, false) },
                    onStderrChunk = { text -> args.onOutput(text, true) },
                ),
            )
            return ExecutionResult(
                exitCode = result.exitCode,
                stdout = result.stdout.trimEnd(),
                stderr = result.stderr.trimEnd(),
                stdoutTruncated = result.stdoutTruncated,
                stderrTruncated = result.stderrTruncated,
                outcome = result.outcome,
            )
        }

        const val DEFAULT_TIMEOUT_MILLIS = 120_000L
        const val MAX_TIMEOUT_MILLIS = 600_000L
        private const val VERSION_PROBE_TIMEOUT_MILLIS = 10_000L
    }
}
