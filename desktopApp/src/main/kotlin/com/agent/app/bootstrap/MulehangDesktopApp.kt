package com.agent.app.bootstrap

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.agent.app.design.JewelDialog
import com.agent.app.design.loadDesktopFontCatalog
import com.agent.app.design.scaledFrameAmbientDensityScale
import com.agent.app.platform.BridgeWindowsTitleBarInputToCompose
import com.agent.app.platform.RegisterGlobalAppearanceShortcuts
import com.agent.app.platform.SuppressWindowsWindowBorder
import com.agent.app.platform.loadDesktopTerminalShellCatalog
import com.agent.app.tool.interaction.DesktopToolInteractionCoordinator
import com.agent.shared.agent.koog.KoogAgentGateway
import com.agent.shared.agent.koog.KoogBranchSummaryGenerator
import com.agent.shared.agent.koog.KoogConversationTitleGenerator
import com.agent.shared.agent.recording.JsonLinesAgentRunRecorder
import com.agent.shared.agent.recording.RecordingAgentGateway
import com.agent.shared.agent.resource.AgentResourceRuntime
import com.agent.shared.agent.resource.DesktopAgentResourceRequestFactory
import com.agent.shared.agent.resource.McpConnectionManager
import com.agent.shared.agent.resource.McpToolRegistryBridge
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
import org.jetbrains.jewel.ui.component.Text

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
    var projectTrustPrompt by remember { mutableStateOf<Path?>(null) }
    var projectTrustFeedback by remember { mutableStateOf<String?>(null) }
    var frameGradientAnchorPx by remember { mutableStateOf<Float?>(null) }
    val toolInteractionCoordinator = remember {
        DesktopToolInteractionCoordinator()
    }
    val agentResourceRuntime = remember { AgentResourceRuntime() }
    val mcpConnectionManager = remember { McpConnectionManager() }
    val mcpConnectionStatuses by mcpConnectionManager.statuses.collectAsState()
    val sessionMediaStore = remember(userHome) { DesktopSessionMediaStore(userHome) }
    val stateHolder = remember { mutableStateOf<ChatWindowState?>(null) }
    val appScope = rememberCoroutineScope()
    val taskPersistenceCoordinator = TaskPersistenceCoordinator(
        repository = remember { SqliteTaskRepository(userHome.resolve(".mulehang/tasks.db")) },
        scope = appScope,
        reportError = { message -> stateHolder.value?.setPersistenceError(message) },
    )
    val koogGateway = remember(toolInteractionCoordinator, mcpConnectionManager) {
        KoogAgentGateway(
            interactionBridge = toolInteractionCoordinator,
            mcpToolRegistryBridge = McpToolRegistryBridge(mcpConnectionManager),
        )
    }
    val windowState = remember {
        ChatWindowState(
            sendMessageUseCase = SendMessageUseCase(
                RecordingAgentGateway(
                    delegate = koogGateway,
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
            branchSummaryGenerator = KoogBranchSummaryGenerator(),
            conversationTitleGenerator = KoogConversationTitleGenerator(),
            resourceSnapshotProvider = { workspacePath ->
                resourceLoadRequest(userHome, workspacePath)?.let(agentResourceRuntime::snapshotFor)
            },
            resourceReloader = { workspacePath ->
                resourceLoadRequest(userHome, workspacePath)?.let { request ->
                    val candidate = agentResourceRuntime.prepareReload(request)
                    if (mcpConnectionManager.reload(candidate.toRuntimeResources().mcpServers)) {
                        agentResourceRuntime.publish(candidate)
                    } else {
                        null
                    }
                }
            },
            workspaceDirectoryExists = { path ->
                path.isNotBlank() && runCatching { Files.isDirectory(Paths.get(path)) }.getOrDefault(false)
            },
            sessionMediaStore = sessionMediaStore,
            onSessionClosed = koogGateway::endSession,
        )
    }
    stateHolder.value = windowState
    val requestClose = remember(windowState, koogGateway, onCloseRequest) {
        {
            windowState.cancelActiveRun()
            appScope.launch {
                windowState.ui.tasks.forEach { conversation ->
                    koogGateway.endSession(conversation.id, conversation.workspacePath)
                }
                koogGateway.shutdown()
                mcpConnectionManager.close()
                windowState.flushPersistence(onCloseRequest)
            }
            Unit
        }
    }

    LaunchedEffect(projectRootState.value) {
        projectRootState.value?.let { projectRoot ->
            uiStateStore.saveRecentWorkspace(projectRoot.toString())
            val repository = DesktopAppSessionRepository(projectRoot = projectRoot, userHome = userHome)
            windowState.updateSessionSnapshot(LoadAppSessionUseCase(repository).invoke())
            projectTrustPrompt = projectRoot.takeIf {
                shouldPromptForProjectTrust(projectRoot = it, projectTrusted = projectResourcesAreTrusted(userHome, it))
            }
        } ?: run {
            projectTrustPrompt = null
        }
        windowState.reloadAgentResources()
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
                        mcpConnectionStatuses = mcpConnectionStatuses,
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
                        globalFeedbackMessage = projectTrustFeedback,
                        onGlobalFeedbackConsumed = { projectTrustFeedback = null },
                        settingsVisible = settingsVisible,
                        onSettingsVisibilityChange = { visible -> settingsVisible = visible },
                    )
                    projectTrustPrompt?.let { projectRoot ->
                        JewelDialog(
                            title = "信任项目资源？",
                            confirmLabel = "信任并加载",
                            dismissLabel = "保持不信任",
                            onConfirm = {
                                val result = trustProjectResources(userHome, projectRoot)
                                projectTrustPrompt = null
                                projectTrustFeedback = result.fold(
                                    onSuccess = {
                                        windowState.refreshActiveResourceSnapshot()
                                        "已信任 ${projectTrustDisplayName(projectRoot)}，项目资源将在后续任务中生效。"
                                    },
                                    onFailure = { error ->
                                        "无法信任 ${projectTrustDisplayName(projectRoot)}：${error.message ?: "未知错误"}"
                                    },
                                )
                            },
                            onDismiss = {
                                projectTrustPrompt = null
                                projectTrustFeedback = "${projectTrustDisplayName(projectRoot)} 尚未信任，项目资源未加载。"
                            },
                        ) {
                            Text("此项目可包含 AGENTS/CLAUDE 指令、Skills、prompts、扩展包和 MCP 服务。信任后才会读取并加载这些项目资源。")
                        }
                    }
                }
            }
        }
    }
}

