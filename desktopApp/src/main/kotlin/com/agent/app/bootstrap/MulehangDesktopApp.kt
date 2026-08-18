package com.agent.app.bootstrap

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.WindowState
import com.agent.app.chat.component.ChatScreen
import com.agent.app.chat.component.ChatTitleBar
import com.agent.app.chat.persistence.TaskPersistenceCoordinator
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.IDEA_TITLE_BAR_HEIGHT
import com.agent.app.design.IDEA_TITLE_BAR_SEPARATOR_HEIGHT
import com.agent.app.design.MulehangTheme
import com.agent.app.design.desktopPalette
import com.agent.app.design.ideaFrameAmbientBackground
import com.agent.app.platform.BridgeWindowsTitleBarInputToCompose
import com.agent.app.platform.SuppressWindowsWindowBorder
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
import org.jetbrains.jewel.window.DecoratedWindow

/**
 * 根 composable，负责加载桌面会话快照并装配窗口状态。
 */
@Composable
internal fun MulehangDesktopApp(
    initialProjectRoot: Path?,
    desktopWindowState: WindowState,
    onCloseRequest: () -> Unit,
) {
    val userHome = remember { Paths.get(System.getProperty("user.home")) }
    val uiStateStore = remember { DesktopUiStateStore(userHome.resolve(".mulehang/ui-state.json")) }
    var themeMode by remember { mutableStateOf(DesktopThemeMode.fromStorage(uiStateStore.loadThemeMode())) }
    val projectRootState = remember {
        mutableStateOf(
            initialProjectRoot ?: uiStateStore.loadRecentWorkspace()
                ?.let(Paths::get)
                ?.let(DesktopProjectRootResolver::resolve),
        )
    }
    var sidebarVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var frameGradientAnchorPx by remember { mutableStateOf<Float?>(null) }
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
    val requestClose = remember(windowState, onCloseRequest) {
        { windowState.flushPersistence(onCloseRequest) }
    }

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

    val palette = desktopPalette(mode = themeMode, systemIsDark = isSystemInDarkTheme())
    MulehangTheme(isDark = palette.isDark, palette = palette) {
        DecoratedWindow(
            onCloseRequest = requestClose,
            state = desktopWindowState,
            title = "mulehang-agent",
        ) {
            SuppressWindowsWindowBorder(window = window, frameColor = palette.frameBackground)
            BridgeWindowsTitleBarInputToCompose(window = window)
            ChatTitleBar(
                state = windowState,
                projectRoot = projectRootState.value,
                sidebarVisible = sidebarVisible,
                onToggleSidebar = { sidebarVisible = !sidebarVisible },
                onOpenSettings = { settingsVisible = true },
                onRequestClose = requestClose,
                onGlobalFeedback = {},
                frameGradientAnchorPx = frameGradientAnchorPx,
                onFrameGradientAnchorChanged = { anchorPx -> frameGradientAnchorPx = anchorPx },
            )
            val contentModifier = if (palette.isDark) {
                val contentOriginYPx = with(LocalDensity.current) {
                    (
                        IDEA_TITLE_BAR_HEIGHT.roundToPx() +
                            IDEA_TITLE_BAR_SEPARATOR_HEIGHT.roundToPx()
                    ).toFloat()
                }
                Modifier
                    .fillMaxSize()
                    .ideaFrameAmbientBackground(
                        frameColor = palette.frameBackground,
                        projectColor = palette.titleBarGradientStart,
                        anchorXPx = frameGradientAnchorPx,
                        originYPx = contentOriginYPx,
                    )
            } else {
                Modifier.fillMaxSize()
            }
            Box(modifier = contentModifier) {
                ChatScreen(
                    state = windowState,
                    sidebarVisible = sidebarVisible,
                    onToggleSidebar = { sidebarVisible = !sidebarVisible },
                    projectRoot = projectRootState.value,
                    userHome = userHome,
                    themeMode = themeMode,
                    onThemeChanged = { updatedMode ->
                        themeMode = updatedMode
                        uiStateStore.saveThemeMode(updatedMode.storageValue)
                    },
                    onSettingsChanged = {
                        projectRootState.value?.let { root ->
                            appScope.launch {
                                val repository = DesktopAppSessionRepository(projectRoot = root, userHome = userHome)
                                windowState.updateSessionSnapshot(LoadAppSessionUseCase(repository).invoke())
                            }
                        }
                    },
                    settingsVisible = settingsVisible,
                    onSettingsVisibilityChange = { visible -> settingsVisible = visible },
                )
            }
        }
    }
}
