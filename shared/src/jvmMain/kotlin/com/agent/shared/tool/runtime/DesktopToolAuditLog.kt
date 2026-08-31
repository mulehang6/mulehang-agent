package com.agent.shared.tool.runtime

import com.agent.shared.tool.model.ApprovalRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/** 将需要审批的本地工具操作追加到按日分割的脱敏 JSONL 审计日志。 */
class DesktopToolAuditLog(workspacePath: String) {
    private val logDirectory: Path = java.nio.file.Paths.get(workspacePath).resolve(".mulehang").resolve("audit")

    /** 记录审批决定，不写入完整补丁、命令、密钥或模型原始输出。 */
    fun record(
        request: ApprovalRequest,
        decision: String,
        source: String = "manual-user",
        reason: String = decision,
    ) = runCatching {
        Files.createDirectories(logDirectory)
        val safeSummary = redact(request.summary)
        val safePath = request.targetPath?.let(::redact).orEmpty()
        val diffSummary = request.diffs.joinToString("|") { "${it.kind}:${it.path}:${it.unifiedDiff.length}" }
        val safeSource = redact(source)
        val safeReason = redact(reason)
        val entry = """{"time":"${java.time.Instant.now()}","tool":"${request.toolName}","decision":"$decision","source":"$safeSource","reason":"$safeReason","risk":"${request.risk}","path":"$safePath","summary":"$safeSummary","diff":"${redact(diffSummary)}"}"""
        Files.writeString(
            logDirectory.resolve("${LocalDate.now()}.jsonl"),
            "$entry${System.lineSeparator()}",
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND,
        )
        log.info { "event=tool_approval tool=${request.toolName} decision=$decision source=$safeSource reason=$safeReason risk=${request.risk} diff_count=${request.diffs.size}" }
    }.getOrDefault(Unit)

    private fun redact(value: String): String = value
        .replace(Regex("(?i)(api[_-]?key|token|password)\\s*[=:]\\s*[^\\s,\"]+"), "$1=[REDACTED]")
        .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    private companion object {
        val log = KotlinLogging.logger { }
    }
}
