package com.agent.app.chat.persistence

import com.agent.app.chat.state.ChatAttachmentUiState
import com.agent.app.chat.state.ChatAttachmentKind
import com.agent.app.chat.state.ChatConversationUiState
import com.agent.app.chat.state.ConversationTitleState
import com.agent.shared.agent.api.AgentConversationHistoryMessage
import com.agent.shared.agent.api.AgentConversationHistoryPart
import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.agent.api.UserInputPart
import com.agent.shared.chat.model.ChatMessage
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ChatRole
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ReasoningItem
import com.agent.shared.chat.model.ToolEventItem
import com.agent.shared.chat.model.ToolEventStatus
import com.agent.shared.tool.model.PermissionPreset
import com.agent.shared.tool.model.FileChangeKind
import com.agent.shared.tool.model.FileDiffLineKind
import com.agent.shared.tool.model.FileDiffLinePreview
import com.agent.shared.tool.model.FileDiffPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证桌面聊天状态与共享持久化快照之间的完整映射。
 */
class ChatTaskSnapshotMapperTest {
    /** 结构化 Diff 必须随时间线持久化，重开任务后不能退回原始补丁文本。 */
    @Test
    fun `should preserve patch editor diff in persisted timeline`() {
        val diffs = listOf(
            FileDiffPreview(
                path = "src/App.kt",
                kind = FileChangeKind.MODIFIED,
                unifiedDiff = "-old\n+new",
                collapsedUnchangedLineCount = 0,
                editorLines = listOf(
                    FileDiffLinePreview(FileDiffLineKind.REMOVED, 1, null, "old"),
                    FileDiffLinePreview(FileDiffLineKind.ADDED, null, 1, "new"),
                ),
            ),
        )
        val source = ChatConversationUiState(
            id = "task-diff",
            title = "Diff 持久化",
            workspacePath = "D:\\workspace",
            items = listOf(
                ToolEventItem(
                    toolName = "apply_patch",
                    status = ToolEventStatus.Finished,
                    fileDiffs = diffs,
                ),
            ),
            executionState = ExecutionState.Idle,
        )

        val restored = ChatTaskSnapshotMapper.toConversation(ChatTaskSnapshotMapper.toPersistedTask(source))

        assertEquals(diffs, (restored.items.single() as ToolEventItem).fileDiffs)
    }

    /**
     * 映射必须保留用户可见和 Agent 可续接的所有原始文本。
     */
    @Test
    fun `should preserve full timeline and history payloads`() {
        val source = ChatConversationUiState(
            id = "task-1",
            title = "完整持久化",
            workspacePath = "D:\\workspace",
            items = listOf(
                ChatMessageItem(ChatMessage(ChatRole.User, "读取 secret.txt")),
                ReasoningItem(summaryText = "摘要", rawText = "原始 reasoning", isStreaming = false),
                ToolEventItem(
                    toolName = "run_powershell",
                    status = ToolEventStatus.Finished,
                    preview = "Get-Content secret.txt",
                    resultPreview = "简短结果",
                    resultDisplay = "完整工具结果",
                ),
            ),
            attachments = listOf(ChatAttachmentUiState("D:\\workspace\\input.txt", "input.txt")),
            history = listOf(
                AgentConversationHistoryMessage.User("读取 secret.txt"),
                AgentConversationHistoryMessage.Assistant(
                    listOf(
                        AgentConversationHistoryPart.Reasoning("摘要", "原始 reasoning"),
                        AgentConversationHistoryPart.ToolCall(name = "run_powershell", argumentsPreview = "Get-Content secret.txt"),
                        AgentConversationHistoryPart.ToolResult(name = "run_powershell", resultPreview = "简短结果"),
                    ),
                ),
            ),
            reasoningEffort = ReasoningEffort.HIGH,
            executionState = ExecutionState.Idle,
            contextUsageFraction = 0.25f,
        )

        assertEquals(source, ChatTaskSnapshotMapper.toConversation(ChatTaskSnapshotMapper.toPersistedTask(source)))
    }

