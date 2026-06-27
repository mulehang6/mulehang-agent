package com.agent.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.agent.app.ui.ChatScreen
import com.agent.app.ui.ChatWindowState
import com.agent.app.ui.DesktopToolInteractionCoordinator
import com.agent.shared.agent.KoogAgentGateway
import com.agent.shared.application.AppSessionSnapshot
import com.agent.shared.application.DesktopAppSessionRepository
import com.agent.shared.application.LoadAppSessionUseCase
import com.agent.shared.application.SendMessageUseCase
import com.agent.shared.state.DesktopUiStateStore
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 根 composable，负责加载桌面会话快照并装配窗口状态。
 */
@Composable
fun MulehangDesktopApp(initialProjectRoot: Path?) {
    val userHome = remember { Paths.get(System.getProperty("user.home")) }
    val uiStateStore = remember { DesktopUiStateStore(userHome.resolve(".mulehang/ui-state.json")) }
    val projectRootState = remember {
        mutableStateOf(
            initialProjectRoot ?: uiStateStore.loadRecentWorkspace()
                ?.let(Paths::get)
                ?.let(DesktopProjectRootResolver::resolve),
        )
    }
    val toolInteractionCoordinator = remember {
        DesktopToolInteractionCoordinator()
    }
    val windowState = remember {
        ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(
                KoogAgentGateway(interactionBridge = toolInteractionCoordinator),
            ),
            snapshot = AppSessionSnapshot(profiles = emptyList(), activeProfile = null),
            projectPath = projectRootState.value?.toString().orEmpty(),
            toolInteractionCoordinator = toolInteractionCoordinator,
            onWorkspaceSelected = { workspacePath ->
                projectRootState.value = DesktopProjectRootResolver.resolve(Paths.get(workspacePath))
            },
        )
    }

    LaunchedEffect(projectRootState.value) {
        val projectRoot = projectRootState.value ?: return@LaunchedEffect
        uiStateStore.saveRecentWorkspace(projectRoot.toString())
        val repository = DesktopAppSessionRepository(projectRoot = projectRoot, userHome = userHome)
        windowState.updateSessionSnapshot(LoadAppSessionUseCase(repository).invoke())
    }

    MaterialTheme {
        ChatScreen(windowState)
    }
}
