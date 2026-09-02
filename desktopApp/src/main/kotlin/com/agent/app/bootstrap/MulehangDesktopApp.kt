package com.agent.app.bootstrap

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
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
import com.agent.app.chat.media.DesktopSessionMediaStore
import com.agent.app.chat.persistence.TaskPersistenceCoordinator
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.DesktopAppearance
import com.agent.app.design.MulehangTheme
import com.agent.app.design.ProvideDesktopAppearance
import com.agent.app.design.adjustedDesktopUiScalePercent
import com.agent.app.design.desktopPalette
import com.agent.app.design.ideaFrameAmbientBackground
import com.agent.app.design.ideaTitleBarContentOriginPx
import com.agent.app.design.loadDesktopFontCatalog
import com.agent.app.design.scaledFrameAmbientDensityScale
import com.agent.app.platform.BridgeWindowsTitleBarInputToCompose
import com.agent.app.platform.RegisterGlobalAppearanceShortcuts
import com.agent.app.platform.SuppressWindowsWindowBorder
import com.agent.app.platform.loadDesktopTerminalShellCatalog
import com.agent.app.tool.interaction.DesktopToolInteractionCoordinator
import com.agent.shared.agent.koog.KoogAgentGateway
import com.agent.shared.agent.koog.KoogConversationTitleGenerator
import com.agent.shared.agent.recording.JsonLinesAgentRunRecorder
import com.agent.shared.agent.recording.RecordingAgentGateway
import com.agent.shared.agent.resource.AgentResourceRuntime
import com.agent.shared.agent.resource.DesktopAgentResourceRequestFactory
import com.agent.shared.chat.usecase.SendMessageUseCase
import com.agent.shared.session.AppSessionSnapshot
import com.agent.shared.session.DesktopAppSessionRepository
import com.agent.shared.session.DesktopAppearancePreferences
import com.agent.shared.session.DesktopTerminalPreferences
import com.agent.shared.session.DesktopUiStateStore
import com.agent.shared.session.LoadAppSessionUseCase
import com.agent.shared.chat.persistence.SqliteTaskRepository
import com.agent.shared.settings.model.ConfigLayer
import com.agent.shared.settings.model.SettingsDocument
import com.agent.shared.settings.persistence.DesktopEnvironmentOverrides
import com.agent.shared.settings.persistence.DesktopPathResolver
import com.agent.shared.settings.persistence.DesktopSettingsRepository
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
    val fontCatalog = remember { loadDesktopFontCatalog() }
    var appearancePreferences by remember { mutableStateOf(uiStateStore.loadAppearancePreferences()) }
    val terminalShellCatalog = remember { loadDesktopTerminalShellCatalog() }
    var terminalPreferences by remember { mutableStateOf(uiStateStore.loadTerminalPreferences()) }
    val appearance = remember(appearancePreferences, fontCatalog) {
        DesktopAppearance(
            preferences = appearancePreferences,
            fontCatalog = fontCatalog,
        )
    }
    val updateAppearancePreferences = remember {
        { updatedPreferences: DesktopAppearancePreferences ->
            appearancePreferences = updatedPreferences.normalized()
        }
    }
    val persistAppearancePreferences = remember(uiStateStore) {
        { updatedPreferences: DesktopAppearancePreferences ->
            uiStateStore.saveAppearancePreferences(updatedPreferences.normalized())
        }
    }
    val applyAndPersistAppearancePreferences = remember(uiStateStore) {
        { updatedPreferences: DesktopAppearancePreferences ->
            val normalizedPreferences = updatedPreferences.normalized()
            appearancePreferences = normalizedPreferences
            uiStateStore.saveAppearancePreferences(normalizedPreferences)
        }
    }
    val applyAndPersistTerminalPreferences = remember(uiStateStore) {
        { updatedPreferences: DesktopTerminalPreferences ->
            val normalizedPreferences = updatedPreferences.normalized()
            terminalPreferences = normalizedPreferences
            uiStateStore.saveTerminalPreferences(normalizedPreferences)
        }
    }
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
    val agentResourceRuntime = remember { AgentResourceRuntime() }
    val sessionMediaStore = remember(userHome) { DesktopSessionMediaStore(userHome) }
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
            resourceSnapshotProvider = { workspacePath ->
                resourceLoadRequest(userHome, workspacePath)?.let(agentResourceRuntime::snapshotFor)
            },
            resourceReloader = { workspacePath ->
                resourceLoadRequest(userHome, workspacePath)?.let(agentResourceRuntime::reload)
            },
            workspaceDirectoryExists = { path ->
                path.isNotBlank() && runCatching { Files.isDirectory(Paths.get(path)) }.getOrDefault(false)
            },
            sessionMediaStore = sessionMediaStore,
        )
    }
    stateHolder.value = windowState
    val requestClose = remember(windowState, onCloseRequest) {
        { windowState.flushPersistence(onCloseRequest) }
    }

    LaunchedEffect(projectRootState.value) {
        projectRootState.value?.let { projectRoot ->
            uiStateStore.saveRecentWorkspace(projectRoot.toString())
            val repository = DesktopAppSessionRepository(projectRoot = projectRoot, userHome = userHome)
            windowState.updateSessionSnapshot(LoadAppSessionUseCase(repository).invoke())
        }
        windowState.refreshActiveResourceSnapshot()
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
    MulehangTheme(
        isDark = palette.isDark,
        palette = palette,
        titleBarScalePercent = appearance.preferences.scalePercent,
    ) {
        DecoratedWindow(
            onCloseRequest = requestClose,
            state = desktopWindowState,
            title = "mulehang-agent",
        ) {
            val nativeTitleBarDensity = LocalDensity.current
            val contentOriginYPx = ideaTitleBarContentOriginPx(
                baseDensity = nativeTitleBarDensity,
                scalePercent = appearance.preferences.scalePercent,
            )
            val frameAmbientDensityScale = scaledFrameAmbientDensityScale(
                baseDensity = nativeTitleBarDensity,
                scalePercent = appearance.preferences.scalePercent,
            )
            SuppressWindowsWindowBorder(window = window, frameColor = palette.frameBackground)
            BridgeWindowsTitleBarInputToCompose(window = window)
            RegisterGlobalAppearanceShortcuts(
                window = window,
                onIncrease = {
                    applyAndPersistAppearancePreferences(
                        appearancePreferences.copy(
                            scalePercent = adjustedDesktopUiScalePercent(appearancePreferences.scalePercent, 1),
                        ),
                    )
                },
                onDecrease = {
                    applyAndPersistAppearancePreferences(
                        appearancePreferences.copy(
                            scalePercent = adjustedDesktopUiScalePercent(appearancePreferences.scalePercent, -1),
                        ),
                    )
                },
            )
            ChatTitleBar(
                state = windowState,
                projectRoot = projectRootState.value,
                appearance = appearance,
                sidebarVisible = sidebarVisible,
                onSidebarVisibilityChange = { visible -> sidebarVisible = visible },
                onOpenSettings = { settingsVisible = true },
                onRequestClose = requestClose,
                onGlobalFeedback = {},
                frameGradientAnchorPx = frameGradientAnchorPx,
                frameAmbientDensityScale = frameAmbientDensityScale,
                onFrameGradientAnchorChanged = { anchorPx -> frameGradientAnchorPx = anchorPx },
            )
            ProvideDesktopAppearance(appearance = appearance) {
                val contentModifier = if (palette.isDark) {
                    Modifier
                        .fillMaxSize()
                        .ideaFrameAmbientBackground(
                            frameColor = palette.frameBackground,
                            projectColor = palette.titleBarGradientStart,
                            anchorXPx = frameGradientAnchorPx,
                            originYPx = contentOriginYPx,
                            canvasDensityScale = frameAmbientDensityScale,
                        )
                } else {
                    Modifier
                        .fillMaxSize()
                        .background(palette.background)
                }
                Box(modifier = contentModifier) {
                    ChatScreen(
                        state = windowState,
                        sidebarVisible = sidebarVisible,
                        onSidebarVisibilityChange = { visible -> sidebarVisible = visible },
                        projectRoot = projectRootState.value,
                        userHome = userHome,
                        themeMode = themeMode,
                        onThemeChanged = { updatedMode ->
                            themeMode = updatedMode
                            uiStateStore.saveThemeMode(updatedMode.storageValue)
                        },
                        appearance = appearance,
                        onAppearanceChanged = updateAppearancePreferences,
                        onAppearanceChangeFinished = persistAppearancePreferences,
                        terminalPreferences = terminalPreferences,
                        terminalShellCatalog = terminalShellCatalog,
                        onTerminalPreferencesChanged = applyAndPersistTerminalPreferences,
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
}

/** 读取原始用户/项目 settings 后构造资源加载请求；仅显式 reload 才重新读取并发布新快照。 */
private fun resourceLoadRequest(
    userHome: Path,
    workspacePath: String,
) = runCatching {
    val workspace = workspacePath.trim().takeIf(String::isNotBlank)?.let(Paths::get)
    val repository = DesktopSettingsRepository(
        pathResolver = DesktopPathResolver(userHome, workspace ?: userHome),
        environmentOverrides = DesktopEnvironmentOverrides(),
    )
    DesktopAgentResourceRequestFactory(userHome).create(
        workspacePath = workspace,
        userDocument = repository.loadDocument(ConfigLayer.USER),
        projectDocument = workspace?.let { repository.loadDocument(ConfigLayer.PROJECT) } ?: SettingsDocument(),
    )
}.getOrNull()
