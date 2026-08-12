package com.agent.app.chat.presentation

import com.agent.app.chat.state.ChatConversationUiState
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import com.agent.shared.tool.model.FileChangeKind
import com.agent.shared.tool.model.FileDiffPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 验证聊天时间线展示文案的最小规则。
 */
class ConversationPresentationTest {

    /**
     * 运行中的会话不应在时间线末尾重复显示工作目录状态。
     */
    @Test
    fun `should not render running workspace status`() {
        assertNull(
            buildSecondaryStatus(
                ChatConversationUiState(
                    id = "conversation",
                    title = "Title",
                    workspacePath = "D:\\repo\\mulehang-agent",
                    executionState = ExecutionState.Running,
                ),
            ),
        )
    }

    /**
     * 已完成思考应使用易读的整秒耗时文案。
     */
    @Test
    fun `should format completed reasoning duration in seconds`() {
        assertEquals("Thought for 2s", buildReasoningDurationLabel(2_001))
    }

    /**
     * 不满一秒的思考应保留毫秒精度，避免显示为零秒。
     */
    @Test
    fun `should format subsecond reasoning duration in milliseconds`() {
        assertEquals("Thought for 430ms", buildReasoningDurationLabel(430))
    }

    /**
     * 聊天正文应直接显示内容，不再拼接角色前缀。
     */
    @Test
    fun `should render chat message without role prefix`() {
        val userItem = ChatMessageItem(ChatMessage(role = ChatRole.User, content = "你好"))
        val assistantItem = ChatMessageItem(ChatMessage(role = ChatRole.Assistant, content = "世界"))

        assertEquals("你好", buildChatMessageText(userItem))
        assertEquals("世界", buildChatMessageText(assistantItem))
    }

    /**
     * 流式思考显示 Thinking，完成后在原位置显示耗时。
     */
    @Test
    fun `should replace thinking headline with completed duration`() {
        assertEquals("Thinking...", buildReasoningHeadline(ReasoningItem(isStreaming = true)))
        assertEquals(
            "Thought for 2s",
            buildReasoningHeadline(ReasoningItem(isStreaming = false, durationMillis = 2_001)),
        )
    }

