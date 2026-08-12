package com.agent.app.bootstrap

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.agent.app.chat.component.ChatScreen
import com.agent.app.chat.persistence.TaskPersistenceCoordinator
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppTypography
import com.agent.app.design.DesktopAccentColor
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.DesktopThemePaletteProvider
import com.agent.app.design.desktopColorScheme
import com.agent.app.design.desktopPalette
import com.agent.app.tool.interaction.DesktopToolInteractionCoordinator
import com.agent.shared.agent.koog.KoogAgentGateway
import com.agent.shared.agent.koog.KoogConversationTitleGenerator
import com.agent.shared.agent.recording.JsonLinesAgentRunRecorder
import com.agent.shared.agent.recording.RecordingAgentGateway
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.session.DesktopAppSessionRepository
import com.agent.shared.session.DesktopUiStateStore
import com.agent.shared.session.LoadAppSessionUseCase
import com.agent.shared.chat.persistence.SqliteTaskRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.launch

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
    val userHome = remember { Paths.get(System.getProperty("user.home")) }
    val uiStateStore = remember { DesktopUiStateStore(userHome.resolve(".mulehang/ui-state.json")) }
    var themeMode by remember { mutableStateOf(DesktopThemeMode.fromStorage(uiStateStore.loadThemeMode())) }
    var accentColor by remember { mutableStateOf(DesktopAccentColor.fromStorage(uiStateStore.loadAccentColor())) }
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
    val stateHolder = remember { mutableStateOf<ChatWindowState?>(null) }
    val appScope = rememberCoroutineScope()
    val taskPersistenceCoordinator = TaskPersistenceCoordinator(
        repository = remember { SqliteTaskRepository(userHome.resolve(".mulehang/tasks.db")) },
        scope = appScope,
        reportError = { message -> stateHolder.value?.setPersistenceError(message) },
    )
    val windowState = remember {
        ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(
                RecordingAgentGateway(
                    delegate = KoogAgentGateway(interactionBridge = toolInteractionCoordinator),
                    recorder = JsonLinesAgentRunRecorder(),
                ),
            ),
            snapshot = AppSessionSnapshot(profiles = emptyList(), activeProfile = null),
            projectPath = projectRootState.value?.toString().orEmpty(),
            toolInteractionCoordinator = toolInteractionCoordinator,
            onWorkspaceSelected = { workspacePath ->
                projectRootState.value = DesktopProjectRootResolver.resolve(Paths.get(workspacePath))
            },
            persistenceCoordinator = taskPersistenceCoordinator,
            conversationTitleGenerator = KoogConversationTitleGenerator(),
            workspaceDirectoryExists = { path ->
                path.isNotBlank() && runCatching { Files.isDirectory(Paths.get(path)) }.getOrDefault(false)
            },
        )
    }
    stateHolder.value = windowState

    LaunchedEffect(projectRootState.value) {
        val projectRoot = projectRootState.value ?: return@LaunchedEffect
        uiStateStore.saveRecentWorkspace(projectRoot.toString())
        val repository = DesktopAppSessionRepository(projectRoot = projectRoot, userHome = userHome)
        windowState.updateSessionSnapshot(LoadAppSessionUseCase(repository).invoke())
    }
    LaunchedEffect(Unit) {
        runCatching { taskPersistenceCoordinator.load() }
            .onSuccess { tasks ->
                windowState.restoreTasks(tasks)
                taskPersistenceCoordinator.activate(windowState.ui.tasks)
            }
            .onFailure { windowState.setPersistenceError("历史任务未加载") }
    }

    val palette = desktopPalette(
        mode = themeMode,
        accent = accentColor,
        systemIsDark = isSystemInDarkTheme(),
    )
    val nativeTitleBarHandle = rememberNativeWindowTitleBar(
        mode = windowChromeMode,
        controlsDark = palette.isDark,
        background = palette.headerBackground,
    )
    DesktopThemePaletteProvider(palette) {
        MaterialTheme(
            colorScheme = desktopColorScheme(palette),
            typography = AppTypography,
        ) {
            ChatScreen(
            state = windowState,
            desktopWindowState = desktopWindowState,
            windowChromeMode = windowChromeMode,
            onTitleBarClientPointerEvent = nativeTitleBarHandle?.let { handle ->
                { handle.forceClientArea() }
            },
            projectRoot = projectRootState.value,
            userHome = userHome,
            themeMode = themeMode,
            accentColor = accentColor,
            onThemeChanged = { updatedMode, updatedAccent ->
                themeMode = updatedMode
                accentColor = updatedAccent
                uiStateStore.saveThemeMode(updatedMode.storageValue)
                uiStateStore.saveAccentColor(updatedAccent.storageValue)
            },
            onSettingsChanged = {
                projectRootState.value?.let { root ->
                    appScope.launch {
                        val repository = DesktopAppSessionRepository(projectRoot = root, userHome = userHome)
                        windowState.updateSessionSnapshot(LoadAppSessionUseCase(repository).invoke())
                    }
                }
            },
                onCloseRequest = { windowState.flushPersistence(onCloseRequest) },
            )
        }
    }
}
