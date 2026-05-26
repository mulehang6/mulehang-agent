package com.agent.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.agent.app.ui.ChatScreen
import com.agent.app.ui.ChatWindowState
import com.agent.shared.agent.KoogAgentGateway
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.SendMessageUseCase

/**
 * 根 composable 骨架。
 */
@Composable
fun MulehangDesktopApp() {
    val windowState = remember {
        ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(KoogAgentGateway()),
            snapshot = AppSessionSnapshot(
                profiles = emptyList(),
                activeProfile = null,
            ),
        )
    }

    MaterialTheme {
        ChatScreen(windowState)
    }
}
