package com.agent.app.chat.state

import com.agent.shared.agent.api.ReasoningEffort
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.model.ConversationEntry
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.tool.model.PermissionPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证聊天窗口初始状态与空白对话工厂规则。
 */
class ConversationFactoryTest {
    @Test
    fun `blank project starts without a placeholder conversation`() {
        val result = initialUiState(
            snapshot = AppSessionSnapshot(profiles = emptyList(), activeProfile = null),
            projectPath = "",
        )

        assertEquals(emptyList(), result.tasks)
        assertEquals("", result.activeTaskId)
    }

    @Test
    fun `new conversation preserves the default empty conversation semantics`() {
        val result = newConversation(
            workspacePath = "E:\\workspace",
            contextWindow = 100,
            reasoningEffort = ReasoningEffort.MEDIUM,
        )

        assertEquals("新建对话", result.title)
        assertEquals("E:\\workspace", result.workspacePath)
        assertEquals(ExecutionState.Idle, result.executionState)
        assertEquals(
            estimateContextUsage(emptyList(), 0, 100),
            result.contextUsageFraction,
        )
        assertTrue(result.isEmptyDefaultConversation())
        assertEquals("重构 ChatWindowState", buildConversationTitle("  重构 ChatWindowState\n第二行  "))
    }

    /** 只切换模型、推理强度或权限时，会话仍是空白任务，侧栏不得显示运行中。 */
    @Test
    fun `configuration metadata does not mark blank conversation as running`() {
        val original = newConversation(
            workspacePath = "E:\\workspace",
            contextWindow = 100,
            reasoningEffort = ReasoningEffort.MEDIUM,
        )
        val configured = original.copy(
            permissionPreset = PermissionPreset.BRAVE,
            entries = listOf(
                ConversationEntry.ModelChange("model", null, 1L, "profile-2"),
                ConversationEntry.ReasoningEffortChange("reasoning", "model", 2L, "HIGH"),
            ),
            activeEntryId = "reasoning",
        )

        assertTrue(configured.isEmptyDefaultConversation())
        assertEquals(ChatTaskStatus.NEW, taskStatusFor(configured))
    }

    /** 没有执行内容的任务即使残留瞬时运行态，也不得显示无限旋转的运行标识。 */
    @Test
    fun `contentless conversation ignores stale running state in sidebar`() {
        val conversation = newConversation(
            workspacePath = "E:\\workspace",
            contextWindow = 100,
            reasoningEffort = ReasoningEffort.MEDIUM,
        ).copy(
            executionState = ExecutionState.Running,
            progressMessage = "正在处理",
        )

        assertEquals(ChatTaskStatus.NEW, taskStatusFor(conversation))
    }
}
