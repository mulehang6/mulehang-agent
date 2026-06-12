package com.agent.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.agent.app.ui.ChatScreen
import com.agent.app.ui.ChatWindowState
import com.agent.shared.agent.KoogAgentGateway
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.DesktopAppSessionRepository
import com.agent.shared.application.LoadAppSessionUseCase
import com.agent.shared.application.SendMessageUseCase
import java.nio.file.Paths

/**
 * 根 composable，负责加载桌面会话快照并装配窗口状态。
 */
@Composable
fun MulehangDesktopApp() {
    val projectRoot = remember {
        DesktopProjectRootResolver.resolve(Paths.get(""))
    }
    val snapshotState = remember {
        mutableStateOf(AppSessionSnapshot(profiles = emptyList(), activeProfile = null))
    }

    LaunchedEffect(projectRoot) {
        val repository = DesktopAppSessionRepository(projectRoot)
        snapshotState.value = LoadAppSessionUseCase(repository).invoke()
    }

    val windowState = remember(snapshotState.value) {
        ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(KoogAgentGateway()),
            snapshot = snapshotState.value,
            projectPath = projectRoot.toString(),
        )
    }

    MaterialTheme {
        ChatScreen(windowState)
    }
}
