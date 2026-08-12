package com.agent.shared.tool.runtime

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.PermissionPreset
import com.agent.shared.tool.model.ToolRisk
import com.agent.shared.tool.model.QuestionPrompt
import com.agent.shared.tool.model.QuestionRequest
import com.agent.shared.tool.model.normalizeQuestionPrompts
import com.agent.shared.tool.policy.DesktopToolPolicy
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * 桌面首批本地工具集合。
 */
@LLMDescription("Mulehang local workspace tools. First discover with list_dir/glob_files/grep_code, then read_file before Update/Delete patches. File creation uses apply_patch directly with *** Add File. Never edit files through Shell. Every mutating tool is previewed and may require approval; external absolute paths are deliberate, not a fallback for workspace paths.")
class DesktopToolSet(
    private val workspacePath: String,
    private val permissionPreset: PermissionPreset,
    private val interactionBridge: DesktopToolInteractionBridge,
    private val isCancelled: () -> Boolean = { false },
    private val powerShellTool: DesktopPowerShellTool = DesktopPowerShellTool(),
    private val approvalAgent: ToolApprovalAgent = ManualFallbackToolApprovalAgent,
) : ToolSet {
    private val fileSupport = DesktopFileToolSupport(workspacePath)
    private val readWriteTools = DesktopReadWriteTools(fileSupport)
    private val globTool = DesktopGlobTool()
    private val grepTool = DesktopGrepTool()
    private val auditLog = DesktopToolAuditLog(workspacePath)
    private val shellTool = DesktopShellTool()

    /**
     * 读取文件内容。
     */
    @Tool
    @LLMDescription("Read one UTF-8 text page. Use before *** Update File or *** Delete File; output has line numbers only for navigation, so never copy the `12: ` prefix into a patch. Example: read_file(path=\"src/App.kt\", offset=1, limit=200). Binary files are rejected; absolute external paths can require approval.")
    fun read_file(
        @LLMDescription("Workspace-relative path, or an absolute path when intentionally reading an external file.") path: String,
        @LLMDescription("First line to read, 1-based. Read the target context before apply_patch.") offset: Int = 1,
        @LLMDescription("Maximum lines to return, 1 to 5000. Use another read_file page instead of requesting the entire large file.") limit: Int = 2_000,
    ): String {
        ensureExternalReadApproval(path)
        return readWriteTools.readFile(path, offset, limit)
    }

    /**
     * 列出目录内容。
     */
    @Tool
    @LLMDescription("List direct entries only, not recursive contents. Example: list_dir(path=\"src\"). Use glob_files for recursive filename discovery and read_file after selecting a file.")
    fun list_dir(
        @LLMDescription("Workspace-relative directory, or an intentionally selected absolute directory. Lists one level only, sorted and capped; use glob_files for recursive discovery.") path: String,
    ): String = readWriteTools.listDir(path)

    /**
     * 按 glob 查找文件。
     */
    @Tool
    @LLMDescription("Find workspace files by glob while excluding build, dependency and VCS directories. Example: glob_files(pattern=\"**/*Test.kt\", path=\"shared\"). Use grep_code when only file contents are known.")
    fun glob_files(
        @LLMDescription("Glob such as **/*.kt, src/**/Test*.kt, or *.json. A bare filename pattern is searched recursively.") pattern: String,
        @LLMDescription("Workspace-relative search root. Keep it as narrow as possible; default is the workspace root.") path: String = ".",
        @LLMDescription("Maximum returned paths, 1 to 200. Narrow path or pattern rather than requesting a very large list.") max_results: Int = 50,
    ): String = globTool.execute(
        DesktopGlobTool.Args(
            pattern = pattern,
            path = path,
            maxResults = max_results,
        ),
    )

    /**
     * 按关键字或正则搜索代码。
     */
    @Tool
    @LLMDescription("Search workspace text and return readable file:line matches, never raw rg JSON. Prefer literal search; set regex=true only for a valid pattern. Example: grep_code(pattern=\"FasterModelResolver\", path=\"shared\"). Read the target before changing it.")
    fun grep_code(
        @LLMDescription("Literal text by default. Set regex=true only for a valid JVM/ripgrep regular expression; escape backslashes for JSON.") pattern: String,
        @LLMDescription("Workspace-relative search root. Narrow it before increasing limits.") path: String = ".",
        @LLMDescription("Optional include glob such as *.kt or **/*Test.kt. It filters files, not matching text.") glob: String? = null,
        @LLMDescription("False means literal search. True interprets pattern as regex; do not set true for ordinary identifiers.") regex: Boolean = false,
        @LLMDescription("True by default. Set false only when casing is genuinely unknown.") case_sensitive: Boolean = true,
        @LLMDescription("Lines before and after each hit. Use 1 to 3 to obtain edit context, then still call read_file before apply_patch.") context_lines: Int = 0,
        @LLMDescription("Maximum matches, 1 to 200. A partial result means narrow the query.") max_results: Int = 50,
        @LLMDescription("Maximum result blocks retained in output, 1 to 200. This is separate from max_results.") head_limit: Int = 20,
        @LLMDescription("Maximum output characters, 200 to 100000. Prefer narrowing path/pattern over increasing it.") max_chars: Int = 24_000,
    ): String = grepTool.execute(
        DesktopGrepTool.Args(
            pattern = pattern,
            path = path,
            glob = glob,
            regex = regex,
            caseSensitive = case_sensitive,
            contextLines = context_lines,
            maxResults = max_results,
            headLimit = head_limit,
            maxChars = max_chars,
        ),
    )

    /** 应用一个 Kilo 兼容、可包含多个文件操作的受审批补丁。 */
    @Tool
    @LLMDescription("Apply Kilo patch text. The only accepted format starts with *** Begin Patch and ends with *** End Patch. Use *** Add File: path followed by +content for new files; use *** Update File: path then @@ and exact context lines (space unchanged, - remove, + add) for existing files; use *** Delete File: path with no body. One call may contain several different files. Do not send Markdown fences, JSON, unified @@ line numbers, read_file line-number prefixes, or shell commands. Exact per-file Diff is previewed before disk changes.")
    fun apply_patch(
        @LLMDescription("Complete Kilo patch text, for example: *** Begin Patch\\n*** Add File: notes.txt\\n+hello\\n*** End Patch. Update File must include one or more @@ blocks with exact unnumbered context.") patch_text: String,
    ): String {
        val pending = readWriteTools.previewPatch(patch_text)
        interactionBridge.onFileDiffPreview("apply_patch", pending.previews)
        val risk = if (pending.containsExternalWrite) ToolRisk.EXTERNAL_WRITE else ToolRisk.WORKSPACE_WRITE
        if (!ensureWriteApproval(
                summary = if (pending.containsExternalWrite) "修改工作区外文件" else "修改工作区文件",
                targetPath = pending.files.singleOrNull()?.target?.path?.toString().orEmpty(),
                payloadPreview = "${pending.files.size} 个文件补丁",
                risk = risk,
                diff = pending.previews.firstOrNull(),
                diffs = pending.previews,
            )
        ) return USER_DECLINED_TOOL_MESSAGE
        return readWriteTools.applyPatch(pending)
    }

    /**
     * 运行 PowerShell 7 脚本。
     */
    @Tool
    @LLMDescription("Run one non-interactive PowerShell 7 command only when file tools cannot perform the task. Example: run_powershell(script=\".\\gradlew.bat :shared:jvmTest\", operation_intent=\"运行 shared 单元测试\"). Working directory is the workspace; never edit files, start a server, delete/reset data, prompt interactively, or print secrets.")
    fun run_powershell(
        @LLMDescription("A non-interactive PowerShell 7 command. Do not run development servers; do not use it for file edits, deletion, resets, or credential output.") script: String,
        @LLMDescription("Required concise Chinese intent describing targets and effect, for example: 检查 shared 模块的单元测试。 Do not repeat the command verbatim.")
        @Suppress("LocalVariableName") operation_intent: String,
        @LLMDescription("Timeout in milliseconds, 1 to 600000. Default 120000; use the smallest realistic duration.")
        timeout_ms: Long = DesktopPowerShellTool.DEFAULT_TIMEOUT_MILLIS,
    ): String {
        val operationIntent = operation_intent.trim()
        check(operationIntent.isNotBlank()) { "执行 PowerShell 时必须说明操作意图。" }
        check(timeout_ms in 1..DesktopPowerShellTool.MAX_TIMEOUT_MILLIS) {
            "PowerShell 超时必须在 1 到 ${DesktopPowerShellTool.MAX_TIMEOUT_MILLIS} 毫秒之间。"
        }
        if (!ensureExecuteApproval(
            toolName = "run_powershell",
            summary = operationIntent,
            payloadPreview = script,
            risk = ToolRisk.COMMAND,
        )) return USER_DECLINED_TOOL_MESSAGE
        return powerShellTool.execute(
            DesktopPowerShellTool.Args(
                script = script,
                workingDirectory = workspacePath,
                timeoutMillis = timeout_ms,
                isCancelled = isCancelled,
                onOutput = { text, isErrorStream ->
                    interactionBridge.onToolOutputChunk(
                        toolName = "run_powershell",
                        text = text,
                        isErrorStream = isErrorStream,
                    )
                },
            ),
        )
    }

    /**
     * 向用户发起问题请求。
     */
    @Tool
    @LLMDescription("Ask only for a material user decision that tools and conversation cannot resolve. Batch current decisions in one call. Prefer questions_json, for example [{\"question\":\"选择范围\",\"options\":[\"仅当前模块\",\"整个项目\"]}]. Do not ask the user to inspect facts available through read/search tools.")
    fun ask_user(
        @LLMDescription("Legacy single question. Use only when questions_json is blank; provide a concise decision, not an open-ended status update.") question: String = "",
        @LLMDescription("Legacy choices for question. Use only with legacy question; duplicates and entries beyond five are discarded.") options: List<String> = emptyList(),
        @LLMDescription("Preferred JSON array only, for example [{\"question\":\"选择保存位置\",\"options\":[\"项目内\",\"用户目录\"]}]. Do not wrap it in Markdown or add commentary.") questions_json: String = "",
    ): String = runBlocking {
        val questions = questionPromptsForToolCall(
            question = question,
            options = options,
            questionsJson = questions_json,
        )
        interactionBridge.requestQuestion(
            QuestionRequest(
                requestId = UUID.randomUUID().toString(),
                toolCallId = "ask_user",
                questions = questions,
                allowFreeText = true,
            ),
        )
    }

    /**
     * 将优先的 JSON 问题数组或兼容单题参数规整为可展示的批量请求。
     */
    private fun questionPromptsForToolCall(
        question: String,
        options: List<String>,
        questionsJson: String,
    ): List<QuestionPrompt> {
        val rawQuestions = if (questionsJson.isBlank()) {
            listOf(QuestionPrompt(question = question, options = options))
        } else {
            runCatching {
                QUESTION_JSON.decodeFromString<List<QuestionPrompt>>(questionsJson)
            }.getOrElse { error ->
                throw IllegalArgumentException(
                    "questions_json 必须是 [{\"question\":\"...\",\"options\":[\"...\"]}] 形式的 JSON 数组。",
                    error,
                )
            }
        }
        return normalizeQuestionPrompts(rawQuestions).takeIf(List<QuestionPrompt>::isNotEmpty)
            ?: throw IllegalArgumentException("ask_user 至少需要一个非空问题。")
    }

    /**
     * 根据权限档位处理写入类工具的审批。
     */
    private fun ensureWriteApproval(
        summary: String,
        targetPath: String,
        payloadPreview: String?,
        risk: ToolRisk,
        diff: com.agent.shared.tool.model.FileDiffPreview? = null,
        diffs: List<com.agent.shared.tool.model.FileDiffPreview> = diff?.let(::listOf).orEmpty(),
    ): Boolean {
        check(!DesktopToolPolicy.isWriteDenied(permissionPreset)) {
            "当前 permission preset=$permissionPreset，禁止修改工作区文件。"
        }
        if (permissionPreset == PermissionPreset.AUTO) {
            val review = reviewAutoApproval(
                ApprovalRequest(UUID.randomUUID().toString(), "apply_patch", summary, targetPath, payloadPreview, risk, diff, diffs),
            )
            when (review.decision) {
                ApprovalDecision.ALLOW -> return true
                ApprovalDecision.DENY -> return false
                ApprovalDecision.ASK -> Unit
            }
        } else if (DesktopToolPolicy.canAutoApproveWrite(permissionPreset) && risk == ToolRisk.WORKSPACE_WRITE) {
            return true
        }
        val request = ApprovalRequest(
            requestId = UUID.randomUUID().toString(),
            toolName = "apply_patch",
            summary = summary,
            targetPath = targetPath,
            payloadPreview = payloadPreview,
            risk = risk,
            diff = diff,
            diffs = diffs,
        )
        val approved = runBlocking {
            interactionBridge.requestApproval(
                request,
            )
        }
        auditLog.record(request, if (approved) "allow" else "deny")
        return approved
    }

    /**
     * 根据权限档位处理执行类工具的审批。
     */
    private fun ensureExecuteApproval(
        toolName: String,
        summary: String,
        payloadPreview: String?,
        risk: ToolRisk,
    ): Boolean {
        check(!DesktopToolPolicy.isExecuteDenied(permissionPreset)) {
            "当前 permission preset=$permissionPreset，禁止执行命令。"
        }
        if (permissionPreset == PermissionPreset.AUTO) {
            val review = reviewAutoApproval(
                ApprovalRequest(UUID.randomUUID().toString(), toolName, summary, payloadPreview = payloadPreview, risk = risk),
            )
            when (review.decision) {
                ApprovalDecision.ALLOW -> return true
                ApprovalDecision.DENY -> return false
                ApprovalDecision.ASK -> Unit
            }
        } else if (DesktopToolPolicy.canAutoApproveExecute(permissionPreset)) {
            return true
        }
        val request = ApprovalRequest(
            requestId = UUID.randomUUID().toString(),
            toolName = toolName,
            summary = summary,
            payloadPreview = payloadPreview,
            risk = risk,
        )
        val approved = runBlocking {
            interactionBridge.requestApproval(
                request,
            )
        }
        auditLog.record(request, if (approved) "allow" else "deny")
        return approved
    }

    /**
     * 通过选定的 Windows shell 执行命令。
     * TODO: 引入 Shell 语法树后，以语法解析结果替代模型提供的 operation_intent。
     */
    @Tool
    @Suppress("unused")
    @LLMDescription("Run one non-interactive command in an explicitly selected Windows shell. Prefer run_powershell; use cmd only for batch/cmd built-ins, and a custom shell only by absolute executable path. Example: run_shell(shell=\"cmd\", command=\"dir\", operation_intent=\"列出工作区根目录\"). Never use it for file edits, servers, destructive commands, prompts, or secrets.")
    fun run_shell(
        @LLMDescription("Exactly powershell, cmd, or an absolute executable path such as C:\\Program Files\\Git\\bin\\bash.exe. Do not supply a relative custom path.") shell: String,
        @LLMDescription("One non-interactive command for the selected shell. Avoid editing files, destructive operations, servers, prompts, and credential output.") command: String,
        @LLMDescription("Required concise Chinese description of target and expected effect; this is shown for approval and is not a shell command.") operation_intent: String,
        @LLMDescription("Timeout in milliseconds, 1 to 600000. Default 120000; use the smallest realistic duration.") timeout_ms: Long = DesktopPowerShellTool.DEFAULT_TIMEOUT_MILLIS,
    ): String {
        require(operation_intent.isNotBlank()) { "执行 Shell 时必须说明操作意图。" }
        require(timeout_ms in 1..DesktopShellTool.MAX_TIMEOUT_MILLIS) { "Shell 超时必须在允许范围内。" }
        if (shellTool.isObviouslyDangerous(command)) {
            auditLog.record(
                request = ApprovalRequest(
                    requestId = UUID.randomUUID().toString(),
                    toolName = "run_shell",
                    summary = operation_intent,
                    payloadPreview = command,
                    risk = ToolRisk.DANGEROUS,
                ),
                decision = "deny",
                source = "hard-rule",
                reason = "dangerous_command",
            )
            return "命令已被安全策略拒绝。"
        }
        if (!ensureExecuteApproval("run_shell", operation_intent, command, ToolRisk.COMMAND)) return USER_DECLINED_TOOL_MESSAGE
        return shellTool.execute(
            DesktopShellTool.Args(
                shell = shell,
                command = command,
                workingDirectory = workspacePath,
                timeoutMillis = timeout_ms,
                isCancelled = isCancelled,
                onOutput = { text, isErrorStream -> interactionBridge.onToolOutputChunk("run_shell", text, isErrorStream) },
            ),
        )
    }

    /** 工作区外读取也必须显式获准，避免无意暴露用户私有文件。 */
    private fun ensureExternalReadApproval(path: String) {
        val resolved = fileSupport.resolveForRead(path)
        if (resolved.isInsideWorkspace) return
        val request = ApprovalRequest(
            requestId = UUID.randomUUID().toString(),
            toolName = "read_file",
            summary = "读取工作区外文件",
            targetPath = resolved.path.toString(),
            risk = ToolRisk.UNKNOWN,
        )
        if (permissionPreset == PermissionPreset.AUTO) {
            when (reviewAutoApproval(request).decision) {
                ApprovalDecision.ALLOW -> return
                ApprovalDecision.DENY -> {
                    error(USER_DECLINED_TOOL_MESSAGE)
                }

                ApprovalDecision.ASK -> Unit
            }
        }
        val approved = runBlocking {
            interactionBridge.requestApproval(request)
        }
        auditLog.record(request, if (approved) "allow" else "deny")
        check(approved) { USER_DECLINED_TOOL_MESSAGE }
    }

    /** 执行 AUTO 审核并以脱敏审计记录来源、结果和人工回退原因。 */
    private fun reviewAutoApproval(request: ApprovalRequest): ApprovalReview {
        val review = (approvalAgent as? DetailedToolApprovalAgent)?.review(request)
            ?: ApprovalReview(approvalAgent.decide(request), "custom-reviewer", "decision_only")
        auditLog.record(
            request = request,
            decision = review.decision.name.lowercase(),
            source = review.source,
            reason = review.reason,
        )
        return review
    }

    private companion object {
        const val USER_DECLINED_TOOL_MESSAGE = "用户已拒绝执行此操作。"
        val QUESTION_JSON = Json { ignoreUnknownKeys = false }
    }
}
