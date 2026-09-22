@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.agent.app.chat.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatWindowState
import com.agent.shared.agent.resource.McpServerConnectionStatus
import com.agent.app.design.DesktopAppearance
import com.agent.app.design.DesktopThemeMode
import com.agent.app.design.LocalDesktopPalette
import com.agent.app.design.RightRailGlyph
import com.agent.app.platform.TerminalShellCatalog
import com.agent.shared.session.DesktopAppearancePreferences
import com.agent.shared.session.DesktopTerminalPreferences
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import java.nio.file.Path

internal const val SIDEBAR_VISIBLE_BY_DEFAULT = false

/** 当前获得输入焦点的工作区 Island。 */
internal enum class WorkspaceIslandFocus {
    NONE,
    CHAT,
    NOTIFICATIONS,
    SETTINGS,
    TERMINAL,
    CONVERSATION_TREE,
}

/** 点击 Island 外区域后清除右侧 Island 焦点。 */
internal fun workspaceFocusAfterExternalPress(): WorkspaceIslandFocus = WorkspaceIslandFocus.NONE

/** 关闭一个 Island 后，将焦点交给仍可见的另一 Island 或聊天区。 */
internal fun workspaceFocusAfterPanelClosed(
    upperTool: UpperRightTool?,
    lowerTool: LowerRightTool?,
): WorkspaceIslandFocus = when {
    lowerTool == LowerRightTool.CONVERSATION_TREE -> WorkspaceIslandFocus.CONVERSATION_TREE
    lowerTool == LowerRightTool.TERMINAL -> WorkspaceIslandFocus.TERMINAL
    upperTool == UpperRightTool.SETTINGS -> WorkspaceIslandFocus.SETTINGS
    upperTool == UpperRightTool.NOTIFICATIONS -> WorkspaceIslandFocus.NOTIFICATIONS
    else -> WorkspaceIslandFocus.CHAT
}

/**
 * 按原型重构后的桌面主界面。
 */
