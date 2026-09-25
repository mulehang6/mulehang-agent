package com.agent.shared.agent.koog

import com.agent.shared.agent.api.AgentRunRequest
import com.agent.shared.agent.status.AgentStatusSnapshot
import com.agent.shared.agent.status.AgentTodoRepository
import com.agent.shared.agent.status.toModelMessage
import com.agent.shared.persistence.DesktopPersistenceDatabase
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 汇集代码可确定的运行指标，并将模型状态消息逐字保存到数据库。 */
internal class AgentRunStatusTracker(
    private val database: DesktopPersistenceDatabase,
    private val request: AgentRunRequest,
    private val runId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val gitStatus: (String) -> String = ::readWorkspaceGitStatus,
) {
    private val startedAt = clock()
    private val toolCallCount = AtomicInteger()
    private val errorCount = AtomicInteger()
    private val errors = mutableListOf<String>()
    private var lastSnapshotFingerprint: AgentStatusSnapshot? = null
    private var contextUsageFraction: Float? = request.contextUsageFraction

    /** Provider 返回实际输入 token 后，下一次模型请求使用该值更新状态。 */
    fun updateUsage(inputTokens: Long?) {
        val window = request.contextWindow?.takeIf { it > 0 } ?: return
        if (inputTokens != null) contextUsageFraction = inputTokens.toFloat() / window
    }

    /** 记录一次工具启动；下一个模型请求将收到更新后的计数。 */
    fun toolStarted() {
        toolCallCount.incrementAndGet()
    }

    /** 记录详细工具错误，最多保留本轮最近五条。 */
    fun toolFailed(reason: String) {
        errorCount.incrementAndGet()
        synchronized(errors) {
            errors += reason
            if (errors.size > 5) errors.removeAt(0)
        }
    }

    /** 生成下一条需要附加的内部消息；未变化时不重复发送。 */
    fun pendingSnapshot(): Pair<AgentStatusSnapshot, String>? {
        val now = clock()
        val snapshot = AgentStatusSnapshot(
            timestampMillis = now,
            turnElapsedMillis = (now - startedAt).coerceAtLeast(0L),
            toolCallCount = toolCallCount.get(),
            errorCount = errorCount.get(),
            errors = synchronized(errors) { errors.toList() },
            todos = AgentTodoRepository(database).list(request.sessionId),
            workspacePath = request.workspacePath,
            gitStatus = gitStatus(request.workspacePath),
            modelId = request.profile.model,
            contextUsageFraction = contextUsageFraction,
            contextWindow = request.contextWindow,
            recoveryState = if (request.resumeRunId == null) "正常运行" else "从检查点继续",
        )
        val fingerprint = snapshot.copy(timestampMillis = 0L, turnElapsedMillis = 0L)
        val message = snapshot.toModelMessage()
        return if (fingerprint == lastSnapshotFingerprint) null else snapshot to message
    }

    /** 状态消息已附加到 Koog prompt 后保存相同文本，失败时阻止模型请求。 */
    fun persist(snapshot: AgentStatusSnapshot, messageText: String) {
        database.write { queries ->
            queries.insertStatusSnapshot(
                id = UUID.randomUUID().toString(),
                conversation_id = request.sessionId,
                run_id = runId,
                turn_id = request.userEntryId.ifBlank { runId },
                payload_version = 1L,
                payload_json = JSON.encodeToString(snapshot),
                model_message_text = messageText,
                created_at = snapshot.timestampMillis,
            )
        }
        lastSnapshotFingerprint = snapshot.copy(timestampMillis = 0L, turnElapsedMillis = 0L)
    }

    private companion object {
        val JSON = Json { encodeDefaults = true }
    }
}

/** 读取当前工作区 Git 分支与已跟踪文件的更改数。 */
private fun readWorkspaceGitStatus(workspacePath: String): String = runCatching {
    val process = ProcessBuilder("git", "-C", workspacePath, "status", "--short", "--branch", "--untracked-files=no")
        .redirectErrorStream(true)
        .start()
    val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().use { it.readLines() } }
    if (!process.waitFor(3, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching "Git 状态读取超时"
    }
    if (process.exitValue() != 0) return@runCatching "非 Git 工作区或不可读取"
    val lines = output.get(1, TimeUnit.SECONDS)
    val branch = lines.firstOrNull()?.removePrefix("## ") ?: "未知分支"
    "$branch；已跟踪文件更改 ${lines.size.minus(1).coerceAtLeast(0)} 项"
}.getOrDefault("Git 状态不可用")
