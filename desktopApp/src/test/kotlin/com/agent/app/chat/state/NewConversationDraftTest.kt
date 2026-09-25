package com.agent.app.chat.state

import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** 验证新会话页和每个目标的草稿只存在于当前进程。 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationDraftTest : ChatWindowTestFixture() {
    /** 首次发送前没有真实会话，工作区新页互相保存独立草稿。 */
    @Test
    fun `keeps new workspace drafts without creating conversations`() = runTest(dispatcher) {
        val state = newState("workspace-a")
        assertTrue(state.ui.tasks.isEmpty())
        assertEquals("", state.ui.activeConversationId)
        state.updateDraft("alpha", 2)
        state.createConversationForWorkspace("workspace-b")
        state.updateDraft("beta", 1)
        state.createConversationForWorkspace("workspace-a")

        assertTrue(state.ui.tasks.isEmpty())
        assertEquals("alpha", state.ui.draft)
        assertEquals(2, state.ui.draftSelectionStart)
        state.createConversationForWorkspace("workspace-b")
        assertEquals("beta", state.ui.draft)
        assertEquals(1, state.ui.draftSelectionStart)
    }

    /** 发送只消费来源草稿；真实会话与新会话草稿相互隔离，重启后全部清空。 */
    @Test
    fun `isolates existing and new drafts and clears them on restart`() = runTest(dispatcher) {
        val state = newState("workspace-a")
        state.updateDraft("first")
        state.sendDraft()
        advanceUntilIdle()
        val conversationId = state.ui.activeConversationId
        assertTrue(conversationId.isNotBlank())
        assertEquals(1, state.ui.tasks.size)
        assertEquals("", state.ui.draft)

        state.updateDraft("existing draft", 3)
        state.createConversationForWorkspace("workspace-a")
        assertEquals("", state.ui.activeConversationId)
        state.updateDraft("new draft", 4)
        state.selectConversation(conversationId)
        assertEquals("existing draft", state.ui.draft)
        assertEquals(3, state.ui.draftSelectionStart)
        state.createConversationForWorkspace("workspace-a")
        assertEquals("new draft", state.ui.draft)
        assertEquals(4, state.ui.draftSelectionStart)

        val restarted = newState("workspace-a")
        assertTrue(restarted.ui.tasks.isEmpty())
        assertEquals("", restarted.ui.draft)
        assertNotEquals(conversationId, restarted.ui.activeConversationId)
    }

    /** 用稳定的测试网关创建一个没有数据库副作用的窗口。 */
    private fun newState(projectPath: String): ChatWindowState = ChatWindowState(
        resourceDispatcher = dispatcher,
        sendMessageUseCase = SendMessageUseCase(idleGateway()),
        snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
        projectPath = projectPath,
    )
}
