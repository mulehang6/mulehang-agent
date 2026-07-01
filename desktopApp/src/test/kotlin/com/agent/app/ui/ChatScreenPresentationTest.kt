package com.agent.app.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.agent.shared.state.ChatMessage
import com.agent.shared.state.ChatMessageItem
import com.agent.shared.state.ChatRole
import com.agent.shared.agent.ReasoningEffort
import com.agent.shared.config.ConfigLayer
import com.agent.shared.config.ConfigProfile
import com.agent.shared.config.ModelVariant
import com.agent.shared.config.ProviderType
import com.agent.shared.state.AppError
import com.agent.shared.state.ExecutionState
import com.agent.shared.state.ReasoningItem
import com.agent.shared.state.ToolEventItem
import com.agent.shared.state.ToolEventStatus
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 验证聊天时间线展示文案的最小规则。
 */
class ChatScreenPresentationTest {

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
     * 思考块标题应保留 Thinking 文案，并区分流式中与完成态。
     */
    @Test
    fun `should keep thinking headline for reasoning block`() {
        assertEquals("Thinking: 思考中...", buildReasoningHeadline(ReasoningItem(isStreaming = true)))
        assertEquals("Thinking:", buildReasoningHeadline(ReasoningItem(isStreaming = false)))
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
    fun `should only expand tool events that have preview details`() {
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
            false,
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
    fun `should not expand details for failed tool event without preview`() {
        val failedItem = ToolEventItem(
            toolName = "error",
            status = ToolEventStatus.Failed,
            preview = null,
            errorMessage = "network timeout",
        )
        assertEquals(false, toolEventHasDetails(failedItem))
    }

    /**
     * pwd 分组标题应只显示末级目录名。
     */
    @Test
    fun `should map workspace path to terminal folder label`() {
        assertEquals("def", buildWorkspaceLabel("E:\\abc\\def"))
        assertEquals("repo-x", buildWorkspaceLabel("D:\\work\\repo-x"))
    }

    /**
     * provider 标签和 reasoning 档位应来自 profile 能力解析。
     */
    @Test
    fun `should expose provider label and reasoning support from profile`() {
        assertEquals("OpenAI Compatible", providerLabel(ProviderType.OPENAI_CHAT_COMPLETIONS))
        assertEquals(
            listOf(
                ModelVariant(id = "high", reasoningEffort = ReasoningEffort.HIGH),
                ModelVariant(id = "max", reasoningEffort = ReasoningEffort.MAX),
            ),
            modelVariantsFor(profile(model = "deepseek-v4-flash")),
        )
        assertEquals(emptyList(), modelVariantsFor(profile(model = "claude-sonnet-4")))
    }

    /**
     * 上下文剩余数值只应出现在 hover tooltip 文案中。
     */
    @Test
    fun `should keep context usage value inside tooltip text only`() {
        assertEquals("58% used", buildContextTooltip(0.58f))
        assertEquals("<0.1% used", buildContextTooltip(0.00002f))
    }

    /**
     * 上下文圆环旁应直接展示当前计算出的占用百分比。
     */
    @Test
    fun `should expose context usage percentage as visible chip label`() {
        assertEquals("58%", buildContextUsageLabel(0.58f))
        assertEquals("<0.1%", buildContextUsageLabel(0.00002f))
    }

    /**
     * 上下文圆环的 sweep angle 应按 0..1 占比换算，并在非零时保留最小可见弧度。
     */
    @Test
    fun `should clamp context ring sweep angle from usage fraction`() {
        assertEquals(208.8f, contextRingSweepAngle(0.58f), 0.001f)
        assertEquals(0f, contextRingSweepAngle(-0.2f), 0.001f)
        assertEquals(360f, contextRingSweepAngle(1.4f), 0.001f)
        assertEquals(6f, contextRingSweepAngle(0.00002f), 0.001f)
    }

    /**
     * 只有当时间线已经停在底部附近时，新增内容才应自动跟随到底部。
     */
    @Test
    fun `should auto scroll only when timeline is already near the latest item`() {
        assertEquals(true, shouldAutoScrollToLatest(lastVisibleIndex = null, totalItems = 6))
        assertEquals(6, timelineAutoScrollAnchorIndex(totalItems = 6))
        assertEquals(true, shouldAutoScrollToLatest(lastVisibleIndex = 6, totalItems = 6))
        assertEquals(true, shouldAutoScrollToLatest(lastVisibleIndex = 5, totalItems = 6))
        assertEquals(false, shouldAutoScrollToLatest(lastVisibleIndex = 2, totalItems = 6))
    }

    /**
     * 如果原本已经跟随到底部，连续追加多个时间线块时不应因为可见索引短暂落后而丢失跟随状态。
     */
    @Test
    fun `should keep following latest while new timeline blocks are appended`() {
        assertEquals(
            true,
            nextAutoScrollFollowState(
                currentFollowLatest = true,
                lastVisibleIndex = 6,
                totalItems = 8,
                previousTotalItems = 6,
            ),
        )
        assertEquals(
            false,
            nextAutoScrollFollowState(
                currentFollowLatest = true,
                lastVisibleIndex = 2,
                totalItems = 8,
                previousTotalItems = 8,
            ),
        )
        assertEquals(
            false,
            nextAutoScrollFollowState(
                currentFollowLatest = false,
                lastVisibleIndex = 6,
                totalItems = 8,
                previousTotalItems = 6,
            ),
        )
    }

    /**
     * 执行中、等待输入或等待审批时主按钮应切为停止态，避免继续显示发送图标。
     */
    @Test
    fun `should expose stop action visual for running and waiting states`() {
        assertEquals(
            ComposerPrimaryActionVisual(symbol = "■", danger = true),
            buildComposerPrimaryActionVisual(ExecutionState.Running),
        )
        assertEquals(
            ComposerPrimaryActionVisual(symbol = "■", danger = true),
            buildComposerPrimaryActionVisual(ExecutionState.WaitingForUserInput),
        )
        assertEquals(
            ComposerPrimaryActionVisual(symbol = "■", danger = true),
            buildComposerPrimaryActionVisual(ExecutionState.WaitingForApproval),
        )
        assertEquals(
            ComposerPrimaryActionVisual(symbol = "↑", danger = false),
            buildComposerPrimaryActionVisual(ExecutionState.Idle),
        )
    }

    /**
     * isStoppable 应覆盖运行、等待输入和等待审批，但不包括空闲和失败。
     */
    @Test
    fun `should identify stoppable execution states`() {
        assertEquals(true, ExecutionState.Running.isStoppable())
        assertEquals(true, ExecutionState.WaitingForUserInput.isStoppable())
        assertEquals(true, ExecutionState.WaitingForApproval.isStoppable())
        assertEquals(false, ExecutionState.Idle.isStoppable())
        assertEquals(false, ExecutionState.Failed(AppError("err", "msg")).isStoppable())
    }

    /**
     * composer 应支持 Enter 发送，同时保留 Shift+Enter 换行。
     */
    @Test
    fun `should submit composer on enter without shift only`() {
        assertEquals(true, shouldSubmitComposerKey(Key.Enter, KeyEventType.KeyUp, isShiftPressed = false))
        assertEquals(false, shouldSubmitComposerKey(Key.Enter, KeyEventType.KeyUp, isShiftPressed = true))
        assertEquals(false, shouldSubmitComposerKey(Key.Enter, KeyEventType.KeyDown, isShiftPressed = false))
    }

    /**
     * 只有真实 update_plan 工具事件携带 plan 列表时，才显示 Plan 卡片。
     */
    @Test
    fun `should show plan card only when update plan tool payload exists`() {
        val visible = extractPlanCard(
            listOf(
                ToolEventItem(
                    toolName = "update_plan",
                    status = ToolEventStatus.Started,
                    preview = """{"explanation":"sync","plan":[{"step":"Inspect files","status":"completed"},{"step":"Refactor UI","status":"in_progress"}]}""",
                ),
            ),
        )
        val hidden = extractPlanCard(
            listOf(
                ToolEventItem(
                    toolName = "read_file",
                    status = ToolEventStatus.Started,
                    preview = """{"path":"README.md"}""",
                ),
            ),
        )

        assertEquals(listOf("Inspect files", "Refactor UI"), visible?.entries?.map { it.text })
        assertEquals(2, visible?.entries?.size)
        assertEquals(true, visible?.entries?.last()?.active)
        assertEquals(null, hidden)
    }

    /**
     * update_plan 的 JSON 字段顺序不固定，步骤文案里也可能包含转义引号。
     */
    @Test
    fun `should parse update plan payload regardless of field order and escaped quotes`() {
        val planCard = extractPlanCard(
            listOf(
                ToolEventItem(
                    toolName = "update_plan",
                    status = ToolEventStatus.Started,
                    preview = """{"plan":[{"status":"completed","step":"Inspect \"Ring\" tokens"},{"status":"in_progress","step":"Port sidebar"}]}""",
                ),
            ),
        )

        assertEquals(listOf("Inspect \"Ring\" tokens", "Port sidebar"), planCard?.entries?.map { it.text })
        assertEquals(listOf(false, true), planCard?.entries?.map { it.active })
    }

    /**
     * 最近一条 update_plan 事件如果只是结果文本，也应继续回退到更早的参数预览。
     */
    @Test
    fun `should fall back to earlier update plan preview when latest event is not parseable`() {
        val planCard = extractPlanCard(
            listOf(
                ToolEventItem(
                    toolName = "update_plan",
                    status = ToolEventStatus.Started,
                    preview = """{"plan":[{"step":"Inspect files","status":"completed"},{"step":"Port Ring UI","status":"in_progress"}]}""",
                ),
                ToolEventItem(
                    toolName = "update_plan",
                    status = ToolEventStatus.Finished,
                    preview = "plan updated",
                ),
            ),
        )

        assertEquals(listOf("Inspect files", "Port Ring UI"), planCard?.entries?.map { it.text })
        assertEquals(listOf(false, true), planCard?.entries?.map { it.active })
    }

    /**
     * 右侧 rail 应保持三段分组，并且只有第一个 code 按钮默认高亮。
     */
    @Test
    fun `should expose grouped right rail buttons with only first item active`() {
        val groups = buildRightRailGroups()

        assertEquals(listOf(3, 2, 2), groups.map { it.size })
        assertEquals(RightRailGlyph.CODE, groups.first().first().glyph)
        assertEquals(RightRailGlyph.UPLOAD, groups[1].first().glyph)
        assertEquals(true, groups.first().first().active)
        assertEquals(
            1,
            groups.flatten().count { it.active },
        )
    }

    /**
     * 顶部 header 操作区应保持 menu / share / settings / help 的固定顺序。
     */
    @Test
    fun `should expose header glyph actions in prototype order`() {
        val actions = buildHeaderActions()

        assertEquals(HeaderGlyph.MENU, actions.left.glyph)
        assertEquals(
            listOf(HeaderGlyph.SHARE, HeaderGlyph.SETTINGS, HeaderGlyph.HELP),
            actions.right.map { it.glyph },
        )
    }

    /**
     * 导出 markdown 时应保留标题、状态和基础消息顺序。
     */
    @Test
    fun `should build conversation markdown transcript`() {
        val markdown = buildConversationMarkdown(
            ChatConversationUiState(
                id = "task-1",
                title = "Prototype Match",
                workspacePath = "D:\\repo\\prototype",
                items = listOf(
                    ChatMessageItem(ChatMessage(role = ChatRole.User, content = "Please refactor UI")),
                    ToolEventItem(
                        toolName = "update_plan",
                        status = ToolEventStatus.Started,
                        preview = """{"step":"Inspect"}""",
                    ),
                    ChatMessageItem(ChatMessage(role = ChatRole.Assistant, content = "Done.")),
                ),
            ),
        )

        assertEquals(true, markdown.contains("# Prototype Match"))
        assertEquals(true, markdown.contains("- Workspace: D:\\repo\\prototype"))
        assertEquals(true, markdown.contains("## User"))
        assertEquals(true, markdown.contains("## Tool `update_plan`"))
        assertEquals(true, markdown.contains("## Assistant"))
    }

    /**
     * 导出 markdown 文件应显式使用 UTF-8，避免 Windows 默认编码破坏 Unicode 内容。
     */
    @Test
    fun `should write conversation markdown using utf 8`() {
        val target = Files.createTempFile("conversation-transcript", ".md").toFile()
        val markdown = "# 中文标题\n\n工具参数: {\"emoji\":\"😀\"}\n"

        try {
            writeConversationMarkdown(target, markdown)

            assertEquals(markdown, target.readText(Charsets.UTF_8))
            assertEquals(markdown.toByteArray(Charsets.UTF_8).toList(), target.readBytes().toList())
        } finally {
            target.delete()
        }
    }

    /**
     * 侧栏应同时保留“沿用当前工作区新建线程”和“强制选择新工作区”两条路径。
     */
    @Test
    fun `should keep both current workspace and directory picker task creation paths`() {
        var pickerCalls = 0
        val pickedWorkspace = "D:\\repo\\new-workspace"

        val currentWorkspaceResult = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = "D:\\repo\\current",
            forceDirectoryPicker = false,
        ) {
            pickerCalls += 1
            pickedWorkspace
        }
        val pickedWorkspaceResult = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = "D:\\repo\\current",
            forceDirectoryPicker = true,
        ) {
            pickerCalls += 1
            pickedWorkspace
        }
        val fallbackWorkspaceResult = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = null,
            forceDirectoryPicker = false,
        ) {
            pickerCalls += 1
            pickedWorkspace
        }
        val cancelledWorkspaceResult = resolveWorkspaceForTaskCreation(
            activeWorkspacePath = "D:\\repo\\current",
            forceDirectoryPicker = true,
        ) {
            pickerCalls += 1
            null
        }

        assertEquals("D:\\repo\\current", currentWorkspaceResult)
        assertEquals(pickedWorkspace, pickedWorkspaceResult)
        assertEquals(pickedWorkspace, fallbackWorkspaceResult)
        assertNull(cancelledWorkspaceResult)
        assertEquals(3, pickerCalls)
    }

    private fun profile(model: String): ConfigProfile = ConfigProfile(
        id = "profile-$model",
        providerType = if (model.startsWith("deepseek", ignoreCase = true)) {
            ProviderType.OPENAI_CHAT_COMPLETIONS
        } else {
            ProviderType.ANTHROPIC
        },
        baseUrl = if (model.startsWith("deepseek", ignoreCase = true)) {
            "https://api.deepseek.com/v1"
        } else {
            "https://api.anthropic.com"
        },
        apiKey = "key",
        model = model,
        enabled = true,
        layer = ConfigLayer.PROJECT,
    )
}