    /** 文件快照与会话媒体引用必须连同图号顺序写入历史，重启后仍可准确重放给模型。 */
    @Test
    fun `should preserve ordered file and image input parts with session media references`() {
        val orderedInput = listOf(
            UserInputPart.Text("先读 "),
            UserInputPart.FileSnapshot("src/App.kt", "fun main() = Unit", "text/x-kotlin"),
            UserInputPart.Text("，再看 "),
            UserInputPart.Image("media-1", "C:\\media\\media-1.png", "image/png", "图1"),
            UserInputPart.Text("，最后看 "),
            UserInputPart.Image("media-2", "C:\\media\\media-2.png", "image/png", "图2"),
        )
        val source = ChatConversationUiState(
            id = "task-multimodal",
            title = "多模态顺序",
            workspacePath = "D:\\workspace",
            attachments = listOf(
                ChatAttachmentUiState(
                    path = "C:\\media\\media-1.png",
                    name = "图1",
                    token = "[图1]",
                    kind = ChatAttachmentKind.IMAGE,
                    mediaId = "media-1",
                    imageLabel = "图1",
                    mimeType = "image/png",
                ),
                ChatAttachmentUiState(
                    path = "C:\\media\\media-2.png",
                    name = "图2",
                    token = "[图2]",
                    kind = ChatAttachmentKind.IMAGE,
                    mediaId = "media-2",
                    imageLabel = "图2",
                    mimeType = "image/png",
                ),
            ),
            history = listOf(AgentConversationHistoryMessage.User("先读 @src/App.kt [图1] [图2]", orderedInput)),
            executionState = ExecutionState.Idle,
        )

        val restored = ChatTaskSnapshotMapper.toConversation(ChatTaskSnapshotMapper.toPersistedTask(source))

        assertEquals(source.attachments, restored.attachments)
        assertEquals(orderedInput, (restored.history.single() as AgentConversationHistoryMessage.User).inputParts)
    }

    /**
     * 重启后不能继续运行已消失的协程或工具进程。
     */
    @Test
    fun `should mark running task as interrupted during restore`() {
        val source = ChatConversationUiState(
            id = "task-running",
            title = "运行中",
            workspacePath = "D:\\workspace",
            reasoningEffort = ReasoningEffort.MEDIUM,
            executionState = ExecutionState.Running,
        )

        val restored = ChatTaskSnapshotMapper.toConversation(ChatTaskSnapshotMapper.toPersistedTask(source))

        assertTrue(restored.executionState is ExecutionState.Failed)
        assertEquals("执行已中断", restored.executionState.error.title)
    }

    /**
     * 非默认的 profile 绑定和权限档位也必须完整往返，不能被悄悄归一化为默认值。
     */
    @Test
    fun `should round trip non default profile and permission preset`() {
        val source = ChatConversationUiState(
            id = "task-profile",
            title = "绑定了非默认配置",
            workspacePath = "D:\\workspace",
            profileId = "deepseek:deepseek-v4-pro",
            reasoningEffort = ReasoningEffort.HIGH,
            permissionPreset = PermissionPreset.BRAVE,
            executionState = ExecutionState.Idle,
        )

        val restored = ChatTaskSnapshotMapper.toConversation(ChatTaskSnapshotMapper.toPersistedTask(source))

        assertEquals("deepseek:deepseek-v4-pro", restored.profileId)
        assertEquals(ReasoningEffort.HIGH, restored.reasoningEffort)
        assertEquals(PermissionPreset.BRAVE, restored.permissionPreset)
    }

    /**
     * 无法识别的历史推理档位和权限档位字符串应安全回退到默认值，而不是抛出异常。
     */
    @Test
    fun `should fall back to defaults for unrecognized persisted reasoning and permission values`() {
        val legacyPersistedTask = ChatTaskSnapshotMapper.toPersistedTask(
            ChatConversationUiState(
                id = "task-legacy",
                title = "旧版本数据",
                workspacePath = "D:\\workspace",
                executionState = ExecutionState.Idle,
            ),
        ).copy(
            reasoningEffort = "ULTRA_UNKNOWN",
            permissionPreset = "UNKNOWN_PRESET",
        )

        val restored = ChatTaskSnapshotMapper.toConversation(legacyPersistedTask)

        assertEquals(ReasoningEffort.MEDIUM, restored.reasoningEffort)
        assertEquals(PermissionPreset.DEFAULT, restored.permissionPreset)
    }

    /**
     * 恢复的会话绝不能永久停留在标题生成中；持久化不记录该状态，恢复后一律归零。
     */
    @Test
    fun `should never restore a conversation stuck in generating title state`() {
        val source = ChatConversationUiState(
            id = "task-generating-title",
            title = "新建对话",
            titleState = ConversationTitleState.GENERATING,
            workspacePath = "D:\\workspace",
            executionState = ExecutionState.Idle,
        )

        val restored = ChatTaskSnapshotMapper.toConversation(ChatTaskSnapshotMapper.toPersistedTask(source))

        assertEquals(ConversationTitleState.NOT_REQUESTED, restored.titleState)
    }
}
