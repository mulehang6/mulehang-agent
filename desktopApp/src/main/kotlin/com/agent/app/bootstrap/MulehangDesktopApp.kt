package com.agent.app.bootstrap

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.agent.app.chat.component.ChatScreen
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppAccent
import com.agent.app.design.AppBackground
import com.agent.app.design.AppChipBackground
import com.agent.app.design.AppDanger
import com.agent.app.design.AppMuted
import com.agent.app.design.AppSidebarBackground
import com.agent.app.design.AppSuccess
import com.agent.app.design.AppText
import com.agent.app.tool.interaction.DesktopToolInteractionCoordinator
import com.agent.shared.agent.koog.KoogAgentGateway
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.session.DesktopAppSessionRepository
import com.agent.shared.session.DesktopUiStateStore
import com.agent.shared.session.LoadAppSessionUseCase
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 根 composable，负责加载桌面会话快照并装配窗口状态。
 */
@Composable
internal fun WindowScope.MulehangDesktopApp(
    initialProjectRoot: Path?,
    desktopWindowState: WindowState,
    windowChromeMode: WindowChromeMode,
    onCloseRequest: () -> Unit,
) {
    val nativeTitleBarHandle = rememberNativeWindowTitleBar(windowChromeMode)
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

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = AppBackground,
            surface = AppSidebarBackground,
            surfaceVariant = AppChipBackground,
            primary = AppAccent,
            secondary = AppSuccess,
            error = AppDanger,
            onBackground = AppText,
            onSurface = AppText,
            onSurfaceVariant = AppMuted,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onError = Color.White,
        ),
    ) {
        ChatScreen(
            state = windowState,
            desktopWindowState = desktopWindowState,
            windowChromeMode = windowChromeMode,
            onTitleBarClientPointerEvent = nativeTitleBarHandle?.let { handle ->
                { handle.forceClientArea() }
            },
            onCloseRequest = onCloseRequest,
        )
    }
}