/** 判断当前用户级设置是否已显式信任目标工作区。 */
internal fun projectResourcesAreTrusted(
    userHome: Path,
    projectRoot: Path,
): Boolean = runCatching {
    val repository = DesktopSettingsRepository(
        pathResolver = DesktopPathResolver(userHome, projectRoot),
        environmentOverrides = DesktopEnvironmentOverrides(),
    )
    DesktopAgentResourceRequestFactory(userHome).create(
        workspacePath = projectRoot,
        userDocument = repository.loadDocument(ConfigLayer.USER),
        projectDocument = repository.loadDocument(ConfigLayer.PROJECT),
    ).projectTrusted
}.getOrDefault(false)

/** 仅在存在尚未信任的工作区时显示一次资源信任确认。 */
internal fun shouldPromptForProjectTrust(
    projectRoot: Path?,
    projectTrusted: Boolean,
): Boolean = projectRoot != null && !projectTrusted

/** 仅将项目路径写入用户级信任清单，项目自己的 settings 不能自行授权。 */
private fun trustProjectResources(
    userHome: Path,
    projectRoot: Path,
): Result<Unit> = runCatching {
    val repository = DesktopSettingsRepository(
        pathResolver = DesktopPathResolver(userHome, projectRoot),
        environmentOverrides = DesktopEnvironmentOverrides(),
    )
    val factory = DesktopAgentResourceRequestFactory(userHome)
    repository.saveDocument(
        ConfigLayer.USER,
        factory.withProjectTrust(
            userDocument = repository.loadDocument(ConfigLayer.USER),
            workspacePath = projectRoot,
            trusted = true,
        ),
    )
}

/** 为信任反馈生成紧凑且可识别的工作区名称。 */
private fun projectTrustDisplayName(projectRoot: Path): String = projectRoot.fileName?.toString() ?: projectRoot.toString()

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
