package com.agent.shared.tool.runtime

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证受控子进程运行器的输出和生命周期边界。
 */
class DesktopProcessRunnerTest {
    /**
     * 进程尚未退出时，已读取的 stdout 必须立即回调给调用方。
     */
    @Test
    fun `should emit stdout before a long running process completes`() {
        val firstOutput = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<DesktopProcessRunner.Result> {
                DesktopProcessRunner().run(
                    DesktopProcessRunner.Args(
                        command = cmd("echo first & ping 127.0.0.1 -n 3 > nul & echo second"),
                        workingDirectory = temporaryDirectory(),
                        timeoutMillis = 5_000,
                        onStdoutChunk = { chunk ->
                            if (chunk.contains("first")) firstOutput.countDown()
                        },
                    ),
                )
            }

            assertTrue(firstOutput.await(2, TimeUnit.SECONDS))
            assertTrue(!future.isDone)
            assertEquals(
                listOf("first", "second"),
                future.get().stdout.lineSequence().map(String::trim).filter(String::isNotEmpty).toList(),
            )
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * stdout 和 stderr 都持续产生数据时，运行器必须同时排空两条管道。
     */
    @Test
    fun `should drain stdout and stderr concurrently`() {
        val result = DesktopProcessRunner().run(
            DesktopProcessRunner.Args(
                command = cmd("for /L %i in (1,1,20000) do @echo stdout-%i & @echo stderr-%i 1>&2"),
                workingDirectory = temporaryDirectory(),
                timeoutMillis = 5_000,
            ),
        )

        assertEquals(DesktopProcessRunner.Outcome.COMPLETED, result.outcome)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("stdout-1"))
        assertTrue(result.stderr.contains("stderr-1"))
    }

    /**
     * 输出达到内存边界后仍要继续排空进程管道，并向调用方报告截断。
     */
    @Test
    fun `should report truncation while draining complete process output`() {
        val result = DesktopProcessRunner(maxOutputBytes = 64).run(
            DesktopProcessRunner.Args(
                command = cmd("for /L %i in (1,1,2000) do @echo a-very-long-output-line-%i"),
                workingDirectory = temporaryDirectory(),
                timeoutMillis = 5_000,
            ),
        )

        assertEquals(DesktopProcessRunner.Outcome.COMPLETED, result.outcome)
        assertTrue(result.stdoutTruncated)
        assertTrue(result.stdout.length <= 64)
    }

    /**
     * 超时的命令必须被终止，而不是无限等待。
     */
    @Test
    fun `should terminate process after timeout`() {
        val result = DesktopProcessRunner().run(
            DesktopProcessRunner.Args(
                command = cmd("ping 127.0.0.1 -n 10 > nul"),
                workingDirectory = temporaryDirectory(),
                timeoutMillis = 100,
            ),
        )

        assertEquals(DesktopProcessRunner.Outcome.TIMED_OUT, result.outcome)
        assertEquals(null, result.exitCode)
    }

    /**
     * 取消信号到达后必须终止正在执行的命令。
     */
    @Test
    fun `should terminate process when cancelled`() {
        val cancelled = AtomicBoolean(true)

        val result = DesktopProcessRunner().run(
            DesktopProcessRunner.Args(
                command = cmd("ping 127.0.0.1 -n 10 > nul"),
                workingDirectory = temporaryDirectory(),
                timeoutMillis = 5_000,
                isCancelled = cancelled::get,
            ),
        )

        assertEquals(DesktopProcessRunner.Outcome.CANCELLED, result.outcome)
        assertEquals(null, result.exitCode)
    }

    /**
     * 组装 Windows 命令解释器调用。
     */
    private fun cmd(command: String): List<String> = listOf("cmd.exe", "/c", command)

    /**
     * 返回存在的运行目录，避免进程继承不可预测的当前目录。
     */
    private fun temporaryDirectory(): File = File(System.getProperty("java.io.tmpdir"))
}
