package com.agent.shared.tool.runtime

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * 在受限时间和内存范围内执行一次短生命周期的本地子进程。
 */
class DesktopProcessRunner(
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    private val processStarter: (ProcessBuilder) -> Process = { builder -> builder.start() },
) {
    init {
        require(maxOutputBytes > 0) { "最大输出字节数必须大于 0。" }
    }

    /**
     * 描述一次受控子进程调用。
     */
    data class Args(
        val command: List<String>,
        val workingDirectory: File,
        val timeoutMillis: Long,
        val isCancelled: () -> Boolean = { false },
    ) {
        init {
            require(command.isNotEmpty()) { "执行命令不能为空。" }
            require(timeoutMillis > 0) { "执行超时必须大于 0。" }
        }
    }

    /**
     * 表示进程的终止原因。
     */
    enum class Outcome {
        COMPLETED,
        TIMED_OUT,
        CANCELLED,
    }

    /**
     * 返回一次进程调用的受限输出和终止状态。
     */
    data class Result(
        val exitCode: Int?,
        val stdout: String,
        val stderr: String,
        val stdoutTruncated: Boolean,
        val stderrTruncated: Boolean,
        val outcome: Outcome,
    )

    /**
     * 启动进程，并在进程存活期间并发排空 stdout 与 stderr。
     */
    fun run(args: Args): Result {
        require(args.workingDirectory.isDirectory) {
            "工作目录不存在或不是目录: ${args.workingDirectory.absolutePath}"
        }
        val process = processStarter(
            ProcessBuilder(args.command).directory(args.workingDirectory),
        )
        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "mulehang-process-output").apply { isDaemon = true }
        }
        try {
            val stdout = readers.submit<CapturedOutput> { readLimited(process.inputStream) }
            val stderr = readers.submit<CapturedOutput> { readLimited(process.errorStream) }
            val outcome = waitForProcess(process, args)
            val capturedStdout = awaitOutput(stdout)
            val capturedStderr = awaitOutput(stderr)
            return Result(
                exitCode = process.exitValueOrNull(outcome),
                stdout = capturedStdout.text,
                stderr = capturedStderr.text,
                stdoutTruncated = capturedStdout.truncated,
                stderrTruncated = capturedStderr.truncated,
                outcome = outcome,
            )
        } finally {
            readers.shutdownNow()
        }
    }

    /**
     * 按轮询周期检查完成、取消与超时状态。
     */
    private fun waitForProcess(process: Process, args: Args): Outcome {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(args.timeoutMillis)
        var restoreInterrupted = false
        try {
            while (true) {
                if (args.isCancelled()) {
                    terminate(process)
                    return Outcome.CANCELLED
                }
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0) {
                    terminate(process)
                    return Outcome.TIMED_OUT
                }
                val waitMillis = minOf(
                    PROCESS_POLL_INTERVAL_MILLIS,
                    maxOf(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)),
                )
                if (process.waitFor(waitMillis, TimeUnit.MILLISECONDS)) {
                    return Outcome.COMPLETED
                }
            }
        } catch (_: InterruptedException) {
            restoreInterrupted = true
            terminate(process)
            return Outcome.CANCELLED
        } finally {
            if (restoreInterrupted) Thread.currentThread().interrupt()
        }
    }

    /**
     * 先尝试正常结束进程，再在宽限期后强制终止。
     */
    private fun terminate(process: Process) {
        process.destroy()
        try {
            if (!process.waitFor(TERMINATION_GRACE_PERIOD_MILLIS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                process.waitFor(FORCED_TERMINATION_WAIT_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 读取输入流到内存上限，同时继续排空余下字节以免子进程阻塞。
     */
    private fun readLimited(input: InputStream): CapturedOutput {
        input.use { stream ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            val captured = ByteArrayOutputStream(minOf(maxOutputBytes, STREAM_BUFFER_SIZE))
            var truncated = false
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                val remaining = maxOutputBytes - captured.size()
                if (remaining > 0) {
                    captured.write(buffer, 0, minOf(remaining, count))
                }
                if (count > remaining) truncated = true
            }
            return CapturedOutput(
                text = captured.toString(StandardCharsets.UTF_8),
                truncated = truncated,
            )
        }
    }

    /**
     * 等待输出读取任务完成，并将读取异常作为执行失败抛出。
     */
    private fun awaitOutput(output: Future<CapturedOutput>): CapturedOutput = try {
        output.get()
    } catch (error: ExecutionException) {
        throw IllegalStateException("读取子进程输出失败。", error.cause)
    }

    /**
     * 仅在进程已结束时读取退出码，超时和取消使用空值避免误导调用方。
     */
    private fun Process.exitValueOrNull(outcome: Outcome): Int? {
        if (outcome != Outcome.COMPLETED) return null
        return try {
        exitValue()
        } catch (_: IllegalThreadStateException) {
        null
        }
    }

    /**
     * 表示单个输出流的受限读取结果。
     */
    private data class CapturedOutput(
        val text: String,
        val truncated: Boolean,
    )

    private companion object {
        const val DEFAULT_MAX_OUTPUT_BYTES = 1_024 * 1_024
        const val PROCESS_POLL_INTERVAL_MILLIS = 50L
        const val TERMINATION_GRACE_PERIOD_MILLIS = 500L
        const val FORCED_TERMINATION_WAIT_MILLIS = 500L
        const val STREAM_BUFFER_SIZE = 8_192
    }
}
