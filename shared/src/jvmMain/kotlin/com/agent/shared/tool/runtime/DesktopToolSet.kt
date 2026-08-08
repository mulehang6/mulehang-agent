package com.agent.shared.tool.runtime

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.agent.shared.tool.interaction.DesktopToolInteractionBridge
import com.agent.shared.tool.model.ApprovalRequest
import com.agent.shared.tool.model.PermissionPreset
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
@LLMDescription("Mulehang desktop local tools")
class DesktopToolSet(
    workspacePath: String,
    private val permissionPreset: PermissionPreset,
    private val interactionBridge: DesktopToolInteractionBridge,
    private val isCancelled: () -> Boolean = { false },
    private val powerShellTool: DesktopPowerShellTool = DesktopPowerShellTool(),
) : ToolSet {
    private val workspacePath = workspacePath
    private val fileSupport = DesktopFileToolSupport(workspacePath)
    private val readWriteTools = DesktopReadWriteTools(fileSupport)
    private val globTool = DesktopGlobTool()
    private val grepTool = DesktopGrepTool()

    /**
     * 读取文件内容。
     */
    @Tool
    @LLMDescription("Read a text file from disk.")
    fun read_file(
        @LLMDescription("Absolute or relative path to the file.") path: String,
    ): String = readWriteTools.readFile(path)

    /**
     * 列出目录内容。
     */
    @Tool
    @LLMDescription("List directory contents.")
    fun list_dir(
        @LLMDescription("Absolute or relative path to the directory.") path: String,
    ): String = readWriteTools.listDir(path)

    /**
     * 按 glob 查找文件。
     */
    @Tool
    @LLMDescription("Find files by glob pattern.")
    fun glob_files(
        @LLMDescription("Glob pattern.") pattern: String,
        @LLMDescription("Search root path.") path: String = ".",
        @LLMDescription("Maximum number of results.") max_results: Int = 50,
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
    @LLMDescription("Search code by keyword or regex.")
    fun grep_code(
        @LLMDescription("Keyword or regex pattern.") pattern: String,
        @LLMDescription("Search root path.") path: String = ".",
        @LLMDescription("Optional file glob filter.") glob: String? = null,
        @LLMDescription("Treat pattern as regex.") regex: Boolean = false,
        @LLMDescription("Whether the match is case-sensitive.") case_sensitive: Boolean = true,
        @LLMDescription("Context lines before and after a hit.") context_lines: Int = 0,
        @LLMDescription("Maximum number of matches.") max_results: Int = 50,
        @LLMDescription("Maximum result blocks kept in the preview.") head_limit: Int = 20,
        @LLMDescription("Maximum output characters.") max_chars: Int = 24_000,
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

    /**
     * 整体写入文件。
     */
    @Tool
    @LLMDescription("Write a file inside the workspace.")
    fun write_file(
        @LLMDescription("Absolute or relative path to the file.") path: String,
        @LLMDescription("Full file content.") content: String,
    ): String {
        if (!ensureWriteApproval(
            toolName = "write_file",
            summary = "写入工作区文件",
            targetPath = path,
            payloadPreview = content.take(240),
        )) return USER_DECLINED_TOOL_MESSAGE
        return readWriteTools.writeFile(path, content)
    }

    /**
     * 定点编辑文件。
     */
    @Tool
    @LLMDescription("Apply a single targeted text replacement in a file.")
    fun edit_file(
        @LLMDescription("Absolute or relative path to the file.") path: String,
        @LLMDescription("Text to replace.") oldText: String,
        @LLMDescription("Replacement text.") newText: String,
    ): String {
        if (!ensureWriteApproval(
            toolName = "edit_file",
            summary = "定点编辑工作区文件",
            targetPath = path,
            payloadPreview = "${oldText.take(80)} -> ${newText.take(80)}",
        )) return USER_DECLINED_TOOL_MESSAGE
        return readWriteTools.editFile(path, oldText, newText)
    }

    /**
     * 运行 PowerShell 7 脚本。
     */
    @Tool
    @LLMDescription("Run a PowerShell 7 script.")
    fun run_powershell(
        @LLMDescription("PowerShell script text.") script: String,
        @LLMDescription("Short Chinese description of the command's intended operation. Must explain what the command will do.")
        @Suppress("LocalVariableName") operation_intent: String,
        @LLMDescription("Optional timeout in milliseconds. Defaults to 120000 and may not exceed 600000.")
        timeout_ms: Long = DesktopPowerShellTool.DEFAULT_TIMEOUT_MILLIS,
    ): String {
        val operationIntent = operation_intent.trim()
        check(operationIntent.isNotBlank()) { "执行 PowerShell 时必须说明操作意图。" }
        check(timeout_ms in 1..DesktopPowerShellTool.MAX_TIMEOUT_MILLIS) {
            "PowerShell 超时必须在 1 到 ${DesktopPowerShellTool.MAX_TIMEOUT_MILLIS} 毫秒之间。"
        }
        if (!ensureExecuteApproval(
            summary = operationIntent,
            payloadPreview = script,
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
    @LLMDescription("Ask all needed user questions at once. Prefer questions_json: a JSON array of objects with question and up to five options. The legacy question and options fields remain supported for one question.")
    fun ask_user(
        @LLMDescription("Legacy single question text. Use only when questions_json is blank.") question: String = "",
        @LLMDescription("Legacy answer choices for question. Use only when questions_json is blank.") options: List<String> = emptyList(),
        @LLMDescription("Preferred JSON array: [{\"question\":\"...\",\"options\":[\"...\"]}]. Ask all needed questions in this single array.") questions_json: String = "",
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
        toolName: String,
        summary: String,
        targetPath: String,
        payloadPreview: String?,
    ): Boolean {
        check(!DesktopToolPolicy.isWriteDenied(permissionPreset)) {
            "当前 permission preset=$permissionPreset，禁止修改工作区文件。"
        }
        if (DesktopToolPolicy.canAutoApproveWrite(permissionPreset)) {
            return true
        }
        val approved = runBlocking {
            interactionBridge.requestApproval(
                ApprovalRequest(
                    requestId = UUID.randomUUID().toString(),
                    toolName = toolName,
                    summary = summary,
                    targetPath = targetPath,
                    payloadPreview = payloadPreview,
                ),
            )
        }
        return approved
    }

    /**
     * 根据权限档位处理执行类工具的审批。
     */
    private fun ensureExecuteApproval(
        summary: String,
        payloadPreview: String?,
    ): Boolean {
        check(!DesktopToolPolicy.isExecuteDenied(permissionPreset)) {
            "当前 permission preset=$permissionPreset，禁止执行命令。"
        }
        if (DesktopToolPolicy.canAutoApproveExecute(permissionPreset)) {
            return true
        }
        val approved = runBlocking {
            interactionBridge.requestApproval(
                ApprovalRequest(
                    requestId = UUID.randomUUID().toString(),
                    toolName = "run_powershell",
                    summary = summary,
                    payloadPreview = payloadPreview,
                ),
            )
        }
        return approved
    }

    private companion object {
        const val USER_DECLINED_TOOL_MESSAGE = "用户已拒绝执行此操作。"
        val QUESTION_JSON = Json { ignoreUnknownKeys = false }
    }
}
