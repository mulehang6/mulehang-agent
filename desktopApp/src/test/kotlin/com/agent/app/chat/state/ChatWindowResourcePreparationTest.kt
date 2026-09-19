package com.agent.app.chat.state

import com.agent.shared.agent.resource.AgentResourceSnapshot
import com.agent.shared.chat.model.ChatMessageItem
import com.agent.shared.chat.model.ExecutionState
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.*

/** 发送路径的反馈必须先于可能阻塞的资源发现。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatWindowResourcePreparationTest : ChatWindowTestFixture() {
    /** 尚未执行资源任务时用户消息已出现，草稿已清空且状态可取消。 */
    @Test
    fun acknowledgesBeforePreparingResources() = runTest(dispatcher) {
        var prepared = false
        lateinit var state: ChatWindowState
        state = ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(idleGateway()),
            snapshot = AppSessionSnapshot(profiles = listOf(profile()), activeProfile = profile()),
            projectPath = "verification-workspace",
            resourceDispatcher = dispatcher,
            resourceSnapshotProvider = {
                assertEquals("", state.ui.draft)
                assertEquals(ExecutionState.Running, state.state.executionState)
                assertEquals("hello", (state.state.items.first() as ChatMessageItem).message.content)
                prepared = true
                AgentResourceSnapshot.empty()
            },
        )
        state.send("hello")
        assertFalse(prepared)
        assertEquals(ExecutionState.Running, state.state.executionState)
        advanceUntilIdle()
        assertTrue(prepared)
        assertEquals(ExecutionState.Idle, state.state.executionState)
    }
}