    /**
     * 工具事件应显示纯文本标题，并把输入输出放到轻量标签里。
     */
    @Test
    fun `should expose plain text tool event headline and kind label`() {
        assertEquals(
            "read_file",
            buildToolEventHeadline(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Started,
                    preview = """{"path":"README.md"}""",
                ),
            ),
        )
        assertEquals(
            "输入",
            buildToolEventKindLabel(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Started,
                    preview = """{"path":"README.md"}""",
                ),
            ),
        )
        assertEquals(
            "输出",
            buildToolEventKindLabel(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Finished,
                    preview = "ok",
                ),
            ),
        )
        assertEquals(
            "正在整理结果",
            buildToolEventHeadline(
                ToolEventItem(
                    toolName = "status",
                    status = ToolEventStatus.Status,
                    preview = "正在整理结果",
                ),
            ),
        )
    }

    /**
     * 只有带输入输出预览的工具事件才需要展开详情。
     */
    @Test
    fun `should expose expandable input details even when the input is empty`() {
        assertEquals(
            true,
            toolEventHasDetails(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Started,
                    preview = """{"path":"README.md"}""",
                ),
            ),
        )
        assertEquals(
            false,
            toolEventHasDetails(
                ToolEventItem(
                    toolName = "status",
                    status = ToolEventStatus.Status,
                    preview = "working",
                ),
            ),
        )
        assertEquals(
            true,
            toolEventHasDetails(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Finished,
                    preview = "",
                ),
            ),
        )
    }

    /**
     * Failed 状态的工具事件应展示失败标题和错误标签，且仍然保留 preview 详情。
     */
    @Test
    fun `should expose failed headline and error label for failed tool event`() {
        val failedItem = ToolEventItem(
            toolName = "read_file",
            status = ToolEventStatus.Failed,
            preview = """{"path":"README.md"}""",
            errorMessage = "file not found",
        )
        assertEquals("失败: read_file", buildToolEventHeadline(failedItem))
        assertEquals("错误", buildToolEventKindLabel(failedItem))
        assertEquals(true, toolEventHasDetails(failedItem))
    }

    /**
     * Failed 状态的工具事件在缺少 preview 时不应展开详情。
     */
    @Test
    fun `should display readable empty input text instead of brackets`() {
        val failedItem = ToolEventItem(
            toolName = "error",
            status = ToolEventStatus.Failed,
            preview = null,
            errorMessage = "network timeout",
        )
        assertEquals("无输入参数", toolEventDetailText(failedItem))
    }

    /**
     * 工具收起时应紧跟工具名展示非空输入，避免把输入藏在展开区。
     */
    @Test
    fun `should expose non blank tool input inline`() {
        assertEquals(
            "{\"path\":\"README.md\"}",
            buildToolEventInlineInput(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Started,
                    preview = "{\"path\":\"README.md\"}",
                ),
            ),
        )
        assertNull(
            buildToolEventInlineInput(
                ToolEventItem(toolName = "list_dir", status = ToolEventStatus.Started, preview = ""),
            ),
        )
    }

    /**
     * 完成的工具事件仍应保留原工具名和输入预览，输出仅在展开区展示。
     */
    @Test
    fun `should keep completed tool event compact with its original input`() {
        val item = ToolEventItem(
            toolName = "run_powershell",
            status = ToolEventStatus.Finished,
            preview = """{"script":"Get-Location"}""",
            resultPreview = "stdout: completed",
        )

        assertEquals("run_powershell", buildToolEventHeadline(item))
        assertEquals("Get-Location", buildToolEventInlineInput(item))
    }

    /**
     * PowerShell 卡片收起时仅保留命令本身，不额外显示工具名。
     */
    @Test
    fun `should hide powershell tool name and retain its command in compact header`() {
        val item = ToolEventItem(
            toolName = "run_powershell",
            status = ToolEventStatus.Finished,
            preview = """{"script":"Get-Location; Get-ChildItem","operation_intent":"查看当前目录"}""",
        )

        assertEquals(false, shouldShowToolEventHeadline(item))
        assertEquals("Get-Location; Get-ChildItem", buildToolEventInlineInput(item))
    }

    /**
     * 终端工具名称在事件流中缺失时，仍需依据参数识别它，避免把意图与 JSON 一同塞入卡片。
     */
    @Test
    fun `should recognize terminal payload when event tool name is generic`() {
        val item = ToolEventItem(
            toolName = "tool",
            status = ToolEventStatus.Started,
            preview = """{"operation_intent":"循环输出 1 到 5000","script":"1..5000 | ForEach-Object { Write-Output ${'$'}_ }"}""",
        )

        assertEquals(false, shouldShowToolEventHeadline(item))
        assertEquals(
            "1..5000 | ForEach-Object { Write-Output ${'$'}_ }",
            buildToolEventInlineInput(item),
        )
    }

    /**
     * Koog 的工具参数预览使用 Kotlin Map 文本格式时，终端卡片仍应只显示实际脚本。
     */
    @Test
    fun `should extract powershell command from koog map preview`() {
        val item = ToolEventItem(
            toolName = "run_powershell",
            status = ToolEventStatus.Finished,
            preview = "{operation_intent=执行脚本, script=& \"${'$'}PWD/script.ps1\", timeout_ms=120000}",
        )

        assertEquals("& \"${'$'}PWD/script.ps1\"", buildToolEventInlineInput(item))
    }

    /**
     * 终端工具的内联输入只显示原始命令，操作意图由卡片上方单独呈现。
     */
    @Test
    fun `should omit terminal operation intent from inline command`() {
        assertEquals(
            "Get-ChildItem env: | Format-Table -AutoSize",
            buildToolEventInlineInput(
                ToolEventItem(
                    toolName = "run_powershell",
                    status = ToolEventStatus.Started,
                    preview = """{"script":"Get-ChildItem env: | Format-Table -AutoSize","operation_intent":"查看所有环境变量（只读操作）"}""",
                    operationIntent = "查看所有环境变量（只读操作）",
                ),
            ),
        )
    }

    /**
     * 运行中和失败的工具事件需要主动暴露上下文，完成态默认保持紧凑。
     */
    @Test
    fun `should keep every tool event collapsed by default`() {
        assertEquals(
            false,
            shouldExpandToolEventByDefault(
                ToolEventItem("read_file", ToolEventStatus.Started, preview = "input"),
            ),
        )
        assertEquals(
            false,
            shouldExpandToolEventByDefault(
                ToolEventItem("read_file", ToolEventStatus.Failed, preview = "input", errorMessage = "failed"),
            ),
        )
        assertEquals(
            false,
            shouldExpandToolEventByDefault(
                ToolEventItem("read_file", ToolEventStatus.Finished, preview = "output"),
            ),
        )
    }

    /**
     * 工具卡片展开时应优先显示完整输出，缺失时才回退到短预览。
     */
    @Test
    fun `should prefer complete tool output for expanded details`() {
        assertEquals(
            "line-1\nline-2",
            toolEventOutputText(
                ToolEventItem(
                    toolName = "run_powershell",
                    status = ToolEventStatus.Finished,
                    resultPreview = "line-1",
                    resultDisplay = "line-1\nline-2",
                ),
            ),
        )
        assertEquals(
            "line-1",
            toolEventOutputText(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Finished,
                    resultPreview = "line-1",
                ),
            ),
        )
    }

    /** 已有结构化 Diff 的补丁卡只能交给编辑器画布渲染，不能回退到原始协议文本。 */
    @Test
    fun `should hide raw patch input and output when structured diff exists`() {
        val item = ToolEventItem(
            toolName = "apply_patch",
            status = ToolEventStatus.Finished,
            preview = "{\"patch_text\":\"*** Begin Patch\"}",
            resultDisplay = "PATCH_APPLIED: 1 个文件",
            fileDiffs = listOf(
                FileDiffPreview(
                    path = "src/App.kt",
                    kind = FileChangeKind.CREATED,
                    unifiedDiff = "+fun main() = Unit",
                    collapsedUnchangedLineCount = 0,
                ),
            ),
        )

        assertNull(buildToolEventInlineInput(item))
        assertNull(toolEventOutputText(item))
    }

    /**
     * 运行中的 PowerShell 一旦产生输出，详情应自动展开以呈现实时反馈。
     */
    @Test
    fun `should auto expand running powershell output`() {
        assertEquals(
            true,
            shouldAutoExpandRunningTerminalOutput(
                ToolEventItem(
                    toolName = "run_powershell",
                    status = ToolEventStatus.Started,
                    resultDisplay = "> Task :shared:compileKotlin\n",
                ),
            ),
        )
        assertEquals(
            false,
            shouldAutoExpandRunningTerminalOutput(
                ToolEventItem("read_file", ToolEventStatus.Started, resultDisplay = "README"),
            ),
        )
    }

    /**
     * 超长终端输出必须拆成受限文本块，供惰性列表只渲染可见内容。
     */
    @Test
    fun `should split complete tool output into bounded display chunks`() {
        val longLine = "x".repeat(2_500)
        val chunks = toolEventOutputChunks(
            ToolEventItem(
                toolName = "run_powershell",
                status = ToolEventStatus.Finished,
                resultDisplay = "first\n$longLine\nlast",
            ),
        )

        assertEquals("first", chunks.first())
        assertEquals("last", chunks.last())
        assertEquals(4, chunks.size)
        assertTrue(chunks.all { it.length <= 2_000 })
    }
}
