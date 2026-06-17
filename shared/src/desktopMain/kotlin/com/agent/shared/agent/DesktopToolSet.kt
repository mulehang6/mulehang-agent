package com.agent.shared.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.agent.shared.state.PermissionPreset
import java.util.UUID
import kotlinx.coroutines.runBlocking

/**
 * 桌面首批本地工具集合。
 */
@LLMDescription("Mulehang desktop local tools")
class DesktopToolSet(
    workspacePath: String,
    private val permissionPreset: PermissionPreset,
    private val interactionBridge: DesktopToolInteractionBridge,
) : ToolSet {
    private val fileSupport = DesktopFileToolSupport(workspacePath)
    private val readWriteTools = DesktopReadWriteTools(fileSupport)
    private val globTool = DesktopGlobTool()
    private val grepTool = DesktopGrepTool()
    private val powerShellTool = DesktopPowerShellTool()

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
        ensureWriteApproval(
            toolName = "write_file",
            summary = "写入工作区文件",
            targetPath = path,
            payloadPreview = content.take(240),
        )
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
        ensureWriteApproval(
            toolName = "edit_file",
            summary = "定点编辑工作区文件",
            targetPath = path,
            payloadPreview = "${oldText.take(80)} -> ${newText.take(80)}",
        )
        return readWriteTools.editFile(path, oldText, newText)
    }

    /**
     * 运行 PowerShell 7 脚本。
     */
    @Tool
    @LLMDescription("Run a PowerShell 7 script.")
    fun run_powershell(
        @LLMDescription("PowerShell script text.") script: String,
    ): String {
        ensureExecuteApproval(
            toolName = "run_powershell",
            summary = "执行 PowerShell 7 脚本",
            payloadPreview = script.take(240),
        )
        return powerShellTool.execute(DesktopPowerShellTool.Args(script = script))
    }

    /**
     * 向用户展示一段说明。
     */
    @Tool
    @LLMDescription("Show a message to the user.")
    fun say_to_user(
        @LLMDescription("Message shown to the user.") message: String,
    ): String = message

    /**
     * 向用户发起问题请求。
     */
    @Tool
    @LLMDescription("Ask the user a question with up to three options and free text.")
    fun ask_user(
        @LLMDescription("Question text.") question: String,
        @LLMDescription("Optional answer choices.") options: List<String> = emptyList(),
    ): String = runBlocking {
        interactionBridge.requestQuestion(
            QuestionRequest(
                requestId = UUID.randomUUID().toString(),
                toolCallId = "ask_user",
                question = question,
                options = options.map(String::trim).filter(String::isNotEmpty).take(3),
                allowFreeText = true,
            ),
        )
    }

    /**
     * 结束当前轮次。
     */
    @Tool
    @LLMDescription("Finish the current conversation turn.")
    fun exit(): String = "exit"

    /**
     * 根据权限档位处理写入类工具的审批。
     */
    private fun ensureWriteApproval(
        toolName: String,
        summary: String,
        targetPath: String,
        payloadPreview: String?,
    ) {
        check(!DesktopToolPolicy.isWriteDenied(permissionPreset)) {
            "当前 permission preset=$permissionPreset，禁止修改工作区文件。"
        }
        if (DesktopToolPolicy.canAutoApproveWrite(permissionPreset)) {
            return
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
        check(approved) { "用户拒绝执行写入操作。" }
    }

    /**
     * 根据权限档位处理执行类工具的审批。
     */
    private fun ensureExecuteApproval(
        toolName: String,
        summary: String,
        payloadPreview: String?,
    ) {
        check(!DesktopToolPolicy.isExecuteDenied(permissionPreset)) {
            "当前 permission preset=$permissionPreset，禁止执行命令。"
        }
        if (DesktopToolPolicy.canAutoApproveExecute(permissionPreset)) {
            return
        }
        val approved = runBlocking {
            interactionBridge.requestApproval(
                ApprovalRequest(
                    requestId = UUID.randomUUID().toString(),
                    toolName = toolName,
                    summary = summary,
                    payloadPreview = payloadPreview,
                ),
            )
        }
        check(approved) { "用户拒绝执行命令。" }
    }
}
