package com.agent.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.state.ConversationState

/**
 * 窗口级状态持有者骨架。
 */
class ChatWindowState(
    private val sendMessageUseCase: SendMessageUseCase,
    snapshot: AppSessionSnapshot,
) {
    /**
     * 当前窗口状态。
     */
    var state by mutableStateOf(
        ConversationState(activeProfileId = snapshot.activeProfile?.id),
    )
        private set

    /**
     * 发送消息占位入口。
     */
    fun send(message: String) {
        TODO("Implement chat state updates and message execution.")
    }
}