@Composable
internal fun ChatScreen(
    state: ChatWindowState,
    mcpConnectionStatuses: List<McpServerConnectionStatus>,
    sidebarVisible: Boolean,
    onSidebarVisibilityChange: (Boolean) -> Unit,
    projectRoot: Path?,
    userHome: Path,
    themeMode: DesktopThemeMode,
    onThemeChanged: (DesktopThemeMode) -> Unit,
    appearance: DesktopAppearance,
    onAppearanceChanged: (DesktopAppearancePreferences) -> Unit,
    onAppearanceChangeFinished: (DesktopAppearancePreferences) -> Unit,
    terminalPreferences: DesktopTerminalPreferences,
    terminalShellCatalog: TerminalShellCatalog,
    onTerminalPreferencesChanged: (DesktopTerminalPreferences) -> Unit,
    onSettingsChanged: () -> Unit,
    globalFeedbackMessage: String? = null,
    onGlobalFeedbackConsumed: () -> Unit = {},
    settingsVisible: Boolean = false,
    onSettingsVisibilityChange: (Boolean) -> Unit = {},
) {
    val palette = LocalDesktopPalette.current
    val resolvedCodeFont = appearance.codeFont
    val terminalAppearance =
        remember(resolvedCodeFont.effectiveAwtFontFamilyName, appearance.preferences.scalePercent) {
            TerminalAppearance(
                codeFontFamily = resolvedCodeFont.effectiveAwtFontFamilyName,
                scalePercent = appearance.preferences.scalePercent,
            )
        }
    val terminalShell = remember(terminalShellCatalog, terminalPreferences) {
        terminalShellCatalog.resolve(terminalPreferences)
    }
    val terminalPanel = rememberTerminalPanelController(
        palette = palette.terminal,
        appearance = terminalAppearance,
        launchCommand = terminalShell.descriptor.launchCommand(),
    )
    var appFeedback by remember { mutableStateOf<AppFeedbackState?>(null) }
    var appFeedbackToken by remember { mutableStateOf(0L) }
    var islandFocus by remember { mutableStateOf(WorkspaceIslandFocus.NONE) }
    var rightTools by remember {
        mutableStateOf(
            RightToolSelection(
                upper = UpperRightTool.SETTINGS.takeIf { settingsVisible },
            ),
        )
    }
    val settingsUiState = remember { SettingsPanelUiState() }
    val showAppFeedback: (AppFeedbackState) -> Unit = { feedback ->
        appFeedbackToken = nextAppFeedbackToken(appFeedbackToken)
        appFeedback = feedback.copy(token = appFeedbackToken)
    }
    val activeConversation = state.ui.activeConversationOrNull

    LaunchedEffect(appFeedback?.token) {
        if (appFeedback != null) {
            delay(2.4.seconds)
            appFeedback = null
        }
    }

    LaunchedEffect(globalFeedbackMessage) {
        globalFeedbackMessage?.takeIf(String::isNotBlank)?.let { message ->
            showAppFeedback(AppFeedbackState(message = message, anchor = null))
            onGlobalFeedbackConsumed()
        }
    }

    LaunchedEffect(terminalPanel.visible) {
        terminalPanel.closePendingTabAfterExit()
        if (
            !terminalPanel.visible &&
            !terminalPanel.tabs.hasActiveTab() &&
            rightTools.lower == LowerRightTool.TERMINAL
        ) {
            rightTools = rightTools.copy(lower = null)
            islandFocus = workspaceFocusAfterPanelClosed(rightTools.upper, null)
        }
    }

    LaunchedEffect(settingsVisible) {
        if (settingsVisible) {
            rightTools = rightTools.copy(upper = UpperRightTool.SETTINGS)
            islandFocus = WorkspaceIslandFocus.SETTINGS
        } else if (rightTools.upper == UpperRightTool.SETTINGS) {
            rightTools = rightTools.copy(upper = null)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = isCompactDesktopLayout(maxWidth.value.toInt())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPointerEvent(
                    eventType = PointerEventType.Press,
                ) {
                    islandFocus = workspaceFocusAfterExternalPress()
                }
                .onPointerEvent(PointerEventType.Move) { event ->
                    val pointerPosition = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    appFeedback?.takeIf { feedback -> feedback.anchor != null }?.let { feedback ->
                        appFeedback = feedback.copy(anchor = feedbackToastAnchor(pointerPosition))
                    }
                },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    TaskSidebarSplitLayout(
                        visible = sidebarVisible,
                        compact = compact,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = ISLANDS_LAYOUT_GAP),
                        sidebar = { sidebarModifier ->
                            TaskSidebarIsland(
                                state = state,
                                compact = compact,
                                onCollapse = { onSidebarVisibilityChange(false) },
                                modifier = sidebarModifier,
                            )
                        },
                        workspace = { workspaceModifier ->
                            WorkspacePanel(
                                state = state,
                                activeRailView = RightRailGlyph.CODE,
                                filterToolActivityOnly = false,
                                terminalTabs = terminalPanel.tabs,
                                terminalPanelVisible = terminalPanel.visible,
                                terminalSessions = terminalPanel.sessions,
                                onSelectTerminalTab = terminalPanel::select,
                                onAddTerminalTab = { activeConversation?.let { terminalPanel.add(it.workspacePath) } },
                                onCloseTerminalTab = terminalPanel::close,
                                onCloseOtherTerminalTabs = terminalPanel::closeOthers,
                                onHideTerminalPanel = {
                                    terminalPanel.hide()
                                    rightTools = rightTools.copy(lower = null)
                                    islandFocus = workspaceFocusAfterPanelClosed(
                                        upperTool = rightTools.upper,
                                        lowerTool = null,
                                    )
                                },
                                sidePanelVisible = rightTools.visible,
                                sidePanel = { sideModifier, onRevealConversationEntry ->
                                    val upper = @Composable { upperModifier: Modifier ->
                                        when (rightTools.upper) {
                                            UpperRightTool.NOTIFICATIONS -> NotificationsPanel(
                                                notifications = settingsUiState.changeNotifications,
                                                onClose = {
                                                    rightTools = rightTools.copy(upper = null)
                                                    islandFocus = workspaceFocusAfterPanelClosed(null, rightTools.lower)
                                                },
                                                modifier = upperModifier,
                                            )

                                            UpperRightTool.SETTINGS -> SettingsPanel(
                                                chatState = state,
                                                projectRoot = projectRoot,
                                                userHome = userHome,
                                                themeMode = themeMode,
                                                onThemeChanged = onThemeChanged,
                                                appearance = appearance,
                                                onAppearanceChanged = onAppearanceChanged,
                                                onAppearanceChangeFinished = onAppearanceChangeFinished,
                                                terminalPreferences = terminalPreferences,
                                                terminalShellCatalog = terminalShellCatalog,
                                                onTerminalPreferencesChanged = onTerminalPreferencesChanged,
                                                onFocus = { islandFocus = WorkspaceIslandFocus.SETTINGS },
                                                onClose = {
                                                    rightTools = rightTools.copy(upper = null)
                                                    onSettingsVisibilityChange(false)
                                                    islandFocus = workspaceFocusAfterPanelClosed(null, rightTools.lower)
                                                },
                                                onSettingsSaved = onSettingsChanged,
                                                onReloadResources = state::reloadAgentResources,
                                                canReloadResources = state.canReloadAgentResources,
                                                extensionPackages = state.extensionPackages,
                                                loadedSkills = state.loadedSkills,
                                                resourceDiagnostics = state.resourceDiagnostics,
                                                mcpServers = state.mcpServers,
                                                mcpConnectionStatuses = mcpConnectionStatuses,
                                                uiState = settingsUiState,
                                                modifier = upperModifier,
                                            )

                                            null -> Box(upperModifier)
                                        }
                                    }
                                    val lower = @Composable { lowerModifier: Modifier ->
                                        when (rightTools.lower) {
                                            LowerRightTool.TERMINAL -> EmbeddedTerminalPanel(
                                                tabs = terminalPanel.tabs,
                                                sessions = terminalPanel.sessions,
                                                onSelectTab = terminalPanel::select,
                                                onAddTab = { activeConversation?.let { terminalPanel.add(it.workspacePath) } },
                                                onCloseTab = terminalPanel::close,
                                                onCloseOtherTabs = terminalPanel::closeOthers,
                                                onHidePanel = {
                                                    terminalPanel.hide()
                                                    rightTools = rightTools.copy(lower = null)
                                                    islandFocus = workspaceFocusAfterPanelClosed(rightTools.upper, null)
                                                },
                                                onFocus = { islandFocus = WorkspaceIslandFocus.TERMINAL },
                                                modifier = lowerModifier,
                                            )

                                            LowerRightTool.CONVERSATION_TREE -> ConversationTreePanel(
                                                state = state,
                                                conversation = activeConversation,
                                                onRevealEntry = onRevealConversationEntry,
                                                onClose = {
                                                    rightTools = rightTools.copy(lower = null)
                                                    islandFocus = workspaceFocusAfterPanelClosed(rightTools.upper, null)
                                                },
                                                modifier = lowerModifier,
                                            )

                                            null -> Box(lowerModifier)
                                        }
                                    }
                                    RightToolStackLayout(
                                        upperVisible = rightTools.upper != null,
                                        lowerVisible = rightTools.lower != null,
                                        modifier = sideModifier,
                                        upper = upper,
                                        lower = lower,
                                    )
                                },
                                compact = compact,
                                modifier = workspaceModifier,
                            )
                        },
                    )
                    if (!compact) {
                        ToolRail(
                            selectedGlyphs = rightTools.selectedGlyphs,
                            notificationsUnread = settingsUiState.changeNotifications.hasUnreadEntries,
                            onToolClick = { glyph ->
                                val upperTool = glyph.toUpperRightTool()
                                if (upperTool != null) {
                                    val next = rightTools.toggle(upperTool)
                                    rightTools = next
                                    onSettingsVisibilityChange(next.upper == UpperRightTool.SETTINGS)
                                    if (next.upper == UpperRightTool.NOTIFICATIONS) {
                                        settingsUiState.changeNotifications.markAllRead()
                                    }
                                    islandFocus = if (next.upper == upperTool) {
                                        if (upperTool == UpperRightTool.SETTINGS) {
                                            WorkspaceIslandFocus.SETTINGS
                                        } else {
                                            WorkspaceIslandFocus.NOTIFICATIONS
                                        }
                                    } else {
                                        workspaceFocusAfterPanelClosed(next.upper, next.lower)
                                    }
                                } else {
                                    when (glyph.toLowerRightTool()) {
                                        LowerRightTool.TERMINAL -> {
                                            if (rightTools.lower == LowerRightTool.TERMINAL) {
                                                terminalPanel.hide()
                                                rightTools = rightTools.copy(lower = null)
                                                islandFocus = workspaceFocusAfterPanelClosed(rightTools.upper, null)
                                            } else if (activeConversation == null) {
                                                showAppFeedback(
                                                    AppFeedbackState(message = "请先选择工作区", anchor = null),
                                                )
                                            } else {
                                                if (!terminalPanel.visible) {
                                                    terminalPanel.toggleFromRail(activeConversation.workspacePath)
                                                }
                                                rightTools = rightTools.copy(lower = LowerRightTool.TERMINAL)
                                                islandFocus = WorkspaceIslandFocus.TERMINAL
                                                appFeedback = null
                                            }
                                        }

                                        LowerRightTool.CONVERSATION_TREE -> {
                                            if (rightTools.lower == LowerRightTool.CONVERSATION_TREE) {
                                                rightTools = rightTools.copy(lower = null)
                                                islandFocus = workspaceFocusAfterPanelClosed(rightTools.upper, null)
                                            } else {
                                                terminalPanel.hide()
                                                rightTools = rightTools.copy(lower = LowerRightTool.CONVERSATION_TREE)
                                                islandFocus = WorkspaceIslandFocus.CONVERSATION_TREE
                                            }
                                        }

                                        null -> Unit
                                    }
                                }
                            },
                            modifier = Modifier
                                .width(TOOL_RAIL_WIDTH_DP.dp)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
        appFeedback?.let { feedback ->
            AppFeedbackOverlay(feedback)
        }
        SettingsChangeNotificationOverlay(
            notifications = settingsUiState.changeNotifications,
            notificationsPageVisible = rightTools.upper == UpperRightTool.NOTIFICATIONS,
        )
    }
}

/**
 * 判断窗口是否应采用收敛侧栏与工具区的紧凑布局。
 */
internal fun isCompactDesktopLayout(widthDp: Int): Boolean = widthDp < 980

/** 返回任务 Island 当前布局级别下的默认分栏宽度。 */
internal fun airSidebarWidthDp(compact: Boolean): Int = taskSidebarDefaultWidthDp(compact)

/**
 * 解析右侧工具栏当前应高亮的按钮。
 */
internal fun resolveActiveRailGlyph(
    activeRailView: RightRailGlyph,
    filterToolActivityOnly: Boolean,
    terminalVisible: Boolean,
): RightRailGlyph = when {
    terminalVisible -> RightRailGlyph.TERMINAL
    filterToolActivityOnly -> RightRailGlyph.FILTER
    else -> activeRailView
}
