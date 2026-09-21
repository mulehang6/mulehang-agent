package com.agent.app.chat.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agent.app.chat.state.ChatWindowState
import com.agent.app.design.AppMuted
import com.agent.app.design.DesktopAppearance
import com.agent.app.design.DesktopThemeMode
import com.agent.app.platform.TerminalShellCatalog
import com.agent.shared.agent.resource.AgentExtensionPackageResource
import com.agent.shared.agent.resource.AgentMcpServerResource
import com.agent.shared.agent.resource.AgentResourceDiagnostic
import com.agent.shared.agent.resource.AgentSkillResource
import com.agent.shared.agent.resource.McpServerConnectionStatus
import com.agent.shared.session.DesktopAppearancePreferences
import com.agent.shared.session.DesktopTerminalPreferences
import com.agent.shared.settings.persistence.DesktopSettingsRepository
import java.nio.file.Path
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar

/** 复用宽窄布局共用的设置内容、滚动区和保存动作。 */
@Composable
internal fun SettingsPanelContent(
    chatState: ChatWindowState,
    uiState: SettingsPanelUiState,
    repository: DesktopSettingsRepository,
    themeMode: DesktopThemeMode,
    onThemeChanged: (DesktopThemeMode) -> Unit,
    appearance: DesktopAppearance,
    onAppearanceChanged: (DesktopAppearancePreferences) -> Unit,
    onAppearanceChangeFinished: (DesktopAppearancePreferences) -> Unit,
    terminalPreferences: DesktopTerminalPreferences,
    terminalShellCatalog: TerminalShellCatalog,
    onTerminalPreferencesChanged: (DesktopTerminalPreferences) -> Unit,
    onSettingsSaved: () -> Unit,
    onReloadResources: suspend () -> Boolean,
    canReloadResources: Boolean,
    projectRoot: Path?,
    userHome: Path,
    extensionPackages: List<AgentExtensionPackageResource>,
    loadedSkills: List<AgentSkillResource>,
    resourceDiagnostics: List<AgentResourceDiagnostic>,
    mcpServers: List<AgentMcpServerResource>,
    mcpConnectionStatuses: List<McpServerConnectionStatus>,
    compact: Boolean,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val triggerResourceReload: () -> Unit = {
        scope.launch {
            if (onReloadResources()) {
                uiState.resourceReloadPending = false
                uiState.mcpReloadPending = false
                uiState.feedback = "资源已重新加载；当前运行不会改变。"
            } else {
                uiState.feedback = "资源重新加载失败，当前运行时保持不变。"
            }
        }
    }
    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = if (shouldShowSettingsContentScrollbar(scrollState.maxValue)) 10.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CompositionLocalProvider(LocalSettingsCompact provides compact) {
                    when (uiState.section) {
                        SettingsSection.APPEARANCE -> when (uiState.appearanceSubsection) {
                            AppearanceSubsection.OVERVIEW -> AppearanceSettingsContent(
                                appearance = appearance,
                                compact = compact,
                                onPreferencesChanged = onAppearanceChanged,
                                onPreferencesChangeFinished = onAppearanceChangeFinished,
                            )

                            AppearanceSubsection.THEME -> ThemeSettingsContent(
                                themeMode = themeMode,
                                compact = compact,
                                onThemeChanged = onThemeChanged,
                            )
                        }

                        SettingsSection.TOOLS -> ToolsSettingsContent(
                            preferences = terminalPreferences,
                            shellCatalog = terminalShellCatalog,
                            compact = compact,
                            onPreferencesChanged = onTerminalPreferencesChanged,
                        )

                        SettingsSection.PROVIDERS -> ProviderSettingsContent(
                            document = uiState.document,
                            search = uiState.search,
                            expandedProviderId = uiState.expandedProviderId,
                            onExpandedProviderChange = { uiState.expandedProviderId = it },
                            onDocumentChange = { uiState.document = it },
                            onChangeNotification = { message ->
                                uiState.changeNotifications.record(
                                    SettingsChangeNotificationCategory.AI_SERVICES,
                                    "${settingsChangeScopeLabel(uiState.layer)}：$message",
                                )
                            },
                            onProviderFieldsChanged = { uiState.providerFieldsChangedSinceLastSave = true },
                            onValidationErrorChange = { key, error ->
                                if (error == null) uiState.settingsValidationErrors.remove(key)
                                else uiState.settingsValidationErrors[key] = error
                            },
                            onValidationErrorsRenamed = { oldPrefix, newPrefix ->
                                renameSettingsValidationErrors(uiState.settingsValidationErrors, oldPrefix, newPrefix)
                            },
                            onValidationErrorsCleared = { prefix ->
                                uiState.settingsValidationErrors.keys
                                    .filter { key -> key == prefix || key.startsWith("$prefix:") }
                                    .toList()
                                    .forEach(uiState.settingsValidationErrors::remove)
                            },
                        )

                        SettingsSection.EXTENSIONS -> ExtensionSettingsContent(
                            subsection = uiState.extensionSubsection,
                            document = uiState.document,
                            layer = uiState.layer,
                            projectRoot = projectRoot,
                            userHome = userHome,
                            extensionPackages = extensionPackages,
                            loadedSkills = loadedSkills,
                            resourceDiagnostics = resourceDiagnostics,
                            mcpServers = mcpServers,
                            savedMcpServers = uiState.lastSavedDocument.agentResources.mcpServers,
                            savedHooks = uiState.lastSavedDocument.hooks,
                            mcpConnectionStatuses = mcpConnectionStatuses,
                            mcpJsonEditorState = uiState.mcpJsonEditorState,
                            mcpReloadPending = uiState.mcpReloadPending,
                            mcpRetryEnabled = canReloadResources,
                            onRetryMcp = triggerResourceReload,
                            onSaveMcp = { persistSettingsArea(uiState, repository, SettingsSaveArea.MCP, onSettingsSaved) },
                            onSaveHooks = { persistSettingsArea(uiState, repository, SettingsSaveArea.HOOKS, onSettingsSaved) },
                            onSaveExtensions = {
                                persistSettingsArea(uiState, repository, SettingsSaveArea.EXTENSIONS, onSettingsSaved)
                            },
                            onDocumentChange = { uiState.document = it },
                            onChangeNotification = { message ->
                                uiState.changeNotifications.record(
                                    SettingsChangeNotificationCategory.EXTENSIONS,
                                    "${settingsChangeScopeLabel(uiState.layer)}：$message",
                                )
                            },
                            onValidationErrorChange = { key, error ->
                                if (error == null) uiState.settingsValidationErrors.remove(key)
                                else uiState.settingsValidationErrors[key] = error
                            },
                            onResourceFilesChanged = { uiState.resourceReloadPending = true },
                        )

                        SettingsSection.SESSIONS -> ArchivedSessionsSettingsContent(
                            state = chatState,
                            search = uiState.search,
                        )
                    }
                }
            }
            if (shouldShowSettingsContentScrollbar(scrollState.maxValue)) {
                VerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                )
            }
        }
        if (uiState.section == SettingsSection.EXTENSIONS) {
            uiState.feedback?.let { feedback ->
                Text(
                    feedback,
                    modifier = Modifier.padding(top = 12.dp),
                    style = JewelTheme.defaultTextStyle.copy(color = AppMuted),
                )
            }
        }
        if (uiState.section == SettingsSection.EXTENSIONS && uiState.resourceReloadPending) {
            ResourceReloadBanner(
                reloadEnabled = canReloadResources,
                onReload = triggerResourceReload,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (uiState.section == SettingsSection.PROVIDERS) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsActionButton("保存", emphasized = true) {
                    persistSettingsArea(uiState, repository, SettingsSaveArea.PROVIDERS, onSettingsSaved)
                }
                uiState.feedback?.let { Text(it, style = JewelTheme.defaultTextStyle.copy(color = AppMuted)) }
            }
        }
    }
}
