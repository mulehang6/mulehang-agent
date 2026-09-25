package com.agent.app.chat.state

import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

/** 验证ChatWindowWorkspaceRemovalTest的状态转换。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatWindowWorkspaceRemovalTest : ChatWindowTestFixture() {
    /** 删除工作区仅解除全部任务的目录关联，不删除会话历史。 */
    @Test
    fun `should disconnect workspace while retaining conversation history`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\workspace",
            workspaceDirectoryExists = { true },
        )
        state.send("保留历史")
        advanceUntilIdle()
        val taskCount = state.ui.tasks.size

        state.disconnectWorkspace("E:\\workspace")

        assertEquals(taskCount, state.ui.tasks.size)
        assertEquals("", state.ui.activeTaskId)
        assertEquals(null, state.ui.activeConversationOrNull)
        assertEquals("", state.ui.tasks.single().workspacePath)
        assertEquals(null, state.ui.tasks.single().workspaceName)
        assertTrue(state.ui.tasks.single().history.isNotEmpty())
        assertTrue(state.ui.workspaceTaskSections.isEmpty())
    }

    /** 删除当前工作区时，应跳转到最近更新且目录可用的其他工作区的新任务。 */
    @Test
    fun `should switch active workspace deletion to newest available workspace task`() = runTest(dispatcher) {
        var now = 100L
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\current",
            clock = { now },
            workspaceDirectoryExists = { it != "E:\\invalid" },
        )
        state.send("保留当前历史")
        advanceUntilIdle()
        val currentConversationId = state.ui.activeConversationId
        now = 200L
        state.createConversationForWorkspace("E:\\older")
        state.send("较早工作区历史")
        advanceUntilIdle()
        now = 300L
        state.createConversationForWorkspace("E:\\newer")
        state.send("较新工作区历史")
        advanceUntilIdle()
        now = 400L
        state.createConversationForWorkspace("E:\\invalid")
        state.send("不可用工作区历史")
        advanceUntilIdle()
        state.selectConversation(currentConversationId)
        state.updateDraft("删除前草稿")

        state.disconnectWorkspace("E:\\current")

        assertEquals("E:\\newer", state.ui.newWorkspacePath)
        assertNull(state.ui.activeConversationOrNull)
        assertEquals(1, state.ui.tasks.count { it.workspacePath == "E:\\newer" })
        assertEquals("", state.ui.draft)
        assertEquals("", state.findConversation(currentConversationId).workspacePath)
    }

    /** 回退工作区已有空白任务时，应直接复用，避免重复创建新任务。 */
    @Test
    fun `should reuse fallback workspace empty task when disconnecting active workspace`() = runTest(dispatcher) {
        var now = 100L
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\current",
            clock = { now },
            workspaceDirectoryExists = { true },
        )
        state.send("保留当前历史")
        advanceUntilIdle()
        val currentConversationId = state.ui.activeConversationId
        now = 200L
        state.createConversationForWorkspace("E:\\target")
        val reusableConversationId = state.ui.activeConversationId
        state.selectConversation(currentConversationId)

        state.disconnectWorkspace("E:\\current")

        assertEquals(reusableConversationId, state.ui.activeTaskId)
        assertEquals(1, state.ui.tasks.size)
        assertEquals("E:\\target", state.ui.newWorkspacePath)
        assertNull(state.ui.activeConversationOrNull)
    }

    /** 删除非当前工作区不得打断当前会话或清空草稿。 */
    @Test
    fun `should retain active conversation when disconnecting another workspace`() = runTest(dispatcher) {
        var now = 100L
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\active",
            clock = { now },
            workspaceDirectoryExists = { true },
        )
        state.send("活动工作区历史")
        advanceUntilIdle()
        val activeConversationId = state.ui.activeConversationId
        now = 200L
        state.createConversationForWorkspace("E:\\disconnected")
        state.send("保留历史")
        advanceUntilIdle()
        state.selectConversation(activeConversationId)
        state.updateDraft("继续编辑")

        state.disconnectWorkspace("E:\\disconnected")

        assertEquals(activeConversationId, state.ui.activeTaskId)
        assertEquals("E:\\active", state.ui.activeConversation.workspacePath)
        assertEquals("继续编辑", state.ui.draft)
        assertEquals(
            "",
            state.ui.tasks.single { it.workspacePath.isBlank() && it.title != DEFAULT_CONVERSATION_TITLE }.workspacePath,
        )
    }

    /** 删除后重新选择原目录，应恢复历史并保留用户设置的工作区名称。 */
    @Test
    fun `should restore detached history when selecting its original workspace`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\crud",
            workspaceDirectoryExists = { true },
        )
        state.send("保留的 crud 历史")
        advanceUntilIdle()
        val historyConversationId = state.ui.activeConversationId
        assertEquals(null, state.editWorkspace("E:\\crud", "CRUD 历史", "E:\\crud"))

        state.disconnectWorkspace("E:\\crud")

        assertEquals("", state.findConversation(historyConversationId).workspacePath)
        assertEquals("E:\\crud", state.findConversation(historyConversationId).detachedWorkspacePath)
        assertEquals("CRUD 历史", state.findConversation(historyConversationId).detachedWorkspaceName)

        state.createConversationForWorkspace("E:\\crud")

        assertEquals("E:\\crud", state.findConversation(historyConversationId).workspacePath)
        assertEquals(null, state.findConversation(historyConversationId).detachedWorkspacePath)
        assertEquals("CRUD 历史", state.findConversation(historyConversationId).workspaceName)
        assertEquals(1, state.ui.tasks.count { it.workspacePath == "E:\\crud" })
        assertEquals("CRUD 历史", state.ui.workspaceTaskSections.single().label)
    }

    /** 路径不同时不得自动认领已解除关联的其他工作区历史。 */
    @Test
    fun `should not restore detached history for a different workspace path`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\crud",
            workspaceDirectoryExists = { true },
        )
        state.send("保留的 crud 历史")
        advanceUntilIdle()
        val historyConversationId = state.ui.activeConversationId
        state.disconnectWorkspace("E:\\crud")

        state.createConversationForWorkspace("E:\\other")

        assertEquals("", state.findConversation(historyConversationId).workspacePath)
        assertEquals("E:\\crud", state.findConversation(historyConversationId).detachedWorkspacePath)
        assertEquals("E:\\other", state.ui.newWorkspacePath)
        assertNull(state.ui.activeConversationOrNull)
    }

    /** 删除工作区时不应将空白默认对话留在未关联历史中。 */
    @Test
    fun `should remove empty placeholder when disconnecting workspace`() = runTest(dispatcher) {
        val state = ChatWindowState(
            resourceDispatcher = dispatcher,
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "E:\\workspace",
            workspaceDirectoryExists = { true },
        )

        state.disconnectWorkspace("E:\\workspace")

        assertTrue(state.ui.tasks.isEmpty())
        assertEquals("", state.ui.activeTaskId)
        assertTrue(state.ui.workspaceTaskSections.isEmpty())
    }

}
