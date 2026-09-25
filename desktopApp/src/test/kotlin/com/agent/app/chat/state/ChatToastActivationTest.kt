package com.agent.app.chat.state

import com.agent.app.platform.ToastActivationTarget
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 热启动和冷启动都要切换到通知的目标会话。 */
class ChatToastActivationTest : ChatWindowTestFixture() {
    /** 会话尚未加载时保存目标，加载后只定位一次。 */
    @Test
    fun `cold activation waits for tasks then points at entry`() {
        val state = state()
        val target = ToastActivationTarget("event", "conversation", "entry")
        state.activateToast(target)
        assertEquals("", state.ui.activeTaskId)
        state.restoreTasks(listOf(ChatConversationUiState("conversation", "测试", workspacePath = "D:/workspace")))
        assertEquals("conversation", state.ui.activeTaskId)
        assertEquals("entry", state.ui.attentionNavigation?.entryId)
        val serial = requireNotNull(state.ui.attentionNavigation).serial
        state.clearAttentionNavigation(serial)
        assertNull(state.ui.attentionNavigation)
    }

    /** 已运行窗口直接切换会话并生成新的定位序号。 */
    @Test
    fun `hot activation switches conversation`() {
        val state = state()
        state.restoreTasks(listOf(
            ChatConversationUiState("first", "一", workspacePath = "D:/workspace"),
            ChatConversationUiState("second", "二", workspacePath = "D:/workspace"),
        ))
        state.activateToast(ToastActivationTarget("event", "second", "entry"))
        assertEquals("second", state.ui.activeTaskId)
        assertEquals("entry", state.ui.attentionNavigation?.entryId)
    }

    /** 使用无运行副作用的窗口状态。 */
    private fun state() = ChatWindowState(
        sendMessageUseCase = SendMessageUseCase(idleGateway()),
        snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
        projectPath = "D:/workspace",
        resourceDispatcher = dispatcher,
    )
}
