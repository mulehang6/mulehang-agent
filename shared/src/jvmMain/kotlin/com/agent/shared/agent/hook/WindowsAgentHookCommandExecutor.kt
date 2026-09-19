package com.agent.shared.agent.hook

import com.agent.shared.tool.runtime.DesktopProcessRunner
import java.io.File

/** 用 Windows cmd 执行用户全局设置中的 Hook 命令。 */
class WindowsAgentHookCommandExecutor(
    private val processRunner: DesktopProcessRunner = DesktopProcessRunner(HOOK_OUTPUT_LIMIT_BYTES),
) : AgentHookCommandExecutor {
    /** 执行一条 Hook 命令并保留 stdout 与 stderr，以供事件语义判定。 */
    override fun execute(
        command: String,
        workingDirectory: File,
        input: String,
        timeoutMillis: Long,
    ): AgentHookCommandResult = executeCancellable(command, workingDirectory, input, timeoutMillis) { false }

    /** 将协程取消传递给进程轮询，超时与取消均回收进程树。 */
    override fun executeCancellable(
        command: String,
        workingDirectory: File,
        input: String,
        timeoutMillis: Long,
        isCancelled: () -> Boolean,
    ): AgentHookCommandResult {
        val result = processRunner.run(
            DesktopProcessRunner.Args(
                command = listOf("cmd.exe", "/d", "/s", "/c", command),
                workingDirectory = workingDirectory,
                timeoutMillis = timeoutMillis,
                standardInput = input,
                isCancelled = isCancelled,
            ),
        )
        return AgentHookCommandResult(
            stdout = result.stdout,
            stderr = result.stderr,
            exitCode = result.exitCode,
            timedOut = result.outcome == DesktopProcessRunner.Outcome.TIMED_OUT,
        )
    }

    private companion object {
        const val HOOK_OUTPUT_LIMIT_BYTES = 64 * 1024
    }
}
