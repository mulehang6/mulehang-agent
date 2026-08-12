package com.agent.shared.tool.runtime

import java.io.File

/** 支持 PowerShell、cmd 与显式指定可执行文件的受限 Windows Shell 执行器。 */
class DesktopShellTool {
    /** 执行参数；自定义 shell 必须是绝对可执行路径。 */
    data class Args(
        val shell: String,
        val command: String,
        val workingDirectory: String,
        val timeoutMillis: Long,
        val isCancelled: () -> Boolean,
        val onOutput: (String, Boolean) -> Unit,
    )

    /** 执行命令；明显破坏性命令应在调用前拒绝。 */
    fun execute(args: Args): String {
        require(!isObviouslyDangerous(args.command)) { "命令包含被硬性拒绝的明显破坏性操作。" }
        val executable = resolveShell(args.shell)
        val command = when (args.shell.lowercase()) {
            "powershell", "pwsh" -> listOf(executable, "-NoLogo", "-NoProfile", "-Command", args.command)
            "cmd" -> listOf(executable, "/d", "/s", "/c", args.command)
            else -> listOf(executable, "-c", args.command)
        }
        val result = DesktopProcessRunner().run(
            DesktopProcessRunner.Args(
                command = command,
                workingDirectory = File(args.workingDirectory),
                timeoutMillis = args.timeoutMillis.coerceIn(1, MAX_TIMEOUT_MILLIS),
                isCancelled = args.isCancelled,
                onStdoutChunk = { text -> args.onOutput(text, false) },
                onStderrChunk = { text -> args.onOutput(text, true) },
            ),
        )
        return DesktopPowerShellTool.ExecutionResult(
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
            stdoutTruncated = result.stdoutTruncated,
            stderrTruncated = result.stderrTruncated,
            outcome = result.outcome,
        ).toDisplayString()
    }

    /** 已知的整盘、用户目录或 Git 历史破坏命令必须 fail closed。 */
    fun isObviouslyDangerous(command: String): Boolean = DANGEROUS_COMMANDS.any { it.containsMatchIn(command) }

    private fun resolveShell(shell: String): String = when (shell.lowercase()) {
        "powershell", "pwsh" -> "pwsh"
        "cmd" -> "cmd.exe"
        else -> {
            val executable = java.nio.file.Paths.get(shell)
            require(executable.isAbsolute && java.nio.file.Files.isExecutable(executable)) { "自定义 shell 必须是可执行的绝对路径。" }
            executable.toString()
        }
    }

    companion object {
        /** Shell 命令允许的最长执行时间。 */
        const val MAX_TIMEOUT_MILLIS = 600_000L
        val DANGEROUS_COMMANDS = listOf(
            Regex("(?i)\\brm\\s+-rf\\s+[/~]"),
            Regex("(?i)\\bremove-item\\b.*\\b-recurse\\b.*\\b-force\\b"),
            Regex("(?i)\\bformat-volume\\b|\\bdiskpart\\b|\\bclear-disk\\b"),
            Regex("(?i)\\bgit\\s+(reset\\s+--hard|clean\\s+-[a-z]*f)"),
        )
    }
}
